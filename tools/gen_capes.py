#!/usr/bin/env python3
"""Generates the capes in src/main/resources/assets/musicmod/textures/cape/.

Original designs drawn by code. Each cape is a 1024x512 texture in the vanilla
cape layout scaled 16x (the outside of the cape is 160x256 px at (16,16)), plus a
160x256 thumbnail of the design for the menu and an index.json with the names.

Usage: python3 tools/gen_capes.py   (needs numpy and Pillow)
"""
import json
import os
import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'musicmod', 'textures', 'cape')
K = 4                      # detail: every design unit is K x K pixels
W, H = 40, 64              # design size in units (all shapes below use these coordinates)
PW, PH = W * K, H * K      # design size in pixels
Y, X = np.mgrid[0:PH, 0:PW].astype(np.float32)
X = (X + 0.5) / K - 0.5
Y = (Y + 0.5) / K - 0.5
capes = []


def rgb(h):
    return np.array([int(h[i:i + 2], 16) for i in (1, 3, 5)], dtype=np.float32)


def grad(top, bottom):
    t = (Y / (H - 1))[..., None]
    return rgb(top) * (1 - t) + rgb(bottom) * t


def put(img, mask, color):
    img[mask] = rgb(color) if isinstance(color, str) else color


def add(cid, label, design):
    design = np.clip(design, 0, 255)
    base = design.reshape(-1, 3).mean(0) * 0.55            # darker lining / edges
    S = 4 * K                                                # pixels per unit of the 64x32 cape layout
    tex = np.zeros((32 * S, 64 * S, 4), dtype=np.float32)
    tex[0:17 * S, 0:22 * S, :3] = base                       # whole cape box
    tex[0:17 * S, 0:22 * S, 3] = 255
    tex[S:17 * S, S:11 * S, :3] = design                     # outside of the cape
    tex[0:22 * S, 22 * S:46 * S, :3] = base                  # elytra area: plain
    tex[0:22 * S, 22 * S:46 * S, 3] = 255
    Image.fromarray(tex.astype(np.uint8), 'RGBA').save(os.path.join(OUT, cid + '.png'))
    Image.fromarray(design.astype(np.uint8), 'RGB').save(os.path.join(OUT, cid + '_icon.png'))
    capes.append({'id': cid, 'label': label})


def stars(img, seed, n=40, color='#ffffff'):
    r = np.random.RandomState(seed)
    for _ in range(n):
        y, x = r.randint(PH - 1), r.randint(PW - 1)
        size = 1 + (r.rand() < 0.3)
        img[y:y + size, x:x + size] = rgb(color) * (0.5 + 0.5 * r.rand())


os.makedirs(OUT, exist_ok=True)

# 1. Note: the mod's music note on a purple gradient.
img = grad('#7b4fe0', '#2a1560')
note = ((X - 13) ** 2 / 30 + (Y - 44) ** 2 / 18 < 1) | ((X - 27) ** 2 / 30 + (Y - 40) ** 2 / 18 < 1)
note |= ((X >= 17) & (X <= 19) & (Y >= 16) & (Y <= 44)) | ((X >= 31) & (X <= 33) & (Y >= 12) & (Y <= 40))
note |= (X >= 17) & (X <= 33) & (np.abs(Y - (18 - (X - 17) * 0.25)) < 3)
put(img, note, '#ffffff')
add('note', 'Note', img)

# 2. Galaxy: nebula blotches and stars.
r = np.random.RandomState(2)
n = np.kron(r.rand(H // 8 + 1, W // 8 + 1), np.ones((8 * K, 8 * K)))[:PH, :PW]
for _ in range(3 * K):
    n = (n + np.roll(n, K, 0) + np.roll(n, -K, 0) + np.roll(n, K, 1) + np.roll(n, -K, 1)) / 5
n = (n + np.roll(n, 3, 0) + np.roll(n, 3, 1)) / 3
img = grad('#0a0822', '#1c1048') + (n[..., None] ** 2) * np.array([150, 60, 200])
stars(img, 3, 60)
add('galaxy', 'Galaxy', img)

# 3. Aurora: green curtains on a night sky.
img = grad('#04101c', '#0a2030')
wave = 22 + 8 * np.sin(X * 0.35) + 4 * np.sin(X * 0.9 + 1)
band = np.exp(-np.clip(Y - wave, 0, None) / 10.0) * (Y >= wave - 2)
img = img + band[..., None] * np.array([40, 230, 150]) * (0.6 + 0.4 * np.sin(X * 2.1)[..., None])
stars(img, 4, 25)
add('aurora', 'Aurora', img)

# 4. Sunset: retro sun with stripes.
img = grad('#2a1a6a', '#ff4fa8')
sun = ((X - 19.5) ** 2 + (Y - 34) ** 2 < 13 ** 2) & ~((Y > 34) & (np.mod(Y, 4) < 1.5))
sun_col = rgb('#fff080') * (1 - ((Y - 21) / 26)[..., None]) + rgb('#ff6a3a') * ((Y - 21) / 26)[..., None]
img[sun] = sun_col[sun]
put(img, Y > 52, '#1a0a30')
put(img, (Y > 52) & ((np.mod(X + (Y - 52) * (X - 20) * 0.08, 8) < 1) | (np.mod(Y, 4) < 1)), '#ff4fd0')
add('sunset', 'Sunset', img)

# 5. Flame.
img = grad('#1a0502', '#3a0a04')
for k, (col, amp) in enumerate((('#c02a0a', 30), ('#ff7a1a', 22), ('#ffd24a', 14))):
    top = H - amp - 9 * np.abs(np.sin(X * 0.45 + k * 1.3)) - 5 * np.abs(np.sin(X * 1.1 + k))
    put(img, Y > top, col)
add('flame', 'Flame', img)

# 6. Ice: diagonal shards.
img = grad('#bfe8ff', '#3a86c8')
shard = np.mod(X * 1.0 + Y * 0.6, 14)
img = img + ((shard < 2)[..., None] * 60.0) - ((shard > 11)[..., None] * 40.0)
stars(img, 6, 18)
add('ice', 'Ice', img)

# 7. Heart on black.
img = np.zeros((PH, PW, 3), dtype=np.float32) + rgb('#0c0c10')
hx, hy = (X - 19.5) / 11.0, -(Y - 30) / 11.0
heart = (hx ** 2 + hy ** 2 - 1) ** 3 - hx ** 2 * hy ** 3 < 0
put(img, heart, '#ff3a6a')
put(img, heart & (hx < -0.3) & (hy > 0.3), '#ff8aa6')
add('heart', 'Heart', img)

# 8. Moon: crescent on navy.
img = grad('#0a1030', '#1a2a5a')
moon = ((X - 20) ** 2 + (Y - 26) ** 2 < 11 ** 2) & ~((X - 25) ** 2 + (Y - 23) ** 2 < 10 ** 2)
put(img, moon, '#fff4c8')
stars(img, 8, 35)
add('moon', 'Moon', img)

# 9. Checker.
img = np.where((((X // 8) + (Y // 8)) % 2 == 0)[..., None], rgb('#f2f2f2'), rgb('#16161a'))
add('checker', 'Checker', img.astype(np.float32))

# 10. Neon stripes.
img = np.zeros((PH, PW, 3), dtype=np.float32) + rgb('#120a24')
d = np.mod(X + Y, 16)
put(img, d < 3, '#ff3fc0')
put(img, (d >= 8) & (d < 11), '#3af0ff')
add('neon', 'Neon', img)

# 11. Mint dots.
img = grad('#b8ffd8', '#3ac88a')
dots = ((np.mod(X + 4 * (Y // 8 % 2), 8) - 3.5) ** 2 + (np.mod(Y, 8) - 3.5) ** 2) < 3
put(img, dots, '#ffffff')
add('mint', 'Mint', img)

# 12. Lightning bolt on dark blue.
img = grad('#101a3a', '#060a18')
bolt = ((Y >= 8) & (Y < 30) & (np.abs(X - (26 - (Y - 8) * 0.45)) < 4)) | ((Y >= 26) & (Y < 34) & (X >= 12) & (X <= 28)) \
    | ((Y >= 30) & (Y < 56) & (np.abs(X - (26 - (Y - 30) * 0.5)) < 3.5 - (Y - 30) * 0.1))
put(img, bolt, '#ffe24a')
add('bolt', 'Bolt', img)

with open(os.path.join(OUT, 'index.json'), 'w') as fh:
    json.dump(capes, fh, indent=1)
print(len(capes), 'capes')
