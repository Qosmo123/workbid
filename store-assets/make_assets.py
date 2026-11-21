"""Builds the Google Play listing images from the app's logo and colours.

    python store-assets/make_assets.py            icon + feature graphic
    python store-assets/make_assets.py shots DIR  also frame raw screenshots from DIR into phone-*.png

Needs Pillow (pip install pillow). Fonts come from Windows (Segoe UI); change FONT_* below on a Mac.
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
INK = (27, 36, 41)
INK_DEEP = (18, 26, 30)
YELLOW = (230, 178, 42)
LIGHT = (238, 240, 236)
MUTED = (151, 163, 167)
TEAL = (13, 102, 112)
FONT_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
FONT_BLACK = r"C:\Windows\Fonts\seguibl.ttf"
FONT_REG = r"C:\Windows\Fonts\segoeui.ttf"


def font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.truetype(FONT_BOLD, size)


def house(draw, cx, cy, scale):
    """The Workbid mark (same paths as res/drawable/ic_launcher_foreground.xml), centred on (cx, cy)."""
    def p(x, y):
        return (cx + (x - 54) * scale, cy + (y - 55) * scale)

    def poly(points, colour, width):
        pts = [p(*q) for q in points]
        draw.line(pts, fill=colour, width=int(width * scale), joint="curve")
        r = width * scale / 2
        for x, y in (pts[0], pts[-1]):
            draw.ellipse([x - r, y - r, x + r, y + r], fill=colour)

    poly([(39, 51), (39, 74), (69, 74), (69, 51)], LIGHT, 6)
    poly([(48, 74), (48, 61), (60, 61), (60, 74)], LIGHT, 4)
    poly([(32, 54), (54, 36), (76, 54)], YELLOW, 7)


def icon():
    s = 4  # draw big, then shrink, for smooth edges
    img = Image.new("RGB", (512 * s, 512 * s), INK)
    house(ImageDraw.Draw(img), 256 * s, 262 * s, 6.4 * s)
    img.resize((512, 512), Image.LANCZOS).save(os.path.join(HERE, "icon-512.png"))


def feature_graphic():
    s = 2
    W, H = 1024 * s, 500 * s
    img = Image.new("RGB", (W, H), INK)
    d = ImageDraw.Draw(img)
    # faint oversized roofline
    d.line([(560 * s, 520 * s), (840 * s, 150 * s), (1120 * s, 520 * s)], fill=(36, 47, 54), width=56 * s, joint="curve")
    # mark
    d.rounded_rectangle([72 * s, 120 * s, 196 * s, 244 * s], radius=28 * s, fill=INK_DEEP)
    house(d, 134 * s, 184 * s, 2.0 * s)
    d.text((72 * s, 262 * s), "Workbid", font=font(FONT_BLACK, 92 * s), fill=LIGHT)
    d.text((76 * s, 378 * s), "Post a home job. Get local bids. Book a date.", font=font(FONT_REG, 30 * s), fill=MUTED)
    img.resize((1024, 500), Image.LANCZOS).save(os.path.join(HERE, "feature-graphic.png"))


SHOTS = [
    ("02-myjobs.png", "Every job, sorted by", "what needs you"),
    ("03-job-bids.png", "Compare bids", "side by side"),
    ("05-findwork.png", "Contractors: find", "work near you"),
    ("06-bidform.png", "Send a price and", "a date in a minute"),
    ("07-schedule.png", "Bookings land in", "your schedule"),
    ("08-workorder.png", "Approve work orders", "with clear totals"),
]


def framed_shots(src_dir):
    """1080x1920 (9:16) store screenshots: caption on top, the app screenshot below with rounded corners."""
    W, H = 1080, 1920
    n = 0
    for name, line1, line2 in SHOTS:
        path = os.path.join(src_dir, name)
        if not os.path.exists(path):
            print("skip (missing):", name)
            continue
        n += 1
        canvas = Image.new("RGB", (W, H), INK)
        d = ImageDraw.Draw(canvas)
        f = font(FONT_BLACK, 74)
        d.text((W / 2, 150), line1, font=f, fill=LIGHT, anchor="mm")
        d.text((W / 2, 245), line2, font=f, fill=YELLOW, anchor="mm")
        shot = Image.open(path).convert("RGB")
        target_w = 820
        target_h = int(shot.height * target_w / shot.width)
        shot = shot.resize((target_w, target_h), Image.LANCZOS)
        mask = Image.new("L", shot.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, shot.width, shot.height], radius=48, fill=255)
        x, y = (W - target_w) // 2, 360
        d.rounded_rectangle([x - 14, y - 14, x + target_w + 14, y + target_h + 14], radius=60, fill=INK_DEEP)
        canvas.paste(shot, (x, y), mask)  # bottom runs off the canvas on purpose
        canvas.save(os.path.join(HERE, f"phone-{n}.png"))
    print(f"framed {n} screenshots")


if __name__ == "__main__":
    icon()
    feature_graphic()
    if len(sys.argv) > 2 and sys.argv[1] == "shots":
        framed_shots(sys.argv[2])
    print("done")
