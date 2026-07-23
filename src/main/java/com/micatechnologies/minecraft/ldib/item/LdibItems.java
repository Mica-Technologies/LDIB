package com.micatechnologies.minecraft.ldib.item;

import com.micatechnologies.minecraft.ldib.LdibRegistry;
import com.micatechnologies.minecraft.ldib.entity.BikeVariant;

/**
 * Every item LDIB adds, created once and handed to {@link LdibRegistry}.
 *
 * <p>Instantiated from {@code preInit} rather than in a static initialiser: an item's constructor
 * calls {@code setCreativeTab}, which touches {@code LdibTab}, and class-load ordering between two
 * classes that reference each other is not something to leave to chance.</p>
 *
 * <p>One {@link ItemBike} per {@link BikeVariant} — the same class parameterised by data, which is
 * the whole point of the variant seam. A new variant is one more line here plus its assets.</p>
 */
public final class LdibItems {

    /** The pedal bicycle. Also the creative-tab icon (see {@code LdibTab}). */
    public static ItemBike bike;

    /** The e-bike — faster assisted top speed, brisker acceleration. */
    public static ItemBike ebike;

    /** The stand-on scooter (Bird / Segway style) — slower, twitchier, standing rider. */
    public static ItemBike scooter;

    private LdibItems() {
        throw new AssertionError("No instances.");
    }

    public static void init() {
        bike = LdibRegistry.addItem(new ItemBike(BikeVariant.BICYCLE));
        ebike = LdibRegistry.addItem(new ItemBike(BikeVariant.EBIKE));
        scooter = LdibRegistry.addItem(new ItemBike(BikeVariant.SCOOTER));
    }

    /** The item form of a given variant — used to hand a bike back out of a rack or dock. */
    public static ItemBike forVariant(BikeVariant variant) {
        switch (variant) {
            case EBIKE:   return ebike;
            case SCOOTER: return scooter;
            case BICYCLE:
            default:      return bike;
        }
    }
}
