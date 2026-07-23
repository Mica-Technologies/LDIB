package com.micatechnologies.minecraft.ldib.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Behavioural tests for the pure-Java bike handling model. These run on a bare JVM in
 * milliseconds — no Minecraft instance — which is the whole point of keeping
 * {@link BikePhysics} Minecraft-free. They pin the qualities the ride is supposed to have
 * (accelerates, tops out, coasts to a stop, brakes hard, does not turn while parked) so that a
 * future tuning change is a deliberate edit to an assertion, not a silent regression.
 */
class BikePhysicsTest {

    private static final BikeTuning TUNING = BikeTuning.defaultBicycle();
    private static final double DT = 1.0D / 20.0D; // one Minecraft tick

    /** Run full throttle for {@code seconds} from rest and return the resulting state. */
    private static BikeState pedalFromRest(double seconds) {
        BikeState s = BikeState.stationary(0.0D);
        int steps = (int) Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            s = BikePhysics.step(s, 1.0D, 0.0D, TUNING, DT);
        }
        return s;
    }

    @Test
    void pedallingAccelerates() {
        BikeState after1s = pedalFromRest(1.0D);
        assertTrue(after1s.speed > 1.0D, "should be moving briskly after a second of pedalling");
    }

    @Test
    void topsOutBelowConfiguredCeilingAndNeverExceedsIt() {
        BikeState after = pedalFromRest(30.0D);
        assertTrue(after.speed <= TUNING.maxSpeed + 1.0e-9D, "must never exceed the hard ceiling");
        // With drag, the steady state should settle near the analytic powered equilibrium.
        double equilibrium = BikePhysics.poweredEquilibriumSpeed(TUNING);
        assertEquals(equilibrium, after.speed, 0.25D, "steady speed should approach powered equilibrium");
    }

    @Test
    void coastingSlowsToAStop() {
        BikeState s = pedalFromRest(10.0D);
        double moving = s.speed;
        assertTrue(moving > 0.5D, "precondition: bike is moving before we coast");
        for (int i = 0; i < 20 * 60; i++) { // coast up to a minute
            s = BikePhysics.step(s, 0.0D, 0.0D, TUNING, DT);
        }
        assertTrue(s.speed < 0.05D, "a coasting bike should roll to (near) rest, was " + s.speed);
        assertTrue(s.speed < moving, "coasting must not speed up");
    }

    @Test
    void brakingStopsFasterThanCoasting() {
        BikeState start = pedalFromRest(10.0D);

        BikeState braking = start;
        int brakeTicks = 0;
        while (braking.speed > 0.1D && brakeTicks < 20 * 30) {
            braking = BikePhysics.step(braking, -1.0D, 0.0D, TUNING, DT);
            brakeTicks++;
        }

        BikeState coasting = start;
        int coastTicks = 0;
        while (coasting.speed > 0.1D && coastTicks < 20 * 120) {
            coasting = BikePhysics.step(coasting, 0.0D, 0.0D, TUNING, DT);
            coastTicks++;
        }

        assertTrue(brakeTicks < coastTicks,
            "braking (" + brakeTicks + " ticks) should stop the bike sooner than coasting ("
                + coastTicks + " ticks)");
    }

    @Test
    void brakingNeverReversesAParkedBike() {
        BikeState s = BikeState.stationary(0.0D);
        for (int i = 0; i < 40; i++) {
            s = BikePhysics.step(s, -1.0D, 0.0D, TUNING, DT);
        }
        assertEquals(0.0D, s.speed, 1.0e-9D, "holding the brake at rest must keep speed at zero");
    }

    @Test
    void steeringDoesNothingWhileParked() {
        BikeState s = BikeState.stationary(30.0D);
        for (int i = 0; i < 40; i++) {
            s = BikePhysics.step(s, 0.0D, 1.0D, TUNING, DT);
        }
        assertEquals(30.0D, s.headingDegrees, 1.0e-9D, "a stationary bike should not turn on the spot");
    }

    @Test
    void steeringTurnsAMovingBikeAndStaysWrapped() {
        BikeState s = pedalFromRest(3.0D).withHeading(0.0D);
        for (int i = 0; i < 20; i++) {
            s = BikePhysics.step(s, 1.0D, 1.0D, TUNING, DT);
        }
        assertTrue(Math.abs(s.headingDegrees) > 1.0D, "a moving bike should change heading when steered");
        assertTrue(s.headingDegrees >= -180.0D && s.headingDegrees < 180.0D,
            "heading must stay wrapped into [-180, 180), was " + s.headingDegrees);
    }

    @Test
    void fasterTuningReachesHigherSpeed() {
        // Mirrors the bicycle-vs-e-bike relationship (LdibConfig.eBikeTuning): a higher assisted top
        // speed and brisker acceleration, everything else shared. Pinned here in pure-Java terms so
        // the "variants are data" promise is regression-tested without a game instance.
        BikeTuning ebike = new BikeTuning(11.0D, 5.5D, 9.0D, 0.6D, 0.010D, 90.0D, 5.0D);

        assertTrue(BikePhysics.poweredEquilibriumSpeed(ebike)
                > BikePhysics.poweredEquilibriumSpeed(TUNING),
            "e-bike equilibrium speed should exceed the bicycle's");

        BikeState bike = BikeState.stationary(0.0D);
        BikeState eb = BikeState.stationary(0.0D);
        for (int i = 0; i < 20 * 8; i++) { // 8 s of full throttle
            bike = BikePhysics.step(bike, 1.0D, 0.0D, TUNING, DT);
            eb = BikePhysics.step(eb, 1.0D, 0.0D, ebike, DT);
        }
        assertTrue(eb.speed > bike.speed, "e-bike should be going faster than the bicycle after 8s");
    }

    @Test
    void wrapDegreesFoldsIntoRange() {
        assertEquals(0.0D, BikePhysics.wrapDegrees(360.0D), 1.0e-9D);
        assertEquals(-90.0D, BikePhysics.wrapDegrees(270.0D), 1.0e-9D);
        assertEquals(10.0D, BikePhysics.wrapDegrees(370.0D), 1.0e-9D);
        assertEquals(-170.0D, BikePhysics.wrapDegrees(190.0D), 1.0e-9D);
    }
}
