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
    // A ring rack: you lean the bike against the hoop and lock the frame to it, so a parked bike sits
    // PARALLEL to the hoop's plane — hence yaw 90 (owner, 2026-07-25).
    //
    // Rotating them forced the spacing to change too. The hoop's arch lies in the XY plane, so a bike
    // turned to match it now runs along local X; two bikes still separated along X would have been
    // parked nose-to-tail 0.6 blocks apart, and a bike is nearly 2 blocks long. They are separated
    // across (local Z) instead — one either side of the hoop, which is where they go on a real one.
    //
    // The two face OPPOSITE ways (yaw 90 / 270), which is how bikes actually end up on a ring rack —
    // and it is what lets them sit this close. Anti-parallel bikes interleave: one's handlebars line up
    // with the other's saddle instead of with its handlebars, so 0.4 blocks apart does not clip where
    // two parallel bikes would. That is also why they can be tucked in to ±0.2 rather than ±0.3, which
    // left a visible gap between bike and hoop (owner, 2026-07-25).
    HOOP("hoop", 1, new Slot[] {
        new Slot(0.0F, -0.2F, 90.0F), new Slot(0.0F, 0.2F, 270.0F)
    }),
    // One pole, a bike clipped to either side of it — two in a single block (owner, 2026-07-25).
    POST("post", 1, new Slot[] {
        new Slot(-0.3F, 0.0F, 0.0F), new Slot(0.3F, 0.0F, 0.0F)
    }),
    // 3 blocks long like the wave: a grid rack is a wide floor rail, so make the block wide too
    // (real wave/grid racks are noticeably wider than a ring rack) — five bikes spread across the span.
    //
    // These `along` values are NOT free: the grid model frames five open slots between wide dividers,
    // and each bike has to sit in the middle of a gap or a divider slices through it. They are exactly
    // the centres `tools/gen_rack_models.py` prints — if that layout is retuned, take the numbers from
    // the script's output rather than nudging these by eye.
    GRID("grid", 3, new Slot[] {
        new Slot(-0.1F, 0.0F, 0.0F), new Slot(0.45F, 0.0F, 0.0F), new Slot(1.0F, 0.0F, 0.0F),
        new Slot(1.55F, 0.0F, 0.0F), new Slot(2.1F, 0.0F, 0.0F)
    }),
    // 3 blocks long (owner, 2026-07-25): a real toast rack is a long row of arches, so it belongs with
    // the wide styles rather than cramming three arches into one block. Five arches, a bike parked
    // inside each U — so like GRID these `along` values come straight from the generator's output.
    CLASSIC("classic", 3, new Slot[] {
        new Slot(-0.15F, 0.0F, 0.0F), new Slot(0.425F, 0.0F, 0.0F), new Slot(1.0F, 0.0F, 0.0F),
        new Slot(1.575F, 0.0F, 0.0F), new Slot(2.15F, 0.0F, 0.0F)
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
