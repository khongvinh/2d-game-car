# ============================================================
# TẠO ẢNH GAME "ĐUA XE SINH TỒN" — SCRIPT 1/2 (chạy trước)
#   Script 2/2: tao_khung_hinh_colab.py (khung hình chạy, cần ảnh của script này).
# Chạy trên Google Colab (Runtime > Change runtime type > T4 GPU).
#  - SDXL 1.0 + LCM-LoRA (8 bước, 1024px), seed cố định theo tên ảnh.
#  - Prompt kiểu TỪ KHÓA, 45–60 token (CLIP chỉ đọc 77 token, vượt bị cắt);
#    script tự đếm token và cảnh báo trước khi vẽ.
#  - Xóa phông (rembg), lọc rác (khói, mảnh rời), cắt sát; hiệu ứng "sang"
#    vẽ trên nền đen rồi lấy độ sáng làm độ trong.
#  - Lưu Drive: MyDrive/Colab_Notebooks/DuaXeSinhTon_assets_v2/<nhóm>/<tên>.png.
#    Ảnh đã có thì bỏ qua (chạy lại không vẽ trùng). Ảnh trong VE_LAI được xóa
#    và vẽ lại một lần mỗi đợt (đổi DOT để vẽ lại đợt mới).
# Cài thư viện ở ô riêng trước:
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
    # --- Nhân vật ---
    ("nhan_vat", "survivor_idle", "male survivor, red cap, brown leather jacket, blue jeans, boots, pistol pointed down, standing", "nguoi", DOC),
    ("nhan_vat", "survivor_run", "male survivor, red cap, brown leather jacket, blue jeans, boots, holding pistol, running", "nguoi", DOC),
    ("nhan_vat", "survivor_jump", "male survivor, red cap, brown leather jacket, blue jeans, holding pistol, jumping", True, VUONG),
    ("nhan_vat", "survivor_shoot", "male survivor, red cap, brown leather jacket, blue jeans, boots, aiming pistol forward", "nguoi", DOC),
    ("nhan_vat", "survivor_hurt", "male survivor, red cap, leather jacket, blue jeans, flinching in pain", True, VUONG),
    ("nhan_vat", "survivor_dead", "male survivor in red cap lying knocked out on the ground", True, VUONG),
    ("nhan_vat", "merchant", "burly merchant, red bandana, beard, green vest, hook hand", True, VUONG),
    # --- Zombie và trùm ---
    ("zombie", "zombie_walker", "shambling green-skinned zombie in torn plaid shirt and jeans, arms reaching forward, walking", "nguoi", DOC),
    ("zombie", "zombie_runner", "fast skinny grey zombie in rags sprinting, leaning forward", "nguoi", DOC),
    ("zombie", "zombie_fat", "huge fat bloated green zombie in torn overalls, waddling", "nguoi", DOC),
    ("zombie", "zombie_spitter", "hunched green zombie with swollen glowing acid throat and dripping mouth, walking", "nguoi", DOC),
    ("zombie", "zombie_armored", "zombie wearing dented riot police armor and cracked helmet, walking", "nguoi", DOC),
    ("zombie", "zombie_bat", "mutant zombie bat flying, wings spread", True, VUONG),
    ("zombie", "zombie_crawler", "legless zombie crawling on its hands", True, VUONG),
    ("zombie", "zombie_dog", "rabid rotten zombie dog running", True, VUONG),
    ("zombie", "boss_brute", "giant muscular butcher zombie boss with bloody apron and huge cleaver, walking", "nguoi", DOC),
    ("zombie", "boss_mutant_worm", "giant pink mutant worm boss rearing up, round mouth full of teeth", True, VUONG),
    ("zombie", "boss_tank_zombie", "massive hulking zombie boss with rusty metal plates bolted onto its body, walking", "nguoi", DOC),
    # --- Xe (nhìn ngang, đầu xe bên phải) ---
    ("xe", "car_motorbike", "rusty dirt motorbike with rider seat", True, NGANG),
    ("xe", "car_sedan", "rusty orange sedan, armor plates, bull bar, " + NGANG_HAN, True, NGANG),
    ("xe", "car_muscle", "black orange muscle car, spikes, both wheels, " + NGANG_HAN, True, NGANG),
    ("xe", "car_pickup", "armored orange pickup truck, both wheels, " + NGANG_HAN, True, NGANG),
    ("xe", "car_bus", "armored orange school bus, metal plates, plow, " + NGANG_HAN, True, NGANG),
    ("xe", "car_monster", "orange monster truck, huge wheels, " + NGANG_HAN, True, NGANG),
    ("xe", "car_tank", "olive military tank, tracks, cannon, " + NGANG_HAN, True, NGANG),
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
    ("vu_khi", "wpn_pistol", "single handgun, horizontal, no hand, " + NGANG_HAN, True, NGANG),
    ("vu_khi", "wpn_smg", "submachine gun", True, VUONG),
    ("vu_khi", "wpn_shotgun", "pump shotgun", True, VUONG),
    ("vu_khi", "wpn_rifle", "assault rifle", True, VUONG),
    ("vu_khi", "wpn_minigun", "rotary minigun", True, VUONG),
    ("vu_khi", "wpn_rocket", "rocket launcher", True, VUONG),
    ("vu_khi", "wpn_flamethrower", "flamethrower with fuel tank", True, VUONG),
    ("vu_khi", "wpn_laser", "sci-fi laser rifle", True, VUONG),
    ("vu_khi", "wpn_bow", "wooden bow with arrow, no person, " + NGANG_HAN, True, VUONG),
    ("vu_khi", "wpn_spear", "long spear, iron tip, horizontal, no person, " + NGANG_HAN, True, NGANG),
    ("vu_khi", "wpn_axe", "fire axe, diagonal", True, VUONG),
    ("vu_khi", "wpn_shuriken", "metal throwing star", True, VUONG),
    ("vu_khi", "wpn_dynamite", "bundle of dynamite with lit fuse", True, VUONG),
    # --- Pháo đài & bệ phòng thủ ---
    ("phong_thu", "def_barricade_spikes", "spiked metal barricade, czech hedgehog", True, VUONG),
    ("do_dac", "def_wall_metal", "tall wall of riveted scrap metal, narrow", True, DOC),
    ("phong_thu", "def_gate", "fortress gate made of scrap metal and car doors", True, VUONG),
    ("phong_thu", "def_watchtower", "wooden watchtower with sniper nest", True, VUONG),
    ("phong_thu", "def_turret_mg", "machine gun turret on tripod base", True, VUONG),
    ("phong_thu", "def_turret_cannon", "heavy cannon turret on armored base", True, VUONG),
    ("phong_thu", "def_turret_flame", "flamethrower turret on base", True, VUONG),
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
    # --- Đồ đạc nhà kho / trại (thay hình vẽ bằng code) ---
    ("do_dac", "prop_door", "closed green metal warehouse door in a frame", True, DOC),
    ("do_dac", "prop_door_locked", "green metal door, boarded planks, chain, padlock, solid", True, DOC),
    ("do_dac", "prop_shelf", "tall rusty warehouse shelf, cardboard boxes, solid", True, DOC),
    ("do_dac", "prop_car_rusty", "abandoned rusty brown car with flat tires, side view", True, NGANG),
    ("do_dac", "prop_crate_stack", "stack of three wooden crates", True, VUONG),
    ("do_dac", "prop_barrel", "rusty oil drum barrel", True, DOC),
    ("do_dac", "prop_pallet", "wooden pallet with sacks", True, NGANG),
    ("do_dac", "prop_catwalk", "long steel catwalk, yellow black hazard stripes, solid, " + NGANG_HAN, True, NGANG),
    ("do_dac", "prop_bench", "old wooden workbench with tools", True, NGANG),
    ("do_dac", "item_crate", "closed wooden loot crate with metal corners and a question mark sticker", True, VUONG),
    ("do_dac", "def_barricade_wood", "barricade of crossed wooden planks nailed together, front view", True, NGANG),
    ("do_dac", "def_sandbags", "low wall of stacked sandbags", True, NGANG),
    # --- Hiệu ứng ---
    ("hieu_ung", "fx_muzzle", "gun muzzle flash, yellow orange, star burst, pointing right", "sang", VUONG),
    ("hieu_ung", "fx_explosion", "round fireball explosion, orange yellow", "sang", VUONG),
    ("hieu_ung", "fx_fire", "single tall cartoon flame, orange yellow", "sang", DOC),
    ("hieu_ung", "fx_spark", "electric blue spark burst", "sang", VUONG),
    ("hieu_ung", "fx_blood", "dark red blood splash, droplets", True, VUONG),
    ("hieu_ung", "fx_dust", "brown dust puff cloud", True, VUONG),
    # --- Nền (không xóa phông) ---
    ("nen", "bg_desert_far", "desert canyon with red mesas and sunset sky, far layer", False, NGANG),
    ("nen", "bg_desert_road", "ruined desert highway with broken poles and wrecked cars", False, NGANG),
    ("nen", "bg_warehouse", "inside a ruined brick warehouse with tall arched windows, shelves and crates", False, NGANG),
    ("nen", "bg_garage", "inside a brick garage workshop with roller door and hanging lamp", False, NGANG),
    ("nen", "bg_fortress", "makeshift survivor fortress camp of scrap walls in the desert", False, NGANG),
    ("nen", "bg_menu_island", "floating desert rock island with a signpost, wrecked cars and a skull flag", False, NGANG),
    ("nen", "bg_city_ruins", "ruined city street at dusk with burning buildings", False, NGANG),
    ("nen", "bg_night_forest", "dark misty forest at night with a red moon", False, NGANG),
    ("nen", "bg_warehouse_2", "inside a ruined factory hall with rusty machines and hanging chains", False, NGANG),
    ("nen", "bg_warehouse_3", "inside an abandoned supermarket with empty shelves and broken lights", False, NGANG),
    ("nen", "bg_volcano_road", "volcanic wasteland road with lava rivers and ash sky", False, NGANG),
    ("nen", "bg_night_road", "dark forest road at night under a big moon", False, NGANG),
    ("nen", "bg_alien_road", "alien planet landscape with purple sky, two moons and strange crystals", False, NGANG),

    # --- Bổ sung: súng lắp trên xe, chướng ngại, trại, tường (trước đây vẽ code) ---
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
    ("do_dac", "obs_crate", "wooden crate, cross braces", True, VUONG),
    ("do_dac", "obs_barrel_explosive", "red explosive barrel, yellow hazard sign", True, DOC),
    ("do_dac", "obs_bunker", "concrete pillbox bunker, gun slit, sandbags, " + NGANG_HAN, True, NGANG),
    ("do_dac", "def_wall_wood", "tall wall of sharpened logs, narrow", True, DOC),
    ("do_dac", "def_wall_concrete", "tall concrete wall, parapet, rebar, narrow", True, DOC),
    ("do_dac", "prop_tent", "patched canvas tent, " + NGANG_HAN, True, NGANG),
    ("do_dac", "prop_fire_barrel", "rusty campfire barrel, no fire", True, DOC),
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
