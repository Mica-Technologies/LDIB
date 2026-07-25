package com.micatechnologies.minecraft.ldib.block;

/**
 * The visual styles of bike rack. Each is its own {@link BlockBikeRack} registration sharing one
 * class and one {@link TileEntityBikeRack}.
 *
 * <p>A rack can span more than one block: {@link #length} is how many blocks it occupies along its
 * facing (its length axis), so a wave rack is a 3×1 structure holding five bikes rather than a
 * cramped 1×1. Placing the master block fills in the extension blocks; breaking any part breaks the
 * whole rack. {@link #slots} give each bike's parking spot in the rack's <b>local frame</b>: local +X
 * runs along the length (the extension axis), local +Z is across it (the direction a parked bike
 * points). The tile-entity renderer rotates that frame by the block's facing.</p>
 *
 * <p><b>Multi-block styles need one block model per part.</b> A model element cannot leave its own
 * block, and the blockstate draws a model once per PART, so a rack longer than 1 is authored as
 * {@code bike_rack_<style>_0/_1/_2} — each holding just that block's slice. Getting this wrong is not
 * subtle but it is easy to miss: until 2026-07-25 all three parts pointed at a single 1×1 model, so a
 * whole rack was drawn compressed into one block, three times over, while the per-block collision
 * boxes correctly spanned all three. Generate the slices with {@code tools/gen_rack_models.py} rather
 * than editing them by hand — it defines each rack once across the full run and clips it, which is
 * what keeps the pattern continuous across a block seam.</p>
 */
public enum RackStyle {

    // Slot(along, across, yaw): along = blocks from the master block's centre along the length axis
    // (local +X); across = blocks off the centre line (local +Z); yaw = bike facing offset in degrees
    // (0 = pointing across the rack, local +Z). Bikes are rendered near full size, so slots are spaced
    // by roughly a bike's width (~0.6 block along X). A single 1x1 block cleanly fits two; racks that
    // should hold more are made longer (see WAVE, a 3x1 holding five).
    HOOP("hoop", 1, new Slot[] {
        new Slot(-0.3F, 0.0F, 0.0F), new Slot(0.3F, 0.0F, 0.0F)
    }),
    POST("post", 1, new Slot[] {
        new Slot(0.0F, 0.0F, 0.0F)
    }),
    // 3 blocks long like the wave: a grid rack is a wide floor rail, so make the block wide too
    // (real wave/grid racks are noticeably wider than a ring rack) — five bikes spread across the
    // span without clipping. The grid block model tiles its repeating bars cleanly across the parts.
    GRID("grid", 3, new Slot[] {
        new Slot(-0.4F, 0.0F, 0.0F), new Slot(0.3F, 0.0F, 0.0F), new Slot(1.0F, 0.0F, 0.0F),
        new Slot(1.7F, 0.0F, 0.0F), new Slot(2.4F, 0.0F, 0.0F)
    }),
    CLASSIC("classic", 1, new Slot[] {
        new Slot(-0.3F, 0.0F, 0.0F), new Slot(0.3F, 0.0F, 0.0F)
    }),
    // 3 blocks long, 5 bikes spread across the full span (blocks 0..2 cover local X in [-0.5, 2.5]),
    // spaced ~0.7 apart so near-full-size bikes don't clip.
    WAVE("wave", 3, new Slot[] {
        new Slot(-0.4F, 0.0F, 0.0F), new Slot(0.3F, 0.0F, 0.0F), new Slot(1.0F, 0.0F, 0.0F),
        new Slot(1.7F, 0.0F, 0.0F), new Slot(2.4F, 0.0F, 0.0F)
    });

    /** One parking spot in the rack's local frame (see the class javadoc). */
    public static final class Slot {
        public final float along;
        public final float across;
        public final float yaw;

        public Slot(float along, float across, float yaw) {
            this.along = along;
            this.across = across;
            this.yaw = yaw;
        }
    }

    private final String key;
    private final int length;
    private final Slot[] slots;

    RackStyle(String key, int length, Slot[] slots) {
        this.key = key;
        this.length = length;
        this.slots = slots;
    }

    /** Shared key for the block registry name (`bike_rack_<key>`), translation key and texture. */
    public String key() {
        return key;
    }

    /** How many blocks this rack occupies along its facing. */
    public int length() {
        return length;
    }

    /** How many bikes this style holds. */
    public int capacity() {
        return slots.length;
    }

    /** Where each held bike sits, in the rack's local frame, indexed by slot. */
    public Slot[] slots() {
        return slots;
    }
}
