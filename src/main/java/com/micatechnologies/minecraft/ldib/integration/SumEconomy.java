package com.micatechnologies.minecraft.ldib.integration;

import com.micatechnologies.minecraft.ldib.Ldib;
import java.lang.reflect.Method;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;

/**
 * Optional, reflection-only bridge to the SUM mod's economy ({@code uia-server-utility-mod}, mod id
 * {@code sum}). SUM exposes a static facade {@code com.micatechnologies.minecraft.sum.economy.
 * EconomyBridge} with {@code isAvailable()}, {@code getBalance(EntityPlayer)} and
 * {@code adjustBalance(EntityPlayer, double)}; that facade transparently handles both SUM's own
 * currency capability and the third-party EconomyInc backend.
 *
 * <p><b>SUM is never a required dependency.</b> Nothing here references a SUM type at compile time —
 * everything is resolved reflectively, once, and guarded by {@link Loader#isModLoaded(String)}. When
 * SUM is absent every method degrades safely ({@link #isAvailable()} is false, balance is 0, charges
 * are no-ops), so LDIB loads and runs identically with or without it. Declare {@code after:sum} in the
 * {@code @Mod} dependencies for load order only.</p>
 */
public final class SumEconomy {

    private static final String SUM_MODID = "sum";
    private static final String BRIDGE_CLASS = "com.micatechnologies.minecraft.sum.economy.EconomyBridge";

    private static boolean resolved;
    private static Method isAvailable;
    private static Method getBalance;
    private static Method adjustBalance;

    private SumEconomy() {
        throw new AssertionError("No instances.");
    }

    /** Resolve SUM's economy facade once, if the mod is loaded. Safe to call when SUM is absent. */
    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!Loader.isModLoaded(SUM_MODID)) {
            return;
        }
        try {
            Class<?> bridge = Class.forName(BRIDGE_CLASS);
            isAvailable = bridge.getMethod("isAvailable");
            getBalance = bridge.getMethod("getBalance", EntityPlayer.class);
            adjustBalance = bridge.getMethod("adjustBalance", EntityPlayer.class, double.class);
            Ldib.LOGGER.info("SUM economy detected; bike-share billing is available.");
        } catch (ReflectiveOperationException e) {
            isAvailable = getBalance = adjustBalance = null;
            Ldib.LOGGER.warn("SUM is loaded but its economy facade could not be resolved; "
                + "bike-share billing disabled.", e);
        }
    }

    /** Whether an economy backend is installed and ready to bill. */
    public static boolean isAvailable() {
        resolve();
        if (isAvailable == null) {
            return false;
        }
        try {
            return (Boolean) isAvailable.invoke(null);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** {@code player}'s balance, or 0 when no economy is available. */
    public static double getBalance(EntityPlayer player) {
        resolve();
        if (getBalance == null) {
            return 0.0D;
        }
        try {
            double bal = (Double) getBalance.invoke(null, player);
            return Double.isNaN(bal) ? 0.0D : bal;
        } catch (ReflectiveOperationException e) {
            return 0.0D;
        }
    }

    /** Add {@code delta} (negative to charge) to {@code player}'s balance; true on success. No-op if absent. */
    public static boolean adjustBalance(EntityPlayer player, double delta) {
        resolve();
        if (adjustBalance == null) {
            return false;
        }
        try {
            return (Boolean) adjustBalance.invoke(null, player, delta);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
