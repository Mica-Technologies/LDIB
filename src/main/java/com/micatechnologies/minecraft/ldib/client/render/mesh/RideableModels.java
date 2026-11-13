package com.micatechnologies.minecraft.ldib.client.render.mesh;

import com.micatechnologies.minecraft.ldib.entity.BikeVariant;

/**
 * Client-side selector mapping a {@link BikeVariant} to the {@link RideableModel} that draws it. This
 * is the client half of the "variants are data" seam: {@code BikeVariant} (common code) cannot name a
 * model type without breaking side discipline, so the entity renderer and the rack and dock renderers
 * ask here instead. It is the one class in this package that names a Minecraft-side type.
 *
 * <p>The instances are shared: a model carries no per-frame state (the pose is passed in with each
 * draw), so one serves every bike of its kind.</p>
 */
public final class RideableModels {

    //                                                 electric, basket
    private static final BikeModel BICYCLE = new BikeModel(false, true);
    private static final BikeModel EBIKE = new BikeModel(true, false);
    /** The public share e-bike (Bluebikes-style): battery pack and a front basket. */
    private static final BikeModel SHARE_EBIKE = new BikeModel(true, true);
    private static final ScooterModel SCOOTER = new ScooterModel(false);
    private static final ScooterModel SCOOTER_FAST = new ScooterModel(true);
    /** The one-wheel board. No share variant — it never joins the fleet (BikeVariant#usesStations). */
    private static final OnewheelModel ONEWHEEL = new OnewheelModel();

    private RideableModels() {
        throw new AssertionError("No instances.");
    }

    /**
     * The model for {@code variant}, honouring the public/personal split: a share e-bike carries a
     * basket the personal e-bike lacks.
     */
    public static RideableModel forVariant(BikeVariant variant, boolean share) {
        // if/else, not switch, to avoid a synthetic switch-map class (see BikeVariant#tuning()).
        if (variant == BikeVariant.EBIKE) {
            return share ? SHARE_EBIKE : EBIKE;
        }
        if (variant == BikeVariant.SCOOTER) {
            return SCOOTER;
        }
        if (variant == BikeVariant.SCOOTER_FAST) {
            return SCOOTER_FAST;
        }
        if (variant == BikeVariant.ONEWHEEL) {
            return ONEWHEEL;
        }
        return BICYCLE;
    }
}
