# ============================================================
# BỔ SUNG ASSET "ĐUA XE SINH TỒN" (chạy sau bản 2)
# Vẽ những thứ game còn vẽ bằng code + vẽ lại các ảnh bản 2 bị hỏng.
# Cùng mô hình/phong cách với tao_asset_colab_v2.py (SDXL 1.0 + LCM-LoRA),
# lưu vào CÙNG thư mục DuaXeSinhTon_assets_v2. Ảnh có trong VE_LAI sẽ bị
# xóa rồi vẽ lại; ảnh mới thì vẽ nếu chưa có (chạy lại không vẽ trùng).
# Runtime: GPU T4. Cài ở ô riêng trước:
#   !pip install -q diffusers transformers accelerate peft rembg onnxruntime
# ============================================================
import os, sys, time, zipfile, io
import torch
import requests
from PIL import Image
from rembg import remove, new_session
from diffusers import AutoPipelineForText2Image

# 1. LẤY ELEVENLABS API KEY TỪ SECRETS (NẾU CÓ)
def lay_key(ten):
    v = None
    try:
        from google.colab import userdata
        v = userdata.get(ten)
    except Exception:
        pass
    return (v or "").strip() or None

ELEVENLABS_API_KEY = lay_key("ELEVENLABS_API_KEY")

# Âm thanh/nhạc đã có ở bộ 1 (DuaXeSinhTon_assets) — để False cho đỡ tốn lượt ElevenLabs
TAO_AM_THANH = False

# 2. KẾT NỐI GOOGLE DRIVE
LUU_VAO_DRIVE = True
if LUU_VAO_DRIVE:
    from google.colab import drive
    drive.mount("/content/drive")
    THU_MUC = "/content/drive/MyDrive/Colab_Notebooks/DuaXeSinhTon_assets_v2"
else:
    THU_MUC = "/content/DuaXeSinhTon_assets_v2"
os.makedirs(THU_MUC, exist_ok=True)

# 3. KHỞI TẠO MÔ HÌNH VẼ ẢNH TRÊN GPU COLAB (SDXL 1.0 + LCM-LoRA)
from diffusers import LCMScheduler, AutoencoderKL
print("⏳ Đang tải SDXL 1.0 + LCM-LoRA vào GPU Colab (lần đầu ~3-5 phút)...")
vae = AutoencoderKL.from_pretrained("madebyollin/sdxl-vae-fp16-fix", torch_dtype=torch.float16)
pipe = AutoPipelineForText2Image.from_pretrained(
    "stabilityai/stable-diffusion-xl-base-1.0",
    vae=vae,
    torch_dtype=torch.float16,
    variant="fp16",
)
pipe.scheduler = LCMScheduler.from_config(pipe.scheduler.config)
pipe.load_lora_weights("latent-consistency/lcm-lora-sdxl")
pipe.fuse_lora()
pipe.to("cuda")
SO_BUOC = 8          # 6-10: nhiều hơn = kỹ hơn nhưng chậm hơn
DO_BAM_SAT = 1.5     # guidance 1-2 cho LCM; cao hơn thì nghe lời mô tả hơn
SEED_GOC = 2024      # đổi số này để ra bộ ảnh khác
print("✅ Mô hình GPU đã sẵn sàng!")

# 4. DANH SÁCH ÂM THANH & NHẠC
AM_THANH = [
    # (nhóm, tên file, mô tả tiếng Anh cho ElevenLabs, số giây)
    ("am_thanh", "sfx_pistol", "single pistol gunshot, punchy, short", 0.6),
    ("am_thanh", "sfx_smg", "short submachine gun burst", 1.0),
    ("am_thanh", "sfx_shotgun", "pump shotgun blast with shell eject", 1.0),
    ("am_thanh", "sfx_minigun", "minigun spinning and firing", 2.0),
    ("am_thanh", "sfx_rocket", "rocket launcher whoosh", 1.2),
    ("am_thanh", "sfx_flame", "flamethrower roaring burst", 1.5),
    ("am_thanh", "sfx_bow", "bow string release and arrow whoosh", 0.7),
    ("am_thanh", "sfx_axe", "heavy axe swing and flesh hit", 0.8),
    ("am_thanh", "sfx_explosion", "big cartoon explosion with debris", 2.0),
    ("am_thanh", "sfx_zombie_groan", "zombie groan, raspy, creepy", 1.8),
    ("am_thanh", "sfx_zombie_death", "zombie death splat, gory squish", 0.8),
    ("am_thanh", "sfx_bat_screech", "mutant bat screech", 0.8),
    ("am_thanh", "sfx_boss_roar", "giant monster roar, deep", 2.5),
    ("am_thanh", "sfx_player_hurt", "man grunting in pain", 0.6),
    ("am_thanh", "sfx_engine_idle", "old truck engine idling, steady loop", 3.0),
    ("am_thanh", "sfx_engine_rev", "truck engine revving hard with nitro", 2.5),
    ("am_thanh", "sfx_car_crash", "car crashing through wooden crates", 1.5),
    ("am_thanh", "sfx_coin", "cartoon coin pickup chime", 0.5),
    ("am_thanh", "sfx_gem", "magic crystal pickup sparkle", 0.6),
    ("am_thanh", "sfx_level_up", "game level up fanfare, short", 1.5),
    ("am_thanh", "sfx_click", "UI button click, metallic", 0.3),
    ("am_thanh", "sfx_door", "rusty metal door opening", 1.2),
    ("am_thanh", "sfx_build", "hammering and drilling, building a barricade", 2.0),
    ("am_thanh", "sfx_turret", "auto turret machine gun firing", 1.2),
    ("am_thanh", "sfx_wave_horn", "air raid siren, warning of an incoming wave", 3.0),
    ("am_thanh", "sfx_win", "victory jingle, triumphant", 2.5),
    ("am_thanh", "sfx_lose", "game over sad sting", 2.0),
    ("nhac", "music_menu", "calm western desert guitar loop, post-apocalyptic mood", 20.0),
    ("nhac", "music_battle", "intense rock drums and distorted guitar loop for zombie battle", 20.0),
    ("nhac", "music_defense", "tense building drums loop for defending a fortress at night", 20.0),
]


# 5. DANH SÁCH ẢNH ĐẦY ĐỦ
VUONG = "1024x1024"
NGANG = "1216x832"
DOC = "832x1216"

# Prompt kiểu TỪ KHÓA, ngắn: CLIP của SDXL chỉ đọc 77 token (gồm 2 token đầu/cuối),
# phần vượt bị cắt mất. Mỗi prompt = mô tả (≤ ~30 token) + phong cách (~20 token) ≈ 45–60 token.
PHONG_CACH_CHUNG = "comic game art, Earn to Die style, thick black outlines, cel shading, gritty post-apocalyptic colors"
PHONG_CACH = PHONG_CACH_CHUNG + ", game sprite, single object centered, whole object in frame, clean edges, detailed, plain white background"
PHONG_CACH_NGUOI = PHONG_CACH_CHUNG + ", full body, side view, facing right, legs apart, one character, white background"
PHONG_CACH_ICON = "mobile game icon, comic style, Earn to Die style, bold black outline, cel shading, bright saturated colors, glossy highlight, clean edges, high detail, game UI asset, single object centered, plain white background"
PHONG_CACH_NEN = PHONG_CACH_CHUNG + ", 2D side-scrolling game background, wide panorama, flat horizon, sunset light, no people"
PHONG_CACH_SANG = PHONG_CACH_CHUNG + ", glowing game visual effect, bright core, single effect centered, nothing else, pure black background"
CAM = "text, watermark, logo, cropped, 3d, photo, realistic, background, shadow, front view, facing left, extra limbs, duplicate"
CAM_NEN = "text, watermark, logo, people, 3d, photo, blurry"

# Chế độ (cột 4): True = xóa phông + lọc rác; False = giữ nguyên (nền, thẻ);
# "sang" = vẽ trên nền đen, độ sáng thành độ trong (lửa/nổ);
# "nguoi" = như True nhưng dùng mô tả toàn thân cho hoạt hình.

# Hướng: mọi thứ QUAY PHẢI (đầu xe / nòng súng / mặt nhân vật về bên phải).
NGANG_HAN = "side view, facing right"
SUNG_XE = "roof turret, side view, barrel right"
HINH = [
    # --- Súng lắp trên xe (game xoay theo hướng ngắm; nòng phải chĩa sang phải) ---
    ("sung_xe", "wpn_mount_pistol", "twin heavy pistols, " + SUNG_XE, True, NGANG),
    ("sung_xe", "wpn_mount_knife", "steel blades and spikes on a bumper ram, " + NGANG_HAN, True, NGANG),
    ("sung_xe", "wpn_mount_smg", "submachine gun, drum magazine, " + SUNG_XE, True, NGANG),
    ("sung_xe", "wpn_mount_crossbow", "heavy crossbow ballista, " + SUNG_XE, True, NGANG),
    ("sung_xe", "wpn_mount_shotgun", "double barrel shotgun, " + SUNG_XE, True, NGANG),
    ("sung_xe", "wpn_mount_catapult", "small catapult arm with rock, " + SUNG_XE, True, NGANG),
    ("sung_xe", "wpn_mount_flamer", "flamethrower, fuel tank, " + SUNG_XE, True, NGANG),
    ("sung_xe", "wpn_mount_rocket", "rocket launcher pod, " + SUNG_XE, True, NGANG),
    ("sung_xe", "wpn_mount_laser", "laser cannon, blue coils, " + SUNG_XE, True, NGANG),
    ("sung_xe", "wpn_mount_minigun", "six-barrel minigun, " + SUNG_XE, True, NGANG),

    # --- Vũ khí tài xế cầm: vẽ lại cho nằm ngang, không có người ---
    ("vu_khi", "wpn_pistol", "single handgun, horizontal, no hand, " + NGANG_HAN, True, NGANG),
    ("vu_khi", "wpn_bow", "wooden bow with arrow, no person, " + NGANG_HAN, True, VUONG),
    ("vu_khi", "wpn_spear", "long spear, iron tip, horizontal, no person, " + NGANG_HAN, True, NGANG),

    # --- Xe: vẽ lại nhìn NGANG HẲN (bản 2 bị chéo 3/4) ---
    ("xe", "car_sedan", "rusty orange sedan, armor plates, bull bar, " + NGANG_HAN, True, NGANG),
    ("xe", "car_pickup", "armored orange pickup truck, both wheels, " + NGANG_HAN, True, NGANG),
    ("xe", "car_bus", "armored orange school bus, metal plates, plow, " + NGANG_HAN, True, NGANG),
    ("xe", "car_tank", "olive military tank, tracks, cannon, " + NGANG_HAN, True, NGANG),
    ("xe", "car_muscle", "black orange muscle car, spikes, both wheels, " + NGANG_HAN, True, NGANG),
    ("xe", "car_monster", "orange monster truck, huge wheels, " + NGANG_HAN, True, NGANG),

    # --- Chướng ngại trên đường, trại, nhà kho (đang vẽ code) ---
    ("do_dac", "obs_crate", "wooden crate, cross braces", True, VUONG),
    ("do_dac", "obs_barrel_explosive", "red explosive barrel, yellow hazard sign", True, DOC),
    ("do_dac", "obs_bunker", "concrete pillbox bunker, gun slit, sandbags, " + NGANG_HAN, True, NGANG),
    ("do_dac", "def_wall_wood", "tall wall of sharpened logs, narrow", True, DOC),
    ("do_dac", "def_wall_metal", "tall wall of riveted scrap metal, narrow", True, DOC),
    ("do_dac", "def_wall_concrete", "tall concrete wall, parapet, rebar, narrow", True, DOC),
    ("do_dac", "prop_tent", "patched canvas tent, " + NGANG_HAN, True, NGANG),
    ("do_dac", "prop_fire_barrel", "rusty campfire barrel, no fire", True, DOC),
    ("do_dac", "prop_catwalk", "long steel catwalk, yellow black hazard stripes, solid, " + NGANG_HAN, True, NGANG),
    ("do_dac", "prop_shelf", "tall rusty warehouse shelf, cardboard boxes, solid", True, DOC),
    ("do_dac", "prop_door_locked", "green metal door, boarded planks, chain, padlock, solid", True, DOC),

    # --- Hiệu ứng: vẽ trên nền ĐEN tuyền (chế độ "sang") ---
    ("hieu_ung", "fx_muzzle", "gun muzzle flash, yellow orange, star burst, pointing right", "sang", VUONG),
    ("hieu_ung", "fx_explosion", "round fireball explosion, orange yellow", "sang", VUONG),
    ("hieu_ung", "fx_fire", "single tall cartoon flame, orange yellow", "sang", DOC),
    ("hieu_ung", "fx_spark", "electric blue spark burst", "sang", VUONG),
    ("hieu_ung", "fx_blood", "dark red blood splash, droplets", True, VUONG),
    ("hieu_ung", "fx_dust", "brown dust puff cloud", True, VUONG),
]

# Ảnh hỏng: xóa rồi vẽ lại MỘT lần cho mỗi ĐỢT (đổi DOT để vẽ lại đợt mới).
# Đợt 2: những ảnh đợt 1 ra sai (prompt cũ quá 77 token nên bị cắt mất phong cách).
DOT = "dot2"
VE_LAI = {
    "wpn_mount_pistol", "wpn_mount_knife", "wpn_mount_crossbow", "wpn_mount_flamer", "wpn_spear",
    "car_monster", "def_wall_concrete", "prop_tent", "prop_catwalk", "prop_door_locked",
    "fx_muzzle", "fx_explosion", "fx_fire", "fx_spark", "fx_blood", "fx_dust",
}


# 6. HÀM XỬ LÝ ÂM THANH
def tao_am_thanh(mo_ta, giay, duong_dan):
    if not ELEVENLABS_API_KEY:
        print("   ⚠️ Bỏ qua âm thanh vì chưa cài ELEVENLABS_API_KEY trong Secrets.")
        return False
    url = "https://api.elevenlabs.io/v1/sound-generation"
    headers = {"xi-api-key": ELEVENLABS_API_KEY, "Content-Type": "application/json"}
    payload = {"text": mo_ta, "duration_seconds": max(0.5, min(22.0, giay))}

    try:
        r = requests.post(url, headers=headers, json=payload, timeout=60)
        if r.status_code == 200:
            with open(duong_dan, "wb") as f:
                f.write(r.content)
            return True
        else:
            print("   ❌ Lỗi ElevenLabs:", r.text[:100])
    except Exception as e:
        print("   ❌ Lỗi kết nối âm thanh:", e)
    return False

# 7. HÀM VẼ ẢNH TRÊN GPU
import zlib
import numpy as np
from collections import deque

def tao_hinh_gpu(mo_ta, kich_thuoc, cam, ten):
    w, h = (int(x) for x in kich_thuoc.split("x"))
    seed = SEED_GOC + zlib.crc32(ten.encode()) % 100000   # cố định theo tên
    image = pipe(
        prompt=mo_ta, negative_prompt=cam, width=w, height=h,
        num_inference_steps=SO_BUOC, guidance_scale=DO_BAM_SAT,
        generator=torch.Generator("cuda").manual_seed(seed),
    ).images[0]
    temp_path = "/content/temp_generated.png"
    image.save(temp_path)
    return temp_path

def loc_rac(anh):
    """Bỏ phần mờ (khói, bụi) và các mảnh rời nhỏ, chỉ giữ chủ thể; cắt sát."""
    a = np.array(anh)
    al = a[:, :, 3]
    al[al < 90] = 0
    k = max(1, max(anh.size) // 160)
    nho = al[::k, ::k] > 0
    h, w = nho.shape
    nhan = np.zeros((h, w), np.int32)
    co = [0]
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
    ra = Image.fromarray(a)
    hop = ra.getchannel("A").getbbox()
    if hop:
        l, t, r, b = hop
        ra = ra.crop((max(0, l - 8), max(0, t - 8), min(ra.width, r + 8), min(ra.height, b + 8)))
    return ra

_rembg = None
def xoa_phong_va_cat(duong_vao, duong_ra):
    global _rembg
    if _rembg is None:
        _rembg = new_session("isnet-general-use")
    anh = Image.open(duong_vao).convert("RGBA")
    anh = remove(anh, session=_rembg)
    loc_rac(anh).save(duong_ra, "PNG")

def sang_thanh_trong(duong_vao, duong_ra):
    """Nền đen -> trong suốt: độ sáng của điểm ảnh thành độ đục."""
    a = np.array(Image.open(duong_vao).convert("RGB")).astype(np.float32)
    sang = a.max(axis=2)
    alpha = np.clip((sang - 20) * 1.4, 0, 255)
    mau = np.clip(a * 255.0 / np.maximum(sang, 1)[..., None], 0, 255)
    ra = Image.fromarray(np.dstack([mau, alpha]).astype(np.uint8), "RGBA")
    hop = ra.getchannel("A").getbbox()
    if hop:
        ra = ra.crop(hop)
    ra.save(duong_ra, "PNG")


def dem_token(p):
    """Số token CLIP của prompt (cả 2 bộ mã hóa SDXL dùng cùng cách tách từ)."""
    return len(pipe.tokenizer(p).input_ids)

def kiem_tra_prompt():
    """In cảnh báo các prompt vượt 77 token TRƯỚC khi vẽ (vượt thì bị cắt đuôi)."""
    loi = 0
    for nhom, ten, mo_ta, che_do, _ in HINH:
        n = dem_token(f"{mo_ta}, {chon_phong_cach(nhom, che_do)[0]}")
        if n > 77:
            loi += 1
            print(f"⚠️  {ten}: {n} token (> 77) — rút gọn mô tả")
    print(f"🔎 Kiểm tra prompt: {len(HINH) - loi}/{len(HINH)} trong giới hạn 77 token")

def chon_phong_cach(nhom, che_do):
    if nhom == "nen":
        return PHONG_CACH_NEN, CAM_NEN
    if che_do == "sang":
        return PHONG_CACH_SANG, CAM
    if che_do == "nguoi":
        return PHONG_CACH_NGUOI, CAM
    if nhom in ("suc_manh", "giao_dien"):
        return PHONG_CACH_ICON, CAM
    return PHONG_CACH, CAM

# 8. VÒNG LẶP CHẠY TỔNG HỢP
def chay():
    xong, bo_qua = 0, 0

    kiem_tra_prompt()

    # --- A. TẠO HÌNH ẢNH ---
    print("\n--- 🎨 BẮT ĐẦU TẠO HÌNH ẢNH ---")
    for nhom, ten, mo_ta, can_xoa_phong, kich_thuoc in HINH:
        os.makedirs(f"{THU_MUC}/{nhom}", exist_ok=True)
        dich = f"{THU_MUC}/{nhom}/{ten}.png"
        da_lam = f"{THU_MUC}/_da_ve_lai_{DOT}.txt"
        xong_roi = set(open(da_lam).read().split()) if os.path.exists(da_lam) else set()
        if os.path.exists(dich) and ten in VE_LAI and ten not in xong_roi:
            os.remove(dich)   # vẽ lại ảnh hỏng (chỉ một lần)
            with open(da_lam, "a") as f:
                f.write(ten + "\n")
        if os.path.exists(dich):
            bo_qua += 1
            continue

        print(f"🎨 Đang vẽ GPU: {nhom}/{ten}...")
        nen, cam = chon_phong_cach(nhom, can_xoa_phong)
        try:
            tam = tao_hinh_gpu(f"{mo_ta}, {nen}", kich_thuoc, cam, ten)
            if can_xoa_phong == "sang":
                sang_thanh_trong(tam, dich)
            elif can_xoa_phong:
                xoa_phong_va_cat(tam, dich)
            else:
                Image.open(tam).convert("RGB").save(dich, "PNG")
            xong += 1
            print(f"   => Đã xong: {dich}")
        except Exception as e:
            print(f"   ❌ Lỗi vẽ ảnh: {e}")

    # --- B. TẠO ÂM THANH ---
    print("\n--- 🔊 BẮT ĐẦU TẠO ÂM THANH ---")
    for nhom, ten, mo_ta, giay in (AM_THANH if TAO_AM_THANH else []):
        os.makedirs(f"{THU_MUC}/{nhom}", exist_ok=True)
        dich = f"{THU_MUC}/{nhom}/{ten}.mp3"
        if os.path.exists(dich):
            bo_qua += 1
            continue

        print(f"🔊 Đang tạo âm thanh: {nhom}/{ten}...")
        if tao_am_thanh(mo_ta, giay, dich):
            xong += 1
            print(f"   => Đã xong sound: {dich}")

    # --- C. ĐÓNG GÓI ZIP ---
    zip_path = f"{THU_MUC}.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for goc, _, files in os.walk(THU_MUC):
            for f in files:
                p = os.path.join(goc, f)
                z.write(p, os.path.relpath(p, os.path.dirname(THU_MUC)))

    print(f"\n🎉 HOÀN THÀNH TOÀN BỘ! Tạo mới {xong} file, bỏ qua {bo_qua} file đã có.")
    print("📁 Thư mục Google Drive:", THU_MUC)

chay()
