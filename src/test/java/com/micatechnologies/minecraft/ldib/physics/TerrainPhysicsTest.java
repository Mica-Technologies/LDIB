package com.micatechnologies.minecraft.ldib.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Behavioural tests for the terrain half of the handling model — hills and what the road is made of.
 *
 * <p>These are the whole argument for keeping {@code physics} Minecraft-free: "does a bike slow down
 * going uphill" is a question about arithmetic, and answering it here takes milliseconds on a bare
 * JVM instead of a play-test on a hill someone has to build first.</p>
 */
class TerrainPhysicsTest {

    private static final BikeTuning TUNING = BikeTuning.defaultBicycle();
    private static final double DT = 1.0D / 20.0D; // one Minecraft tick
    private static final double EPS = 1.0e-9D;

    /** Ride {@code seconds} at the given throttle over {@code terrain}, from {@code startSpeed}. */
    private static double rideFor(double seconds, double throttle, Terrain terrain, double startSpeed) {
        BikeState s = new BikeState(startSpeed, 0.0D);
        for (int i = 0; i < (int) Math.round(seconds / DT); i++) {
            s = BikePhysics.step(s, throttle, 0.0D, TUNING, terrain, DT);
        }
        return s.speed;
    }

    private static Terrain slope(double grade) {
        return new Terrain(grade, 1.0D, 1.0D);
    }

    // --- The seam itself -----------------------------------------------------------------------

    @Test
    void theTerrainlessOverloadIsExactlyFlatGround() {
        BikeState start = new BikeState(4.0D, 90.0D);
        BikeState withoutTerrain = BikePhysics.step(start, 1.0D, 0.5D, TUNING, DT);
        BikeState withFlat = BikePhysics.step(start, 1.0D, 0.5D, TUNING, Terrain.FLAT, DT);
        assertEquals(withoutTerrain.speed, withFlat.speed, EPS,
            "adding terrain must not have changed how a bike rides on the level");
        assertEquals(withoutTerrain.headingDegrees, withFlat.headingDegrees, EPS);
    }

    @Test
    void flatTerrainIsRecognisableAsSuch() {
        assertTrue(Terrain.FLAT.isFlatAndPlain());
        assertTrue(new Terrain(0.0D, 1.0D, 1.0D).isFlatAndPlain());
        assertTrue(!slope(0.1D).isFlatAndPlain());
    }

    @Test
    void slopeSineSaturatesRatherThanRunningAway() {
        // sin(atan(g)) -> 1 as g -> infinity, so even an absurd measured grade can never produce more
        // than one gravity of deceleration. This is what makes a mis-measured cliff survivable.
        assertEquals(0.0D, slope(0.0D).slopeSine(), EPS);
        assertTrue(slope(1000.0D).slopeSine() < 1.0D);
        assertTrue(slope(1000.0D).slopeSine() > 0.999D);
        assertEquals(-slope(0.3D).slopeSine(), slope(-0.3D).slopeSine(), EPS, "must be symmetric");
    }

    // --- Hills ---------------------------------------------------------------------------------

    @Test
    void climbingIsSlowerThanTheFlat() {
        double flat = rideFor(20.0D, 1.0D, Terrain.FLAT, 0.0D);
        double uphill = rideFor(20.0D, 1.0D, slope(0.15D), 0.0D);
        assertTrue(uphill < flat,
            "pedalling up a 15% grade should settle slower than the flat (" + uphill + " vs " + flat + ")");
    }

    @Test
    void steeperIsSlowerStill() {
        double gentle = rideFor(20.0D, 1.0D, slope(0.08D), 0.0D);
        double steep = rideFor(20.0D, 1.0D, slope(0.25D), 0.0D);
        assertTrue(steep < gentle, "a steeper climb should be slower, was " + steep + " vs " + gentle);
    }

    @Test
    void descendingAcceleratesACoastingBike() {
        // No throttle at all: the hill alone should be doing the work.
        double rolled = rideFor(3.0D, 0.0D, slope(-0.20D), 1.0D);
        assertTrue(rolled > 1.0D, "a coasting bike should pick up speed downhill, ended at " + rolled);
    }

    @Test
    void aCoastingBikeStillStopsOnTheFlat() {
        double rolled = rideFor(60.0D, 0.0D, Terrain.FLAT, 5.0D);
        assertTrue(rolled < 0.05D, "level ground must still bring a coasting bike to rest, was " + rolled);
    }

    @Test
    void stallingOnAClimbRollsYouBackDown() {
        // Stopped, facing uphill, no input: gravity should start walking the bike backwards.
        double after = rideFor(1.5D, 0.0D, slope(0.25D), 0.0D);
        assertTrue(after < 0.0D, "a bike stopped facing uphill should roll back, ended at " + after);
    }

    @Test
    void gradeIsSignedAgainstTheHeadingNotTheTravel() {
        // The same hill, the same heading, one rolling forwards and one already rolling back. Gravity
        // pulls the same way down the slope in both cases — that is what "signed against the heading"
        // buys, and it is why reversing needs no special case.
        Terrain uphill = slope(0.2D);
        double fromForward = BikePhysics.step(new BikeState(2.0D, 0.0D), 0.0D, 0.0D, TUNING, uphill, DT).speed;
        double fromBackward = BikePhysics.step(new BikeState(-0.5D, 0.0D), 0.0D, 0.0D, TUNING, uphill, DT).speed;
        assertTrue(fromForward < 2.0D, "rolling forward up a hill must lose speed");
        assertTrue(fromBackward < -0.5D, "already rolling back down it must gain backward speed");
    }

    @Test
    void aDescentNeverBreaksTheSpeedCeiling() {
        // The ceiling is the mod's margin against the "moved too quickly" kick, and a long hill is
        // exactly where it would otherwise be spent.
        double bombing = rideFor(60.0D, 1.0D, slope(-0.35D), 0.0D);
        assertTrue(bombing <= TUNING.maxSpeed + EPS,
            "a descent must not exceed maxSpeed, reached " + bombing);
    }

    @Test
    void aBikeFacingUphillNeverRunsAwayBackwards() {
        double runaway = rideFor(120.0D, 0.0D, slope(0.35D), 0.0D);
        assertTrue(runaway >= -TUNING.maxReverseSpeed - EPS,
            "rolling back must stay inside the reverse ceiling, reached " + runaway);
    }

    @Test
    void zeroSlopeGravityDisablesHillsEntirely() {
        BikeTuning noHills = new BikeTuning(7.0D, 3.5D, 9.0D, 0.6D, 0.010D, 90.0D, 5.0D, 1.2D, 2.0D, 0.0D);
        BikeState onAHill = BikePhysics.step(new BikeState(4.0D, 0.0D), 0.0D, 0.0D, noHills, slope(0.3D), DT);
        BikeState onTheFlat = BikePhysics.step(new BikeState(4.0D, 0.0D), 0.0D, 0.0D, noHills, Terrain.FLAT, DT);
        assertEquals(onTheFlat.speed, onAHill.speed, EPS, "slopeGravity 0 must restore flat-world behaviour");
    }

    // --- Surfaces ------------------------------------------------------------------------------

    @Test
    void aDraggySurfaceBleedsSpeedFasterWhenCoasting() {
        double onRoad = rideFor(2.0D, 0.0D, new Terrain(0.0D, 1.0D, 1.0D), 5.0D);
        double onSand = rideFor(2.0D, 0.0D, new Terrain(0.0D, 1.0D, 2.5D), 5.0D);
        assertTrue(onSand < onRoad, "sand should scrub speed faster than tarmac, " + onSand + " vs " + onRoad);
    }

    @Test
    void aFastSurfaceHoldsSpeedBetter() {
        double normal = rideFor(2.0D, 0.0D, new Terrain(0.0D, 1.0D, 1.0D), 5.0D);
        double smooth = rideFor(2.0D, 0.0D, new Terrain(0.0D, 1.0D, 0.7D), 5.0D);
        assertTrue(smooth > normal, "a smoother surface should hold speed better");
    }

    @Test
    void surfaceDragDoesNotChangeTheTopSpeedCeiling() {
        double onSand = rideFor(60.0D, 1.0D, new Terrain(0.0D, 1.0D, 2.5D), 0.0D);
        assertTrue(onSand <= TUNING.maxSpeed + EPS);
        assertTrue(onSand > 0.0D, "you can still make progress across sand, just slowly");
    }

    // --- Grip ----------------------------------------------------------------------------------

    @Test
    void gripScalesTheThingsTractionActuallyLimits() {
        BikeTuning icy = TUNING.withGrip(0.25D);
        assertEquals(TUNING.brakeDeceleration * 0.25D, icy.brakeDeceleration, EPS);
        assertEquals(TUNING.maxSteerRateDegPerSec * 0.25D, icy.maxSteerRateDegPerSec, EPS);
        assertEquals(TUNING.pedalAcceleration * 0.25D, icy.pedalAcceleration, EPS);
    }

    @Test
    void gripLeavesTopSpeedAndRollingResistanceAlone() {
        BikeTuning icy = TUNING.withGrip(0.25D);
        assertEquals(TUNING.maxSpeed, icy.maxSpeed, EPS,
            "ice does not lower how fast a bike can go, only how well you control it");
        assertEquals(TUNING.rollingResistance, icy.rollingResistance, EPS,
            "rolling resistance is Terrain#rollFactor's job, not grip's");
        assertEquals(TUNING.airDrag, icy.airDrag, EPS);
    }

    @Test
    void fullGripIsANoOp() {
        assertSame(TUNING, TUNING.withGrip(1.0D), "grip of exactly 1 should not allocate a new tuning");
    }

    @Test
    void gripIsClampedSoAMistypedConfigCannotBreakTheHandling() {
        assertTrue(TUNING.withGrip(-5.0D).brakeDeceleration > 0.0D, "negative grip must not invert braking");
        assertTrue(TUNING.withGrip(0.0D).brakeDeceleration > 0.0D, "zero grip must still stop, eventually");
        assertTrue(TUNING.withGrip(1000.0D).maxSteerRateDegPerSec
            <= TUNING.maxSteerRateDegPerSec * 2.0D + EPS, "absurd grip must be capped");
    }

    @Test
    void iceLengthensBrakingDistance() {
        double fromSpeed = 6.0D;
        BikeState dry = new BikeState(fromSpeed, 0.0D);
        BikeState icy = new BikeState(fromSpeed, 0.0D);
        BikeTuning icyTuning = TUNING.withGrip(0.25D);
        for (int i = 0; i < 10; i++) {
            dry = BikePhysics.step(dry, -1.0D, 0.0D, TUNING, Terrain.FLAT, DT);
            icy = BikePhysics.step(icy, -1.0D, 0.0D, icyTuning, new Terrain(0.0D, 0.25D, 1.0D), DT);
        }
        assertTrue(icy.speed > dry.speed,
            "braking on ice should still be carrying speed when tarmac has stopped: "
                + icy.speed + " vs " + dry.speed);
    }

    @Test
    void assistAndGripCompose() {
        // A flat-battery e-bike on ice: the assist decides what the motor offers, grip decides how
        // much of it the ground takes. Applying grip last must not be undone by the assist blend.
        BikeTuning powered = new BikeTuning(11.0D, 5.5D, 9.0D, 0.6D, 0.010D, 90.0D, 5.0D, 1.2D, 2.0D);
        BikeTuning dead = BikeTuning.defaultBicycle();
        BikeTuning flatOnIce = powered.withAssist(dead, 0.0D).withGrip(0.25D);
        assertEquals(dead.maxSpeed, flatOnIce.maxSpeed, EPS, "a flat battery still caps the speed");
        assertEquals(dead.pedalAcceleration * 0.25D, flatOnIce.pedalAcceleration, EPS,
            "and the ice still takes a quarter of what is left");
    }

    @Test
    void slopeGravitySurvivesBothDerivations() {
        BikeTuning base = new BikeTuning(7.0D, 3.5D, 9.0D, 0.6D, 0.01D, 90.0D, 5.0D, 1.2D, 2.0D, 6.0D);
        assertEquals(6.0D, base.withGrip(0.5D).slopeGravity, EPS);
        assertEquals(6.0D, base.withAssist(BikeTuning.defaultBicycle(), 0.5D).slopeGravity, EPS);
    }

    // --- Steps: kerbs, slabs and whole blocks --------------------------------------------------
    //
    // A step is not a slope, and the two are charged by different machinery: a grade is a rate inside
    // step(), a lip is an event handled once by afterStepUp(). These pin the properties the mechanic
    // was designed around — see BikeTuning#stepClimbSpeed.

    /** A slab and a whole block, the two heights the feature exists to tell apart. */
    private static final double SLAB = 0.5D;
    private static final double BLOCK = 1.0D;

    /** A bicycle at its cruising speed, which is what the defaults were tuned against. */
    private static final double CRUISE = 7.0D;

    @Test
    void aStepCostsSpeedAndATallerStepCostsMore() {
        double overSlab = BikePhysics.afterStepUp(CRUISE, SLAB, TUNING);
        double overBlock = BikePhysics.afterStepUp(CRUISE, BLOCK, TUNING);
        assertTrue(overSlab < CRUISE, "a slab should cost something: " + overSlab);
        assertTrue(overBlock < overSlab,
            "a whole block should cost more than a slab: " + overBlock + " vs " + overSlab);
    }

    @Test
    void aTallerStepCostsDisproportionatelyMore() {
        // The whole point of charging energy rather than speed. Doubling the height must more than
        // double the speed lost, or a hillside is just a series of equal little taxes.
        double slabLoss = CRUISE - BikePhysics.afterStepUp(CRUISE, SLAB, TUNING);
        double blockLoss = CRUISE - BikePhysics.afterStepUp(CRUISE, BLOCK, TUNING);
        assertTrue(blockLoss > slabLoss * 2.0D,
            "a block should cost more than twice a slab, not exactly twice: "
                + blockLoss + " vs " + slabLoss);
    }

    @Test
    void momentumHelpsYouOverAKerb() {
        // Fixed energy cost, so it is a small tax on a rider with speed and a wall to one crawling.
        double fast = BikePhysics.afterStepUp(CRUISE, BLOCK, TUNING) / CRUISE;
        double slow = BikePhysics.afterStepUp(2.0D, BLOCK, TUNING) / 2.0D;
        assertTrue(fast > slow,
            "carrying speed should preserve a bigger fraction of it: " + fast + " vs " + slow);
    }

    @Test
    void aStepIsNeverADeadStop() {
        // The floor that keeps a survival hillside from being a series of standing starts for the
        // slower variants — a scooter has nothing like a full block's worth of energy to spend.
        double crawling = BikePhysics.afterStepUp(1.0D, BLOCK, TUNING);
        assertTrue(crawling > 0.0D, "a lip taller than your momentum must not stop you dead");
        assertEquals(TUNING.stepClimbRetain, crawling, EPS,
            "and what it leaves you is the retain floor");
    }

    @Test
    void aStepDoesNotTurnYouRound() {
        // Signed like every other speed in the model: a rideable walked backwards up a kerb pays the
        // same price and keeps going backwards.
        double backwards = BikePhysics.afterStepUp(-CRUISE, SLAB, TUNING);
        assertTrue(backwards < 0.0D, "reversing over a kerb must stay reversing: " + backwards);
        assertEquals(BikePhysics.afterStepUp(CRUISE, SLAB, TUNING), -backwards, EPS,
            "and cost exactly what it costs going forwards");
    }

    @Test
    void nothingIsChargedForFlatGroundOrADrop() {
        assertEquals(CRUISE, BikePhysics.afterStepUp(CRUISE, 0.0D, TUNING), EPS);
        assertEquals(CRUISE, BikePhysics.afterStepUp(CRUISE, -BLOCK, TUNING), EPS,
            "dropping off a kerb lifts nothing, so it costs nothing here");
    }

    @Test
    void zeroClimbSpeedRestoresFreeSteps() {
        BikeTuning free = new BikeTuning(7.0D, 3.5D, 9.0D, 0.6D, 0.01D, 90.0D, 5.0D, 1.2D, 2.0D,
            BikeTuning.DEFAULT_SLOPE_GRAVITY, 0.0D, BikeTuning.DEFAULT_STEP_CLIMB_RETAIN);
        assertEquals(CRUISE, BikePhysics.afterStepUp(CRUISE, BLOCK, free), EPS,
            "stepClimbSpeed = 0 must be exactly the behaviour from before kerbs cost anything");
    }

    @Test
    void twoLipsInOneTickCostTheSameAsOneTallOne() {
        // The entity measures rise over a whole tick, so it cannot see two separate steps. Energy
        // adds, which is what makes that the same answer rather than an approximation of it.
        double together = BikePhysics.afterStepUp(9.0D, SLAB + SLAB, TUNING);
        double separately = BikePhysics.afterStepUp(BikePhysics.afterStepUp(9.0D, SLAB, TUNING),
            SLAB, TUNING);
        assertEquals(separately, together, 1.0e-9D);
    }

    @Test
    void theStepCostIsTunedForABicycleAtCruise() {
        // Pins the feel the defaults were chosen for, so retuning is a deliberate act: about an eighth
        // of your speed to a slab, about a third to a whole block.
        double slabLoss = 1.0D - BikePhysics.afterStepUp(CRUISE, SLAB, TUNING) / CRUISE;
        double blockLoss = 1.0D - BikePhysics.afterStepUp(CRUISE, BLOCK, TUNING) / CRUISE;
        assertTrue(slabLoss > 0.05D && slabLoss < 0.20D, "slab should be a dip, not a stop: " + slabLoss);
        assertTrue(blockLoss > 0.25D && blockLoss < 0.45D,
            "a whole block should be plainly felt and still rideable: " + blockLoss);
    }

    @Test
    void stepClimbSurvivesBothDerivations() {
        BikeTuning base = new BikeTuning(7.0D, 3.5D, 9.0D, 0.6D, 0.01D, 90.0D, 5.0D, 1.2D, 2.0D,
            6.0D, 4.0D, 0.3D);
        assertEquals(4.0D, base.withGrip(0.5D).stepClimbSpeed, EPS);
        assertEquals(0.3D, base.withGrip(0.5D).stepClimbRetain, EPS);
        assertEquals(4.0D, base.withAssist(BikeTuning.defaultBicycle(), 0.5D).stepClimbSpeed, EPS,
            "a flat battery does not make you worse at kerbs");
        assertEquals(0.3D, base.withAssist(BikeTuning.defaultBicycle(), 0.5D).stepClimbRetain, EPS);
    }
}
