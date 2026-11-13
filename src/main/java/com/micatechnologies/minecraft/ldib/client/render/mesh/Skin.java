package com.micatechnologies.minecraft.ldib.client.render.mesh;

/**
 * The layout of a rideable's skin atlas: which rectangle is which material. Every skin PNG (one per
 * variant, plus the muted fleet livery) paints these same rectangles in its own colours, so one set
 * of models wears any of them.
 *
 * <p><b>This table and {@code REGIONS} in {@code tools/gen_skins.py} are the same table.</b> Change
 * one and you must change the other, then re-run the script. Rectangles keep at least 4 px of
 * unpainted gap between them so a face sampling to the very edge of its own never picks up its
 * neighbour's colour.</p>
 *
 * <pre>
 *   material     offset     size     painted as
 *   TYRE         (0, 0)     12 x 8   near-black rubber with tread lugs
 *   FRAME        (16, 0)    12 x 20  the variant's paint, highlight down the left edge
 *   ACCENT       (32, 0)    26 x 10  saddle / grips / deck — the secondary colour
 *   METAL        (64, 0)    16 x 5   grey brushed hardware
 *   FATTYRE      (88, 0)    18 x 8   the one-wheel's slick
 *   ALLOY        (112, 0)   8 x 6    bright rim / spoke / crank metal
 *   GLOW_WHITE   (64, 12)   16 x 16  headlight halo, fading to black at the edge
 *   GLOW_RED     (84, 12)   16 x 16  tail light halo, likewise
 *   SWATCHES     (104, 12)  12 x 4   six flat 2 x 4 colours; see the {@code *_LENS} constants
 *   SHELL        (0, 28)    24 x 8   one-wheel deck plastic
 *   RAIL         (32, 28)   22 x 12  one-wheel anodised rails
 * </pre>
 */
public final class Skin {

    public static final int ATLAS_W = 128;
    public static final int ATLAS_H = 64;

    public static final Material TYRE = new Material(0, 0, 12, 8);
    public static final Material FRAME = new Material(16, 0, 12, 20);
    public static final Material ACCENT = new Material(32, 0, 26, 10);
    public static final Material METAL = new Material(64, 0, 16, 5);
    public static final Material FATTYRE = new Material(88, 0, 18, 8);
    public static final Material ALLOY = new Material(112, 0, 8, 6);
    public static final Material GLOW_WHITE = new Material(64, 12, 16, 16);
    public static final Material GLOW_RED = new Material(84, 12, 16, 16);
    public static final Material SHELL = new Material(0, 28, 24, 8);
    public static final Material RAIL = new Material(32, 28, 22, 12);

    private static final Material SWATCHES = new Material(104, 12, 12, 4);
    /** A headlight lens that is switched off: dull glass. */
    public static final Material HEAD_LENS_OFF = SWATCHES.sub(0, 0, 2, 4);
    /** A lit headlight lens. Drawn full-bright, so this is the colour it glows. */
    public static final Material HEAD_LENS_ON = SWATCHES.sub(2, 0, 2, 4);
    /** A tail light lens that is switched off: dark red plastic. */
    public static final Material REAR_LENS_OFF = SWATCHES.sub(4, 0, 2, 4);
    /** A lit tail light lens. */
    public static final Material REAR_LENS_ON = SWATCHES.sub(6, 0, 2, 4);
    /** Amber reflector plastic — the wheel reflectors that make a turning wheel read as turning. */
    public static final Material REFLECTOR = SWATCHES.sub(8, 0, 2, 4);
    /** Dark grey moulded hardware: light housings, pedals, the chain. */
    public static final Material HARDWARE = SWATCHES.sub(10, 0, 2, 4);

    private Skin() {
        throw new AssertionError("No instances.");
    }
}
