#!/usr/bin/env python3
"""Frame raw SceneDeck screenshots into Play Store marketing compositions.

Wraps each 1080x2400 raw capture in a minimal device frame on the brand
indigo ground with a caption, exporting 1080x2400 PNGs to ../framed/.

Usage: frame_screenshots.py  (run from anywhere; paths are relative to this file)
Requires: cairosvg, Pillow.
"""
import io
import os

import cairosvg
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "..", "screenshots", "raw")
OUT = os.path.join(HERE, "..", "screenshots", "framed")
BRAND = os.path.join(HERE, "..", "..", "brand", "logo.svg")

W, H = 1080, 2400
# System-bar crop on raw captures (status bar top, 3-button nav bottom).
CROP_TOP, CROP_BOTTOM = 96, 2274

# Phone frame geometry (canvas coords).
BEZEL = 26
PHONE_X, PHONE_Y = 62, 330
PHONE_W, PHONE_H = 956, 1920
SCREEN_X = PHONE_X + BEZEL
SCREEN_Y = PHONE_Y + BEZEL
SCREEN_W = PHONE_W - 2 * BEZEL
SCREEN_H = PHONE_H - 2 * BEZEL

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

SHOTS = [
    ("live-scenedeck-dark.png", "Your stream, at your fingertips", "Tap a card. You're live on that scene."),
    ("mixer-live-meters.png", "A mixer that moves with you", "Live meters, faders and mute on every strip."),
    ("stats-dashboard.png", "Stream health at a glance", "FPS, render time, drops and bitrate trends."),
    ("inventory-curation.png", "Curate your deck", "Roles, accents, icons and order — stored locally."),
    ("settings-themes.png", "Make it yours", "Twelve theme families, light and dark."),
    ("live-obs.png", "One deck, many looks", "Shown here in the OBS theme."),
    ("live-dracula.png", "One deck, many looks", "Shown here in the Dracula theme."),
]

BG_SVG = """<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">
  <defs>
    <radialGradient id="bg" cx="0.5" cy="0.30" r="0.9">
      <stop offset="0" stop-color="#201A38"/>
      <stop offset="1" stop-color="#0D0A16"/>
    </radialGradient>
    <radialGradient id="glow" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0" stop-color="#7C5CFF" stop-opacity="0.22"/>
      <stop offset="1" stop-color="#7C5CFF" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <rect width="{w}" height="{h}" fill="url(#bg)"/>
  <g fill="none" stroke="#A79BFA" stroke-opacity="0.05" stroke-width="2">
    {cards}
  </g>
  <circle cx="{gx}" cy="{gy}" r="620" fill="url(#glow)"/>
</svg>"""


def render_bg():
    cards = []
    cw, ch, gap = 175, 105, 26
    cols = int(W / (cw + gap)) + 2
    rows = int(H / (ch + gap)) + 2
    for r in range(rows):
        for c in range(cols):
            x = -20 + c * (cw + gap)
            y = -20 + r * (ch + gap)
            cards.append(
                f'<rect x="{x}" y="{y}" width="{cw}" height="{ch}" rx="18"/>')
    svg = BG_SVG.format(w=W, h=H, cards="\n    ".join(cards), gx=W // 2, gy=H - 300)
    png = cairosvg.svg2png(bytestring=svg.encode(), output_width=W, output_height=H)
    return Image.open(io.BytesIO(png)).convert("RGBA")


def rounded_mask(size, radius):
    mask = Image.new("L", size, 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius=radius, fill=255)
    return mask


def load_logo(size):
    png = cairosvg.svg2png(url=BRAND, output_width=size, output_height=size)
    return Image.open(io.BytesIO(png)).convert("RGBA")


def frame(shot_path, headline, subline, bg, logo):
    canvas = bg.copy()
    d = ImageDraw.Draw(canvas)

    # Caption
    f_head = ImageFont.truetype(FONT_BOLD, 62)
    f_sub = ImageFont.truetype(FONT_REG, 38)
    tw = d.textlength(headline, font=f_head)
    d.text(((W - tw) / 2, 118), headline, font=f_head, fill="#F4F2FA")
    sw = d.textlength(subline, font=f_sub)
    d.text(((W - sw) / 2, 208), subline, font=f_sub, fill="#A79BFA")

    # Phone body
    d.rounded_rectangle(
        [PHONE_X, PHONE_Y, PHONE_X + PHONE_W, PHONE_Y + PHONE_H],
        radius=92, fill="#1B1530", outline="#3D3359", width=3)

    # Screenshot into screen area
    shot = Image.open(shot_path).convert("RGB")
    shot = shot.crop((0, CROP_TOP, shot.width, CROP_BOTTOM))
    shot = shot.resize((SCREEN_W, SCREEN_H), Image.LANCZOS)
    mask = rounded_mask((SCREEN_W, SCREEN_H), 68)
    canvas.paste(shot, (SCREEN_X, SCREEN_Y), mask)

    # Brand lockup bottom-center
    ls = 72
    lg = logo.resize((ls, ls), Image.LANCZOS)
    f_brand = ImageFont.truetype(FONT_BOLD, 40)
    bw = d.textlength("SceneDeck", font=f_brand)
    total = ls + 18 + bw
    x0 = (W - total) / 2
    canvas.paste(lg, (int(x0), 2300), lg)
    d.text((x0 + ls + 18, 2312), "SceneDeck", font=f_brand, fill="#F4F2FA")

    return canvas.convert("RGB")


def main():
    os.makedirs(OUT, exist_ok=True)
    bg = render_bg()
    logo = load_logo(512)
    for name, head, sub in SHOTS:
        src = os.path.join(RAW, name)
        dst = os.path.join(OUT, name)
        frame(src, head, sub, bg, logo).save(dst, "PNG")
        print("framed", name)


if __name__ == "__main__":
    main()
