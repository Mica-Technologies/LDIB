package com.micatechnologies.minecraft.ldib.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.ldib.entity.BikeVariant;
import org.junit.jupiter.api.Test;

/**
 * Pure-Java tests for the bike-share price list. Like the physics tests these need no Minecraft
 * instance — a {@link ShareTariff} is built directly from numbers (not {@link ShareTariff#fromConfig},
 * which would read {@link com.micatechnologies.minecraft.ldib.LdibConfig}) — so the pricing rules
 * (unlock fee + per-minute-by-variant, both scooter speeds shared, "is anything billed") are pinned
 * against a silent regression. The billing that actually moves currency lives behind the optional SUM
 * reflection wrapper and isn't exercised here; this locks down the math it bills with.
 */
class ShareTariffTest {

    /** A representative priced tariff: every variant a distinct, non-zero rate so selection is testable. */
    private static final ShareTariff PRICED = new ShareTariff(1.0D, 0.20D, 1.0D / 3.0D, 0.25D);

    private static final double EPS = 1.0e-9D;

    @Test
    void totalIsUnlockFeePlusMinutesAtTheVariantRate() {
        // Bike at $0.20/min for 5 minutes, plus the $1 unlock fee.
        assertEquals(1.0D + 5 * 0.20D, PRICED.totalFor(BikeVariant.BICYCLE, 5), EPS);
    }

    @Test
    void ratePerMinuteSelectsByVariant() {
        assertEquals(0.20D, PRICED.ratePerMinute(BikeVariant.BICYCLE), EPS);
        assertEquals(1.0D / 3.0D, PRICED.ratePerMinute(BikeVariant.EBIKE), EPS);
        assertEquals(0.25D, PRICED.ratePerMinute(BikeVariant.SCOOTER), EPS);
    }

    @Test
    void bothScooterSpeedsShareOneRate() {
        assertEquals(PRICED.ratePerMinute(BikeVariant.SCOOTER),
            PRICED.ratePerMinute(BikeVariant.SCOOTER_FAST), EPS,
            "the standard and performance scooter are billed at the same per-minute rate");
    }

    @Test
    void aFasterVariantRateCostsMoreForTheSameRide() {
        // The e-bike's higher per-minute rate must make an identical-length ride cost strictly more.
        assertTrue(PRICED.totalFor(BikeVariant.EBIKE, 10) > PRICED.totalFor(BikeVariant.BICYCLE, 10),
            "an e-bike ride should cost more than the same-length bike ride at these rates");
    }

    @Test
    void zeroMinutesChargesOnlyTheUnlockFee() {
        assertEquals(PRICED.unlockFee, PRICED.totalFor(BikeVariant.BICYCLE, 0), EPS);
    }

    @Test
    void negativeMinutesNeverDiscountBelowTheUnlockFee() {
        // totalFor clamps minutes at 0, so a clock glitch can't produce a credit.
        assertEquals(PRICED.unlockFee, PRICED.totalFor(BikeVariant.BICYCLE, -3), EPS);
    }

    @Test
    void isPaidWhenAnyFeeOrRateIsPositive() {
        assertTrue(PRICED.isPaid(), "a tariff with a positive unlock fee and rates is billed");
        assertTrue(new ShareTariff(0.0D, 0.20D, 0.0D, 0.0D).isPaid(), "any positive rate makes it billed");
        assertTrue(new ShareTariff(1.0D, 0.0D, 0.0D, 0.0D).isPaid(), "a lone unlock fee makes it billed");
    }

    @Test
    void freeTariffChargesNothingAndIsNotPaid() {
        assertFalse(ShareTariff.FREE.isPaid(), "the FREE tariff bills nothing");
        assertEquals(0.0D, ShareTariff.FREE.totalFor(BikeVariant.EBIKE, 42), EPS,
            "the FREE tariff charges nothing no matter the ride");
        assertFalse(new ShareTariff(0.0D, 0.0D, 0.0D, 0.0D).isPaid(), "an all-zero tariff is not billed");
    }

    // --- Rebalancing ---------------------------------------------------------------------------

    /** Priced, with 0.5 credit per end, busy at 75% full and starved at 25%. */
    private static final ShareTariff REBALANCING =
        new ShareTariff(1.0D, 0.20D, 1.0D / 3.0D, 0.25D, 0.5D, 0.75D, 0.25D);

    @Test
    void fillIsBikesOverDocksAndUnknownWithNoDocks() {
        assertEquals(0.75D, ShareTariff.fill(3, 1), EPS);
        assertEquals(-1.0D, ShareTariff.fill(0, 0), EPS, "a station with no docks cannot be measured");
    }

    @Test
    void eachRebalancingEndEarnsCreditOnItsOwn() {
        assertEquals(0.5D, REBALANCING.rebalanceCredit(1.0D, 0.5D, 5.0D), EPS, "pickup from a full station");
        assertEquals(0.5D, REBALANCING.rebalanceCredit(0.5D, 0.0D, 5.0D), EPS, "drop-off at an empty station");
        assertEquals(1.0D, REBALANCING.rebalanceCredit(0.8D, 0.2D, 5.0D), EPS, "both ends");
        assertEquals(0.0D, REBALANCING.rebalanceCredit(0.5D, 0.5D, 5.0D), EPS, "neither end");
    }

    @Test
    void creditNeverExceedsTheFare() {
        // Two one-dock stations are always full-to-empty in both directions; the cap is what stops that
        // from paying out more than the rides cost.
        assertEquals(0.8D, REBALANCING.rebalanceCredit(1.0D, 0.0D, 0.8D), EPS);
        assertEquals(0.0D, REBALANCING.rebalanceCredit(1.0D, 0.0D, 0.0D), EPS, "a free ride earns nothing");
    }

    @Test
    void unmeasuredStationsAndTariffsWithoutRebalancingEarnNothing() {
        assertEquals(0.0D, REBALANCING.rebalanceCredit(-1.0D, -1.0D, 5.0D), EPS, "standalone docks");
        assertEquals(0.0D, PRICED.rebalanceCredit(1.0D, 0.0D, 5.0D), EPS, "rebalancing switched off");
        assertFalse(PRICED.isBusy(1.0D));
        assertFalse(ShareTariff.FREE.isStarved(0.0D));
        assertTrue(REBALANCING.isStarved(0.0D));
    }
}
