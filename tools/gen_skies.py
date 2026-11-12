#!/usr/bin/env python3
"""Generates the sky textures in src/main/resources/assets/musicmod/skies/.

Each texture is a cube map in the OptiFine sky layout (3 columns x 2 rows):
    bottom | top  | east
    south  | west | north
Everything is a function of the view direction, so the side faces join
seamlessly. Near the zenith the picture is made symmetric under 90-degree turns
and mirroring, so the top face matches the sides whichever way a renderer
orients it.

Usage: python3 tools/gen_skies.py   (needs numpy and Pillow)
"""
import os
import numpy as np
from PIL import Image

N = 320  # pixels per cube face
OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'musicmod', 'skies')


def hash3(ix, iy, iz, seed):
    h = (ix.astype(np.uint32) * np.uint32(374761393) + iy.astype(np.uint32) * np.uint32(668265263)
         + iz.astype(np.uint32) * np.uint32(2147483647) + np.uint32(seed * 1013904223 & 0xFFFFFFFF))
    h = (h ^ (h >> np.uint32(13))) * np.uint32(1274126177)
    h = h ^ (h >> np.uint32(16))
    return (h & np.uint32(0xFFFFFF)).astype(np.float32) / np.float32(0x1000000)


def vnoise(p, seed):
    i = np.floor(p).astype(np.int64)
    f = (p - i).astype(np.float32)
    u = f * f * (3 - 2 * f)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = ((u[..., 0] if dx else 1 - u[..., 0]) * (u[..., 1] if dy else 1 - u[..., 1])
                     * (u[..., 2] if dz else 1 - u[..., 2]))
                out = out + w * hash3(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz, seed)
    return out


def fbm(p, seed, octaves=5):
    total, amp, norm = 0, 0.5, 0
    for o in range(octaves):
        total = total + amp * vnoise(p * (2 ** o), seed + o * 17)
        norm += amp
        amp *= 0.5
    return total / norm


def stars(d, seed, density, cells=70.0):
    p = d * cells
    i = np.floor(p).astype(np.int64)
    f = p - i
    exists = hash3(i[..., 0], i[..., 1], i[..., 2], seed) < density
    c = np.stack([0.3 + 0.4 * hash3(i[..., 0], i[..., 1], i[..., 2], seed + k) for k in (1, 2, 3)], -1)
    dist2 = ((f - c) ** 2).sum(-1)
    size = 0.004 + 0.03 * hash3(i[..., 0], i[..., 1], i[..., 2], seed + 4) ** 3
    bright = 0.45 + 0.55 * hash3(i[..., 0], i[..., 1], i[..., 2], seed + 5)
    return np.where(exists, bright * np.exp(-dist2 / size), 0.0)


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def ramp(e, stops):
    """Piecewise-linear color ramp over elevation (degrees)."""
    xs = [s[0] for s in stops]
    cols = np.array([s[1] for s in stops], dtype=np.float32) / 255.0
    return np.stack([np.interp(e, xs, cols[:, k]) for k in range(3)], -1)


def rgb(h):
    return [int(h[i:i + 2], 16) for i in (1, 3, 5)]


# --- skies: each returns RGB (float, 0..1+) for unit directions d -----------

def nebula(d, e, az):
    base = ramp(e, [(-90, rgb('#0b0420')), (0, rgb('#4a1f8f')), (25, rgb('#2b1166')), (90, rgb('#120735'))])
    n1 = fbm(d * 2.1 + 3.0, 11)
    n2 = fbm(d * 3.3 - 5.0, 29)
    cloud = smooth(0.45, 0.75, n1)[..., None] * np.array([0.75, 0.25, 0.95])
    cloud2 = smooth(0.5, 0.8, n2)[..., None] * np.array([0.2, 0.35, 0.95])
    st = stars(d, 3, 0.05)[..., None]
    return base + 0.6 * cloud + 0.45 * cloud2 + st * np.array([1.0, 0.95, 1.0])


def galaxy(d, e, az):
    base = ramp(e, [(-90, rgb('#02030a')), (0, rgb('#0d1433')), (30, rgb('#070b22')), (90, rgb('#03040f'))])
    centre = 16 + 7 * np.sin(2 * az + 0.6)
    band = np.exp(-((e - centre) / 9.0) ** 2) * (0.35 + 0.9 * fbm(d * 4.0, 41))
    dust = smooth(0.5, 0.72, fbm(d * 7.0 + 9.0, 53))
    glow = (band * (1 - 0.6 * dust))[..., None] * np.array([0.75, 0.7, 1.0])
    core = (np.exp(-((e - centre) / 4.0) ** 2) * smooth(0.4, 0.8, fbm(d * 2.5, 61)))[..., None] * np.array([1.0, 0.8, 0.65])
    st = (stars(d, 7, 0.09) + 0.8 * stars(d, 8, 0.12, 120.0) * (0.4 + band))[..., None]
    return base + 0.7 * glow + 0.35 * core + st


def aurora(d, e, az):
    base = ramp(e, [(-90, rgb('#010608')), (0, rgb('#06202a')), (35, rgb('#04131f')), (90, rgb('#020912'))])
    flat = np.stack([np.cos(az), np.zeros_like(az), np.sin(az)], -1)
    centre = 14 + 12 * (fbm(flat * 2.2, 71, 3) - 0.5) * 2
    curtain = np.exp(-np.clip(e - centre, 0, None) / 9.0) * np.exp(-(np.clip(centre - e, 0, None) / 2.5) ** 2)
    rays = 0.45 + 0.9 * fbm(flat * 26.0, 83, 3)
    strength = smooth(0.35, 0.7, fbm(flat * 1.6 + 4.0, 97, 3))
    a = (curtain * rays * strength)[..., None]
    tint = np.where((e - centre)[..., None] > 9, np.array([0.55, 0.3, 0.9]), np.array([0.25, 1.0, 0.6]))
    st = stars(d, 13, 0.06)[..., None]
    return base + 1.25 * a * tint + st * np.array([0.9, 1.0, 1.0])


def starry(d, e, az):
    base = ramp(e, [(-90, rgb('#03061a')), (0, rgb('#12245c')), (30, rgb('#0a1640')), (90, rgb('#050a25'))])
    mist = smooth(0.5, 0.85, fbm(d * 2.4, 101))[..., None] * np.array([0.15, 0.2, 0.45])
    st = (stars(d, 17, 0.11) + 0.7 * stars(d, 18, 0.14, 130.0))[..., None]
    return base + 0.5 * mist + st * np.array([1.0, 1.0, 0.95])


def _sunset(d, e, az, stops, sun_col, cloud_col, seed):
    base = ramp(e, stops)
    sun_dir = np.array([-np.cos(np.radians(4)), np.sin(np.radians(4)), 0.0])  # low in the west
    ang = np.degrees(np.arccos(np.clip((d * sun_dir).sum(-1), -1, 1)))
    glow = (np.exp(-(ang / 28.0) ** 2) * 0.55 + np.exp(-(ang / 7.0) ** 2) * 0.9)[..., None] * sun_col
    streaks = smooth(0.52, 0.78, fbm(d * np.array([3.0, 14.0, 3.0]), seed))
    low = np.exp(-((e - 16) / 14.0) ** 2)
    clouds = (streaks * low)[..., None] * cloud_col
    st = (stars(d, seed + 3, 0.04) * smooth(35, 70, e))[..., None]
    return base * (1 - 0.35 * (streaks * low)[..., None]) + glow + 0.55 * clouds + 0.6 * st


def sunset(d, e, az):
    stops = [(-90, rgb('#2a1420')), (-4, rgb('#7a3a2a')), (0, rgb('#ff9a4a')), (10, rgb('#ff6f6f')),
             (28, rgb('#8a4a9c')), (55, rgb('#34306e')), (90, rgb('#1a1d4a'))]
    return _sunset(d, e, az, stops, np.array([1.0, 0.7, 0.3]), np.array([1.0, 0.55, 0.45]), 113)


def pink_sunset(d, e, az):
    stops = [(-90, rgb('#3a2440')), (-4, rgb('#b06a8a')), (0, rgb('#ffc2a8')), (10, rgb('#ff9ec4')),
             (30, rgb('#c79be0')), (60, rgb('#8f8fe0')), (90, rgb('#6a78d0'))]
    return _sunset(d, e, az, stops, np.array([1.0, 0.8, 0.7]), np.array([1.0, 0.8, 0.9]), 127)



def _tangent(d, az_deg, el_deg):
    """Flat coordinates (degrees) around a point of the sky: u to the right, v up, plus the angle to it."""
    az, el = np.radians(az_deg), np.radians(el_deg)
    c = np.array([np.cos(el) * np.cos(az), np.sin(el), np.cos(el) * np.sin(az)])
    r = np.cross(c, np.array([0.0, 1.0, 0.0]))
    r /= np.linalg.norm(r)
    up = np.cross(r, c)
    dc = np.clip((d * c).sum(-1), 1e-6, None)
    front = (d * c).sum(-1) > 0.2
    u = np.degrees((d * r).sum(-1) / dc)
    v = np.degrees((d * up).sum(-1) / dc)
    return np.where(front, u, 999.0), np.where(front, v, 999.0)


def saturn(d, e, az):
    base = ramp(e, [(-90, rgb('#03030c')), (0, rgb('#141235')), (30, rgb('#0b0a24')), (90, rgb('#05051a'))])
    mist = smooth(0.5, 0.8, fbm(d * 2.6 + 2.0, 211))[..., None] * np.array([0.25, 0.2, 0.4])
    col = base + 0.35 * mist + (stars(d, 31, 0.08) + 0.6 * stars(d, 32, 0.1, 120.0))[..., None]
    u, v = _tangent(d, -90.0, 10.0)                     # the planet hangs low in the north
    tilt = np.radians(-18)
    ur, vr = u * np.cos(tilt) - v * np.sin(tilt), u * np.sin(tilt) + v * np.cos(tilt)
    R = 6.2
    rr = np.sqrt(ur ** 2 + vr ** 2)
    ring_r = np.sqrt(ur ** 2 + (vr / 0.28) ** 2)
    ring = ((ring_r > 1.35 * R) & (ring_r < 2.3 * R)).astype(float) * (0.55 + 0.45 * np.sin(ring_r * 4.0))
    gap = (np.abs(ring_r - 1.9 * R) < 0.12 * R)
    ring = np.where(gap, 0.0, ring)
    planet = smooth(R, R - 0.25, rr)
    bands = 0.82 + 0.18 * np.sin(vr * 2.6 + 0.6 * np.sin(ur * 0.7))
    light = np.clip(0.55 + 0.45 * (-ur * 0.6 + vr * 0.5) / R, 0.15, 1.0)
    pcol = (bands * light)[..., None] * np.array([0.93, 0.8, 0.6])
    rcol = ring[..., None] * np.array([0.85, 0.76, 0.6])
    behind = vr > 0                                      # far half of the ring passes behind the planet
    col = col * (1 - (ring * 0.9)[..., None]) + rcol
    col = np.where((planet > 0)[..., None] & (behind | (ring <= 0))[..., None], col * (1 - planet[..., None]) + pcol * planet[..., None], col)
    return col


def eclipse(d, e, az):
    base = ramp(e, [(-90, rgb('#02040b')), (0, rgb('#1a2647')), (25, rgb('#0d1630')), (90, rgb('#050a1c'))])
    col = base + (stars(d, 37, 0.07) * 0.9)[..., None]
    u, v = _tangent(d, -90.0, 13.0)
    rr = np.sqrt(u ** 2 + v ** 2)
    ang = np.arctan2(v, u)
    rays = 0.6 + 0.4 * np.sin(ang * 23 + 3 * np.sin(ang * 5)) * np.sin(ang * 7 + 1.3)
    R = 3.8
    corona = np.exp(-np.clip(rr - R, 0, None) / 1.3) + 0.55 * np.exp(-np.clip(rr - R, 0, None) / 5.0) * rays
    col = col + corona[..., None] * np.array([0.9, 0.95, 1.0])
    return col * (1 - smooth(R + 0.15, R - 0.1, rr))[..., None]


def blood_moon(d, e, az):
    base = ramp(e, [(-90, rgb('#0a0203')), (0, rgb('#5a1614')), (20, rgb('#3a0d10')), (90, rgb('#120407'))])
    u, v = _tangent(d, -90.0, 13.0)
    rr = np.sqrt(u ** 2 + v ** 2)
    col = base + (np.exp(-rr / 16.0) * 0.5)[..., None] * np.array([0.9, 0.2, 0.15]) + (stars(d, 41, 0.06) * 0.8)[..., None]
    R = 6.6
    disk = smooth(R, R - 0.3, rr)
    seas = smooth(0.45, 0.7, fbm(np.stack([u, v, np.zeros_like(u)], -1) * 0.35 + 7.0, 223))
    craters = smooth(0.62, 0.7, fbm(np.stack([u, v, np.zeros_like(u)], -1) * 1.3, 227))
    shade = np.clip(1.0 - 0.35 * (rr / R) ** 3, 0, 1) * (1 - 0.3 * seas) * (1 - 0.25 * craters)
    mcol = shade[..., None] * np.array([1.0, 0.36, 0.22])
    return col * (1 - disk[..., None]) + mcol * disk[..., None]


def neon_horizon(d, e, az):
    base = ramp(e, [(-90, rgb('#120018')), (-3, rgb('#3a0a4a')), (0, rgb('#ff3fb4')), (8, rgb('#c026b8')),
                    (24, rgb('#4a1a8a')), (50, rgb('#1c0f4a')), (90, rgb('#0d0828'))])
    u, v = _tangent(d, 180.0, 0.5)                      # sun sitting on the western horizon
    rr = np.sqrt(u ** 2 + v ** 2)
    R = 7.0
    stripes = (np.mod(v + 30, 1.6) > 0.55) | (v > 2.5)
    sun = smooth(R, R - 0.3, rr) * stripes * (v > -0.3)
    scol = ramp(np.clip(v * 3, -5, 30), [(-5, rgb('#ff4fa0')), (10, rgb('#ffb040')), (30, rgb('#fff080'))])
    glow = (np.exp(-rr / 14.0) * 0.45)[..., None] * np.array([1.0, 0.35, 0.7])
    st = (stars(d, 43, 0.06) * smooth(18, 45, e))[..., None]
    return (base + glow) * (1 - sun[..., None]) + scol * sun[..., None] + st


def cherry(d, e, az):
    base = ramp(e, [(-90, rgb('#4a3050')), (-4, rgb('#c88aa8')), (0, rgb('#ffd0dc')), (12, rgb('#ffb3d0')),
                    (30, rgb('#d9a6e8')), (60, rgb('#b19cf0')), (90, rgb('#9a8cf0'))])
    puffs = smooth(0.48, 0.72, fbm(d * np.array([2.5, 7.0, 2.5]) + 1.0, 233))
    low = np.exp(-((e - 20) / 16.0) ** 2)
    wisps = smooth(0.55, 0.8, fbm(d * np.array([5.0, 16.0, 5.0]), 239)) * np.exp(-((e - 9) / 8.0) ** 2)
    return base * (1 - 0.15 * (puffs * low)[..., None]) + 0.5 * (puffs * low)[..., None] * np.array([1.0, 0.92, 0.98]) \
        + 0.35 * wisps[..., None] * np.array([1.0, 0.8, 0.9])


SKIES = {'nebula': nebula, 'galaxy': galaxy, 'aurora': aurora, 'starry': starry,
         'sunset': sunset, 'pink_sunset': pink_sunset, 'saturn': saturn, 'eclipse': eclipse,
         'blood_moon': blood_moon, 'neon_horizon': neon_horizon, 'cherry': cherry}


def shade(fn, d):
    e = np.degrees(np.arcsin(np.clip(d[..., 1], -1, 1)))
    az = np.arctan2(d[..., 2], d[..., 0])
    return fn(d, e, az)


def render(fn):
    a = (np.arange(N) + 0.5) / N * 2 - 1          # left -> right
    b = 1 - (np.arange(N) + 0.5) / N * 2          # top -> bottom of the image is +1 -> -1
    A, B = np.meshgrid(a, b)
    one = np.ones_like(A)
    faces = {                                      # direction for each face pixel
        'south': (-A, B, one), 'west': (-one, B, -A), 'north': (A, B, -one), 'east': (one, B, A),
        'top': (A, one, B), 'bottom': (A, -one, B),
    }
    layout = [['bottom', 'top', 'east'], ['south', 'west', 'north']]
    img = np.zeros((2 * N, 3 * N, 3), dtype=np.float32)
    for r, row in enumerate(layout):
        for c, name in enumerate(row):
            d = np.stack(faces[name], -1).astype(np.float64)
            d /= np.linalg.norm(d, axis=-1, keepdims=True)
            raw = shade(fn, d)
            # Symmetric version (8 turns/mirrors about the vertical axis), used near the poles.
            sym = 0
            x, y, z = d[..., 0], d[..., 1], d[..., 2]
            for (px, pz) in ((x, z), (-x, z), (x, -z), (-x, -z), (z, x), (-z, x), (z, -x), (-z, -x)):
                sym = sym + shade(fn, np.stack([px, y, pz], -1)) / 8.0
            w = smooth(20, 32, np.abs(np.degrees(np.arcsin(np.clip(y, -1, 1)))))[..., None]
            img[r * N:(r + 1) * N, c * N:(c + 1) * N] = raw * (1 - w) + sym * w
    img = np.clip(img, 0, 1) ** (1 / 1.05)
    return Image.fromarray((img * 255 + 0.5).astype(np.uint8), 'RGB')


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    import sys
    only = sys.argv[1:]
    for name, fn in SKIES.items():
        if only and name not in only:
            continue
        path = os.path.join(OUT, name + '.png')
        render(fn).save(path, optimize=True)
        print(name, os.path.getsize(path) // 1024, 'KB')
