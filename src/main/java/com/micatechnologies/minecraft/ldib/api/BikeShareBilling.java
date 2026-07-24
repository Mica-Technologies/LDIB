package com.micatechnologies.minecraft.ldib.api;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.entity.BikeVariant;
import com.micatechnologies.minecraft.ldib.integration.SumEconomy;
import net.minecraft.entity.player.EntityPlayer;

/**
 * The seam for charging for a bike-share rental. LDIB ships a <b>free</b> implementation and, when an
 * economy mod (SUM) is present and a {@link ShareTariff} is priced, a paying one — but the economy mod is
 * always <b>optional</b>: it is reached only through {@link SumEconomy}, a reflection wrapper that degrades
 * to "unavailable" when the mod is absent, so LDIB neither compiles against nor requires it.
 *
 * <p>Rentals are post-paid: {@link #canCheckOut} gates starting a session (must at least afford the unlock
 * fee) and {@link #charge} bills the unlock fee plus the elapsed minutes at the ridden variant's rate when
 * the bike is returned.</p>
 */
public interface BikeShareBilling {

    /** Whether {@code player} may start a rental now (can afford the unlock fee). Free billing: always true. */
    boolean canCheckOut(EntityPlayer player);

    /**
     * Bill {@code player} for a completed rental of {@code minutes} on {@code variant} and return the
     * amount actually charged (0 when free or when no economy is available).
     */
    double charge(EntityPlayer player, BikeVariant variant, int minutes);

    /** The billing in effect: the economy when it's installed, enabled and priced; otherwise free. */
    static BikeShareBilling active() {
        if (LdibConfig.shareUseEconomy && ShareTariff.fromConfig().isPaid() && SumEconomy.isAvailable()) {
            return ECONOMY;
        }
        return FREE;
    }

    /** The price list a rider would be billed against right now, or {@link ShareTariff#FREE} when free. */
    static ShareTariff activeTariff() {
        return active() == ECONOMY ? ShareTariff.fromConfig() : ShareTariff.FREE;
    }

    /** Rides are free: anyone may check out and nothing is ever charged. */
    BikeShareBilling FREE = new BikeShareBilling() {
        @Override
        public boolean canCheckOut(EntityPlayer player) {
            return true;
        }

        @Override
        public double charge(EntityPlayer player, BikeVariant variant, int minutes) {
            return 0.0D;
        }
    };

    /** Per-variant billing through the installed economy (SUM), via the {@link SumEconomy} wrapper. */
    BikeShareBilling ECONOMY = new BikeShareBilling() {
        @Override
        public boolean canCheckOut(EntityPlayer player) {
            // Post-paid, so allow check-out as long as the account can cover the unlock fee.
            return SumEconomy.getBalance(player) >= ShareTariff.fromConfig().unlockFee;
        }

        @Override
        public double charge(EntityPlayer player, BikeVariant variant, int minutes) {
            double cost = ShareTariff.fromConfig().totalFor(variant, minutes);
            if (cost > 0.0D) {
                SumEconomy.adjustBalance(player, -cost);
            }
            return cost;
        }
    };
}
