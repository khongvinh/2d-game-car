#!/usr/bin/env python3
"""Copy assets made by tao_asset_colab.py into the app (app/src/main/assets).

Usage: python3 tools/assets/nhap_asset.py <thư mục DuaXeSinhTon_assets đã giải nén> [--v2]
  --v2: bộ ảnh bản 2 (đã vẽ quay phải) — không lật/cắt theo bảng sửa của bộ 1.
  Thư mục khung_hinh/ (<tên>_0.png...): khung hình chạy, giữ nguyên khung bao chung.

Pictures are kept in full colour (no palette, no JPEG): sprites as RGBA PNG at
most 512 px (animation frames 400 px) so the phone does not run out of memory,
backgrounds as PNG. Sprites are cleaned first: faint haze (smoke, dust) is
dropped and stray blobs that are not attached to the main subject are erased,
then the picture is cropped to the subject. Sounds go to sfx/, music to music/.
"""
import os
import shutil
import sys
from collections import deque

import numpy as np
from PIL import Image


# The game treats every sprite as facing right. Pictures the AI drew facing
# left are mirrored here; CUT_LEFT drops junk on the left (gun smoke).
FACING_LEFT = {"survivor_run", "zombie_walker", "boss_brute", "zombie_armored", "zombie_bat", "boss_tank_zombie"}
CUT_LEFT = {"survivor_run": 0.4}
CUT_RIGHT = {}


# pictures where the AI drew a second, smaller copy: keep only the big one
ONLY_BIGGEST = {"car_sedan", "car_tank", "wpn_mount_rocket", "prop_catwalk", "wpn_smg", "wpn_shotgun", "wpn_laser", "wpn_minigun", "part_chassis",
                "part_gun_barrel", "survivor_dead"}


# pictures with several copies stacked one above the other: keep only the top one
TOP_BAND = {"prop_catwalk", "wpn_smg"}


def top_band(im):
    """Crop to the first horizontal band of the picture that has content."""
    a = np.array(im.getchannel("A")) > 40
    rows = a.any(axis=1)
    ys = np.nonzero(rows)[0]
    if len(ys) == 0:
        return im
    y0 = ys[0]
    y1 = y0
    gap = max(6, im.height // 60)
    while y1 + 1 < len(rows) and (rows[y1 + 1] or rows[y1 + 1: y1 + 1 + gap].any()):
        y1 += 1
    out = im.crop((0, y0, im.width, y1 + 1))
    box = out.getchannel("A").getbbox()
    return out.crop(box) if box else out


def clean(im, crop=True, keep=0.3):
    """Erase haze and loose blobs around the subject, crop to what is left."""
    a = np.array(im)
    alpha = a[:, :, 3]
    alpha[alpha < 90] = 0
    # label blobs on a small copy (fast enough in plain Python)
    k = max(1, max(im.size) // 160)
    small = alpha[::k, ::k] > 0
    h, w = small.shape
    lab = np.zeros((h, w), np.int32)
    sizes = [0]
    for y in range(h):
        for x in range(w):
            if small[y, x] and not lab[y, x]:
                n = len(sizes)
                lab[y, x] = n
                q = deque([(y, x)])
                cnt = 0
                while q:
                    cy, cx = q.popleft()
                    cnt += 1
                    for dy in (-1, 0, 1):
                        for dx in (-1, 0, 1):
                            ny, nx = cy + dy, cx + dx
                            if 0 <= ny < h and 0 <= nx < w and small[ny, nx] and not lab[ny, nx]:
                                lab[ny, nx] = n
                                q.append((ny, nx))
                sizes.append(cnt)
    if len(sizes) > 2:
        big = max(sizes)
        keep = np.array([s >= big * keep for s in sizes])
        keep[0] = False
        mask = keep[lab]
        full = np.kron(mask, np.ones((k, k), bool))[: alpha.shape[0], : alpha.shape[1]]
        pad = np.zeros(alpha.shape, bool)
        pad[: full.shape[0], : full.shape[1]] = full
        alpha[~pad] = 0
    a[:, :, 3] = alpha
    out = Image.fromarray(a)
    if not crop:
        return out
    box = out.getchannel("A").getbbox()
    if box:
        l, t, r, b = box
        out = out.crop((max(0, l - 4), max(0, t - 4), min(out.width, r + 4), min(out.height, b + 4)))
    return out


src = sys.argv[1]
V2 = "--v2" in sys.argv[2:]
if V2:
    # bộ 2 (vẽ lại lần 3, prompt dưới 77 token): những ảnh AI vẫn vẽ quay trái
    FACING_LEFT = {"survivor_idle", "survivor_run", "survivor_shoot", "zombie_walker", "zombie_crawler", "car_sedan", "car_motorbike", "car_tank",
                   "wpn_mount_laser", "wpn_mount_shotgun", "wpn_mount_pistol", "wpn_mount_smg",
                   "survivor_ride", "wpn_flamethrower"}
    CUT_LEFT = {}
    CUT_RIGHT = {"survivor_ride": 0.7}   # a piece of vehicle the AI drew on the right
root = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets")
img_dir = os.path.join(root, "img")
os.makedirs(img_dir, exist_ok=True)
n = 0
for group in sorted(os.listdir(src)):
    gdir = os.path.join(src, group)
    if not os.path.isdir(gdir):
        continue
    for f in sorted(os.listdir(gdir)):
        p = os.path.join(gdir, f)
        name, ext = os.path.splitext(f)
        if ext == ".png":
            im = Image.open(p)
            if group == "nen":
                im.convert("RGB").save(os.path.join(img_dir, name + ".png"), "PNG")
                old = os.path.join(img_dir, name + ".jpg")
                if os.path.exists(old):
                    os.remove(old)
            elif group == "khung_hinh":
                # animation frames share one bounding box: clean but never crop
                im = clean(im.convert("RGBA"), crop=False)
                im.thumbnail((400, 400))  # drawn ~120-220 px tall
                im.save(os.path.join(img_dir, name + ".png"), "PNG")
            elif group == "hieu_ung" and name.startswith("fx_") and name not in ("fx_blood", "fx_dust"):
                im = im.convert("RGBA")
                im.thumbnail((512, 512))
                im.save(os.path.join(img_dir, name + ".png"), "PNG")  # glow: keep soft edges
            else:
                im = im.convert("RGBA")
                if name in CUT_LEFT:
                    im = im.crop((int(im.width * CUT_LEFT[name]), 0, im.width, im.height))
                if name in CUT_RIGHT:
                    im = im.crop((0, 0, int(im.width * CUT_RIGHT[name]), im.height))
                im = clean(im, keep=0.75 if name in ONLY_BIGGEST else 0.3)
                if name in TOP_BAND:
                    im = top_band(im)
                if name in FACING_LEFT:
                    im = im.transpose(Image.FLIP_LEFT_RIGHT)
                # full colour, but no bigger than the screen ever shows it: a
                # 1024 px RGBA bitmap takes 4 MB of phone memory once decoded
                im.thumbnail((512, 512))
                im.save(os.path.join(img_dir, name + ".png"), "PNG")
            n += 1
        elif ext == ".mp3":
            sub = "music" if group == "nhac" else "sfx"
            os.makedirs(os.path.join(root, sub), exist_ok=True)
            shutil.copy(p, os.path.join(root, sub, f))
            n += 1
print(f"Đã nhập {n} file vào {os.path.normpath(root)}")
