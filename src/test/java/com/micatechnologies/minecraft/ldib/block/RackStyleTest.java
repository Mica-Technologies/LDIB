package com.micatechnologies.minecraft.ldib.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Invariants for the bike-rack styles. {@link RackStyle} happens to be pure data with no Minecraft
 * types, so it can be pinned on a bare JVM alongside the physics tests — worth doing, because rack
 * geometry has drifted silently twice: the multi-block models were authored 1×1 while the collision
 * boxes spanned three blocks, and the grid's bike slots were spaced to a bar pattern that no longer
 * existed. Neither is visible to the compiler or the server smoke test, and only one of them is
 * obvious in-game.
 *
 * <p>These do not check that a rack looks <i>good</i> — that needs an eyeball. They check the things
 * that are provably wrong when they break: a bike parked outside the blocks it belongs to, a capacity
 * that disagrees with the slots, or a style quietly losing its parking spots.</p>
 */
class RackStyleTest {

    @Test
    void everyStyleHoldsAtLeastOneBike() {
        for (RackStyle style : RackStyle.values()) {
            assertTrue(style.capacity() >= 1, style + " should hold at least one bike");
        }
    }

    @Test
    void capacityAlwaysMatchesTheSlotCount() {
        for (RackStyle style : RackStyle.values()) {
            assertEquals(style.slots().length, style.capacity(),
                style + ": capacity() must be derived from the slots, never hard-coded alongside them");
        }
    }

    @Test
    void everySlotSitsWithinTheBlocksTheRackActuallyOccupies() {
        // A rack of length L covers local X in [-0.5, L-0.5]: the master block is centred on 0 and each
        // extension adds one block along the facing. A slot outside that renders a bike parked in mid-air
        // past the end of the rack, which is exactly the sort of thing that survives review by looking
        // plausible in a diff.
        for (RackStyle style : RackStyle.values()) {
            double min = -0.5D;
            double max = style.length() - 0.5D;
            for (RackStyle.Slot slot : style.slots()) {
                assertTrue(slot.along >= min && slot.along <= max,
                    style + " slot at along=" + slot.along + " falls outside the rack's footprint ["
                        + min + ", " + max + "] for length " + style.length());
            }
        }
    }

    /** Minimum spacing for two bikes pointing the same way — roughly a bike's width. */
    private static final double MIN_GAP_PARALLEL = 0.5D;

    /**
     * Minimum spacing for two bikes pointing opposite ways. They interleave — one's handlebars sit
     * beside the other's saddle rather than beside its handlebars — so they tuck closer without
     * clipping. This is what lets the hoop's pair sit tight against the ring.
     */
    private static final double MIN_GAP_OPPOSED = 0.3D;

    @Test
    void slotsDoNotOverlapEachOther() {
        // Bikes render near full size, so slots too close together clip. How close is too close depends
        // on their relative orientation, which is why this is not a single flat threshold.
        for (RackStyle style : RackStyle.values()) {
            RackStyle.Slot[] slots = style.slots();
            for (int i = 0; i < slots.length; i++) {
                for (int j = i + 1; j < slots.length; j++) {
                    double gap = Math.abs(slots[i].along - slots[j].along)
                        + Math.abs(slots[i].across - slots[j].across);
                    double turn = Math.abs(wrap180(slots[i].yaw - slots[j].yaw));
                    boolean opposed = turn > 135.0D;   // near enough anti-parallel to interleave
                    double min = opposed ? MIN_GAP_OPPOSED : MIN_GAP_PARALLEL;
                    assertTrue(gap >= min,
                        style + " slots " + i + " and " + j + " are only " + gap + " blocks apart ("
                            + (opposed ? "opposed" : "parallel") + ", needs " + min
                            + "); near-full-size bikes will clip");
                }
            }
        }
    }

    /** Fold a yaw difference into [-180, 180] so 270 vs 90 reads as opposed, not as 180 apart twice. */
    private static double wrap180(double degrees) {
        double d = degrees % 360.0D;
        if (d > 180.0D) {
            d -= 360.0D;
        }
        if (d < -180.0D) {
            d += 360.0D;
        }
        return d;
    }

    @Test
    void theWideStylesSpanThreeBlocksAndHoldFive() {
        // Owner's call: wave, grid and (from 2026-07-25) classic are the wide racks. If any drops back
        // to length 1 its block models — bike_rack_<style>_0/_1/_2, from tools/gen_rack_models.py — are
        // wrong too, and that mismatch is invisible to the compiler.
        for (RackStyle style : new RackStyle[] {RackStyle.WAVE, RackStyle.GRID, RackStyle.CLASSIC}) {
            assertEquals(3, style.length(), style + " is a wide rack and should span three blocks");
            assertEquals(5, style.capacity(), style + " should hold five bikes");
        }
    }

    @Test
    void theSingleBlockStylesStayOneBlock() {
        // Owner, 2026-07-25: a ring rack and a post are deliberately smaller than the wide styles.
        for (RackStyle style : new RackStyle[] {RackStyle.HOOP, RackStyle.POST}) {
            assertEquals(1, style.length(), style + " is meant to stay a single block");
        }
    }

    @Test
    void hoopBikesLieAlongTheHoopFacingOppositeWaysAndTuckedInClose() {
        // Owner, 2026-07-25: bikes on a ring rack lie along the hoop (rotated 90), face opposite ways,
        // and sit tight against it — "the real way people mount bikes to them".
        //
        // These three facts are one decision, not three, which is why they are pinned together. Turned
        // bikes run along local X, so they have to be separated ACROSS or they would park nose-to-tail
        // inside each other; and it is being anti-parallel that lets them interleave close enough to
        // touch the hoop. Change any one of these and re-check the others.
        RackStyle.Slot[] slots = RackStyle.HOOP.slots();
        assertEquals(2, slots.length, "a hoop takes a bike either side");
        for (RackStyle.Slot slot : slots) {
            assertEquals(0.0F, slot.along, 1.0e-6F,
                "a rotated hoop bike should sit on the hoop's centre line");
            assertTrue(Math.abs(slot.across) > 0.0F && Math.abs(slot.across) <= 0.25F,
                "hoop bikes should be tucked in close to the ring, was across=" + slot.across);
        }
        assertTrue(slots[0].across < 0.0F && slots[1].across > 0.0F,
            "the hoop's two bikes should sit on opposite sides of the hoop");
        assertTrue(Math.abs(wrap180(slots[0].yaw - slots[1].yaw)) > 135.0D,
            "the hoop's two bikes should face opposite ways, as they do on a real ring rack");
        for (RackStyle.Slot slot : slots) {
            double offAxis = Math.abs(wrap180(slot.yaw - 90.0D));
            assertTrue(offAxis < 1.0e-6D || Math.abs(offAxis - 180.0D) < 1.0e-6D,
                "a hoop bike should lie along the hoop (yaw 90 or 270), was " + slot.yaw);
        }
    }

    @Test
    void aPostTakesABikeOnEitherSide() {
        // Owner, 2026-07-25: "a bike attached on either side, so total of two." It held one until then.
        assertEquals(2, RackStyle.POST.capacity(), "a post should take a bike on either side");
        RackStyle.Slot[] slots = RackStyle.POST.slots();
        assertTrue(slots[0].along < 0.0D && slots[1].along > 0.0D,
            "the post's two bikes should sit on opposite sides of the pole, not both on one side");
    }
}
