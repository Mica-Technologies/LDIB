package com.micatechnologies.minecraft.ldib.block;

/**
 * The visual styles of bike rack. Each is its own {@link BlockBikeRack} registration sharing one
 * class and one {@link TileEntityBikeRack}.
 *
 * <p>A rack can span more than one block: {@link #length} is how many blocks it occupies along its
 * facing (its length axis), so a wave rack is a 3×1 structure holding five bikes rather than a
 * cramped 1×1. Placing the master block fills in the extension blocks; breaking any part breaks the
 * whole rack. {@link #slots} give each bike's parking spot in the rack's <b>local frame</b>: local +X
 * runs along the length (the extension axis, matching the block models, which are drawn spanning X),
 * local +Z is across it (the direction a parked bike points). The tile-entity renderer rotates that
 * frame by the block's facing.</p>
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
    GRID("grid", 1, new Slot[] {
        new Slot(-0.3F, 0.0F, 0.0F), new Slot(0.3F, 0.0F, 0.0F)
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
