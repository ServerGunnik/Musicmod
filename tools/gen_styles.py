#!/usr/bin/env python3
"""Generates the PvP style textures in src/main/resources/assets/musicmod/styles/.

Original pixel art drawn by code: recolored obsidian, totems, end crystals and
respawn anchors. Also writes index.json, which lists every style, its label,
its preview color and which game texture each file replaces.

Usage: python3 tools/gen_styles.py   (needs numpy and Pillow)
"""
import json
import os
import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'musicmod', 'styles')
index = {}


def rgb(h):
    return np.array([int(h[i:i + 2], 16) for i in (1, 3, 5)], dtype=np.float32)


def noise(shape, seed, cell=3):
    """Blocky value noise in 0..1 (looks like Minecraft stone blotches)."""
    r = np.random.RandomState(seed)
    small = r.rand(shape[0] // cell + 2, shape[1] // cell + 2)
    big = np.kron(small, np.ones((cell, cell)))[:shape[0], :shape[1]]
    return np.clip(0.65 * big + 0.35 * r.rand(*shape), 0, 1)


def shade(n, dark, mid, light):
    """Maps noise to a 3-step palette."""
    out = np.zeros(n.shape + (3,), dtype=np.float32)
    out[:] = mid
    out[n < 0.38] = dark
    out[n > 0.72] = light
    return out


def save(kind, style, label, color, files):
    d = os.path.join(OUT, kind, style)
    os.makedirs(d, exist_ok=True)
    entry = {'label': label, 'color': color, 'files': {}}
    for target, img in files.items():
        name = os.path.basename(target)
        arr = np.clip(img, 0, 255).astype(np.uint8)
        Image.fromarray(arr, 'RGBA' if arr.shape[-1] == 4 else 'RGB').save(os.path.join(d, name))
        entry['files'][target] = f'{kind}/{style}/{name}'
    index.setdefault(kind, {})[style] = entry


def rgba(img, alpha=255):
    a = np.full(img.shape[:2] + (1,), alpha, dtype=np.float32)
    return np.concatenate([img, a], -1)


# ---------------------------------------------------------------- obsidian
def obsidian(style, label, dark, mid, light, seed, extra=None):
    img = shade(noise((16, 16), seed), rgb(dark), rgb(mid), rgb(light))
    if extra:
        extra(img, np.random.RandomState(seed + 1))
    save('obsidian', style, label, mid, {'textures/block/obsidian.png': img})


def _stars(img, r):
    for _ in range(14):
        img[r.randint(16), r.randint(16)] = rgb('#ffffff') * (0.6 + 0.4 * r.rand())


def _outline(img, r):
    img[0, :] = img[-1, :] = img[:, 0] = img[:, -1] = rgb('#f2f2ff')


def _cracks(img, r):
    y, x = 8, 0
    for x in range(16):
        y = int(np.clip(y + r.randint(-1, 2), 1, 14))
        img[y, x] = rgb('#ff8a1e')
        if r.rand() < 0.35:
            img[min(15, y + 1), x] = rgb('#ffc24a')
    x = 5
    for y in range(16):
        x = int(np.clip(x + r.randint(-1, 2), 1, 14))
        if r.rand() < 0.7:
            img[y, x] = rgb('#ff6a00')


obsidian('midnight', 'Midnight', '#080d2a', '#121b4d', '#22307a', 1)
obsidian('crimson', 'Crimson', '#2a060d', '#5a0f1e', '#8c1c30', 2)
obsidian('starry', 'Starry', '#04040a', '#0a0a16', '#141428', 3, _stars)
obsidian('outline', 'Outline', '#0b0714', '#150f26', '#21183a', 4, _outline)
obsidian('lava', 'Lava', '#0a0604', '#17100b', '#241a12', 5, _cracks)
obsidian('frost', 'Frost', '#4d6a84', '#7c9cb8', '#b4d0e6', 6)


# ------------------------------------------------------------------- totem
def totem(style, label, body, light, dark, eye):
    img = np.zeros((16, 16, 4), dtype=np.float32)
    b, l, k, e = rgb(body), rgb(light), rgb(dark), rgb(eye)

    def px(x, y, c):
        img[y, x, :3] = c
        img[y, x, 3] = 255

    def box(x1, y1, x2, y2, c):
        for yy in range(y1, y2 + 1):
            for xx in range(x1, x2 + 1):
                px(xx, yy, c)

    box(5, 1, 10, 5, b)          # head
    box(5, 1, 10, 1, l)
    box(5, 5, 10, 5, k)
    px(6, 3, e); px(9, 3, e)     # eyes
    px(7, 4, k); px(8, 4, k)     # mouth
    box(3, 7, 12, 8, b)          # arms
    box(3, 7, 12, 7, l)
    px(3, 8, k); px(12, 8, k)
    box(6, 6, 9, 12, b)          # body
    box(6, 6, 6, 12, l)
    box(9, 6, 9, 12, k)
    box(7, 9, 8, 10, e)          # gem on the chest
    box(5, 13, 10, 14, b)        # base
    box(5, 14, 10, 14, k)
    save('totem', style, label, body, {'textures/item/totem_of_undying.png': img})


totem('gold', 'Gold', '#f2c230', '#ffe680', '#b07a10', '#3fd66b')
totem('ice', 'Ice', '#8fd3f0', '#d6f3ff', '#4a8fb0', '#ffffff')
totem('amethyst', 'Amethyst', '#a56be8', '#d6b3ff', '#6a3aa8', '#ff5a9a')
totem('lava', 'Lava', '#f07a1e', '#ffd040', '#a8300a', '#fff2a0')
totem('galaxy', 'Galaxy', '#4a35b0', '#8a78f0', '#241866', '#7ae8ff')
totem('emerald', 'Emerald', '#2fc46a', '#8af0b0', '#157a3e', '#fff7b0')


# ------------------------------------------------------------- end crystal
def crystal(style, label, core, glow, frame, seed):
    """64x32: glass cube at (0,0), core cube at (32,0), base slab at (0,16)."""
    img = np.zeros((32, 64, 4), dtype=np.float32)
    c, g, f = rgb(core), rgb(glow), rgb(frame)
    r = np.random.RandomState(seed)
    # Glass frames: only the edges of every 8x8 face are solid (cutout rendering).
    for fy in range(0, 16, 8):
        for fx in range(0, 32, 8):
            face = img[fy:fy + 8, fx:fx + 8]
            face[0, :, :3] = face[-1, :, :3] = face[:, 0, :3] = face[:, -1, :3] = f
            face[0, :, 3] = face[-1, :, 3] = face[:, 0, 3] = face[:, -1, 3] = 255
            for k in (1, 6):                       # dotted inner line
                for t in range(1, 7, 2):
                    face[k, t, :3] = f * 0.9; face[k, t, 3] = 255
                    face[t, k, :3] = f * 0.9; face[t, k, 3] = 255
    # Core: glowing blotches with a small rune.
    n = noise((16, 32), r.randint(1000), 2)
    core_img = shade(n, c * 0.7, c, g)
    for fy in range(0, 16, 8):
        for fx in range(0, 32, 8):
            for (x, y) in ((3, 2), (3, 3), (3, 4), (4, 4), (4, 5), (2, 3)):
                core_img[fy + y, fx + x] = g * 0.55 + 90
    img[0:16, 32:64, :3] = core_img
    img[0:16, 32:64, 3] = 255
    # Base: dark stone tinted with the style color.
    base = shade(noise((16, 48), r.randint(1000)), c * 0.12 + 8, c * 0.2 + 16, c * 0.32 + 26)
    img[16:32, 0:48, :3] = base
    img[16:32, 0:48, 3] = 255
    save('crystal', style, label, core, {'textures/entity/end_crystal/end_crystal.png': img})


crystal('amethyst', 'Amethyst', '#9b5cf0', '#e0c8ff', '#e6dcff', 101)
crystal('aqua', 'Aqua', '#35c6f0', '#c8f4ff', '#d8f6ff', 102)
crystal('inferno', 'Inferno', '#f0622a', '#ffd0a0', '#ffe2cc', 103)
crystal('toxic', 'Toxic', '#4fe070', '#d0ffd8', '#dcffe2', 104)
crystal('frost', 'Frost', '#cfe6ff', '#ffffff', '#ffffff', 105)
crystal('gold', 'Gold', '#f2c430', '#fff0b0', '#fff2c8', 106)
crystal('eclipse', 'Eclipse', '#2a2a30', '#8a8a96', '#f0f0f4', 107)


# ---------------------------------------------------------- respawn anchor
def anchor(style, label, stone, accent, glow):
    s, a, g = rgb(stone), rgb(accent), rgb(glow)
    seed = sum(map(ord, style))

    def stone_tex(k):
        return shade(noise((16, 16), seed + k), s * 0.6, s, s * 1.35 + 6)

    files = {}
    for charges in range(5):
        img = stone_tex(charges)
        img[0, :] = img[-1, :] = s * 0.45
        img[5:11, 2:14] = s * 0.35                           # recessed panel
        for i in range(4):                                   # four charge lamps
            x = 3 + i * 3
            img[7:9, x:x + 2] = g if i < charges else a * 0.3
        img[3, 2:14] = a * 0.8
        img[12, 2:14] = a * 0.8
        files[f'textures/block/respawn_anchor_side{charges}.png'] = img
    top_off = stone_tex(7)
    top_off[3:13, 3:13] = s * 0.3
    top_off[2, 2:14] = top_off[13, 2:14] = top_off[2:14, 2] = top_off[2:14, 13] = a * 0.6
    files['textures/block/respawn_anchor_top_off.png'] = top_off
    top = top_off.copy()
    yy, xx = np.mgrid[0:16, 0:16]
    dist = np.sqrt((yy - 7.5) ** 2 + (xx - 7.5) ** 2)
    swirl = (np.sin(dist * 1.6 + np.arctan2(yy - 7.5, xx - 7.5) * 2) > 0)
    inner = dist < 5.2
    top[inner & swirl] = g
    top[inner & ~swirl] = a
    files['textures/block/respawn_anchor_top.png'] = top
    files['textures/block/respawn_anchor_bottom.png'] = stone_tex(9)
    save('anchor', style, label, accent, files)


anchor('vibrant', 'Vibrant', '#2a1c3a', '#9b3cf0', '#e6b8ff')
anchor('golden', 'Golden', '#2e2416', '#e09a1e', '#ffe08a')
anchor('aqua', 'Aqua', '#16282e', '#1eb8d0', '#b0f6ff')
anchor('void', 'Void', '#161618', '#9a9aa6', '#ffffff')
anchor('neon', 'Neon', '#1a1630', '#f02ac0', '#3af0ff')
anchor('royal', 'Royal', '#241a30', '#7a3ad0', '#ffd24a')

with open(os.path.join(OUT, 'index.json'), 'w') as fh:
    json.dump(index, fh, indent=1)
print({k: len(v) for k, v in index.items()})
