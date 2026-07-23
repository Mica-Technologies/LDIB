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

    private static final ModelBike BIKE = new ModelBike();
    private static final ModelScooter SCOOTER = new ModelScooter();

    private RideableModels() {
        throw new AssertionError("No instances.");
    }

    /** The model for {@code variant}: the scooter model for scooters, the bike model otherwise. */
    public static ModelRideable forVariant(BikeVariant variant) {
        return variant == BikeVariant.SCOOTER ? SCOOTER : BIKE;
    }
}
