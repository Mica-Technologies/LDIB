package com.micatechnologies.minecraft.ldib.physics;

/**
 * The handling constants that make one rideable feel different from another — a pedal bike from
 * an e-bike from a scooter — gathered into one immutable value object.
 *
 * <p>Kept as plain data (no Minecraft types) so {@link BikePhysics} stays unit-testable and so a
 * new vehicle variant is a new set of numbers, not a new code path. The entity layer builds one of
 * these from {@code LdibConfig} (for the shared physics knobs) plus the per-variant values that
 * will eventually live on the item/entity; see docs/AGENT-PLANS/MASTER_PLAN.md, "Variants are data".</p>
 */
public final class BikeTuning {

    /** Top forward speed under pedal power alone, blocks/second. A brisk cycling pace is ~6–7. */
    public final double maxSpeed;

    /** Forward acceleration while the rider is pedalling/throttling, blocks/second². */
    public final double pedalAcceleration;

    /** Deceleration while the rider is actively braking, blocks/second². Larger than coast drag. */
    public final double brakeDeceleration;

    /**
     * Fraction of speed shed per second to rolling resistance and drivetrain friction when
     * coasting (no pedal, no brake). Applied as timestep-independent exponential decay.
     */
    public final double rollingResistance;

    /** Quadratic air-drag coefficient (per block/s of speed, per second). Dominates near top speed. */
    public final double airDrag;

    /**
     * Maximum steering rate in degrees/second at low speed. Real bikes turn tightest slowly and
     * shallowly at speed; {@link #steerSpeedFalloff} models that.
     */
    public final double maxSteerRateDegPerSec;

    /**
     * Speed (blocks/s) at which the achievable steer rate has fallen to half of
     * {@link #maxSteerRateDegPerSec}. Prevents twitchy, physically-silly fast turns.
     */
    public final double steerSpeedFalloff;

    public BikeTuning(double maxSpeed,
                      double pedalAcceleration,
                      double brakeDeceleration,
                      double rollingResistance,
                      double airDrag,
                      double maxSteerRateDegPerSec,
                      double steerSpeedFalloff) {
        this.maxSpeed = maxSpeed;
        this.pedalAcceleration = pedalAcceleration;
        this.brakeDeceleration = brakeDeceleration;
        this.rollingResistance = rollingResistance;
        this.airDrag = airDrag;
        this.maxSteerRateDegPerSec = maxSteerRateDegPerSec;
        this.steerSpeedFalloff = steerSpeedFalloff;
    }

    /**
     * A reasonable pedal-bicycle feel. Used as the MVP default and as the baseline the test suite
     * pins behaviour against; e-bike and scooter variants adjust from here.
     */
    public static BikeTuning defaultBicycle() {
        return new BikeTuning(
            /* maxSpeed              */ 7.0D,
            /* pedalAcceleration     */ 3.5D,
            /* brakeDeceleration     */ 9.0D,
            /* rollingResistance     */ 0.6D,
            /* airDrag               */ 0.010D,
            /* maxSteerRateDegPerSec */ 90.0D,
            /* steerSpeedFalloff     */ 5.0D);
    }
}
