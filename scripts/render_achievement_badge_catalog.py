#!/usr/bin/env python3
"""Validate and render a contact sheet for SubHub achievement badge resources."""

from __future__ import annotations

import hashlib
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
MANAGER = ROOT / "app/src/main/java/com/subhub/app/stats/AchievementManager.java"
DRAWABLES = ROOT / "app/src/main/res/drawable-nodpi"
OUTPUT = ROOT / "docs/brand/achievement-badge-catalog.png"

ICON_SIZE = 192
COLUMNS = 7
CARD_WIDTH = 220
CARD_HEIGHT = 250
MARGIN = 28


def catalog_ids() -> list[str]:
    source = MANAGER.read_text(encoding="utf-8")
    return re.findall(r'a\("([a-z0-9_]+)"', source)


def font(size: int) -> ImageFont.ImageFont:
    for candidate in ("arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(candidate, size=size)
        except OSError:
            continue
    return ImageFont.load_default()


def main() -> None:
    ids = catalog_ids()
    if len(ids) != 58 or len(ids) != len(set(ids)):
        raise SystemExit(f"Expected 58 unique achievement IDs, found {len(ids)}")

    badges: list[tuple[str, Image.Image]] = []
    digests: set[str] = set()
    for achievement_id in ids:
        path = DRAWABLES / f"achievement_badge_{achievement_id}.webp"
        if not path.is_file():
            raise SystemExit(f"Missing {path.relative_to(ROOT)}")
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if digest in digests:
            raise SystemExit(f"Reused badge pixels: {path.name}")
        digests.add(digest)

        badge = Image.open(path).convert("RGBA")
        if badge.size != (ICON_SIZE, ICON_SIZE):
            raise SystemExit(f"Unexpected dimensions for {path.name}: {badge.size}")
        if any(badge.getpixel(point)[3] for point in (
                (0, 0), (ICON_SIZE - 1, 0),
                (0, ICON_SIZE - 1), (ICON_SIZE - 1, ICON_SIZE - 1))):
            raise SystemExit(f"Badge corners must be transparent: {path.name}")
        badges.append((achievement_id, badge))

    rows = (len(badges) + COLUMNS - 1) // COLUMNS
    width = MARGIN * 2 + COLUMNS * CARD_WIDTH
    height = 112 + MARGIN + rows * CARD_HEIGHT
    sheet = Image.new("RGB", (width, height), "#09050f")
    draw = ImageDraw.Draw(sheet)
    title_font = font(42)
    label_font = font(18)
    meta_font = font(20)

    draw.text((MARGIN, 24), "SUBHUB · 58 ORIGINAL ACHIEVEMENT BADGES",
              fill="#f5edf8", font=title_font)
    draw.text((MARGIN, 76), "One badge per milestone · coordinated tier families",
              fill="#c986df", font=meta_font)

    for index, (achievement_id, badge) in enumerate(badges):
        column = index % COLUMNS
        row = index // COLUMNS
        left = MARGIN + column * CARD_WIDTH
        top = 112 + MARGIN + row * CARD_HEIGHT
        draw.rounded_rectangle(
            (left + 6, top + 6, left + CARD_WIDTH - 8, top + CARD_HEIGHT - 10),
            radius=20,
            fill="#171020",
            outline="#4f255f",
            width=2,
        )
        icon_left = left + (CARD_WIDTH - ICON_SIZE) // 2
        sheet.paste(badge, (icon_left, top + 10), badge)
        label = achievement_id.replace("_", " ")
        box = draw.textbbox((0, 0), label, font=label_font)
        text_width = box[2] - box[0]
        draw.text(
            (left + (CARD_WIDTH - text_width) // 2, top + 210),
            label,
            fill="#eee5f2",
            font=label_font,
        )

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUTPUT, optimize=True)
    print(f"Rendered {len(badges)} unique badges to {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
