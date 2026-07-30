package com.micatechnologies.minecraft.ldib.physics;

/**
 * What the ground is doing to a rideable right now: how steeply it tilts, and what it is made of.
 *
 * <p>This is the seam that lets the world affect the ride without the handling model learning
 * anything about Minecraft. {@link BikePhysics} was written for a flat, frictionless-underfoot world
 * — the docs called it out as a deliberate gap ("a graded road is climbed at the same effort as a
 * flat one") — and the fix is <b>not</b> to teach the model about blocks. It is to have the entity
 * layer look at the world, boil it down to the three numbers below, and hand them in. Everything here
 * is plain data, so the model stays unit-testable on a bare JVM and stays deterministic: both sides
 * read the same blocks and do the same arithmetic.</p>
 *
 * <p>Grip is deliberately <i>not</i> a field the model reads directly — it is folded into a derived
 * {@link BikeTuning} by {@link BikeTuning#withGrip}, the same way a flat battery is folded in by
 * {@link BikeTuning#withAssist}. Only {@link #grade} and {@link #rollFactor} are read during a step.
 * {@link #gripFactor} rides along here anyway because it comes from the same block lookup and it
 * would be silly to sample the world twice.</p>
 */
public final class Terrain {

    /** Level, dry tarmac: no slope, ordinary grip, ordinary rolling resistance. */
    public static final Terrain FLAT = new Terrain(0.0D, 1.0D, 1.0D);

    /**
     * Slope along the direction the rideable is <b>pointing</b>, as rise over run. Positive is
     * uphill, so a bike facing up a 1-in-5 climb sees {@code +0.2} whether it is rolling forwards up
     * it or backwards down it — which is what makes the gravity term come out right in both cases
     * without a special case for reversing.
     */
    public final double grade;

    /**
     * How much of normal traction the surface offers. {@code 1} is dry tarmac; lower is ice, wet
     * stone or loose gravel. Scales the things traction actually limits — braking, steering and
     * getting the power down — via {@link BikeTuning#withGrip}. It deliberately does <b>not</b> touch
     * top speed: ice does not lower how fast you can eventually go, it lowers your ability to reach
     * that speed and to do anything about it once you have.
     */
    public final double gripFactor;

    /**
     * Multiplier on the tuning's rolling resistance. {@code 1} is a made road; above 1 is grass,
     * sand or gravel dragging at the tyres. This is the main lever that makes a built road worth
     * riding on, and the reason a fat-tyred one-wheel can be given an easier time off-road than a
     * road bike simply by tuning what its variant does with the same number.
     */
    public final double rollFactor;

    public Terrain(double grade, double gripFactor, double rollFactor) {
        this.grade = grade;
        this.gripFactor = gripFactor;
        this.rollFactor = rollFactor;
    }

    /** This terrain's surface properties with a different slope — the common case when re-sampling. */
    public Terrain withGrade(double newGrade) {
        return new Terrain(newGrade, gripFactor, rollFactor);
    }

    /**
     * The sine of the slope angle, which is the fraction of gravity that acts along the road.
     *
     * <p>{@code sin(atan(grade))} reduces to {@code grade / sqrt(1 + grade²)} — same answer, no trig
     * calls, and it is well-behaved as the grade grows: it tends to 1 rather than running away, so a
     * badly-measured cliff-face grade can never produce more than one gravity of deceleration.</p>
     */
    public double slopeSine() {
        return grade / Math.sqrt(1.0D + grade * grade);
    }

    /** Whether this is level ground with ordinary footing — lets callers skip the whole business. */
    public boolean isFlatAndPlain() {
        return grade == 0.0D && gripFactor == 1.0D && rollFactor == 1.0D;
    }

    @Override
    public String toString() {
        return String.format("Terrain[grade=%.3f grip=%.2f roll=%.2f]", grade, gripFactor, rollFactor);
    }
}
