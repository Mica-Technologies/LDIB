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
    SCOOTER_FAST(3, "scooter_fast", RiderPose.STANDING),
    /**
     * A self-balancing one-wheel board — one fat tyre between two foot pads, ridden across the board
     * in a surf stance. The one <b>standalone</b> rideable: see {@link #usesStations()}.
     */
    ONEWHEEL(4, "onewheel", RiderPose.BOARD);

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
        return this == EBIKE || this == SCOOTER || this == SCOOTER_FAST || this == ONEWHEEL;
    }

    /**
     * Whether this variant has anything to do with the bike-share network or the rack infrastructure —
     * kiosks, docks, the public fleet, and owner-locked racks. True for every bike and scooter.
     *
     * <p><b>False for {@link #ONEWHEEL}</b>, which is a standalone device by design. Both halves of
     * that fall out of one physical fact, which is why one predicate covers them rather than two: a
     * board has no frame. There is nothing for a rack's lock to pass through, and nothing for a dock's
     * pedestal to grab by the wheel. So a one-wheel is never dispensed by a kiosk, never returned to a
     * dock, never locked to a rack — you pick it up and carry it, which is what people do with them.</p>
     *
     * <p>Enforced at every entrance to that infrastructure: {@code BlockBikeDock} (both the ridden
     * return and the operator stocking gesture), {@code BlockBikeRack}, and {@code RideableActions},
     * which sends a one-wheel straight to the pick-up path instead of hunting for a rack.</p>
     */
    public boolean usesStations() {
        return this != ONEWHEEL;
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
     * The muted public-fleet livery for this variant, {@code ldib:textures/entity/share_<key>.png}.
     *
     * <p>A variant that never joins the fleet ({@link #usesStations()}) has no such skin painted, and
     * falls back to its own. Nothing should ever ask — a share bike can only come from a dock, and a
     * dock will not take one — but the fallback means the failure mode if something ever does is the
     * right board rather than the missing-texture checkerboard.</p>
     */
    public ResourceLocation shareTexture() {
        if (!usesStations()) {
            return texture();
        }
        return new ResourceLocation(LdibConstants.MOD_NAMESPACE, "textures/entity/share_" + key + ".png");
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
        if (this == ONEWHEEL) {
            return LdibConfig.onewheelTuning();
        }
        return LdibConfig.bicycleTuning();
    }

    /**
     * Whether this variant runs off a battery. The powered ones do; a pedal bicycle obviously does
     * not, and asking it for a charge always reports full so nothing downstream needs to special-case
     * it. Deliberately the same set as {@link #hasLights()} today, but kept as its own question —
     * "has a motor" and "has lamps" are different facts that happen to coincide, and a dynamo-lit
     * pedal bike or an unlit e-bike would split them.
     */
    public boolean hasBattery() {
        return this == EBIKE || this == SCOOTER || this == SCOOTER_FAST || this == ONEWHEEL;
    }

    /**
     * Blocks this variant travels under power on a full charge, from config; {@code 0} means the
     * battery is disabled and the variant always runs at full assist.
     */
    public double rangeBlocks() {
        if (this == EBIKE) {
            return LdibConfig.ebikeRangeBlocks;
        }
        if (this == SCOOTER) {
            return LdibConfig.scooterRangeBlocks;
        }
        if (this == SCOOTER_FAST) {
            return LdibConfig.scooterFastRangeBlocks;
        }
        if (this == ONEWHEEL) {
            return LdibConfig.onewheelRangeBlocks;
        }
        return 0.0D;
    }

    /**
     * How this variant handles with a flat battery — the {@code unpowered} end that
     * {@link BikeTuning#withAssist} interpolates from.
     *
     * <p>A dead e-bike is just a (heavy) bicycle: it falls back to the pedal-bike numbers, so you can
     * always ride home under your own legs. A dead scooter has no legs to fall back on, so it gets its
     * own slow kick-along tuning rather than the bicycle's — being stranded is not a fun mechanic, but
     * neither is a flat scooter that still does 22 mph. A dead one-wheel is slower still: there is
     * nothing to kick, so it is being walked rather than scooted.</p>
     */
    public BikeTuning unpoweredTuning() {
        if (this == EBIKE) {
            return LdibConfig.bicycleTuning();
        }
        if (this == SCOOTER || this == SCOOTER_FAST) {
            return LdibConfig.scooterKickTuning();
        }
        if (this == ONEWHEEL) {
            return LdibConfig.onewheelPushTuning();
        }
        return tuning();
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
