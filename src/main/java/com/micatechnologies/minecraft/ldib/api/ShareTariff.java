package com.micatechnologies.minecraft.ldib.api;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.entity.BikeVariant;
import io.netty.buffer.ByteBuf;

/**
 * A bike-share price list: a flat unlock fee plus a per-minute rate that varies by variant (pedal bike,
 * e-bike, and scooter are priced apart). Amounts are in the installed economy's currency.
 *
 * <p>Rates live in server config; this immutable value object is built server-side ({@link #fromConfig()})
 * and shipped to the kiosk screen over the wire ({@link #toBytes}/{@link #fromBytes}) so the client can
 * show real prices without re-reading server config or re-deriving whether an economy is installed.</p>
 */
public final class ShareTariff {

    /** No charge — the tariff in effect when billing is off or every fee/rate is zero. */
    public static final ShareTariff FREE = new ShareTariff(0.0D, 0.0D, 0.0D, 0.0D);

    public final double unlockFee;
    private final double bikePerMinute;
    private final double ebikePerMinute;
    private final double scooterPerMinute;

    public ShareTariff(double unlockFee, double bikePerMinute, double ebikePerMinute, double scooterPerMinute) {
        this.unlockFee = unlockFee;
        this.bikePerMinute = bikePerMinute;
        this.ebikePerMinute = ebikePerMinute;
        this.scooterPerMinute = scooterPerMinute;
    }

    /** The current price list from server config (whether it's actually billed is up to the billing seam). */
    public static ShareTariff fromConfig() {
        return new ShareTariff(LdibConfig.shareUnlockFee, LdibConfig.shareRateBikePerMinute,
            LdibConfig.shareRateEbikePerMinute, LdibConfig.shareRateScooterPerMinute);
    }

    /** The per-minute rate for {@code variant}. Both scooter speeds share one rate. */
    public double ratePerMinute(BikeVariant variant) {
        if (variant == BikeVariant.EBIKE) {
            return ebikePerMinute;
        }
        if (variant == BikeVariant.SCOOTER || variant == BikeVariant.SCOOTER_FAST) {
            return scooterPerMinute;
        }
        return bikePerMinute; // BIKE (and any future pedal variant)
    }

    /** Total charge for a completed rental of {@code minutes} on {@code variant}: unlock fee + time. */
    public double totalFor(BikeVariant variant, int minutes) {
        return unlockFee + Math.max(0, minutes) * ratePerMinute(variant);
    }

    /** Whether anything is actually billed (any fee or per-minute rate is positive). */
    public boolean isPaid() {
        return unlockFee > 0.0D || bikePerMinute > 0.0D || ebikePerMinute > 0.0D || scooterPerMinute > 0.0D;
    }

    public double bikePerMinute() {
        return bikePerMinute;
    }

    public double ebikePerMinute() {
        return ebikePerMinute;
    }

    public double scooterPerMinute() {
        return scooterPerMinute;
    }

    public void toBytes(ByteBuf buf) {
        buf.writeDouble(unlockFee);
        buf.writeDouble(bikePerMinute);
        buf.writeDouble(ebikePerMinute);
        buf.writeDouble(scooterPerMinute);
    }

    public static ShareTariff fromBytes(ByteBuf buf) {
        return new ShareTariff(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
