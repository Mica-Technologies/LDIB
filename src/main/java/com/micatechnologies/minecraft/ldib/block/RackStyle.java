package com.micatechnologies.minecraft.ldib.block;

/**
 * The visual styles of bike rack. Each becomes its own {@link BlockBikeRack} registration sharing
 * one class and one {@link TileEntityBikeRack} — separate blocks (not a metadata property) is the
 * idiomatic 1.12.2 way to give each style its own static model, and it avoids the block-state ceiling.
 *
 * <p>Each style also defines how many bikes it holds and <b>where</b> each one sits ({@link #slots}),
 * so the tile-entity renderer can draw the locked bikes on the rack. Capacity is a property of the
 * shape: a single hoop takes two bikes, a lone post one, a long toast rack several.</p>
 */
public enum RackStyle {

    // Slot positions are block fractions (x, z in 0..1) plus the bike's facing yaw in degrees.
    // Bikes park perpendicular to the rack rail (length along Z), so they overhang front/back — a
    // parked bike is ~1 block long. Capacity is slots.length.
    HOOP("hoop", new Slot[] {
        new Slot(0.35F, 0.5F, 0.0F), new Slot(0.65F, 0.5F, 0.0F)
    }),
    POST("post", new Slot[] {
        new Slot(0.5F, 0.5F, 0.0F)
    }),
    GRID("grid", new Slot[] {
        new Slot(0.22F, 0.5F, 0.0F), new Slot(0.41F, 0.5F, 0.0F),
        new Slot(0.59F, 0.5F, 0.0F), new Slot(0.78F, 0.5F, 0.0F)
    }),
    CLASSIC("classic", new Slot[] {
        new Slot(0.13F, 0.5F, 0.0F), new Slot(0.25F, 0.5F, 0.0F),
        new Slot(0.44F, 0.5F, 0.0F), new Slot(0.56F, 0.5F, 0.0F),
        new Slot(0.75F, 0.5F, 0.0F), new Slot(0.87F, 0.5F, 0.0F)
    }),
    WAVE("wave", new Slot[] {
        new Slot(0.25F, 0.5F, 0.0F), new Slot(0.5F, 0.5F, 0.0F), new Slot(0.75F, 0.5F, 0.0F)
    });

    /** One parking spot on a rack: where the bike sits (block fractions) and which way it faces. */
    public static final class Slot {
        public final float x;
        public final float z;
        public final float yaw;

        public Slot(float x, float z, float yaw) {
            this.x = x;
            this.z = z;
            this.yaw = yaw;
        }
    }

    private final String key;
    private final Slot[] slots;

    RackStyle(String key, Slot[] slots) {
        this.key = key;
        this.slots = slots;
    }

    /** Shared key for the block registry name (`bike_rack_<key>`), translation key and texture. */
    public String key() {
        return key;
    }

    /** How many bikes this style holds. */
    public int capacity() {
        return slots.length;
    }

    /** Where each held bike sits, indexed by slot. */
    public Slot[] slots() {
        return slots;
    }
}
