package com.micatechnologies.minecraft.ldib.entity;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.LdibConstants;
import com.micatechnologies.minecraft.ldib.physics.BikeTuning;
import net.minecraft.util.ResourceLocation;

/**
 * A kind of rideable — bicycle, e-bike, and (later) scooters. This is the "variants are data" seam
 * from the master plan: a variant bundles the handling numbers ({@link #tuning()}), the look
 * ({@link #texture()}) and the registry/lang key ({@link #key()}), so adding a new rideable is a new
 * enum constant plus assets, not a new entity class or a new movement code path.
 *
 * <p>This is the entity layer, not the physics layer — it names a Minecraft {@link ResourceLocation}
 * and reads {@link LdibConfig}. The pure-Java handling model in {@code ldib.physics} stays
 * Minecraft-free; a variant merely <i>selects</i> which {@link BikeTuning} the model runs with.</p>
 *
 * <p>The {@link #id()} values are persisted in NBT and synced over the network, so <b>keep them
 * stable</b> — append new variants, never renumber existing ones.</p>
 */
public enum BikeVariant {

    BICYCLE(0, "bike", RiderPose.SEATED),
    EBIKE(1, "ebike", RiderPose.SEATED),
    /** A stand-on kick scooter in the spirit of Bird / Segway — same physics stack, standing rider. */
    SCOOTER(2, "scooter", RiderPose.STANDING),
    /** A faster performance scooter (~22 mph vs the standard ~12) — same model, more speed. */
    SCOOTER_FAST(3, "scooter_fast", RiderPose.STANDING);

    private final int id;
    private final String key;
    private final RiderPose pose;

    BikeVariant(int id, String key, RiderPose pose) {
        this.id = id;
        this.key = key;
        this.pose = pose;
    }

    /** How the rider sits on this variant — seated on a bike, standing on a scooter. */
    public RiderPose pose() {
        return pose;
    }

    /**
     * Whether this variant has electric lights — a headlight that comes on in the dark and a brake
     * light. The e-bike and scooter do (they're powered); a plain pedal bicycle does not. Presentation
     * only: read by the renderer, never by the physics.
     */
    public boolean hasLights() {
        return this == EBIKE || this == SCOOTER || this == SCOOTER_FAST;
    }

    /** Stable network/NBT id. Never renumber. */
    public int id() {
        return id;
    }

    /** Shared key for the item registry name, translation key and texture path. */
    public String key() {
        return key;
    }

    /** The entity texture for this variant, {@code ldib:textures/entity/<key>.png}. */
    public ResourceLocation texture() {
        return new ResourceLocation(LdibConstants.MOD_NAMESPACE, "textures/entity/" + key + ".png");
    }

    /**
     * The handling this variant runs with, pulled live from config so retuning needs no code change.
     *
     * <p>Deliberately {@code if}/{@code else} rather than {@code switch (this)}: a switch over an enum
     * makes javac emit a synthetic {@code BikeVariant$1} switch-map class, a separate class file that
     * is one more thing to go missing (it crashed a dev client whose classes were rebuilt underneath
     * it: {@code NoClassDefFoundError: BikeVariant$1}). Reference comparisons need no synthetic class.</p>
     */
    public BikeTuning tuning() {
        if (this == EBIKE) {
            return LdibConfig.eBikeTuning();
        }
        if (this == SCOOTER) {
            return LdibConfig.scooterTuning();
        }
        if (this == SCOOTER_FAST) {
            return LdibConfig.scooterFastTuning();
        }
        return LdibConfig.bicycleTuning();
    }

    /** Resolve a persisted/synced id back to a variant, defaulting to {@link #BICYCLE} if unknown. */
    public static BikeVariant byId(int id) {
        for (BikeVariant v : values()) {
            if (v.id == id) {
                return v;
            }
        }
        return BICYCLE;
    }
}
