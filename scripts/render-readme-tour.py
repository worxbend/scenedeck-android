#!/usr/bin/env python3
"""Assemble the README tour from rendered application previews (requires ffmpeg)."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "assets" / "readme"
PREVIEWS = {
    "scenes": "feature/live/broadcast_idle_dark.png",
    "streaming": "feature/live/broadcast_streaming_dark.png",
    "recording": "feature/live/broadcast_recording_dark.png",
    "studio": "feature/live/broadcast_studio_dark.png",
    "scene-switching": "feature/live/broadcast_switched_scene_dark.png",
    "stream-and-record": "feature/live/broadcast_simultaneous_dark.png",
    "mixer": "feature/mixer/mixer_screen_dark.png",
    "stats": "feature/stats/stats_screen_populated_dark.png",
    "settings": "feature/settings/settings_scenedeck_dark.png",
    "connections": "feature/connections/connections_scenedeck_dark.png",
    "scenes-light": "feature/live/live_deck_scenedeck_light.png",
    "recording-light": "feature/live/broadcast_recording_light.png",
}
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def caption(text: str, color: str, size: int, y: int) -> str:
    return (f"drawtext=fontfile={FONT}:text='{text}':fontcolor={color}:"
            f"fontsize={size}:x=425:y={y}")


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, source in PREVIEWS.items():
        shutil.copyfile(ROOT / source, OUTPUT / f"{name}.png")
    with tempfile.TemporaryDirectory(prefix="scenedeck-tour-") as staging:
        manifest = Path(staging) / "frames.txt"
        frames = []
        for name in PREVIEWS:
            path = str(OUTPUT / f"{name}.png").replace("'", "'\\''")
            frames.append(f"file '{path}'\nduration 2\n")
        frames.append(f"file '{path}'\n")
        manifest.write_text("".join(frames), encoding="utf-8")
        filters = [
            "fps=6", "scale=270:600:force_original_aspect_ratio=decrease",
            "pad=960:720:90:90:color=0x0F141B",
            caption("SCENEDECK", "0x80BDFF", 18, 160),
            caption("Your studio.", "white", 40, 210),
            caption("In your pocket.", "white", 40, 262),
            caption("SCENES  /  MIXER  /  STATS", "0x80BDFF", 18, 352),
            caption("Kotlin + Compose + OBS", "0xB5C0CF", 18, 396),
            caption("Rendered app previews", "0x7B899C", 14, 560),
        ]
        graph = ",".join(filters) + ",split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer"
        subprocess.run([
            "ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0",
            "-i", str(manifest), "-vf", graph, "-loop", "0", str(OUTPUT / "studio-tour.gif"),
        ], check=True)


if __name__ == "__main__":
    main()
