package com.micatechnologies.minecraft.ldib.physics;

/**
 * Immutable kinematic state of a bike: how fast it is going and which way it points.
 *
 * <p>This is the entire state the {@link BikePhysics} model integrates. World position is a
 * downstream consequence of it — the entity layer turns {@code (speed, heading)} into
 * {@code motionX/motionZ} at the tick boundary — so nothing about a {@code World}, a
 * {@code Vec3d} or a Minecraft tick belongs in this class. Keeping it plain Java is what lets
 * the whole handling model be unit-tested on a bare JVM in milliseconds.</p>
 *
 * <p>Units are SI-ish and world-scaled: <b>metres and seconds</b>, with one Minecraft block
 * treated as one metre (the same convention the sibling RCMC mod uses). Heading is a compass
 * yaw in degrees, matching Minecraft's {@code Entity.rotationYaw} so the entity layer needs no
 * angle conversion.</p>
 */
public final class BikeState {

    /** Forward ground speed along the current heading, in blocks/second. Never negative for the MVP. */
    public final double speed;

    /** Heading in degrees, using Minecraft's yaw convention (0 = +Z/south, increasing clockwise). */
    public final double headingDegrees;

    public BikeState(double speed, double headingDegrees) {
        this.speed = speed;
        this.headingDegrees = headingDegrees;
    }

    /** The at-rest state pointing along {@code headingDegrees}. */
    public static BikeState stationary(double headingDegrees) {
        return new BikeState(0.0D, headingDegrees);
    }

    public BikeState withSpeed(double newSpeed) {
        return new BikeState(newSpeed, headingDegrees);
    }

    public BikeState withHeading(double newHeadingDegrees) {
        return new BikeState(speed, newHeadingDegrees);
    }

    @Override
    public String toString() {
        return String.format("BikeState[speed=%.3f b/s, heading=%.1f deg]", speed, headingDegrees);
    }
}
