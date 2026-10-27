package com.micatechnologies.minecraft.ldib.api;

/**
 * Distance and direction from a player to a station, for "nearest station" lists and arrows. Pure
 * arithmetic on plain numbers — no Minecraft types — so it is unit-tested on a bare JVM like the
 * physics, and any phone app or HUD can use it without reimplementing Minecraft's yaw convention.
 *
 * <p>Angles use <b>Minecraft yaw</b>: degrees clockwise from south, so 0 faces +Z, 90 faces −X (west),
 * 180 faces −Z (north) and −90 faces +X (east). That is the convention {@code rotationYaw} is in, which
 * is what lets {@link #relativeBearing} turn a station's bearing into "ahead / left / behind" for the
 * way a player is facing.</p>
 */
public final class StationMath {

    private StationMath() {
        throw new AssertionError("No instances.");
    }

    /** Horizontal distance, blocks. Height is ignored: you ride to a station, you don't fly to it. */
    public static double distance(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The yaw that faces from {@code from} toward {@code to}, in {@code [-180, 180)}. */
    public static double bearing(double fromX, double fromZ, double toX, double toZ) {
        // Minecraft forward for a yaw is (-sin yaw, cos yaw), so yaw = atan2(-dx, dz).
        return wrap(Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ)));
    }

    /**
     * Where a target at {@code bearing} lies relative to a player facing {@code facingYaw}, in
     * {@code [-180, 180)}: 0 dead ahead, positive to the right, negative to the left, ±180 behind.
     * Positive is right because Minecraft's yaw grows clockwise.
     */
    public static double relativeBearing(double facingYaw, double bearing) {
        return wrap(bearing - facingYaw);
    }

    /** The eight-point compass name (N, NE, E, ...) of a yaw. */
    public static String compass(double yaw) {
        // Yaw 180 is north; rotate so north is 0 and clockwise is positive, then pick the octant.
        String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        double fromNorth = wrap(yaw - 180.0D);
        int index = (int) Math.floor((fromNorth + 360.0D + 22.5D) / 45.0D) % 8;
        return points[index];
    }

    /** Fold an angle into {@code [-180, 180)}. */
    public static double wrap(double degrees) {
        double d = degrees % 360.0D;
        if (d >= 180.0D) {
            d -= 360.0D;
        }
        if (d < -180.0D) {
            d += 360.0D;
        }
        return d;
    }
}
