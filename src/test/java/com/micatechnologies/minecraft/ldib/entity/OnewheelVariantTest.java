package com.micatechnologies.minecraft.ldib.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.physics.BikePhysics;
import com.micatechnologies.minecraft.ldib.physics.BikeState;
import com.micatechnologies.minecraft.ldib.physics.BikeTuning;
import org.junit.jupiter.api.Test;

/**
 * What makes the one-wheel a one-wheel, pinned against silent regression.
 *
 * <p>Two rules here are load-bearing and neither is visible from the code that enforces them, which
 * is spread across the dock, the rack and the grab action: <b>a one-wheel is standalone</b> (it never
 * touches the share network or a rack) and <b>it rides in the scooter's speed class</b>, which is what
 * the owner asked for rather than the ~20 mph a real board manages. Both are one edited constant away
 * from quietly becoming untrue.
 *
 * <p>These read {@link LdibConfig}'s static defaults rather than calling {@code LdibConfig.init},
 * which would want a Forge {@code Configuration} and a file on disk. That is exactly the values a
 * fresh install runs with, so it is the right thing to pin anyway.</p>
 */
class OnewheelVariantTest {

    private static final double DT = 1.0D / 20.0D; // one Minecraft tick
    private static final double EPS = 1.0e-9D;

    /** Run full throttle for {@code seconds} from rest on {@code tuning}. */
    private static double topSpeedAfter(BikeTuning tuning, double seconds) {
        BikeState s = BikeState.stationary(0.0D);
        for (int i = 0; i < (int) Math.round(seconds / DT); i++) {
            s = BikePhysics.step(s, 1.0D, 0.0D, tuning, DT);
        }
        return s.speed;
    }

    // --- Standalone: no docks, no racks, no fleet ---------------------------------------------

    @Test
    void theOneWheelIsTheOnlyVariantThatSkipsTheStations() {
        assertFalse(BikeVariant.ONEWHEEL.usesStations(),
            "the one-wheel is a standalone device — no dock, no rack, no share fleet");
        for (BikeVariant v : BikeVariant.values()) {
            if (v != BikeVariant.ONEWHEEL) {
                assertTrue(v.usesStations(), v + " should still use racks and docks");
            }
        }
    }

    @Test
    void aVariantOutsideTheFleetFallsBackToItsOwnSkin() {
        // No share livery is painted for it (see tools/gen_skins.py), so asking for one must not
        // produce a path to a PNG that doesn't exist — that renders as the missing-texture checkerboard.
        assertEquals(BikeVariant.ONEWHEEL.texture(), BikeVariant.ONEWHEEL.shareTexture(),
            "a variant with no fleet livery should fall back to its personal skin");
        assertNotEquals(BikeVariant.SCOOTER.texture(), BikeVariant.SCOOTER.shareTexture(),
            "a fleet variant should still have a distinct share livery");
    }

    // --- Identity ------------------------------------------------------------------------------

    @Test
    void theNetworkIdRoundTripsAndIsAppendedNotRenumbered() {
        assertEquals(4, BikeVariant.ONEWHEEL.id(), "ids are persisted in NBT — append, never renumber");
        assertSame(BikeVariant.ONEWHEEL, BikeVariant.byId(4));
        // The four that shipped before it must not have moved.
        assertSame(BikeVariant.BICYCLE, BikeVariant.byId(0));
        assertSame(BikeVariant.EBIKE, BikeVariant.byId(1));
        assertSame(BikeVariant.SCOOTER, BikeVariant.byId(2));
        assertSame(BikeVariant.SCOOTER_FAST, BikeVariant.byId(3));
    }

    @Test
    void itIsRiddenStandingAcrossTheBoard() {
        assertSame(RiderPose.BOARD, BikeVariant.ONEWHEEL.pose());
        assertFalse(RiderPose.BOARD.seated(), "a board rider stands");
        assertEquals(90.0F, RiderPose.BOARD.bodyYawOffset(), EPS,
            "the body turns 90° out of travel — that is what makes it a board stance");
        // Everything you face forward on must stay facing forward.
        assertEquals(0.0F, RiderPose.SEATED.bodyYawOffset(), EPS);
        assertEquals(0.0F, RiderPose.STANDING.bodyYawOffset(), EPS);
    }

    // --- Handling ------------------------------------------------------------------------------

    @Test
    void itRidesInTheStandardScooterSpeedClass() {
        double onewheel = topSpeedAfter(LdibConfig.onewheelTuning(), 30.0D);
        double scooter = topSpeedAfter(LdibConfig.scooterTuning(), 30.0D);
        assertEquals(scooter, onewheel, 0.5D,
            "the owner asked for scooter-class speed, not the ~20 mph a real board does");
        assertTrue(onewheel < topSpeedAfter(LdibConfig.scooterFastTuning(), 30.0D),
            "it must stay well under the performance scooter");
    }

    @Test
    void itOutAcceleratesAndOutCarvesTheScooterItMatchesOnSpeed() {
        assertTrue(topSpeedAfter(LdibConfig.onewheelTuning(), 1.0D)
                > topSpeedAfter(LdibConfig.scooterTuning(), 1.0D),
            "a hub motor under your feet should pull harder off the line");
        assertTrue(LdibConfig.onewheelTuning().steerSpeedFalloff
                > LdibConfig.scooterTuning().steerSpeedFalloff,
            "a board keeps carving at speed where a scooter's small wheels get twitchy");
    }

    @Test
    void aFlatBatteryCrawlsRatherThanStrandsYou() {
        BikeTuning powered = BikeVariant.ONEWHEEL.tuning();
        BikeTuning dead = BikeVariant.ONEWHEEL.unpoweredTuning();
        assertTrue(dead.maxSpeed > 0.0D, "being stranded is not a fun mechanic — it must still move");
        assertTrue(dead.maxSpeed < powered.maxSpeed / 2.0D,
            "…but a flat board should be a walk home, not a ride");
        // Nothing to kick, unlike a scooter, so it is the slowest dead rideable here.
        assertTrue(dead.maxSpeed < LdibConfig.scooterKickTuning().maxSpeed,
            "a dead board is walked, not scooted");
        // A dead battery costs you the motor, never the brakes or the steering.
        assertEquals(powered.brakeDeceleration, dead.brakeDeceleration, EPS);
        assertEquals(powered.maxSteerRateDegPerSec, dead.maxSteerRateDegPerSec, EPS);
    }

    @Test
    void itIsPoweredAndLit() {
        assertTrue(BikeVariant.ONEWHEEL.hasBattery(), "it is an electric board");
        assertTrue(BikeVariant.ONEWHEEL.rangeBlocks() > 0.0D, "…so it has a range to run down");
        assertTrue(BikeVariant.ONEWHEEL.hasLights(), "real boards carry a light bar at each end");
    }

    // --- Server → client config sync -----------------------------------------------------------

    @Test
    void theNewTuningIsSyncedToClients() {
        // Every value that changes movement RESULTS has to reach a client or it will desync from the
        // board it is steering. The sync array is append-only, so the new knobs must be at the end and
        // applySyncable must round-trip them.
        double[] snapshot = LdibConfig.captureSyncable();
        assertTrue(snapshot.length >= 35, "the one-wheel's eight knobs should be appended to the sync array");

        double original = LdibConfig.onewheelMaxSpeed;
        try {
            double[] fromServer = snapshot.clone();
            fromServer[27] = original + 3.0D;   // pretend the server runs a faster board
            LdibConfig.applySyncable(fromServer);
            assertEquals(original + 3.0D, LdibConfig.onewheelMaxSpeed, EPS,
                "a server's one-wheel top speed must reach the client");
        } finally {
            LdibConfig.applySyncable(snapshot);
        }
        assertEquals(original, LdibConfig.onewheelMaxSpeed, EPS, "restored for other tests");
    }

    @Test
    void anOlderServerSendingAShorterArrayLeavesTheNewValuesAlone() {
        // applySyncable reads defensively so joining a server that predates the one-wheel is not a
        // failed login. Truncate to the pre-one-wheel length and check nothing throws or is zeroed.
        double[] snapshot = LdibConfig.captureSyncable();
        double original = LdibConfig.onewheelMaxSpeed;
        try {
            double[] shortArray = new double[27]; // exactly what the previous release sent
            System.arraycopy(snapshot, 0, shortArray, 0, 27);
            LdibConfig.applySyncable(shortArray);
            assertEquals(original, LdibConfig.onewheelMaxSpeed, EPS,
                "an older server that has never heard of the one-wheel must leave our value be");
        } finally {
            LdibConfig.applySyncable(snapshot);
        }
    }
}
