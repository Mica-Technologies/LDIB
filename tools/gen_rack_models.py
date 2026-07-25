#!/usr/bin/env python3
"""Generate the per-part block models + blockstates for LDIB's multi-block bike racks.

WHY THIS EXISTS
---------------
The wave and grid racks are 3 blocks long (``RackStyle.length()``), but their block models were
authored as a single 1x1 model and the blockstate pointed all three PART values at it. The result
in-game: a whole rack crammed into one block, drawn three times in a row, while the collision boxes
(computed in Java, per block) correctly spanned all three. Visually squished, physically 3 wide.

A model element cannot leave its own block -- 1.12.2 permits -16..32, but a 3-block span authored in
one file would still be drawn once per part, tripling it. So each part needs its OWN model containing
just that block's slice of the rack.

Hand-authoring three slices per style is where mistakes live: the pattern has to continue exactly
across a block seam, so a plateau or post that straddles x=16 has to be split into two pieces that
line up to the pixel. Instead this defines each rack ONCE over the full 48-pixel run and clips it to
each block. Change the design in one place, regenerate, and the seams stay correct by construction.

Run from the repo root:  python tools/gen_rack_models.py

Emits, for each 3-long style:
  assets/ldib/models/block/bike_rack_<style>_<part>.json   (part = 0,1,2 -- the in-world slices)
  assets/ldib/blockstates/bike_rack_<style>.json           (facing x part -> slice)

Deliberately does NOT touch ``bike_rack_<style>.json`` (the original 1-block model): the ITEM model
parents to it, and a compact one-block rack is the right inventory icon -- a 3-block structure cannot
be shown in a slot. The 1-long styles (hoop, post, classic) are left alone.
"""

import json
import os

BLOCK = 16          # pixels per block
LENGTH = 3          # blocks along the rack's facing
SPAN = BLOCK * LENGTH
END_INSET = 1.0     # leave a 1px margin at each far end, as the original models do

# Model +X runs along the rack's facing, so part i owns global x in [16i, 16i+16). That matches
# BlockBikeRack, which places extension i at pos.offset(facing, i), and the blockstate rotations
# below, which treat facing=east as the identity.
ROTATIONS = {"east": None, "south": 90, "west": 180, "north": 270}

FACE_NAMES = ("down", "up", "north", "south", "west", "east")


def box(x0, x1, y0, y1, z0=7.0, z1=9.0):
    return {"x": (x0, x1), "y": (y0, y1), "z": (z0, z1)}


def wave_elements():
    """An undulating bar over the full run: six shallow waves, on feet, on a continuous base rail.

    The bar alternates between a low course (y 3..4) and a high one (y 6..7) every 4 px, joined by
    1 px uprights -- the same proportions as the original single-block model, just continued across
    all three blocks instead of compressed into one.
    """
    els = [box(END_INSET, SPAN - END_INSET, 0.0, 1.0)]  # base rail, full span

    step = 4.0
    boundaries = [i * step for i in range(int(SPAN / step) + 1)]  # 0,4,...,48
    lows = []
    for i in range(len(boundaries) - 1):
        x0 = max(boundaries[i], END_INSET)
        x1 = min(boundaries[i + 1], SPAN - END_INSET)
        if x1 - x0 <= 0:
            continue
        high = (i % 2 == 0)
        els.append(box(x0, x1, 6.0, 7.0) if high else box(x0, x1, 3.0, 4.0))
        if not high:
            lows.append((x0, x1))

    # Uprights bridging low course to high course at every interior boundary. One of these straddles
    # x=16 and another x=32; clipping splits each into two abutting halves.
    for b in boundaries[1:-1]:
        els.append(box(b - 0.5, b + 0.5, 3.0, 7.0))

    # Short feet under each low course, so the wave looks supported rather than floating.
    for x0, x1 in lows:
        c = (x0 + x1) / 2.0
        els.append(box(c - 0.5, c + 0.5, 1.0, 3.0))

    return els


def grid_elements():
    """A wide floor rail: top and bottom rails with evenly repeating uprights every 2 px."""
    els = [
        box(END_INSET, SPAN - END_INSET, 0.0, 1.0),   # base rail
        box(END_INSET, SPAN - END_INSET, 9.0, 10.0),  # top rail
    ]
    x = 2.0
    while x + 1.0 <= SPAN - END_INSET:
        els.append(box(x, x + 1.0, 1.0, 9.0))
        x += 2.0
    return els


STYLES = {"wave": wave_elements, "grid": grid_elements}


def clip_to_part(els, part):
    """The slice of ``els`` inside block ``part``, translated into that block's own 0..16 space."""
    lo, hi = part * BLOCK, (part + 1) * BLOCK
    out = []
    for e in els:
        x0, x1 = max(e["x"][0], lo), min(e["x"][1], hi)
        if x1 - x0 <= 1e-6:  # no overlap, or a zero-width touch exactly on the seam
            continue
        out.append({"x": (x0 - lo, x1 - lo), "y": e["y"], "z": e["z"]})
    return out


def as_json_number(v):
    """Keep whole numbers integral so the emitted JSON reads like hand-authored model files."""
    return int(v) if float(v).is_integer() else round(float(v), 4)


def model_json(style, els):
    tex = "ldib:blocks/rack_%s" % style
    return {
        "textures": {"particle": tex, "rack": tex},
        "elements": [
            {
                "from": [as_json_number(e["x"][0]), as_json_number(e["y"][0]), as_json_number(e["z"][0])],
                "to": [as_json_number(e["x"][1]), as_json_number(e["y"][1]), as_json_number(e["z"][1])],
                "faces": {name: {"texture": "#rack"} for name in FACE_NAMES},
            }
            for e in els
        ],
    }


def blockstate_json(style):
    variants = {}
    for facing, y in ROTATIONS.items():
        for part in range(LENGTH):
            entry = {"model": "ldib:bike_rack_%s_%d" % (style, part)}
            if y is not None:
                entry["y"] = y
            variants["facing=%s,part=%d" % (facing, part)] = entry
    return {"variants": variants}


def write(path, data):
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")
    print("  wrote %s" % os.path.relpath(path))


def main():
    root = os.path.join("src", "main", "resources", "assets", "ldib")
    models = os.path.join(root, "models", "block")
    states = os.path.join(root, "blockstates")
    if not os.path.isdir(models):
        raise SystemExit("run this from the repo root (did not find %s)" % models)

    for style, build in STYLES.items():
        els = build()
        print("%s: %d elements over %d px" % (style, len(els), SPAN))
        for part in range(LENGTH):
            part_els = clip_to_part(els, part)
            write(os.path.join(models, "bike_rack_%s_%d.json" % (style, part)),
                  model_json(style, part_els))
        write(os.path.join(states, "bike_rack_%s.json" % style), blockstate_json(style))


if __name__ == "__main__":
    main()
