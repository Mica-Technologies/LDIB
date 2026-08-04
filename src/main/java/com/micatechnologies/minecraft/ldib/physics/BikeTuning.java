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

    /**
     * Gravity along a slope, blocks/second², as felt by a rideable pointing straight up a 45° hill
     * would be {@code slopeGravity · sin(45°)}. Set to {@code 0} to switch hill effort off entirely.
     *
     * <p>Deliberately <b>not</b> 9.81. Minecraft's metre is generous and its hills are steep — a real
     * g makes a 1-in-3 road physically unrideable on a bicycle, which is realistic and no fun. Treat
     * it as a feel knob, not a physical constant.</p>
     *
     * <p>It lives here rather than being passed with {@link Terrain} because it belongs to the
     * <i>vehicle's</i> numbers, alongside {@link #airDrag} and {@link #rollingResistance}, which are
     * likewise shared across every variant today but are free to differ tomorrow. The slope itself is
     * a property of the world and stays in {@code Terrain}; how hard this machine feels it is a
     * property of the machine.</p>
     */
    public final double slopeGravity;

    /** Slope gravity for a tuning built without one — see {@link #slopeGravity}. */
    public static final double DEFAULT_SLOPE_GRAVITY = 4.5D;

    /**
     * How dearly this machine buys a <b>step</b> — a kerb, a slab, a whole block — as opposed to a
     * slope. Expressed as the speed (blocks/s) at which a rideable arrives at a full one-block lip
     * carrying <i>exactly</i> enough momentum for the lip to take all of it.
     *
     * <p>A step is not a slope and must not be charged like one. Gravity along a grade
     * ({@link #slopeGravity}) is a continuous force acting over a continuous climb; hopping a lip is a
     * discrete event, and {@link BikePhysics#afterStepUp} charges it discretely — as kinetic energy,
     * {@code v² -= stepClimbSpeed² · rise}, which is what makes the two things this tuning has to get
     * right fall out of one number rather than a table:</p>
     * <ul>
     *   <li><b>Taller costs disproportionately more.</b> Because the cost lands in {@code v²} and the
     *       speed comes back out through a square root, a rider at 7 blocks/s loses about an eighth of
     *       their speed to a slab and about a third to a full block — not twice as much, more than
     *       twice as much. That is the asymmetry the mechanic exists for.</li>
     *   <li><b>Carrying speed helps.</b> The cost is a fixed number of joules, so it is a small tax on
     *       a rider with momentum and a wall to one crawling at the lip — which is exactly how kerbs
     *       work on a real bicycle.</li>
     * </ul>
     *
     * <p>Deliberately larger than the pure {@code √(2·g·h)} a frictionless ramp would ask for: a wheel
     * striking a vertical face is a collision, and most of what it takes out goes to heat and to
     * shoving the machine about rather than to lifting it. Set to {@code 0} to make steps free again.</p>
     *
     * <p>It belongs to the <i>vehicle</i>, alongside {@link #airDrag}, for a reason that will matter
     * the moment anyone tunes it: how well a lip is absorbed is mostly wheel diameter. A 26" bicycle
     * wheel rolls over a kerb a 6" scooter wheel slams into. Every variant shares one value today
     * because nobody has measured what the difference should be, not because there isn't one.</p>
     */
    public final double stepClimbSpeed;

    /**
     * The fraction of its speed a step-up may never take a rideable below, {@code 0}–{@code 1}.
     *
     * <p>Without it the energy sum bottoms out at zero and a lip taller than a rider's momentum stops
     * them dead — which is realistic, and is a trap: a scooter at its 5.4 blocks/s top speed does not
     * have a full block's worth of energy to spend, so <i>every</i> block-high step would halt it, and
     * a survival-world hillside would be a series of standing starts. This floor turns that into a
     * heavy price instead of a wall. It bites only at the bottom end; at any speed where the energy sum
     * leaves more than this, it never comes up.</p>
     *
     * <p>The default is what makes the slow variants work off-road, and it is worth knowing which knob
     * to reach for: it is the <i>only</i> thing standing between a scooter and a dead stop at every
     * block-high rise, so it decides how a scooter and a one-wheel climb, while
     * {@link #stepClimbSpeed} decides how a bicycle and an e-bike do. They barely interact — at
     * bicycle speeds the energy sum is above this floor and the floor is never consulted at all.</p>
     */
    public final double stepClimbRetain;

    /** Step-climb values for a tuning built without them — see {@link #stepClimbSpeed}. */
    public static final double DEFAULT_STEP_CLIMB_SPEED = 5.5D;
    public static final double DEFAULT_STEP_CLIMB_RETAIN = 0.40D;

    /**
     * A tuning with the default slope gravity. Kept so the nine numbers that predate hills still
     * construct a valid tuning — every existing caller and test uses this form.
     */
    public BikeTuning(double maxSpeed,
                      double pedalAcceleration,
                      double brakeDeceleration,
                      double rollingResistance,
                      double airDrag,
                      double maxSteerRateDegPerSec,
                      double steerSpeedFalloff,
                      double maxReverseSpeed,
                      double reverseAcceleration) {
        this(maxSpeed, pedalAcceleration, brakeDeceleration, rollingResistance, airDrag,
            maxSteerRateDegPerSec, steerSpeedFalloff, maxReverseSpeed, reverseAcceleration,
            DEFAULT_SLOPE_GRAVITY);
    }

    /**
     * A tuning with the default step-climb cost. Kept for the same reason as the nine-argument form:
     * the ten numbers that predate kerbs still describe a valid machine.
     */
    public BikeTuning(double maxSpeed,
                      double pedalAcceleration,
                      double brakeDeceleration,
                      double rollingResistance,
                      double airDrag,
                      double maxSteerRateDegPerSec,
                      double steerSpeedFalloff,
                      double maxReverseSpeed,
                      double reverseAcceleration,
                      double slopeGravity) {
        this(maxSpeed, pedalAcceleration, brakeDeceleration, rollingResistance, airDrag,
            maxSteerRateDegPerSec, steerSpeedFalloff, maxReverseSpeed, reverseAcceleration,
            slopeGravity, DEFAULT_STEP_CLIMB_SPEED, DEFAULT_STEP_CLIMB_RETAIN);
    }

    public BikeTuning(double maxSpeed,
                      double pedalAcceleration,
                      double brakeDeceleration,
                      double rollingResistance,
                      double airDrag,
                      double maxSteerRateDegPerSec,
                      double steerSpeedFalloff,
                      double maxReverseSpeed,
                      double reverseAcceleration,
                      double slopeGravity,
                      double stepClimbSpeed,
                      double stepClimbRetain) {
        this.maxSpeed = maxSpeed;
        this.pedalAcceleration = pedalAcceleration;
        this.brakeDeceleration = brakeDeceleration;
        this.rollingResistance = rollingResistance;
        this.airDrag = airDrag;
        this.maxSteerRateDegPerSec = maxSteerRateDegPerSec;
        this.steerSpeedFalloff = steerSpeedFalloff;
        this.maxReverseSpeed = maxReverseSpeed;
        this.reverseAcceleration = reverseAcceleration;
        this.slopeGravity = slopeGravity;
        this.stepClimbSpeed = stepClimbSpeed;
        this.stepClimbRetain = stepClimbRetain;
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
            this.reverseAcceleration,
            this.slopeGravity,
            this.stepClimbSpeed,
            this.stepClimbRetain);
    }

    /** Least and most traction a surface may claim, so a mistyped config cannot break the handling. */
    private static final double MIN_GRIP = 0.05D;
    private static final double MAX_GRIP = 2.0D;

    /**
     * This tuning as ridden on a surface offering {@code grip} times normal traction — what the bike
     * actually handles like on ice, gravel or wet stone.
     *
     * <p>Traction limits three things, and they are exactly the three scaled here: how hard you can
     * <b>brake</b>, how sharply you can <b>steer</b>, and how much power you can put down before the
     * tyre gives up ({@link #pedalAcceleration}). It deliberately leaves {@link #maxSpeed} alone —
     * ice does not lower the speed a bike is capable of, it lowers your ability to get there and your
     * options once you have. It also leaves {@link #rollingResistance} alone, because that is a
     * different physical thing and {@link Terrain#rollFactor} already carries it.</p>
     *
     * <p><b>Apply this last</b>, after {@link #withAssist}. Assist decides what the motor is offering;
     * grip decides how much of that the ground will accept. Composed the other way round, a fresh
     * battery would quietly undo the ice.</p>
     */
    public BikeTuning withGrip(double grip) {
        double g = grip < MIN_GRIP ? MIN_GRIP : (grip > MAX_GRIP ? MAX_GRIP : grip);
        if (g == 1.0D) {
            return this;
        }
        return new BikeTuning(
            this.maxSpeed,
            this.pedalAcceleration * g,
            this.brakeDeceleration * g,
            this.rollingResistance,
            this.airDrag,
            this.maxSteerRateDegPerSec * g,
            this.steerSpeedFalloff,
            this.maxReverseSpeed,
            this.reverseAcceleration,
            this.slopeGravity,
            this.stepClimbSpeed,
            this.stepClimbRetain);
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
