package com.micatechnologies.minecraft.ldib.api;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.entity.BikeVariant;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * A bike-share price list: a flat unlock fee plus a per-minute rate that varies by variant (pedal bike,
 * e-bike, and scooter are priced apart). Amounts are in {@link #currency}: SUM's money, or whole items
 * of the configured currency item when SUM is not in use (see {@link BikeShareBilling}).
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

    /**
     * Rebalancing credit per qualifying end of a ride: taking a bike from a {@link #isBusy busy}
     * station, and returning one to a {@link #isStarved starved} one. 0 turns rebalancing off.
     */
    public final double rebalanceCreditPerEnd;
    /** Station fill (bikes ÷ docks) at or above which taking a bike there earns credit. */
    public final double busyFill;
    /** Station fill at or below which returning a bike there earns credit. */
    public final double starvedFill;

    /**
     * What amounts are counted in: a money symbol ({@code "$"}) when {@link #wholeItems} is false, an
     * item's singular name ({@code "emerald"}) when it is true, or empty for none.
     */
    public final String currency;
    /**
     * Whether amounts are counted in whole items. Prices are then charged rounded <b>up</b> to a whole
     * item, and credit rounded to the nearest one (still never more than was charged).
     */
    public final boolean wholeItems;

    /** A price list with no rebalancing credit. */
    public ShareTariff(double unlockFee, double bikePerMinute, double ebikePerMinute, double scooterPerMinute) {
        this(unlockFee, bikePerMinute, ebikePerMinute, scooterPerMinute, 0.0D, 1.0D, 0.0D);
    }

    public ShareTariff(double unlockFee, double bikePerMinute, double ebikePerMinute, double scooterPerMinute,
                       double rebalanceCreditPerEnd, double busyFill, double starvedFill) {
        this(unlockFee, bikePerMinute, ebikePerMinute, scooterPerMinute, rebalanceCreditPerEnd, busyFill,
            starvedFill, "", false);
    }

    public ShareTariff(double unlockFee, double bikePerMinute, double ebikePerMinute, double scooterPerMinute,
                       double rebalanceCreditPerEnd, double busyFill, double starvedFill,
                       String currency, boolean wholeItems) {
        this.unlockFee = unlockFee;
        this.bikePerMinute = bikePerMinute;
        this.ebikePerMinute = ebikePerMinute;
        this.scooterPerMinute = scooterPerMinute;
        this.rebalanceCreditPerEnd = rebalanceCreditPerEnd;
        this.busyFill = busyFill;
        this.starvedFill = starvedFill;
        this.currency = currency == null ? "" : currency;
        this.wholeItems = wholeItems;
    }

    /** This price list counted in {@code currency}; see {@link #currency} and {@link #wholeItems}. */
    public ShareTariff withCurrency(String currency, boolean wholeItems) {
        return new ShareTariff(unlockFee, bikePerMinute, ebikePerMinute, scooterPerMinute,
            rebalanceCreditPerEnd, busyFill, starvedFill, currency, wholeItems);
    }

    // --- Currency -----------------------------------------------------------------------------

    /** Whole items a price of {@code amount} costs: rounded up, so a fractional rate is never free. */
    public static int itemsOwed(double amount) {
        if (amount <= 0.0D) {
            return 0;
        }
        // The epsilon keeps 1.0000000001 (accumulated rate arithmetic) from becoming 2.
        return (int) Math.ceil(amount - 1.0e-9D);
    }

    /** {@code amount} for display: "3 emeralds", "1 emerald", "$2.50", or "2.50" with no currency. */
    public String format(double amount) {
        if (wholeItems) {
            long n = Math.round(amount);
            return n + " " + (n == 1 ? currency : plural(currency));
        }
        return currency + String.format(Locale.ROOT, "%.2f", amount);
    }

    /** A price for display: as {@link #format}, but rounded up to whole items the way it is charged. */
    public String formatPrice(double amount) {
        return wholeItems ? format(itemsOwed(amount)) : format(amount);
    }

    /**
     * A per-minute rate for display. Money reads "$0.20/min". Items cannot be split, so a rate under
     * one item a minute reads as how long one item lasts — "1 emerald / 5 min" — rather than rounding
     * up to a misleading "1 emerald/min"; the fare itself is still charged rounded up at the end.
     */
    public String formatRate(double perMinute) {
        if (!wholeItems || perMinute <= 0.0D || perMinute >= 1.0D) {
            return format(perMinute) + "/min";
        }
        long minutesPerItem = Math.max(1L, Math.round(1.0D / perMinute));
        return format(1) + " / " + minutesPerItem + " min";
    }

    /** The currency's plural name ("emeralds"); the symbol itself for money. */
    public String pluralCurrency() {
        return wholeItems ? plural(currency) : currency;
    }

    private static String plural(String noun) {
        return noun.isEmpty() || noun.endsWith("s") ? noun : noun + "s";
    }

    /** The current price list from server config (whether it's actually billed is up to the billing seam). */
    public static ShareTariff fromConfig() {
        return new ShareTariff(LdibConfig.shareUnlockFee, LdibConfig.shareRateBikePerMinute,
            LdibConfig.shareRateEbikePerMinute, LdibConfig.shareRateScooterPerMinute,
            LdibConfig.shareRebalanceCredit, LdibConfig.shareBusyFill, LdibConfig.shareStarvedFill);
    }

    // --- Rebalancing --------------------------------------------------------------------------
    //
    // Docked fleets drift: everyone rides downhill to the shops, and by evening one station is full
    // and the next is bare. Real systems (Citi Bike's "Angels") pay riders to undo that, and so does
    // this — a credit for taking a bike from a busy station and another for returning one to a starved
    // station, each end counted on its own.

    /**
     * How full a station is, {@code 0} (no bikes) to {@code 1} (every dock taken), or {@code -1} when
     * there is no station to measure (a standalone dock, or one with no docks at all) — which never
     * qualifies for anything.
     */
    public static double fill(int bikes, int freeDocks) {
        int docks = bikes + freeDocks;
        return docks <= 0 ? -1.0D : (double) bikes / docks;
    }

    /** Whether a station this full is one riders are paid to take bikes FROM. */
    public boolean isBusy(double fill) {
        return rebalanceCreditPerEnd > 0.0D && fill >= 0.0D && fill >= busyFill;
    }

    /** Whether a station this full is one riders are paid to return bikes TO. */
    public boolean isStarved(double fill) {
        return rebalanceCreditPerEnd > 0.0D && fill >= 0.0D && fill <= starvedFill;
    }

    /**
     * The credit owed for a ride that took its bike from a station at {@code pickupFill} and returned
     * it to one at {@code dropoffFill} (both measured without the ridden bike's own effect), for which
     * the rider was {@code charged}.
     *
     * <p><b>Capped at the fare.</b> A rebalancing ride can be free, never profitable. Without the cap
     * two one-dock stations are a money machine: the bike between them is always leaving a full
     * station for an empty one, in both directions, forever. With it, nothing anyone does with the
     * fleet can make it pay out more than was paid in.</p>
     */
    public double rebalanceCredit(double pickupFill, double dropoffFill, double charged) {
        int ends = (isBusy(pickupFill) ? 1 : 0) + (isStarved(dropoffFill) ? 1 : 0);
        double credit = Math.max(0.0D, Math.min(ends * rebalanceCreditPerEnd, charged));
        // In whole items, to the nearest one — a half-item credit per end still pays — and never more
        // than the whole items that were charged.
        return wholeItems ? Math.min(Math.round(credit), Math.floor(charged)) : credit;
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
        buf.writeDouble(rebalanceCreditPerEnd);
        buf.writeDouble(busyFill);
        buf.writeDouble(starvedFill);
        byte[] name = currency.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(name.length, MAX_CURRENCY_BYTES);
        buf.writeByte(length);
        buf.writeBytes(name, 0, length);
        buf.writeBoolean(wholeItems);
    }

    public static ShareTariff fromBytes(ByteBuf buf) {
        double unlock = buf.readDouble();
        double bike = buf.readDouble();
        double ebike = buf.readDouble();
        double scooter = buf.readDouble();
        double credit = buf.readDouble();
        double busy = buf.readDouble();
        double starved = buf.readDouble();
        byte[] name = new byte[buf.readUnsignedByte()];
        buf.readBytes(name);
        boolean wholeItems = buf.readBoolean();
        return new ShareTariff(unlock, bike, ebike, scooter, credit, busy, starved,
            new String(name, StandardCharsets.UTF_8), wholeItems);
    }

    /** Longest currency name sent, in UTF-8 bytes; it is a length byte on the wire. */
    private static final int MAX_CURRENCY_BYTES = 255;
}
