package com.micatechnologies.minecraft.ldib.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Behavioural tests for the battery model and the assist blend it feeds. Same spirit as
 * {@link BikePhysicsTest}: pure Java, no Minecraft instance, and each test pins a property the
 * feature is supposed to have rather than a number it happens to produce — so retuning the defaults
 * stays a config change, while breaking "a flat e-bike still rides like a bicycle" fails the build.
 */
class BatteryModelTest {

    private static final double DT = 1.0D / 20.0D; // one Minecraft tick

    /** The shipped bicycle and e-bike relationship, in pure-Java terms (see LdibConfig). */
    private static final BikeTuning BICYCLE = BikeTuning.defaultBicycle();
    private static final BikeTuning EBIKE =
        new BikeTuning(11.0D, 5.5D, 9.0D, 0.6D, 0.010D, 90.0D, 5.0D);

    private static final double RESERVE = 0.15D;

    // --- Spending charge ---------------------------------------------------------------------

    @Test
    void drainIsProportionalToDistanceCovered() {
        assertEquals(0.5D, BatteryModel.drain(BatteryModel.FULL, 100.0D, 200.0D), 1.0e-9D,
            "half the range should spend half the charge");
        assertEquals(0.75D, BatteryModel.drain(BatteryModel.FULL, 50.0D, 200.0D), 1.0e-9D);
    }

    @Test
    void aFullChargeLastsExactlyItsRatedRange() {
        double range = 400.0D;
        double charge = BatteryModel.FULL;
        // Ride the rated range in one-block bites.
        for (int i = 0; i < (int) range; i++) {
            charge = BatteryModel.drain(charge, 1.0D, range);
        }
        assertEquals(BatteryModel.EMPTY, charge, 1.0e-9D,
            "a full charge should be exactly used up by its rated range");
    }

    @Test
    void drainNeverGoesBelowEmpty() {
        assertEquals(BatteryModel.EMPTY, BatteryModel.drain(0.1D, 10000.0D, 100.0D), 1.0e-9D,
            "overrunning the range should flatten the battery, not go negative");
    }

    @Test
    void coastingAndBrakingCostNothing() {
        // The entity only calls drain under positive throttle, but the model defends the invariant
        // itself: no distance under power, no spend.
        assertEquals(0.5D, BatteryModel.drain(0.5D, 0.0D, 100.0D), 1.0e-9D);
        assertEquals(0.5D, BatteryModel.drain(0.5D, -25.0D, 100.0D), 1.0e-9D,
            "a negative distance must never charge the battery back up");
    }

    @Test
    void zeroRangeDisablesTheBatteryEntirely() {
        assertEquals(BatteryModel.FULL, BatteryModel.drain(BatteryModel.FULL, 99999.0D, 0.0D), 1.0e-9D,
            "range 0 is the config escape hatch: the battery never runs down");
    }

    // --- Assist curve ------------------------------------------------------------------------

    @Test
    void assistIsFullUntilTheReserve() {
        assertEquals(1.0D, BatteryModel.assist(BatteryModel.FULL, RESERVE), 1.0e-9D);
        assertEquals(1.0D, BatteryModel.assist(0.5D, RESERVE), 1.0e-9D);
        assertEquals(1.0D, BatteryModel.assist(RESERVE, RESERVE), 1.0e-9D,
            "assist should still be full exactly at the reserve, not already fading");
    }

    @Test
    void assistTapersLinearlyThroughTheReserve() {
        assertEquals(0.5D, BatteryModel.assist(RESERVE / 2.0D, RESERVE), 1.0e-9D,
            "halfway through the reserve should give half assist");
        double justAbove = BatteryModel.assist(RESERVE * 0.9D, RESERVE);
        double justBelow = BatteryModel.assist(RESERVE * 0.1D, RESERVE);
        assertTrue(justAbove > justBelow, "assist must decrease monotonically as charge falls");
    }

    @Test
    void aFlatBatteryGivesNoAssist() {
        assertEquals(0.0D, BatteryModel.assist(BatteryModel.EMPTY, RESERVE), 1.0e-9D);
        assertEquals(0.0D, BatteryModel.assist(-1.0D, RESERVE), 1.0e-9D);
    }

    @Test
    void zeroReserveRestoresAnAbruptCutout() {
        assertEquals(1.0D, BatteryModel.assist(0.001D, 0.0D), 1.0e-9D,
            "with no reserve configured there is no taper — full assist right to the end");
        assertEquals(0.0D, BatteryModel.assist(0.0D, 0.0D), 1.0e-9D);
    }

    @Test
    void clampFoldsOutOfRangeValuesAndTreatsNaNAsEmpty() {
        assertEquals(BatteryModel.FULL, BatteryModel.clamp(4.0D), 1.0e-9D);
        assertEquals(BatteryModel.EMPTY, BatteryModel.clamp(-4.0D), 1.0e-9D);
        assertEquals(0.25D, BatteryModel.clamp(0.25D), 1.0e-9D);
        assertEquals(BatteryModel.EMPTY, BatteryModel.clamp(Double.NaN), 1.0e-9D,
            "a corrupt NBT charge should read as flat, not poison the physics with NaN");
    }

    // --- The assist blend --------------------------------------------------------------------

    @Test
    void fullAssistLeavesTheVariantTuningAlone() {
        BikeTuning blended = EBIKE.withAssist(BICYCLE, 1.0D);
        assertEquals(EBIKE.maxSpeed, blended.maxSpeed, 1.0e-9D);
        assertEquals(EBIKE.pedalAcceleration, blended.pedalAcceleration, 1.0e-9D);
    }

    @Test
    void aFlatEbikeHandlesLikeAPedalBicycle() {
        BikeTuning blended = EBIKE.withAssist(BICYCLE, 0.0D);
        assertEquals(BICYCLE.maxSpeed, blended.maxSpeed, 1.0e-9D,
            "with the motor gone an e-bike should top out where a bicycle does");
        assertEquals(BICYCLE.pedalAcceleration, blended.pedalAcceleration, 1.0e-9D);
    }

    @Test
    void assistBlendsOnlySpeedAndAcceleration() {
        // An unpowered baseline that differs in EVERY field, so anything wrongly interpolated shows up.
        BikeTuning odd = new BikeTuning(1.0D, 1.0D, 99.0D, 9.9D, 0.9D, 999.0D, 99.0D);
        BikeTuning blended = EBIKE.withAssist(odd, 0.0D);
        assertEquals(EBIKE.brakeDeceleration, blended.brakeDeceleration, 1.0e-9D,
            "a flat battery must not change the brakes");
        assertEquals(EBIKE.rollingResistance, blended.rollingResistance, 1.0e-9D);
        assertEquals(EBIKE.airDrag, blended.airDrag, 1.0e-9D);
        assertEquals(EBIKE.maxSteerRateDegPerSec, blended.maxSteerRateDegPerSec, 1.0e-9D,
            "a flat battery must not change how the bike steers");
        assertEquals(EBIKE.steerSpeedFalloff, blended.steerSpeedFalloff, 1.0e-9D);
    }

    @Test
    void assistIsClampedToItsRange() {
        assertEquals(EBIKE.maxSpeed, EBIKE.withAssist(BICYCLE, 5.0D).maxSpeed, 1.0e-9D);
        assertEquals(BICYCLE.maxSpeed, EBIKE.withAssist(BICYCLE, -5.0D).maxSpeed, 1.0e-9D);
    }

    // --- The two together, through the handling model ----------------------------------------

    @Test
    void aDrainedEbikeStillRidesButNoFasterThanABicycle() {
        BikeTuning flat = EBIKE.withAssist(BICYCLE, BatteryModel.assist(BatteryModel.EMPTY, RESERVE));

        BikeState s = BikeState.stationary(0.0D);
        for (int i = 0; i < 20 * 30; i++) {
            s = BikePhysics.step(s, 1.0D, 0.0D, flat, DT);
        }
        assertTrue(s.speed > 1.0D, "a flat e-bike must still be rideable — you can always pedal home");
        assertTrue(s.speed <= BICYCLE.maxSpeed + 1.0e-9D,
            "a flat e-bike must not outrun a pedal bicycle, was " + s.speed);
    }

    @Test
    void chargeStateOrdersTopSpeedsAsExpected() {
        double full = topSpeed(EBIKE.withAssist(BICYCLE, BatteryModel.assist(1.0D, RESERVE)));
        double low = topSpeed(EBIKE.withAssist(BICYCLE, BatteryModel.assist(RESERVE / 2.0D, RESERVE)));
        double empty = topSpeed(EBIKE.withAssist(BICYCLE, BatteryModel.assist(0.0D, RESERVE)));
        assertTrue(full > low, "a healthy battery should beat one into its reserve");
        assertTrue(low > empty, "a reserve battery should still beat a flat one");
    }

    /** Full throttle for 30 s — long enough for any of these tunings to settle at its ceiling. */
    private static double topSpeed(BikeTuning tuning) {
        BikeState s = BikeState.stationary(0.0D);
        for (int i = 0; i < 20 * 30; i++) {
            s = BikePhysics.step(s, 1.0D, 0.0D, tuning, DT);
        }
        return s.speed;
    }
}
