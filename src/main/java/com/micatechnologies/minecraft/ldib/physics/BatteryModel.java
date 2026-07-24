package com.micatechnologies.minecraft.ldib.physics;

/**
 * The battery model for powered rideables — how a charge is spent and what a fading one does to the
 * assist. Pure Java, zero Minecraft types, no state of its own: every method is a function of its
 * arguments, which is what lets the whole thing be unit-tested on a bare JVM alongside
 * {@link BikePhysics} (see {@code BatteryModelTest}).
 *
 * <p><b>Charge is a fraction</b>, {@code 0.0} empty to {@code 1.0} full, rather than a watt-hour
 * count. The thing a player actually cares about is "how much further can I go", and expressing that
 * as a fraction of a configured <i>range in blocks</i> means the tuning knob is the number a server
 * owner wants to set ("an e-bike goes 4000 blocks on a charge") instead of an energy unit they would
 * have to convert.</p>
 *
 * <p><b>Spend is per block travelled, not per tick.</b> A bike that is stopped at a light burns
 * nothing, and a slow ride and a fast ride over the same route cost the same — which is both simpler
 * to reason about and closer to how range is quoted for real e-bikes. Coasting and braking are free:
 * only the motor draws, so only a positive throttle drains.</p>
 */
public final class BatteryModel {

    /** A full charge. */
    public static final double FULL = 1.0D;

    /** An empty charge. */
    public static final double EMPTY = 0.0D;

    private BatteryModel() {
        throw new AssertionError("No instances.");
    }

    /**
     * Spend charge for a distance travelled under power.
     *
     * @param charge         current charge fraction
     * @param distanceBlocks distance travelled under power this step, in blocks (negative is ignored)
     * @param rangeBlocks    blocks a full charge is worth; {@code <= 0} disables drain entirely, which
     *                       is the config escape hatch for servers that would rather not track a
     *                       battery at all
     * @return the new charge fraction, never below {@link #EMPTY}
     */
    public static double drain(double charge, double distanceBlocks, double rangeBlocks) {
        if (rangeBlocks <= 0.0D || distanceBlocks <= 0.0D) {
            return clamp(charge);
        }
        return clamp(charge - distanceBlocks / rangeBlocks);
    }

    /**
     * How much of the motor's help is available at this charge, {@code 0.0} (none) to {@code 1.0}
     * (full) — the number {@link BikeTuning#withAssist} interpolates with.
     *
     * <p>Full assist holds right down to {@code reserveFraction}, then <b>tapers linearly to nothing
     * at empty</b> rather than cutting out. A cliff would be both a nasty surprise mid-junction and
     * unlike the real thing; a taper gives the rider the "this is going flat" warning through the
     * controls, which is the whole point of modelling a battery rather than a fuel gauge.</p>
     *
     * @param reserveFraction charge below which assist starts fading; {@code <= 0} restores the cliff
     */
    public static double assist(double charge, double reserveFraction) {
        double c = clamp(charge);
        if (c <= EMPTY) {
            return 0.0D;
        }
        if (reserveFraction <= 0.0D) {
            return FULL;
        }
        if (c >= reserveFraction) {
            return FULL;
        }
        return c / reserveFraction;
    }

    /** Fold a charge into {@code [EMPTY, FULL]}, tolerating NaN (which reads as empty). */
    public static double clamp(double charge) {
        if (Double.isNaN(charge)) {
            return EMPTY;
        }
        return charge < EMPTY ? EMPTY : (charge > FULL ? FULL : charge);
    }
}
