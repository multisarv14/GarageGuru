"""Generate Garage Guru launcher icons from the master 1024px badge artwork."""
import math
import os
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, 'assets/branding/logo_master_1024.png')
MAIN = os.path.join(ROOT, 'app/src/main')
RES = os.path.join(MAIN, 'res')

# Badge geometry measured from the master artwork
BADGE_CX, BADGE_CY, BADGE_R = 512, 489, 413
INNER_R = 330            # inside the chrome ring
KEY_LO, KEY_HI = 95, 165  # luminance keying range that isolates the silver mark

DENSITIES = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}

src = Image.open(SRC).convert('RGBA')


def extract_mark(lo=KEY_LO, hi=KEY_HI):
    """Silver GG+wrench mark on transparent background, tightly cropped."""
    box = (BADGE_CX - INNER_R, BADGE_CY - INNER_R,
           BADGE_CX + INNER_R, BADGE_CY + INNER_R)
    inner = src.crop(box)
    alpha = inner.convert('L').point(
        lambda v: 0 if v <= lo else (255 if v >= hi else int((v - lo) * 255 / (hi - lo))))
    circle = Image.new('L', inner.size, 0)
    ImageDraw.Draw(circle).ellipse((0, 0, inner.size[0] - 1, inner.size[1] - 1), fill=255)
    alpha = ImageChops.multiply(alpha, circle)
    mark = inner.copy()
    mark.putalpha(alpha)
    return mark.crop(mark.getbbox())


def extract_badge():
    """The whole chrome-ringed badge, alpha-masked to a clean circle.

    The launcher mask is a circle on some phones and a squircle on others, so the round
    outline has to live in the artwork itself rather than being borrowed from the mask.
    """
    box = (BADGE_CX - BADGE_R, BADGE_CY - BADGE_R,
           BADGE_CX + BADGE_R, BADGE_CY + BADGE_R)
    badge = src.crop(box)
    circle = Image.new('L', badge.size, 0)
    ImageDraw.Draw(circle).ellipse((0, 0, badge.size[0] - 1, badge.size[1] - 1), fill=255)
    badge.putalpha(ImageChops.multiply(badge.getchannel('A'), circle))
    return badge


def max_radius(img, threshold=60):
    w, h = img.size
    a = img.getchannel('A').load()
    cx, cy = w / 2, h / 2
    best = 0.0
    for y in range(h):
        for x in range(w):
            if a[x, y] > threshold:
                best = max(best, math.hypot(x - cx, y - cy))
    return best


MARK = extract_mark()
MARK_R = max_radius(MARK)
BADGE = extract_badge()

# Badge diameter on the 108dp canvas. The visible area of an adaptive icon is 72dp;
# 60dp sits comfortably inside the 66dp safe zone, so the badge reads as a smaller,
# cleanly centred coin with breathing room on every launcher.
BADGE_DP = 60

# Adaptive icons: 108dp canvas, keep the mark inside the 66dp safe circle (r=33dp).
SAFE_R = 32.5


def adaptive_badge(size):
    """The full round badge centred on a transparent `size`x`size` canvas."""
    dia = max(1, round(size * BADGE_DP / 108.0))
    layer = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    layer.alpha_composite(BADGE.resize((dia, dia), Image.LANCZOS),
                          ((size - dia) // 2, (size - dia) // 2))
    return layer


def adaptive_layer(size, mark):
    """Mark centred on a transparent `size`x`size` canvas, scaled into the safe circle."""
    scale = (size * SAFE_R / 108.0) / MARK_R
    w = max(1, round(mark.width * scale))
    h = max(1, round(mark.height * scale))
    layer = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    layer.alpha_composite(mark.resize((w, h), Image.LANCZOS),
                          ((size - w) // 2, (size - h) // 2))
    return layer


def dark_field(size):
    """Radial charcoal field matching the badge interior."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    half = size / 2
    for i in range(int(half), 0, -1):
        t = i / half
        c = (int(0x14 + (0x33 - 0x14) * (1 - t)), int(0x16 + (0x36 - 0x16) * (1 - t)),
             int(0x1A + (0x3B - 0x1A) * (1 - t)), 255)
        d.ellipse((half - i, half - i, half + i, half + i), fill=c)
    return img


def legacy(size):
    """Pre-adaptive launcher icon (API < 26). These are never masked by the system,
    so the badge is drawn at full bleed and its own circle is the icon outline."""
    S = size * 4  # supersample, then downscale
    canvas = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    canvas.alpha_composite(BADGE.resize((S, S), Image.LANCZOS))
    out = canvas.resize((size, size), Image.LANCZOS)
    return out.filter(ImageFilter.UnsharpMask(radius=size / 96, percent=55, threshold=2))


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    print(f'{os.path.relpath(path, MAIN)}  {img.size[0]}x{img.size[1]}')


# Monochrome layer: harder key so the themed-icon silhouette stays legible.
mono_src = extract_mark(lo=110, hi=140)
mono_r = max_radius(mono_src)

for name, factor in DENSITIES.items():
    d = f'{RES}/mipmap-{name}'
    save(legacy(round(48 * factor)), f'{d}/ic_launcher.png')
    save(legacy(round(48 * factor)), f'{d}/ic_launcher_round.png')
    save(adaptive_badge(round(108 * factor)), f'{d}/ic_launcher_foreground.png')

    size = round(108 * factor)
    scale = (size * SAFE_R / 108.0) / mono_r
    w, h = max(1, round(mono_src.width * scale)), max(1, round(mono_src.height * scale))
    mono = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    flat = Image.new('RGBA', (w, h), (255, 255, 255, 255))
    flat.putalpha(mono_src.resize((w, h), Image.LANCZOS).getchannel('A'))
    mono.alpha_composite(flat, ((size - w) // 2, (size - h) // 2))
    save(mono, f'{d}/ic_launcher_monochrome.png')

# Play Store listing icon: 512x512, no transparency, matches the adaptive icon.
store = Image.new('RGBA', (512, 512), (0x0E, 0x11, 0x14, 255))
gd = ImageDraw.Draw(store)
for i in range(362, 0, -1):  # 256*sqrt(2): the gradient must reach the corners
    t = i / 256.0
    c = (int(0x14 + (0x33 - 0x14) * max(0.0, 1 - t)), int(0x16 + (0x36 - 0x16) * max(0.0, 1 - t)),
         int(0x1A + (0x3B - 0x1A) * max(0.0, 1 - t)), 255)
    gd.ellipse((256 - i, 256 - i, 256 + i, 256 + i), fill=c)
store.alpha_composite(adaptive_badge(512))
save(store.convert('RGB'), f'{MAIN}/ic_launcher-playstore.png')

print(f'\nbadge {BADGE.size[0]}x{BADGE.size[1]} -> {BADGE_DP}dp of the 108dp canvas '
      f'(72dp visible), so every launcher mask shows the same round badge')
