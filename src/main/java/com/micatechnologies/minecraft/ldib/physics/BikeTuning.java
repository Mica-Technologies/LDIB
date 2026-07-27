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

    /**
     * Top <b>backwards</b> speed, blocks/second, as a positive number; the model clamps speed to
     * {@code [-maxReverseSpeed, maxSpeed]}. Backing up is walking the thing back with your feet on
     * every variant we ship — no bike or scooter has a reverse gear — so this is a small fraction of
     * the forward ceiling, and it stays out of {@link #withAssist}: a flat battery does not make you
     * worse at pushing.
     */
    public final double maxReverseSpeed;

    /** Acceleration while backing up, blocks/second². Deliberately weak — see {@link #maxReverseSpeed}. */
    public final double reverseAcceleration;

    public BikeTuning(double maxSpeed,
                      double pedalAcceleration,
                      double brakeDeceleration,
                      double rollingResistance,
                      double airDrag,
                      double maxSteerRateDegPerSec,
                      double steerSpeedFalloff,
                      double maxReverseSpeed,
                      double reverseAcceleration) {
        this.maxSpeed = maxSpeed;
        this.pedalAcceleration = pedalAcceleration;
        this.brakeDeceleration = brakeDeceleration;
        this.rollingResistance = rollingResistance;
        this.airDrag = airDrag;
        this.maxSteerRateDegPerSec = maxSteerRateDegPerSec;
        this.steerSpeedFalloff = steerSpeedFalloff;
        this.maxReverseSpeed = maxReverseSpeed;
        this.reverseAcceleration = reverseAcceleration;
    }

    /**
     * This tuning with its motor assist scaled back toward {@code unpowered} — what a powered
     * rideable actually handles like at a given battery level.
     *
     * <p>Only {@link #maxSpeed} and {@link #pedalAcceleration} are interpolated, because those are the
     * only two things a motor contributes. A flat battery does not change your brakes, your rolling
     * resistance or how sharply you can turn, so those come from this tuning unchanged — blending them
     * too would quietly make a low battery <i>handle</i> differently, which is not what "the assist
     * cut out" means.</p>
     *
     * <p>At {@code assist == 1} the result is this tuning; at {@code assist == 0} it is
     * {@code unpowered}'s speed and acceleration. So a dead e-bike rides like the pedal bicycle it is
     * built on — you can still get home, just under your own legs.</p>
     *
     * @param unpowered the handling with no assist at all
     * @param assist    {@code [0, 1]}, from {@link BatteryModel#assist}
     */
    public BikeTuning withAssist(BikeTuning unpowered, double assist) {
        double t = assist < 0.0D ? 0.0D : (assist > 1.0D ? 1.0D : assist);
        return new BikeTuning(
            unpowered.maxSpeed + (this.maxSpeed - unpowered.maxSpeed) * t,
            unpowered.pedalAcceleration + (this.pedalAcceleration - unpowered.pedalAcceleration) * t,
            this.brakeDeceleration,
            this.rollingResistance,
            this.airDrag,
            this.maxSteerRateDegPerSec,
            this.steerSpeedFalloff,
            this.maxReverseSpeed,
            this.reverseAcceleration);
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
            /* steerSpeedFalloff     */ 5.0D,
            /* maxReverseSpeed       */ 1.2D,
            /* reverseAcceleration   */ 2.0D);
    }
}
