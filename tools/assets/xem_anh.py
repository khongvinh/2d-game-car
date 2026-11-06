#!/usr/bin/env python3
"""Ghép nhiều ảnh PNG thành một bảng (nền xám, có tên) để xem nhanh hướng/chất lượng.

Dùng: python3 -I tools/assets/xem_anh.py <ảnh ra.png> <ảnh 1.png> <ảnh 2.png> ...
"""
import os
import sys

from PIL import Image, ImageDraw

out, files = sys.argv[1], sys.argv[2:]
W, S = 8, 180
sheet = Image.new("RGB", (W * S, max(1, (len(files) + W - 1) // W) * (S + 14)), (90, 90, 90))
d = ImageDraw.Draw(sheet)
for i, f in enumerate(files):
    im = Image.open(f).convert("RGBA")
    im.thumbnail((S, S))
    x, y = (i % W) * S, (i // W) * (S + 14)
    sheet.paste(im, (x, y), im)
    d.text((x + 2, y + S), os.path.basename(f)[:-4][:28], fill=(255, 255, 0))
sheet.save(out)
print(out)
