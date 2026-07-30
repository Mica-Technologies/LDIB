package com.micatechnologies.minecraft.ldib.physics;

/**
 * The bike handling model: given a state, the rider's inputs and a timestep, produce the next
 * state. Pure Java, zero Minecraft types, no side effects — every method is a function of its
 * arguments, which is exactly what makes it testable without a game (see {@code BikePhysicsTest}).
 *
 * <p>The model is deliberately 2-D and kinematic, not a rigid-body simulation: a bike ridden with
 * WASD has effectively two controllable degrees of freedom — forward speed and heading — and
 * modelling only those keeps the result deterministic across client and server, which is what the
 * ride needs to feel smooth under Minecraft's netcode. The only axis here is still "along the current
 * heading" — <b>vertical motion remains the entity layer's job</b>, and this class never learns where
 * the bike is. What it does now know is how steeply the road tilts and what it is paved with, handed
 * in as a {@link Terrain}: three plain numbers the entity derives from the world. Slope costs or gives
 * speed along the heading; the surface scales rolling resistance and (through
 * {@link BikeTuning#withGrip}) braking, steering and traction.</p>
 *
 * <p><b>Integration.</b> Like RCMC's integrator this updates speed first, then heading, then lets
 * the caller derive position from the <i>new</i> speed — semi-implicit (symplectic) Euler, which
 * does not pump energy into the system the way explicit Euler does. Drag is applied as an
 * exponential decay so the outcome does not depend on how finely the tick is sub-divided.</p>
 */
public final class BikePhysics {

    /**
     * Speed (blocks/s) below which a bike counts as parked: too slow to steer, and — for the entity
     * layer, which shares this threshold — too slow to be worth stepping at all.
     */
    public static final double MIN_ROLLING_SPEED = 1.0e-4D;

    private BikePhysics() {
        throw new AssertionError("No instances.");
    }

    /**
     * Advance one step.
     *
     * @param state    the current state
     * @param throttle rider forward input in {@code [-1, 1]}: {@code +1} full pedal, {@code 0}
     *                 coast, {@code -1} brake and then back up.
     * @param steer    rider turn input in {@code [-1, 1]}: {@code -1} hard left, {@code +1} hard
     *                 right, matching Minecraft's clockwise-positive yaw.
     * @param tuning   the handling constants for this vehicle
     * @param dt       timestep in seconds (e.g. {@code 1/20} for a whole tick, less when sub-stepping)
     * @return the next state
     */
    public static BikeState step(BikeState state, double throttle, double steer,
                                 BikeTuning tuning, double dt) {
        return step(state, throttle, steer, tuning, Terrain.FLAT, dt);
    }

    /**
     * Advance one step over {@code terrain} — the same model, told what the ground is doing.
     *
     * <p>Terrain enters in exactly two places, both of them existing terms rather than new machinery:
     * gravity along the slope is one more acceleration on the speed, and the surface scales the
     * rolling resistance that was already there. The third thing a surface does — limit braking,
     * steering and traction — is not handled here at all; it is folded into {@code tuning} by
     * {@link BikeTuning#withGrip} before the call, so this method stays a function of its arguments
     * and the tuning stays the single description of "how this machine behaves right now".</p>
     *
     * @param terrain the slope and surface under the rideable; {@link Terrain#FLAT} for none
     */
    public static BikeState step(BikeState state, double throttle, double steer,
                                 BikeTuning tuning, Terrain terrain, double dt) {
        double throttleClamped = clamp(throttle, -1.0D, 1.0D);
        double steerClamped = clamp(steer, -1.0D, 1.0D);

        // --- 1. Speed. Apply the rider's longitudinal input, then passive losses. ---
        //
        // Longitudinal input means two different things depending on which way the bike is already
        // rolling, which is what makes one key do both jobs: pushed AGAINST the direction of travel it
        // is the brake (strong), and only once the bike is at rest does it drive the other way (weak).
        // Each branch stops exactly at zero rather than sailing through it, so "brake to a halt" and
        // "then start backing up" are two distinct, separately-felt phases of holding one key rather
        // than a lurch through the middle at braking authority.
        double speed = state.speed;

        if (throttleClamped > 0.0D) {
            speed = speed < 0.0D
                ? Math.min(0.0D, speed + tuning.brakeDeceleration * throttleClamped * dt)
                : speed + tuning.pedalAcceleration * throttleClamped * dt;
        } else if (throttleClamped < 0.0D) {
            speed = speed > 0.0D
                ? Math.max(0.0D, speed + tuning.brakeDeceleration * throttleClamped * dt)
                : speed + tuning.reverseAcceleration * throttleClamped * dt;
        }

        // Gravity along the slope. Signed against the HEADING, not against the direction of travel,
        // which is what makes one term cover every case: pointing uphill it bleeds off forward speed
        // and hurries a backwards roll, pointing downhill it does the reverse, and a rider who stalls
        // on a climb rolls back down without any of it being special-cased. Mass-independent, as
        // gravity on a slope is — a heavier bike is not slower uphill in a kinematic model.
        speed -= tuning.slopeGravity * terrain.slopeSine() * dt;

        // Rolling resistance: exponential decay, timestep-independent. Decaying toward zero is already
        // the right thing at negative speed — it opposes a reversing bike exactly as it opposes a
        // rolling one. Scaled by the surface, which is the main reason a made road is worth riding on.
        speed *= Math.exp(-tuning.rollingResistance * terrain.rollFactor * dt);
        // Quadratic air drag, written as -k*v*|v| rather than -k*v^2 so it stays a *resistance*: the
        // squared form is positive whichever way you are going, which would have it shoving a
        // reversing bike backwards ever faster.
        speed -= tuning.airDrag * speed * Math.abs(speed) * dt;

        // The ceilings hold on a descent too, and that is deliberate. Letting gravity carry a rider
        // past maxSpeed downhill would be more realistic and more fun, but maxSpeed is the mod's
        // safety margin against the server's "vehicle moved too quickly" kick (master plan, Appendix
        // A.1), and a long hill is exactly where that margin would be spent. So a descent means
        // reaching top speed without pedalling, not exceeding it. The reverse ceiling matters even
        // more now: it is what stops a bike abandoned facing up a hill from rolling away at ever-
        // increasing speed. Revisit only alongside the fast-vehicle work, never on its own.
        if (speed > tuning.maxSpeed) {
            speed = tuning.maxSpeed;
        }
        if (speed < -tuning.maxReverseSpeed) {
            speed = -tuning.maxReverseSpeed;
        }

        // --- 2. Heading. Steering authority falls off with speed. A parked bike does not turn. ---
        // Reversing flips the sign: a bike backing up with the bars turned left swings its rear to the
        // right, the same way a car reverses. Steering authority itself depends on how fast you are
        // going, not which way, hence the absolute values.
        double heading = state.headingDegrees;
        if (Math.abs(speed) > MIN_ROLLING_SPEED) {
            double steerRate = tuning.maxSteerRateDegPerSec
                * (tuning.steerSpeedFalloff / (tuning.steerSpeedFalloff + Math.abs(speed)));
            heading += Math.signum(speed) * steerClamped * steerRate * dt;
            heading = wrapDegrees(heading);
        }

        return new BikeState(speed, heading);
    }

    /**
     * Analytic coasting top speed is 0; this returns the powered equilibrium speed where pedal
     * thrust balances drag, i.e. what {@link #step} converges to under full throttle. Handy for
     * tuning and asserted by the test suite.
     */
    public static double poweredEquilibriumSpeed(BikeTuning tuning) {
        // Solve pedalAcceleration = rollingResistance*v + airDrag*v^2 (small-decay linearisation of
        // the exponential term), clamped to the configured ceiling.
        double a = tuning.airDrag;
        double b = tuning.rollingResistance;
        double c = -tuning.pedalAcceleration;
        double v;
        if (a < 1.0e-9D) {
            v = -c / b;
        } else {
            v = (-b + Math.sqrt(b * b - 4.0D * a * c)) / (2.0D * a);
        }
        return Math.min(v, tuning.maxSpeed);
    }

    /** Fold an angle into {@code [-180, 180)}, matching {@code MathHelper.wrapDegrees}. */
    public static double wrapDegrees(double degrees) {
        double d = degrees % 360.0D;
        if (d >= 180.0D) {
            d -= 360.0D;
        }
        if (d < -180.0D) {
            d += 360.0D;
        }
        return d;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
