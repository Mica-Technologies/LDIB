package com.micatechnologies.minecraft.ldib.physics;

/**
 * The bike handling model: given a state, the rider's inputs and a timestep, produce the next
 * state. Pure Java, zero Minecraft types, no side effects — every method is a function of its
 * arguments, which is exactly what makes it testable without a game (see {@code BikePhysicsTest}).
 *
 * <p>The model is deliberately 2-D and kinematic, not a rigid-body simulation: a bike ridden with
 * WASD has effectively two controllable degrees of freedom — forward speed and heading — and
 * modelling only those keeps the result deterministic across client and server, which is what the
 * ride needs to feel smooth under Minecraft's netcode. Vertical motion (gravity, going up a slope)
 * is the entity layer's job, not this class's; here the world is flat and the only axis is "along
 * the current heading".</p>
 *
 * <p><b>Integration.</b> Like RCMC's integrator this updates speed first, then heading, then lets
 * the caller derive position from the <i>new</i> speed — semi-implicit (symplectic) Euler, which
 * does not pump energy into the system the way explicit Euler does. Drag is applied as an
 * exponential decay so the outcome does not depend on how finely the tick is sub-divided.</p>
 */
public final class BikePhysics {

    private BikePhysics() {
        throw new AssertionError("No instances.");
    }

    /**
     * Advance one step.
     *
     * @param state    the current state
     * @param throttle rider forward input in {@code [-1, 1]}: {@code +1} full pedal, {@code 0}
     *                 coast, {@code -1} full brake. (Reverse is not modelled in the MVP; a
     *                 negative input below a stopped bike simply holds it at rest.)
     * @param steer    rider turn input in {@code [-1, 1]}: {@code -1} hard left, {@code +1} hard
     *                 right, matching Minecraft's clockwise-positive yaw.
     * @param tuning   the handling constants for this vehicle
     * @param dt       timestep in seconds (e.g. {@code 1/20} for a whole tick, less when sub-stepping)
     * @return the next state
     */
    public static BikeState step(BikeState state, double throttle, double steer,
                                 BikeTuning tuning, double dt) {
        double throttleClamped = clamp(throttle, -1.0D, 1.0D);
        double steerClamped = clamp(steer, -1.0D, 1.0D);

        // --- 1. Speed. Apply the rider's longitudinal input, then passive losses. ---
        double speed = state.speed;

        if (throttleClamped > 0.0D) {
            speed += tuning.pedalAcceleration * throttleClamped * dt;
        } else if (throttleClamped < 0.0D) {
            // Braking opposes motion; it cannot push a stopped bike backwards.
            speed += tuning.brakeDeceleration * throttleClamped * dt; // throttle < 0, so this subtracts
        }

        // Rolling resistance: exponential decay, timestep-independent.
        speed *= Math.exp(-tuning.rollingResistance * dt);
        // Quadratic air drag: -k * v^2, integrated over the step (still only acting to slow down).
        speed -= tuning.airDrag * speed * speed * dt;

        if (speed < 0.0D) {
            speed = 0.0D;
        }
        if (speed > tuning.maxSpeed) {
            speed = tuning.maxSpeed;
        }

        // --- 2. Heading. Steering authority falls off with speed. A parked bike does not turn. ---
        double heading = state.headingDegrees;
        if (speed > 1.0e-4D) {
            double steerRate = tuning.maxSteerRateDegPerSec
                * (tuning.steerSpeedFalloff / (tuning.steerSpeedFalloff + speed));
            heading += steerClamped * steerRate * dt;
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
