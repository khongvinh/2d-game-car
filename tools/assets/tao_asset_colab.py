# ============================================================
#  TẠO ASSET CHO GAME "ĐUA XE SINH TỒN" — Google Colab, MIỄN PHÍ
#  Ảnh: FLUX.1-schnell trên Hugging Face (gradio_client)
#  Xóa phông: rembg (chạy ngay trên Colab)
#  Âm thanh: ElevenLabs (không bắt buộc — bỏ trống key thì bỏ qua)
#  Dán toàn bộ vào 1 ô rồi bấm Chạy. Chạy lại được: file đã có sẽ bỏ qua.
# ============================================================
import os, sys, time, zipfile, tempfile

# Cài thư viện ở một ô riêng trước (chỉ lần đầu):
#   !pip install -q gradio_client rembg onnxruntime
import requests
from PIL import Image
from gradio_client import Client
from rembg import remove, new_session

# ------------------------------------------------------------
# 1. KEY (đều miễn phí, để trong 🔑 Secrets của Colab)
#    HF_TOKEN: huggingface.co → Settings → Access Tokens → New token (Read).
#              Không bắt buộc, nhưng có token thì được nhiều lượt GPU miễn phí hơn.
#    ELEVENLABS_API_KEY: chỉ cần nếu muốn tạo âm thanh.
# ------------------------------------------------------------
def lay_key(ten, bat_buoc=False):
    v = None
    try:
        from google.colab import userdata
        v = userdata.get(ten)
    except Exception:
        pass
    v = (v or "").strip()
    if v and (any(ch.isspace() for ch in v) or len(v) > 300):
        raise SystemExit(f"❌ {ten} không đúng: ô Value phải chỉ chứa đúng chuỗi key (không dán đoạn code). Sửa trong 🔑 Secrets rồi chạy lại.")
    if not v and bat_buoc:
        from getpass import getpass
        v = getpass(f"Nhập {ten}: ").strip()
    return v or None

HF_TOKEN = lay_key("HF_TOKEN")
ELEVENLABS_API_KEY = lay_key("ELEVENLABS_API_KEY")

# ------------------------------------------------------------
# 2. CÀI ĐẶT
# ------------------------------------------------------------
LUU_VAO_DRIVE = True
# Nhóm cần tạo (xóa bớt cho nhanh): "nhan_vat", "zombie", "xe", "manh_xe", "vu_khi",
# "phong_thu", "vat_pham", "suc_manh", "giao_dien", "nen", "am_thanh", "nhac"
CHON_NHOM = ["nhan_vat", "zombie", "xe", "manh_xe", "vu_khi", "phong_thu",
             "vat_pham", "suc_manh", "giao_dien", "nen", "am_thanh", "nhac"]
# True = mỗi nhóm chỉ tạo 1 file để xem thử phong cách
CHAY_THU = True
# Model trên Hugging Face (Space miễn phí)
SPACE = "black-forest-labs/FLUX.1-schnell"

if LUU_VAO_DRIVE:
    from google.colab import drive
    drive.mount("/content/drive")
    THU_MUC = "/content/drive/MyDrive/Colab_Notebooks/DuaXeSinhTon_assets"
else:
    THU_MUC = "/content/DuaXeSinhTon_assets"
os.makedirs(THU_MUC, exist_ok=True)

# Một phong cách chung. Nền trắng trơn để rembg tách phông sạch.
PHONG_CACH = (
    "2D side-view mobile game sprite in the style of Earn to Die, hand-drawn comic art, "
    "bold dark outlines, gritty post-apocalyptic desert colors, cel shading, "
    "single subject centered, whole object visible with margin around it, "
    "isolated on a plain flat pure white background, no shadow on the ground, no text, no watermark"
)
PHONG_CACH_ICON = (
    "square mobile game UI icon, hand-drawn comic style, bold dark outline, bright readable colors, "
    "single object centered, isolated on a plain flat pure white background, no text, no watermark"
)
PHONG_CACH_NEN = (
    "2D side-scrolling mobile game background art, hand-drawn comic style like Earn to Die, "
    "post-apocalyptic, warm sunset light, wide panoramic scene, no characters, no text"
)

# ------------------------------------------------------------
# 3. DANH SÁCH ASSET: (nhóm, tên file, mô tả, xóa phông?, kích thước)
# ------------------------------------------------------------
VUONG = "1024x1024"
NGANG = "1344x768"

HINH = [
    # --- Nhân vật ---
    ("nhan_vat", "survivor_idle", "male survivor, red cap, brown leather jacket, jeans, holding a pistol, standing, facing right", True, VUONG),
    ("nhan_vat", "survivor_run", "male survivor, red cap, brown leather jacket, jeans, running with a pistol, facing right", True, VUONG),
    ("nhan_vat", "survivor_dead", "male survivor in red cap lying knocked out on the ground", True, VUONG),
    ("nhan_vat", "merchant", "burly merchant, red bandana, beard, green vest, spiked shoulder pad, hook hand, half body portrait", True, VUONG),

    # --- Zombie và trùm ---
    ("zombie", "zombie_walker", "shambling zombie in torn shirt, arms forward, facing left", True, VUONG),
    ("zombie", "zombie_runner", "fast skinny sprinting zombie, facing left", True, VUONG),
    ("zombie", "zombie_fat", "huge fat bloated zombie, facing left", True, VUONG),
    ("zombie", "zombie_spitter", "zombie spitting green acid, swollen throat, facing left", True, VUONG),
    ("zombie", "zombie_armored", "zombie wearing riot police armor and helmet, facing left", True, VUONG),
    ("zombie", "zombie_bat", "mutant zombie bat flying, wings spread", True, VUONG),
    ("zombie", "zombie_crawler", "legless zombie crawling on the ground, facing left", True, VUONG),
    ("zombie", "zombie_dog", "rabid zombie dog running, facing left", True, VUONG),
    ("zombie", "boss_brute", "giant butcher zombie boss with cleaver and apron, facing left", True, VUONG),
    ("zombie", "boss_mutant_worm", "giant pink mutant sandworm boss with huge teeth bursting from the ground", True, VUONG),
    ("zombie", "boss_tank_zombie", "massive armored tank-sized zombie boss with metal plates bolted on, facing left", True, VUONG),

    # --- Xe (nhìn ngang, đầu xe bên phải) ---
    ("xe", "car_motorbike", "rusty dirt motorbike with rider seat, side view facing right", True, NGANG),
    ("xe", "car_sedan", "rusty old sedan car with armor plates and bull bar, side view facing right", True, NGANG),
    ("xe", "car_muscle", "black muscle car with spikes and roof gun, side view facing right", True, NGANG),
    ("xe", "car_pickup", "armored pickup truck with mounted machine gun, side view facing right", True, NGANG),
    ("xe", "car_bus", "armored school bus with welded metal plates and plow, side view facing right", True, NGANG),
    ("xe", "car_monster", "monster truck with huge wheels and flamethrower, side view facing right", True, NGANG),
    ("xe", "car_tank", "military tank with cannon, side view facing right", True, NGANG),
    ("xe", "car_wreck_burning", "burning wrecked pickup truck, side view", True, NGANG),

    # --- Mảnh ghép (thu thập đủ để lắp) ---
    ("manh_xe", "part_engine", "car engine block part", True, VUONG),
    ("manh_xe", "part_wheel", "big off-road tire with spikes", True, VUONG),
    ("manh_xe", "part_armor", "riveted steel armor plate", True, VUONG),
    ("manh_xe", "part_chassis", "rusty car chassis frame", True, VUONG),
    ("manh_xe", "part_fuel_tank", "jerry-can style fuel tank part", True, VUONG),
    ("manh_xe", "part_gun_barrel", "machine gun barrel part", True, VUONG),
    ("manh_xe", "part_scope", "rifle scope part", True, VUONG),
    ("manh_xe", "part_gear", "big metal cog gear", True, VUONG),
    ("manh_xe", "part_circuit", "salvaged electronic circuit board", True, VUONG),
    ("manh_xe", "part_brick", "stack of bricks and cement bag for building", True, VUONG),

    # --- Vũ khí ---
    ("vu_khi", "wpn_pistol", "handgun, side view facing right", True, VUONG),
    ("vu_khi", "wpn_smg", "submachine gun, side view facing right", True, VUONG),
    ("vu_khi", "wpn_shotgun", "pump shotgun, side view facing right", True, VUONG),
    ("vu_khi", "wpn_rifle", "assault rifle, side view facing right", True, VUONG),
    ("vu_khi", "wpn_minigun", "rotary minigun, side view facing right", True, VUONG),
    ("vu_khi", "wpn_rocket", "rocket launcher, side view facing right", True, VUONG),
    ("vu_khi", "wpn_flamethrower", "flamethrower with fuel tank, side view facing right", True, VUONG),
    ("vu_khi", "wpn_laser", "sci-fi laser rifle, side view facing right", True, VUONG),
    ("vu_khi", "wpn_bow", "hunting bow with arrow, side view", True, VUONG),
    ("vu_khi", "wpn_spear", "makeshift spear, diagonal", True, VUONG),
    ("vu_khi", "wpn_axe", "fire axe, diagonal", True, VUONG),
    ("vu_khi", "wpn_shuriken", "metal throwing star", True, VUONG),
    ("vu_khi", "wpn_dynamite", "bundle of dynamite with lit fuse", True, VUONG),

    # --- Pháo đài & bệ phòng thủ ---
    ("phong_thu", "def_barricade_wood", "wooden plank barricade with nails", True, VUONG),
    ("phong_thu", "def_barricade_spikes", "spiked metal barricade, czech hedgehog", True, VUONG),
    ("phong_thu", "def_sandbags", "sandbag wall", True, VUONG),
    ("phong_thu", "def_wall_metal", "scrap metal fortress wall segment", True, VUONG),
    ("phong_thu", "def_gate", "fortress gate made of scrap metal and car doors", True, VUONG),
    ("phong_thu", "def_watchtower", "wooden watchtower with sniper nest", True, VUONG),
    ("phong_thu", "def_turret_mg", "machine gun turret on tripod base, facing right", True, VUONG),
    ("phong_thu", "def_turret_cannon", "heavy cannon turret on armored base, facing right", True, VUONG),
    ("phong_thu", "def_turret_flame", "flamethrower turret on base, facing right", True, VUONG),
    ("phong_thu", "def_turret_tesla", "tesla coil electric turret", True, VUONG),
    ("phong_thu", "def_turret_mortar", "mortar launcher turret", True, VUONG),
    ("phong_thu", "def_landmine", "landmine half buried in sand", True, VUONG),
    ("phong_thu", "def_generator", "diesel generator with cables", True, VUONG),
    ("phong_thu", "def_build_slot", "empty concrete turret platform with yellow hazard stripes", True, VUONG),

    # --- Vật phẩm rơi ---
    ("vat_pham", "item_cash", "stack of green banknotes", True, VUONG),
    ("vat_pham", "item_gem", "glowing red crystal gem", True, VUONG),
    ("vat_pham", "item_medkit", "first aid kit", True, VUONG),
    ("vat_pham", "item_fuel", "red jerry can of fuel", True, VUONG),
    ("vat_pham", "item_ammo", "military ammo box", True, VUONG),
    ("vat_pham", "item_scrap", "pile of scrap metal", True, VUONG),
    ("vat_pham", "item_blueprint", "rolled blue blueprint paper", True, VUONG),
    ("vat_pham", "item_crate", "wooden loot crate", True, VUONG),
    ("vat_pham", "item_chest", "metal treasure case with yellow hazard tape", True, VUONG),

    # --- Biểu tượng sức mạnh (chọn khi lên cấp) ---
    ("suc_manh", "pw_dual_magazine", "two taped rifle magazines", False, VUONG),
    ("suc_manh", "pw_dynamite", "dynamite sticks with sparks", False, VUONG),
    ("suc_manh", "pw_matchstick", "burning matchstick with big flame", False, VUONG),
    ("suc_manh", "pw_nitrogen", "frosty liquid nitrogen flask", False, VUONG),
    ("suc_manh", "pw_explode_corpse", "zombie skull exploding", False, VUONG),
    ("suc_manh", "pw_shoes", "running sneakers with speed lines", False, VUONG),
    ("suc_manh", "pw_orbit_junk", "spinning saw blades orbiting", False, VUONG),
    ("suc_manh", "pw_adrenaline", "adrenaline syringe", False, VUONG),
    ("suc_manh", "pw_battery", "car battery with lightning bolts", False, VUONG),
    ("suc_manh", "pw_damage", "red card with crossed guns and plus sign", False, VUONG),
    ("suc_manh", "pw_fire_rate", "bullets with speed arrows", False, VUONG),
    ("suc_manh", "pw_health", "green card with heart and plus sign", False, VUONG),
    ("suc_manh", "pw_magnet", "horseshoe magnet pulling coins", False, VUONG),
    ("suc_manh", "pw_double_jump", "boots with springs", False, VUONG),
    ("suc_manh", "pw_vampire", "blood drop with fangs", False, VUONG),

    # --- Giao diện ---
    ("giao_dien", "ui_skull", "zombie skull boss marker", True, VUONG),
    ("giao_dien", "ui_lock", "heavy padlock", True, VUONG),
    ("giao_dien", "ui_star", "gold star badge", True, VUONG),
    ("giao_dien", "ui_home", "house icon on round metal button", True, VUONG),
    ("giao_dien", "ui_garage", "car icon on round metal button", True, VUONG),
    ("giao_dien", "ui_skills", "star over boxes icon on round metal button", True, VUONG),
    ("giao_dien", "ui_mission", "clipboard with checklist", True, VUONG),
    ("giao_dien", "ui_trophy", "gold trophy cup", True, VUONG),
    ("giao_dien", "ui_gift", "gift crate on a parachute", True, VUONG),

    # --- Nền (không xóa phông) ---
    ("nen", "bg_desert_far", "desert canyon with red mesas and sunset sky, far layer", False, NGANG),
    ("nen", "bg_desert_road", "ruined desert highway with broken poles and wrecked cars", False, NGANG),
    ("nen", "bg_warehouse", "inside a ruined brick warehouse with tall arched windows, shelves and crates", False, NGANG),
    ("nen", "bg_garage", "inside a brick garage workshop with roller door and hanging lamp", False, NGANG),
    ("nen", "bg_fortress", "makeshift survivor fortress camp of scrap walls in the desert", False, NGANG),
    ("nen", "bg_menu_island", "floating desert rock island with a signpost, wrecked cars and a skull flag", False, NGANG),
    ("nen", "bg_city_ruins", "ruined city street at dusk with burning buildings", False, NGANG),
    ("nen", "bg_night_forest", "dark misty forest at night with a red moon", False, NGANG),
]

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

# ------------------------------------------------------------
# 4. TẠO ẢNH (FLUX.1-schnell) + XÓA PHÔNG (rembg)
# ------------------------------------------------------------
_client = None
_rembg = None

class HetLuot(Exception):
    """Hết lượt GPU miễn phí trên Hugging Face — chạy lại sau."""

def client():
    global _client
    if _client is None:
        try:
            _client = Client(SPACE, hf_token=HF_TOKEN) if HF_TOKEN else Client(SPACE)
        except TypeError:
            _client = Client(SPACE, token=HF_TOKEN) if HF_TOKEN else Client(SPACE)
    return _client

def tao_hinh(mo_ta, kich_thuoc, thu_lai=3):
    """Trả về đường dẫn ảnh PNG/WEBP tạm do Space sinh ra."""
    w, h = (int(x) for x in kich_thuoc.split("x"))
    for lan in range(thu_lai):
        try:
            try:
                kq = client().predict(
                    prompt=mo_ta, seed=0, randomize_seed=True,
                    width=w, height=h, num_inference_steps=4,
                    api_name="/infer",
                )
            except (TypeError, ValueError):
                # Space đổi tên tham số: gọi theo thứ tự
                kq = client().predict(mo_ta, 0, True, w, h, 4, api_name="/infer")
            return kq[0] if isinstance(kq, (list, tuple)) else kq
        except Exception as e:
            loi = str(e)
            if "quota" in loi.lower() or "exceeded" in loi.lower():
                raise HetLuot(loi)
            print(f"   lỗi, thử lại ({lan + 1}/{thu_lai}):", loi[:200])
            time.sleep(10 * (lan + 1))
    return None

def xoa_phong_va_cat(duong_vao, duong_ra):
    """Xóa phông bằng rembg, cắt sát vật thể (chừa lề 8px), lưu PNG trong suốt."""
    global _rembg
    if _rembg is None:
        _rembg = new_session("isnet-general-use")
    anh = Image.open(duong_vao).convert("RGBA")
    anh = remove(anh, session=_rembg)
    hop = anh.getchannel("A").getbbox()
    if hop:
        l, t, r, b = hop
        anh = anh.crop((max(0, l - 8), max(0, t - 8), min(anh.width, r + 8), min(anh.height, b + 8)))
    anh.save(duong_ra, "PNG")

def luu_png(duong_vao, duong_ra):
    Image.open(duong_vao).convert("RGB").save(duong_ra, "PNG")

# ------------------------------------------------------------
# 5. ÂM THANH (ElevenLabs, không bắt buộc)
# ------------------------------------------------------------
def tao_am_thanh(mo_ta, giay, duong_dan):
    for lan in range(3):
        try:
            r = requests.post("https://api.elevenlabs.io/v1/sound-generation",
                              headers={"xi-api-key": ELEVENLABS_API_KEY}, timeout=180,
                              json={"text": mo_ta, "duration_seconds": max(0.5, min(22.0, giay)), "prompt_influence": 0.5})
            if r.status_code == 200:
                with open(duong_dan, "wb") as f:
                    f.write(r.content)
                return True
            print("   ❌ ElevenLabs:", r.text[:200])
            if r.status_code not in (429, 500, 502, 503):
                return False
        except requests.RequestException as e:
            print("   mạng lỗi:", e)
        time.sleep(5 * (lan + 1))
    return False

# ------------------------------------------------------------
# 6. CHẠY
# ------------------------------------------------------------
def chay():
    xong, loi, bo_qua = 0, [], 0
    da_thu = set()
    het_luot = False
    for nhom, ten, mo_ta, can_xoa_phong, kich_thuoc in HINH:
        if nhom not in CHON_NHOM or (CHAY_THU and nhom in da_thu):
            continue
        da_thu.add(nhom)
        os.makedirs(f"{THU_MUC}/{nhom}", exist_ok=True)
        dich = f"{THU_MUC}/{nhom}/{ten}.png"
        if os.path.exists(dich):
            bo_qua += 1
            continue
        if het_luot:
            loi.append(ten)
            continue
        print(f"🎨 {nhom}/{ten}")
        nen = PHONG_CACH_NEN if nhom == "nen" else PHONG_CACH_ICON if nhom in ("suc_manh", "giao_dien") else PHONG_CACH
        try:
            tam = tao_hinh(f"{mo_ta}. {nen}", kich_thuoc)
        except HetLuot:
            print("⏳ Hết lượt GPU miễn phí của Hugging Face. Đợi vài tiếng (hoặc thêm HF_TOKEN) rồi chạy lại ô này — ảnh đã tạo được giữ nguyên.")
            het_luot = True
            loi.append(ten)
            continue
        if not tam:
            loi.append(ten)
            continue
        try:
            if can_xoa_phong:
                xoa_phong_va_cat(tam, dich)
            else:
                luu_png(tam, dich)
            xong += 1
        except Exception as e:
            print("   ❌ lưu ảnh lỗi:", e)
            loi.append(ten)
    for nhom, ten, mo_ta, giay in AM_THANH:
        if nhom not in CHON_NHOM or (CHAY_THU and nhom in da_thu):
            continue
        da_thu.add(nhom)
        os.makedirs(f"{THU_MUC}/{nhom}", exist_ok=True)
        dich = f"{THU_MUC}/{nhom}/{ten}.mp3"
        if os.path.exists(dich):
            bo_qua += 1
            continue
        if not ELEVENLABS_API_KEY:
            continue
        print(f"🔊 {nhom}/{ten}")
        if tao_am_thanh(mo_ta, giay, dich):
            xong += 1
        else:
            loi.append(ten)

    zip_path = f"{THU_MUC}.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for goc, _, files in os.walk(THU_MUC):
            for f in files:
                p = os.path.join(goc, f)
                z.write(p, os.path.relpath(p, os.path.dirname(THU_MUC)))
    print(f"\n✅ Tạo mới {xong} file, bỏ qua {bo_qua} file đã có.")
    if loi:
        print(f"❌ Chưa xong {len(loi)} file (chạy lại ô này để làm tiếp):", ", ".join(loi[:20]), "..." if len(loi) > 20 else "")
    print("📁 Thư mục:", THU_MUC)
    print("📦 File zip:", zip_path)

chay()
