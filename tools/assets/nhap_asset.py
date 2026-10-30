#!/usr/bin/env python3
"""Copy assets made by tao_asset_colab.py into the app (app/src/main/assets).

Usage: python3 tools/assets/nhap_asset.py <thư mục DuaXeSinhTon_assets đã giải nén>

Sprites are shrunk to at most 384 px (backgrounds to 1280 px wide, saved as JPEG)
so the APK stays small. Sounds go to sfx/, music to music/.
"""
import os
import shutil
import sys

from PIL import Image

src = sys.argv[1]
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
                im = im.convert("RGB")
                im.thumbnail((1280, 1280))
                im.save(os.path.join(img_dir, name + ".jpg"), "JPEG", quality=85, optimize=True)
            else:
                im = im.convert("RGBA")
                im.thumbnail((384, 384))
                im.save(os.path.join(img_dir, name + ".png"), "PNG", optimize=True)
            n += 1
        elif ext == ".mp3":
            sub = "music" if group == "nhac" else "sfx"
            os.makedirs(os.path.join(root, sub), exist_ok=True)
            shutil.copy(p, os.path.join(root, sub, f))
            n += 1
print(f"Đã nhập {n} file vào {os.path.normpath(root)}")
