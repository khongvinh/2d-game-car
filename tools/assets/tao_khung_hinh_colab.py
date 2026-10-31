# ============================================================
# TẠO KHUNG HÌNH CHẠY (HOẠT HÌNH THẬT) CHO "ĐUA XE SINH TỒN"
# Chạy SAU tao_asset_colab_v2.py (cần ảnh gốc bản 2 của từng nhân vật).
# Cách làm: script tự dựng bộ xương OpenPose cho 8 tư thế của một vòng chạy
# (nhìn ngang, quay phải), rồi SDXL + ControlNet OpenPose vẽ nhân vật theo
# từng tư thế; IP-Adapter lấy ảnh gốc làm mẫu để mọi khung giữ cùng mặt,
# áo quần, màu sắc. Cùng seed cho mọi khung. Xóa phông, rồi cắt TẤT CẢ khung
# của một nhân vật theo CÙNG một khung bao để chân không bị nhảy.
# Kết quả: Drive .../DuaXeSinhTon_assets_v2/khung_hinh/<tên>_0.png ... _7.png
# Game tự phát các khung này (có khung thì không dùng kiểu cắt chân nữa).
#
# Runtime: GPU T4. Cài ở ô riêng trước:
#   !pip install -q diffusers transformers accelerate peft rembg onnxruntime
# ============================================================
import os, math, zlib
import numpy as np
import torch
from PIL import Image, ImageDraw
from collections import deque
from rembg import remove, new_session
from diffusers import StableDiffusionXLControlNetPipeline, ControlNetModel, AutoencoderKL, LCMScheduler

from google.colab import drive
drive.mount("/content/drive")
GOC = "/content/drive/MyDrive/Colab_Notebooks/DuaXeSinhTon_assets_v2"
RA = f"{GOC}/khung_hinh"
os.makedirs(RA, exist_ok=True)
os.makedirs(f"{RA}/_xuong", exist_ok=True)   # ảnh bộ xương để xem thử

SO_KHUNG = 8
RONG, CAO = 832, 1216
SO_BUOC = 8
DO_BAM_SAT = 1.5
SEED = 777
DO_GIONG_MAU = 0.65   # IP-Adapter: cao = giống ảnh gốc hơn nhưng ít theo tư thế hơn (0.5-0.8)
# Vẽ lại các nhân vật mà khung hình lần trước không đồng đều (giữ màu áo quần chặt hơn)
VE_LAI = {"survivor_run": 0.85, "boss_brute": 0.85}   # tên -> DO_GIONG_MAU riêng

# (tên ảnh gốc trong bộ v2, thư mục, mô tả, kiểu đi: "chay" | "di" | "lach")
NHAN_VAT = [
    ("survivor_run", "nhan_vat", "male survivor, orange-red baseball cap, open brown leather jacket over a light blue t-shirt, "
     "light blue denim jeans on both legs, brown boots, short dark beard, holding a pistol, same outfit in every frame", "chay"),
    ("zombie_walker", "zombie", "shambling green-skinned zombie in torn plaid shirt and jeans, arms reaching forward", "di"),
    ("zombie_runner", "zombie", "fast skinny grey zombie in rags", "chay"),
    ("zombie_fat", "zombie", "huge fat bloated green zombie in torn overalls", "lach"),
    ("zombie_spitter", "zombie", "hunched green zombie with swollen glowing acid throat", "di"),
    ("zombie_armored", "zombie", "zombie wearing dented riot police armor and cracked helmet", "di"),
    ("boss_brute", "zombie", "giant hulking muscular green zombie butcher boss, very broad shoulders and thick arms, "
     "orange apron with blood stains, dark trousers, holding a huge meat cleaver, same outfit in every frame", "lach"),
]

PHONG_CACH = (
    "2D mobile game art in the style of Earn to Die 2, hand-drawn comic illustration, "
    "thick clean black outlines, flat cel shading, gritty post-apocalyptic palette, "
    "game sprite, full body from head to feet, strict side view profile facing right, "
    "one single character only, isolated on plain flat pure white background"
)
CAM = ("different clothes, changing colors, text, watermark, logo, frame, multiple views, character sheet, duplicate, extra limbs, "
       "extra characters, smoke, dust, muzzle flash, motion lines, shadow, ground, scenery, "
       "cropped, cut off, blurry, photo, 3d render, realistic, facing left, front view")

# ---------- 1. Bộ xương OpenPose cho một vòng bước ----------
# 18 điểm COCO: 0 mũi,1 cổ,2 vai P,3 khuỷu P,4 cổ tay P,5 vai T,6 khuỷu T,7 cổ tay T,
# 8 hông P,9 gối P,10 cổ chân P,11 hông T,12 gối T,13 cổ chân T,14 mắt P,15 mắt T,16 tai P,17 tai T
MAU = [[255, 0, 0], [255, 85, 0], [255, 170, 0], [255, 255, 0], [170, 255, 0], [85, 255, 0], [0, 255, 0],
       [0, 255, 85], [0, 255, 170], [0, 255, 255], [0, 170, 255], [0, 85, 255], [0, 0, 255], [85, 0, 255],
       [170, 0, 255], [255, 0, 255], [255, 0, 170], [255, 0, 85]]
NOI = [[2, 3], [2, 6], [3, 4], [4, 5], [6, 7], [7, 8], [2, 9], [9, 10], [10, 11], [2, 12], [12, 13],
       [13, 14], [2, 1], [1, 15], [15, 17], [1, 16], [16, 18]]


def tu_the(t, kieu):
    """Điểm khớp (x, y) cho pha t (0..1) của vòng bước, nhân vật quay phải."""
    a = 2 * math.pi * t
    if kieu == "chay":
        dui, goi, tay, nghieng, nhun = 38, 70, 45, 12, 22
    elif kieu == "lach":
        dui, goi, tay, nghieng, nhun = 18, 25, 15, 4, 10
    else:
        dui, goi, tay, nghieng, nhun = 25, 40, 25, 8, 12
    L_DUI, L_CANG, L_THAN, L_TAY1, L_TAY2 = 230, 230, 300, 170, 160
    hip = np.array([RONG / 2, CAO - 80 - L_DUI - L_CANG + nhun * abs(math.sin(a))])
    ln = math.radians(nghieng)
    co = hip + L_THAN * np.array([math.sin(ln), -math.cos(ln)])
    p = {}
    p[1] = co
    p[0] = co + np.array([40, -70])
    p[14] = p[0] + np.array([-5, -18]); p[15] = p[0] + np.array([-12, -16])
    p[16] = co + np.array([-15, -70]); p[17] = co + np.array([-25, -68])

    def chan(pha):
        g_dui = math.radians(dui * math.sin(pha))
        g_goi = math.radians(goi * max(0.0, math.sin(pha + math.pi / 2)))  # gập gối khi đưa chân lên
        goi_ = hip + L_DUI * np.array([math.sin(g_dui), math.cos(g_dui)])
        g2 = g_dui - g_goi
        co_chan = goi_ + L_CANG * np.array([math.sin(g2), math.cos(g2)])
        return goi_, co_chan

    def tay_(pha, vai):
        g = math.radians(tay * math.sin(pha))
        khuyu = vai + L_TAY1 * np.array([math.sin(g), math.cos(g)])
        g2 = g + math.radians(60 if kieu == "chay" else 25)
        return khuyu, khuyu + L_TAY2 * np.array([math.sin(g2), math.cos(g2)])

    p[8] = hip + np.array([-6, 0]); p[11] = hip + np.array([6, 0])
    p[9], p[10] = chan(a); p[12], p[13] = chan(a + math.pi)
    p[2] = co + np.array([-8, 8]); p[5] = co + np.array([8, 8])
    p[3], p[4] = tay_(a + math.pi, p[2]); p[6], p[7] = tay_(a, p[5])
    return p


def ve_xuong(p):
    anh = Image.new("RGB", (RONG, CAO), (0, 0, 0))
    d = ImageDraw.Draw(anh)
    for i, (u, v) in enumerate(NOI):
        a, b = p[u - 1], p[v - 1]
        d.line([tuple(a), tuple(b)], fill=tuple(int(c * 0.6) for c in MAU[i]), width=12)
    for i in range(18):
        x, y = p[i]
        d.ellipse([x - 7, y - 7, x + 7, y + 7], fill=tuple(MAU[i]))
    return anh


# ---------- 2. Mô hình ----------
print("⏳ Đang tải SDXL + ControlNet OpenPose + IP-Adapter...")
cn = ControlNetModel.from_pretrained("thibaud/controlnet-openpose-sdxl-1.0", torch_dtype=torch.float16)
vae = AutoencoderKL.from_pretrained("madebyollin/sdxl-vae-fp16-fix", torch_dtype=torch.float16)
pipe = StableDiffusionXLControlNetPipeline.from_pretrained(
    "stabilityai/stable-diffusion-xl-base-1.0", controlnet=cn, vae=vae,
    torch_dtype=torch.float16, variant="fp16")
pipe.scheduler = LCMScheduler.from_config(pipe.scheduler.config)
pipe.load_lora_weights("latent-consistency/lcm-lora-sdxl")
pipe.fuse_lora()
pipe.load_ip_adapter("h94/IP-Adapter", subfolder="sdxl_models", weight_name="ip-adapter_sdxl.bin")
pipe.set_ip_adapter_scale(DO_GIONG_MAU)
pipe.enable_model_cpu_offload()   # vừa RAM GPU T4
print("✅ Sẵn sàng!")

_rembg = new_session("isnet-general-use")


def loc_rac(anh):
    """Bỏ phần mờ và mảnh rời nhỏ (không cắt)."""
    a = np.array(anh)
    al = a[:, :, 3]
    al[al < 90] = 0
    k = max(1, max(anh.size) // 160)
    nho = al[::k, ::k] > 0
    h, w = nho.shape
    nhan = np.zeros((h, w), np.int32); co = [0]
    for y in range(h):
        for x in range(w):
            if nho[y, x] and not nhan[y, x]:
                n = len(co); nhan[y, x] = n; q = deque([(y, x)]); dem = 0
                while q:
                    cy, cx = q.popleft(); dem += 1
                    for dy in (-1, 0, 1):
                        for dx in (-1, 0, 1):
                            ny, nx = cy + dy, cx + dx
                            if 0 <= ny < h and 0 <= nx < w and nho[ny, nx] and not nhan[ny, nx]:
                                nhan[ny, nx] = n; q.append((ny, nx))
                co.append(dem)
    if len(co) > 2:
        giu = np.array([c >= max(co) * 0.3 for c in co]); giu[0] = False
        m = np.kron(giu[nhan], np.ones((k, k), bool))[: al.shape[0], : al.shape[1]]
        day = np.zeros(al.shape, bool); day[: m.shape[0], : m.shape[1]] = m
        al[~day] = 0
    a[:, :, 3] = al
    return Image.fromarray(a)


def chay():
    for ten, nhom, mo_ta, kieu in NHAN_VAT:
        da_lam = f"{RA}/_da_ve_lai.txt"
        xong_roi = set(open(da_lam).read().split()) if os.path.exists(da_lam) else set()
        lam_lai = ten in VE_LAI and ten not in xong_roi
        if os.path.exists(f"{RA}/{ten}_{SO_KHUNG - 1}.png") and not lam_lai:
            print("⏭  Đã có:", ten); continue
        pipe.set_ip_adapter_scale(VE_LAI.get(ten, DO_GIONG_MAU))
        mau_path = f"{GOC}/{nhom}/{ten}.png"
        if not os.path.exists(mau_path):
            print("⚠️  Thiếu ảnh gốc (chạy tao_asset_colab_v2.py trước):", mau_path); continue
        mau = Image.open(mau_path).convert("RGBA")
        nen_trang = Image.new("RGB", mau.size, (255, 255, 255)); nen_trang.paste(mau, mask=mau.split()[3])
        khung = []
        for i in range(SO_KHUNG):
            p = tu_the(i / SO_KHUNG, kieu)
            xuong = ve_xuong(p)
            xuong.save(f"{RA}/_xuong/{ten}_{i}.png")
            print(f"🎞  {ten} khung {i + 1}/{SO_KHUNG}")
            anh = pipe(prompt=f"{mo_ta}, {'running' if kieu == 'chay' else 'walking'}. {PHONG_CACH}",
                       negative_prompt=CAM, image=xuong, ip_adapter_image=nen_trang,
                       width=RONG, height=CAO, num_inference_steps=SO_BUOC, guidance_scale=DO_BAM_SAT,
                       controlnet_conditioning_scale=0.9,
                       generator=torch.Generator("cuda").manual_seed(SEED)).images[0]
            khung.append(loc_rac(remove(anh.convert("RGBA"), session=_rembg)))
        # một khung bao chung cho mọi khung -> chân đứng yên một chỗ, không giật
        hop = None
        for k in khung:
            b = k.getchannel("A").getbbox()
            if b:
                hop = b if hop is None else (min(hop[0], b[0]), min(hop[1], b[1]), max(hop[2], b[2]), max(hop[3], b[3]))
        for i, k in enumerate(khung):
            (k.crop(hop) if hop else k).save(f"{RA}/{ten}_{i}.png", "PNG")
        if lam_lai:
            with open(da_lam, "a") as f:
                f.write(ten + "\n")
        print("✅ Xong:", ten)
    print("🎉 HOÀN THÀNH. Thư mục:", RA, "(xem _xuong/ để thấy bộ xương từng khung)")


chay()
