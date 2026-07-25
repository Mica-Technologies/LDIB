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
be shown in a slot. The 1-long styles (hoop, post, classic) are correct as-is and are left alone.
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


# --- Wave profile. All four are pixels; tweak here and regenerate rather than editing the JSON. ---
WAVE_RAIL_TOP = 1.0     # top of the continuous base rail
WAVE_FOOT_TOP = 4.0     # feet rise from the rail to the low course
WAVE_LOW = (4.0, 5.0)   # the low course of the undulating bar
WAVE_HIGH = (9.0, 10.0) # the high course
WAVE_STEP = 4.0         # run length of each half-wave; smaller = more, tighter undulations


def wave_elements():
    """An undulating bar over the full run: six shallow waves, on feet, on a continuous base rail.

    The bar alternates between a low course and a high one every ``WAVE_STEP`` px, joined by 1 px
    uprights -- the original single-block model's shape, continued across all three blocks instead of
    compressed into one.

    Raised 2026-07-25 at the owner's request ("should stretch a bit taller"): the crest was at 7 px
    (0.44 block), which read as squat once the rack had three blocks of run to spread across. It now
    tops out at 10 px (0.63 block), matching the grid rack's top rail so the two wide styles agree.
    NOTE: that is slightly above ``BlockBikeRack.RACK_AABB``, which is 0.6 tall on purpose -- 0.6 is
    exactly the vanilla step height, so a rack stays step-over-able. The crest poking a couple of
    pixels above the collision box is the deliberate trade (grid already did this); raising the AABB
    to match would silently make every rack a wall.
    """
    els = [box(END_INSET, SPAN - END_INSET, 0.0, WAVE_RAIL_TOP)]  # base rail, full span

    step = WAVE_STEP
    boundaries = [i * step for i in range(int(SPAN / step) + 1)]  # 0,4,...,48
    lows = []
    for i in range(len(boundaries) - 1):
        x0 = max(boundaries[i], END_INSET)
        x1 = min(boundaries[i + 1], SPAN - END_INSET)
        if x1 - x0 <= 0:
            continue
        high = (i % 2 == 0)
        els.append(box(x0, x1, *(WAVE_HIGH if high else WAVE_LOW)))
        if not high:
            lows.append((x0, x1))

    # Uprights bridging low course to high course at every interior boundary. One of these straddles
    # x=16 and another x=32; clipping splits each into two abutting halves.
    for b in boundaries[1:-1]:
        els.append(box(b - 0.5, b + 0.5, WAVE_LOW[0], WAVE_HIGH[1]))

    # Short feet under each low course, so the wave looks supported rather than floating.
    for x0, x1 in lows:
        c = (x0 + x1) / 2.0
        els.append(box(c - 0.5, c + 0.5, WAVE_RAIL_TOP, WAVE_FOOT_TOP))

    return els


# --- Grid profile. A framed rack of open wheel slots. ---
GRID_SLOTS = 5          # must match the number of Slots on RackStyle.GRID
GRID_POST_W = 2.0       # width of the end uprights and the dividers between slots
GRID_RAIL_TOP = 1.0     # top of the base rail
GRID_TOP_RAIL = (9.0, 10.0)


def grid_layout():
    """Divider/upright x-ranges and the resulting slot centres, in pixels across the full run.

    Returns ``(posts, slot_centres)``. The slot centres are what ``RackStyle.GRID``'s Slots have to
    agree with -- see ``grid_elements`` for why.
    """
    lo, hi = END_INSET, SPAN - END_INSET
    n_posts = GRID_SLOTS + 1                                    # one each end, one between each pair
    slot_w = ((hi - lo) - n_posts * GRID_POST_W) / GRID_SLOTS
    posts, centres = [], []
    x = lo
    for i in range(n_posts):
        posts.append((x, x + GRID_POST_W))
        x += GRID_POST_W
        if i < GRID_SLOTS:
            centres.append(x + slot_w / 2.0)
            x += slot_w
    return posts, centres


def grid_elements():
    """A framed rack of open wheel slots: two continuous rails, closed ends, dividers between slots.

    Redesigned 2026-07-25. The previous version was a picket fence -- 1 px uprights at a 2 px pitch,
    24 of them across the run. That is uniformly periodic, which made it *look identical* whether it
    was tiled correctly across three blocks or (as the bug did) drawn three times over: there is no
    large-scale shape for the eye to measure the rack against, so it read as a squished 1x1 pattern
    repeated even once the geometry was right. The owner reported exactly that.

    So the composition, not the tiling, is the fix: a handful of wide dividers framing five open slots
    spans the whole rack visibly, and each slot obviously belongs to one bike.

    The slot centres this produces MUST match ``RackStyle.GRID``'s Slots, or dividers will cut straight
    through parked bikes. The script prints them for exactly that reason -- keep the two in step.
    """
    posts, _ = grid_layout()
    els = [
        box(END_INSET, SPAN - END_INSET, 0.0, GRID_RAIL_TOP),   # base rail, full span
        box(END_INSET, SPAN - END_INSET, *GRID_TOP_RAIL),       # top rail, full span
    ]
    for x0, x1 in posts:
        els.append(box(x0, x1, GRID_RAIL_TOP, GRID_TOP_RAIL[0]))
    return els


# --- Classic profile. A toast rack: a row of inverted-U arches, one wheel slotted into each. ---
CLASSIC_ARCHES = 5      # must match the number of Slots on RackStyle.CLASSIC
CLASSIC_ARCH_W = 5.0    # outer width of one arch
CLASSIC_BAR = 1.0       # thickness of the uprights and the arch's top bar
CLASSIC_TOP = (9.0, 10.0)


def classic_layout():
    """Arch centres in pixels across the full run. A bike parks *inside* each arch, so these are also
    the slot centres ``RackStyle.CLASSIC`` must use."""
    lo, hi = END_INSET, SPAN - END_INSET
    pitch = (hi - lo) / CLASSIC_ARCHES
    return [lo + pitch * (i + 0.5) for i in range(CLASSIC_ARCHES)]


def classic_elements():
    """A continuous base rail carrying evenly spaced inverted-U arches.

    Widened to three blocks 2026-07-25 at the owner's request. It keeps the single-block model's
    character exactly -- 1 px uprights joined by a top bar at y 9..10, a wheel slotted into each U --
    there are simply five arches spread across the full run instead of three crammed into one block.
    """
    els = [box(END_INSET, SPAN - END_INSET, 0.0, 1.0)]  # base rail, full span
    half = CLASSIC_ARCH_W / 2.0
    for c in classic_layout():
        left, right = c - half, c + half
        els.append(box(left, left + CLASSIC_BAR, 1.0, CLASSIC_TOP[1]))    # upright
        els.append(box(right - CLASSIC_BAR, right, 1.0, CLASSIC_TOP[1]))  # upright
        els.append(box(left, right, *CLASSIC_TOP))                        # top bar of the U
    return els


STYLES = {"wave": wave_elements, "grid": grid_elements, "classic": classic_elements}

# Styles whose Java-side Slot positions are dictated by the generated geometry. Printed at the end of
# a run so RackStyle can be kept in step; see the note in main().
SLOT_LAYOUTS = {"grid": lambda: grid_layout()[1], "classic": classic_layout}


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

    # Some slots are load-bearing on the Java side too: a grid bike has to sit in the gap between two
    # dividers, and a classic bike inside an arch. Get those wrong and the geometry cuts through parked
    # bikes. Print them so a mismatch is obvious the moment a layout is retuned.
    print("\nRackStyle Slot `along` values dictated by this geometry:")
    for style in sorted(SLOT_LAYOUTS):
        centres = SLOT_LAYOUTS[style]()
        alongs = [c / BLOCK - 0.5 for c in centres]  # px across the run -> blocks from master's centre
        print("  %-8s px %s" % (style, ", ".join("%.2f" % c for c in centres)))
        print("  %-8s -> new Slot(%s, 0.0F, 0.0F)"
              % ("", "F), new Slot(".join("%.4g" % a for a in alongs)))


if __name__ == "__main__":
    main()
