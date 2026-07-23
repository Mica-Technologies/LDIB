#!/usr/bin/env python3
"""Generate the per-MATERIAL painted entity skins for LDIB rideables.

Skins are painted per material, not per box: a handful of solid-colour (softly
shaded) rectangles on one 128x64 atlas. Each rectangle is a MATERIAL region; the
models (client/render/ModelRideable.java) build every part at the texture offset
of the material it is made of, so all parts sharing a material share one region.

The atlas layout here MUST match the offset/size constants in ModelRideable.java.
A W x H x D box unwraps to a (2D+2W)-wide x (D+H)-tall footprint; each region is
sized to the largest box that uses it, across both the bike and the scooter:

  material  offset (u,v)   region w x h
  --------  ------------   ------------
  TYRE      (0, 0)         12 x 8
  FRAME     (16, 0)        12 x 20
  ACCENT    (32, 0)        26 x 10
  METAL     (64, 0)        16 x 5

Run:  python tools/gen_skins.py
Writes the four entity PNGs and, with --preview, an 8x scaled PNG per variant to
tools/preview/ for eyeballing that the regions land where the UV offsets expect.
"""
import os
import sys

from PIL import Image

ATLAS_W, ATLAS_H = 128, 64

# material -> (u, v, w, h)  -- keep in lockstep with ModelRideable.java
REGIONS = {
    "tyre":   (0, 0, 12, 8),
    "frame":  (16, 0, 12, 20),
    "accent": (32, 0, 26, 10),
    "metal":  (64, 0, 16, 5),
}

# Per-variant palette: material -> base RGB (original art, brand-inspired only).
NEAR_BLACK = (26, 26, 29)
NEUTRAL_METAL = (108, 110, 116)
PALETTES = {
    # pedal bicycle: classic blue frame, tan saddle/grips, near-black tyres.
    "bike": {
        "tyre":   NEAR_BLACK,
        "frame":  (38, 92, 178),
        "accent": (201, 170, 121),
        "metal":  NEUTRAL_METAL,
    },
    # e-bike (the "electric" one): deep red frame, dark-grey accents, black tyres.
    "ebike": {
        "tyre":   (20, 20, 22),
        "frame":  (152, 30, 36),
        "accent": (70, 72, 79),
        "metal":  (84, 86, 92),
    },
    # kick scooter: white stem, lime grip-tape deck, black tyres, grey fender.
    "scooter": {
        "tyre":   (20, 20, 22),
        "frame":  (232, 233, 236),
        "accent": (139, 201, 51),
        "metal":  NEUTRAL_METAL,
    },
    # performance scooter: matte-black stem, white-stripe deck, black tyres, dark fender.
    "scooter_fast": {
        "tyre":   (18, 18, 20),
        "frame":  (36, 37, 41),
        "accent": (229, 230, 233),
        "metal":  (80, 82, 88),
    },
}

# Public bike-share fleet livery: one MUTED, uniform look shared by every variant, so the fleet reads
# as bland/institutional and distinct from the colourful personal bikes. Geometry (basket/battery/deck)
# still tells the variants apart; only the colour is uniform.
SHARE_LIVERY = {
    "tyre":   NEAR_BLACK,
    "frame":  (100, 116, 138),   # desaturated slate-blue
    "accent": (150, 152, 158),   # muted grey
    "metal":  (110, 112, 118),
}
for _base in ("bike", "ebike", "scooter", "scooter_fast"):
    PALETTES["share_" + _base] = SHARE_LIVERY


def _mul(base, f):
    return tuple(max(0, min(255, round(c * f))) for c in base)


def shade(base, x, w, y, h, mat="frame"):
    """Hand-painted per-material shading so the low-poly parts read as 3D.

    Every region gets the same light-from-the-top-left base gradient (the top of
    a region unwraps to the up-facing faces), then a material-specific detail
    pass paints in the character the flat colour can't carry on its own:

      tyre   - a bright top rim + a diagonal tread-lug pattern
      frame  - a rounded-metal specular highlight near the left, weld seam mid-tube
      accent - leather/grip stipple with periodic stitch / grip channel lines
      metal  - vertical brushed streaks with a rivet at each corner
    """
    fv = 1.12 - 0.30 * (y / max(1, h - 1))      # 1.12 (top) -> 0.82 (bottom)
    fh = 1.02 - 0.12 * (x / max(1, w - 1))      # 1.02 (left) -> 0.90 (right)
    f = fv * fh

    if mat == "tyre":
        if y == 0:
            f *= 1.40                            # bright rim edge
        elif (x + y) % 3 == 0:
            f *= 1.20                            # raised tread lug
        else:
            f *= 0.90                            # groove between lugs
    elif mat == "frame":
        spec = 1.0 - abs(x - 1.4) / max(1.0, w * 0.7)
        f *= 0.86 + 0.52 * max(0.0, spec)        # specular sheen near the left, darker right
        if y in (h // 2, h // 2 + 1):
            f *= 0.90                            # subtle weld seam across the tube
    elif mat == "accent":
        if y <= 1:
            f *= 1.14                            # top sheen band
        f *= 1.06 if (x + y) % 2 == 0 else 0.95  # fine grain/grip stipple
        if x % 6 == 3:
            f *= 0.88                            # stitching / grip channel line
    elif mat == "metal":
        f *= 1.08 if x % 2 == 0 else 0.94        # vertical brushed streaks
        rivets = {(1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)}
        if (x, y) in rivets:
            f *= 0.68                            # rivet shadow
        elif (x - 1, y - 1) in rivets:
            f *= 1.28                            # rivet highlight

    return _mul(base, f)


def build(palette):
    img = Image.new("RGBA", (ATLAS_W, ATLAS_H), (0, 0, 0, 0))
    px = img.load()
    for mat, (u, v, w, h) in REGIONS.items():
        base = palette[mat]
        # Paint the footprint plus a 1px clamp-extended pad (gaps are >=4px, so
        # the pad never touches a neighbour) for belt-and-braces against sampling
        # at the exact edge.
        for oy in range(-1, h + 1):
            for ox in range(-1, w + 1):
                gx, gy = u + ox, v + oy
                if not (0 <= gx < ATLAS_W and 0 <= gy < ATLAS_H):
                    continue
                cx = min(w - 1, max(0, ox))
                cy = min(h - 1, max(0, oy))
                r, g, b = shade(base, cx, w, cy, h, mat)
                px[gx, gy] = (r, g, b, 255)
    return img


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.normpath(os.path.join(
        here, "..", "src", "main", "resources", "assets", "ldib",
        "textures", "entity"))
    os.makedirs(out_dir, exist_ok=True)
    preview = "--preview" in sys.argv
    if preview:
        prev_dir = os.path.join(here, "preview")
        os.makedirs(prev_dir, exist_ok=True)
    for name, palette in PALETTES.items():
        img = build(palette)
        path = os.path.join(out_dir, name + ".png")
        img.save(path)
        print("wrote", path, img.size)
        if preview:
            scale = 8
            big = img.resize((ATLAS_W * scale, ATLAS_H * scale), Image.NEAREST)
            ppath = os.path.join(prev_dir, name + "_x8.png")
            big.save(ppath)
            print("  preview", ppath)


if __name__ == "__main__":
    main()
