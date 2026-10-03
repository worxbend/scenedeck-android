#!/usr/bin/env python3
"""Render existing brand SVGs and export app previews for the Play store listing.

Run with: uv run --with pillow --with cairosvg python3 scripts/prepare-play-assets.py
"""

from pathlib import Path
import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
IMAGES = ROOT / "fastlane/metadata/android/en-US/images"


def main():
    screenshots = IMAGES / "phoneScreenshots"
    screenshots.mkdir(parents=True, exist_ok=True)
    logo = (ROOT / "assets/brand/logo.svg").read_text().replace('rx="112"', '')
    cairosvg.svg2png(bytestring=logo.encode(), write_to=str(IMAGES / "icon.png"))
    with Image.open(IMAGES / "icon.png") as image:
        image.convert("RGBA").save(IMAGES / "icon.png")
    cairosvg.svg2png(url=str(ROOT / "assets/store/feature-graphic-1024x500.svg"),
                    write_to=str(IMAGES / "featureGraphic.png"))
    with Image.open(IMAGES / "featureGraphic.png") as image:
        image.convert("RGB").save(IMAGES / "featureGraphic.png")
    for index, name in enumerate(["scenes", "mixer", "stats", "connections"], 1):
        with Image.open(ROOT / f"assets/readme/{name}.png") as image:
            image = image.convert("RGB")
            # Keep every app pixel. Add a neutral matte for Play's maximum 2:1 ratio.
            width = max(image.width, (image.height + 1) // 2)
            export = Image.new("RGB", (width, image.height), image.getpixel((0, 0)))
            export.paste(image, ((width - image.width) // 2, 0))
            assert min(export.size) >= 320 and max(export.size) <= 3840
            assert max(export.size) <= 2 * min(export.size)
            export.save(screenshots / f"{index:02d}-{name}.png")
    print(f"Play listing images exported to {IMAGES}")


if __name__ == "__main__":
    main()
