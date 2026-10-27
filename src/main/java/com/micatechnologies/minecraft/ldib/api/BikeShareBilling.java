package com.micatechnologies.minecraft.ldib.api;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.entity.BikeVariant;
import com.micatechnologies.minecraft.ldib.integration.ItemCurrency;
import com.micatechnologies.minecraft.ldib.integration.SumEconomy;
import java.util.Locale;
import java.util.OptionalDouble;
import net.minecraft.entity.player.EntityPlayer;

/**
 * The seam for charging for a bike-share rental, and paying rebalancing credit back. Three ways to
 * pay, chosen by {@code bikeshare.currency}:
 *
 * <ul>
 *   <li>{@link #SUM} — money, through SUM's public economy API ({@link SumEconomy}), when SUM is
 *       installed and its operator allows LDIB.</li>
 *   <li>{@link #ITEMS} — a physical item, emeralds by default ({@code bikeshare.currencyItem}), taken
 *       from and given to the rider's inventory. This is what keeps the bike-share a working, paid
 *       system on a server without SUM.</li>
 *   <li>{@link #FREE} — nothing is charged.</li>
 * </ul>
 *
 * <p>The default, {@code auto}, uses SUM when it is usable and items otherwise, so a server that adds
 * SUM later moves to money on its own, and one whose SUM does not (yet) allow LDIB keeps billing in
 * items rather than going free. Rentals are post-paid: {@link #canCheckOut} only gates starting one on
 * affording the unlock fee, and {@link #charge} bills unlock fee plus minutes on return.</p>
 */
public interface BikeShareBilling {

    /** Whether {@code player} may start a rental now (can cover the unlock fee). */
    boolean canCheckOut(EntityPlayer player);

    /**
     * Bill {@code player} for a completed rental of {@code minutes} on {@code variant}, and return what
     * was actually taken, in {@link #tariff()}'s currency. A rider short of the full fare pays what they
     * have rather than nothing.
     */
    double charge(EntityPlayer player, BikeVariant variant, int minutes);

    /** Pay {@code amount} back to {@code player}; return what was actually paid. */
    double credit(EntityPlayer player, double amount, String reason);

    /** The price list this billing charges by, with its currency. */
    ShareTariff tariff();

    /** The billing in effect right now; see the class description. */
    static BikeShareBilling active() {
        if (!ShareTariff.fromConfig().isPaid() || !LdibConfig.shareUseEconomy) {
            return FREE; // useEconomy=false is the older off switch, and still means free
        }
        String mode = LdibConfig.shareCurrency.toLowerCase(Locale.ROOT);
        if ("free".equals(mode)) {
            return FREE;
        }
        if ("items".equals(mode)) {
            return ITEMS;
        }
        if ("sum".equals(mode)) {
            return SumEconomy.isAvailable() ? SUM : FREE;
        }
        return SumEconomy.isAvailable() ? SUM : ITEMS; // auto, and anything unrecognised
    }

    /** The price list a rider would be billed against right now, currency included. */
    static ShareTariff activeTariff() {
        return active().tariff();
    }

    /** The display name of what amounts are counted in: SUM's symbol, an item's plural name, or "". */
    static String currencyName() {
        ShareTariff tariff = activeTariff();
        return tariff.wholeItems ? tariff.pluralCurrency() : tariff.currency;
    }

    /** Rides are free: anyone may check out and nothing is ever charged or paid. */
    BikeShareBilling FREE = new BikeShareBilling() {
        @Override
        public boolean canCheckOut(EntityPlayer player) {
            return true;
        }

        @Override
        public double charge(EntityPlayer player, BikeVariant variant, int minutes) {
            return 0.0D;
        }

        @Override
        public double credit(EntityPlayer player, double amount, String reason) {
            return 0.0D;
        }

        @Override
        public ShareTariff tariff() {
            return ShareTariff.FREE;
        }
    };

    /** Money, through SUM's economy. */
    BikeShareBilling SUM = new BikeShareBilling() {
        @Override
        public boolean canCheckOut(EntityPlayer player) {
            OptionalDouble balance = SumEconomy.balance(player);
            // A balance SUM will not show is no reason to refuse; the charge on return still has its say.
            return !balance.isPresent() || balance.getAsDouble() >= tariff().unlockFee;
        }

        @Override
        public double charge(EntityPlayer player, BikeVariant variant, int minutes) {
            double cost = tariff().totalFor(variant, minutes);
            if (cost <= 0.0D) {
                return 0.0D;
            }
            String reason = String.format(Locale.ROOT, "LDIB bike-share: %d min on %s", minutes, variant.key());
            // Post-paid, so a long ride can outrun the balance that covered the unlock fee. If SUM refuses
            // the whole fare, take what is left, and report only what actually moved.
            if (SumEconomy.spend(player, cost, reason) == null) {
                return cost;
            }
            OptionalDouble balance = SumEconomy.balance(player);
            if (balance.isPresent() && balance.getAsDouble() > 0.0D) {
                double rest = balance.getAsDouble();
                if (SumEconomy.spend(player, rest, reason + " (part payment)") == null) {
                    return rest;
                }
            }
            return 0.0D;
        }

        @Override
        public double credit(EntityPlayer player, double amount, String reason) {
            return amount > 0.0D && SumEconomy.credit(player, amount, reason) == null ? amount : 0.0D;
        }

        @Override
        public ShareTariff tariff() {
            return ShareTariff.fromConfig().withCurrency(SumEconomy.currencySymbol(), false);
        }
    };

    /** A physical item, from the rider's inventory. */
    BikeShareBilling ITEMS = new BikeShareBilling() {
        @Override
        public boolean canCheckOut(EntityPlayer player) {
            return ItemCurrency.count(player) >= ShareTariff.itemsOwed(tariff().unlockFee);
        }

        @Override
        public double charge(EntityPlayer player, BikeVariant variant, int minutes) {
            int owed = ShareTariff.itemsOwed(tariff().totalFor(variant, minutes));
            // A rider short of the fare hands over what they have — the same rule as money.
            return ItemCurrency.take(player, Math.min(owed, ItemCurrency.count(player)));
        }

        @Override
        public double credit(EntityPlayer player, double amount, String reason) {
            int whole = (int) Math.floor(amount + 1.0e-9D);
            return whole > 0 ? ItemCurrency.give(player, whole) : 0.0D;
        }

        @Override
        public ShareTariff tariff() {
            return ShareTariff.fromConfig().withCurrency(ItemCurrency.singularName(), true);
        }
    };
}
