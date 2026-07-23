package com.micatechnologies.minecraft.ldib.client.render;

import com.micatechnologies.minecraft.ldib.entity.BikeVariant;

/**
 * Client-side selector mapping a {@link BikeVariant} to the {@link ModelRideable} that draws it. This
 * is the client half of the "variants are data" seam: {@code BikeVariant} (common code) can't name a
 * client model type without breaking side discipline, so the entity renderer and the rack / dock
 * tile-entity renderers ask here instead.
 *
 * <p>The returned instances are shared singletons — rendering is single-threaded on the client render
 * thread and the models are stateless between frames apart from wheel spin, which each caller sets
 * immediately before drawing (the rack renderer already reused one model this way).</p>
 */
public final class RideableModels {

    //                                              battery, basket
    private static final ModelBike BICYCLE_MODEL = new ModelBike(false, true);   // basket only
    private static final ModelBike EBIKE_MODEL = new ModelBike(true, false);     // battery only
    /** The public share e-bike (Bluebikes-style): battery pack AND a front basket. */
    private static final ModelBike SHARE_EBIKE_MODEL = new ModelBike(true, true);
    private static final ModelScooter SCOOTER_MODEL = new ModelScooter(false);
    private static final ModelScooter SCOOTER_FAST_MODEL = new ModelScooter(true);

    private RideableModels() {
        throw new AssertionError("No instances.");
    }

    /**
     * The model for {@code variant}, honouring the public/personal split: a share e-bike carries a
     * basket the personal e-bike lacks. One instance per (variant, share) combination that differs, so
     * each can carry its own distinguishing accessory geometry.
     */
    public static ModelRideable forVariant(BikeVariant variant, boolean share) {
        // if/else, not switch, to avoid a synthetic switch-map class (see BikeVariant#tuning()).
        if (variant == BikeVariant.EBIKE) {
            return share ? SHARE_EBIKE_MODEL : EBIKE_MODEL;
        }
        if (variant == BikeVariant.SCOOTER) {
            return SCOOTER_MODEL;
        }
        if (variant == BikeVariant.SCOOTER_FAST) {
            return SCOOTER_FAST_MODEL;
        }
        return BICYCLE_MODEL;
    }
}
