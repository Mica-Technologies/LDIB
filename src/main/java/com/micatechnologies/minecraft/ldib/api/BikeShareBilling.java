package com.micatechnologies.minecraft.ldib.api;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.integration.SumEconomy;
import net.minecraft.entity.player.EntityPlayer;

/**
 * The seam for charging for a bike-share rental. LDIB ships a <b>free</b> implementation and, when an
 * economy mod (SUM) is present and a rate is configured, a paying one — but the economy mod is always
 * <b>optional</b>: it is reached only through {@link SumEconomy}, a reflection wrapper that degrades to
 * "unavailable" when the mod is absent, so LDIB neither compiles against nor requires it.
 *
 * <p>Rentals are post-paid: {@link #canCheckOut} gates starting a session and {@link #charge} bills
 * for the elapsed minutes when the bike is returned.</p>
 */
public interface BikeShareBilling {

    /** Whether {@code player} may start a rental now (e.g. can afford it). Free billing: always true. */
    boolean canCheckOut(EntityPlayer player);

    /**
     * Bill {@code player} for a completed rental of {@code minutes} and return the amount actually
     * charged (0 when free or when no economy is available).
     */
    double charge(EntityPlayer player, int minutes);

    /** The billing in effect: the economy when it's installed, enabled and priced; otherwise free. */
    static BikeShareBilling active() {
        if (LdibConfig.shareRatePerMinute > 0.0D && LdibConfig.shareUseEconomy && SumEconomy.isAvailable()) {
            return ECONOMY;
        }
        return FREE;
    }

    /** Rides are free: anyone may check out and nothing is ever charged. */
    BikeShareBilling FREE = new BikeShareBilling() {
        @Override
        public boolean canCheckOut(EntityPlayer player) {
            return true;
        }

        @Override
        public double charge(EntityPlayer player, int minutes) {
            return 0.0D;
        }
    };

    /** Per-minute billing through the installed economy (SUM), via the {@link SumEconomy} wrapper. */
    BikeShareBilling ECONOMY = new BikeShareBilling() {
        @Override
        public boolean canCheckOut(EntityPlayer player) {
            // Post-paid, so allow check-out as long as the account isn't already empty.
            return SumEconomy.getBalance(player) > 0.0D;
        }

        @Override
        public double charge(EntityPlayer player, int minutes) {
            double cost = Math.max(0, minutes) * LdibConfig.shareRatePerMinute;
            if (cost > 0.0D) {
                SumEconomy.adjustBalance(player, -cost);
            }
            return cost;
        }
    };
}
