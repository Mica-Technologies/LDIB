package com.micatechnologies.minecraft.ldib.integration;

import com.micatechnologies.minecraft.ldib.Ldib;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;

/**
 * LDIB's optional link to SUM's economy ({@code uia-server-utility-mod}, mod id {@code sum}), through
 * SUM's <b>public</b> economy API: {@code com.micatechnologies.minecraft.sum.api.SumEconomy} and the
 * {@code EconomyHandle} it hands out. Modelled on CSM's {@code CsmSumEconomy}, which does the same job.
 *
 * <p><b>SUM is never required</b>, so nothing here is compiled against it: every SUM type is reached by
 * reflection, and only when SUM is loaded. A missing, older or refusing SUM cannot throw anywhere;
 * {@link #isAvailable()} is simply false and the bike-share bills in items instead
 * ({@code BikeShareBilling}), with one log line saying why.</p>
 *
 * <p>SUM only deals with mods its operator has allowed. LDIB asks as {@code ldib}, and needs
 * {@code ldib=wallet_write} in SUM's {@code economy_integration.allowedMods}; a fresh SUM server allows
 * nothing. While refused, SUM is asked again every half minute, so allowing LDIB and running
 * {@code /sum econ api reload} takes effect without a restart.</p>
 *
 * <p>The handle is asked for once the server has <i>started</i> (SUM attaches its economy while the
 * server is starting, and loads after LDIB) and released when it stops. Every call is server-thread
 * only; SUM refuses any other thread.</p>
 */
public final class SumEconomy {

    /** SUM's mod id, and the id LDIB asks under. */
    public static final String SUM_MOD_ID = "sum";
    public static final String INTEGRATION_ID = "ldib";
    /** The SUM economy API revision this was written against. */
    private static final int API_VERSION = 1;
    private static final String API = "com.micatechnologies.minecraft.sum.api.";
    /** How often to ask SUM again while it refuses. */
    private static final long ASK_AGAIN_MILLIS = 30_000L;

    private static Object handle;
    private static Method walletSpend;
    private static Method walletCredit;
    private static Method walletBalance;
    private static Method resultIsOk;
    private static Method resultMessage;
    private static Method handleStatus;
    private static Method statusSymbol;
    private static boolean deniedLogged;
    private static volatile boolean wanted;
    private static volatile long lastAskMillis;

    private SumEconomy() {
        throw new AssertionError("No instances.");
    }

    /** Ask SUM for an economy handle. Called when the server has started; safe with no SUM. */
    public static synchronized void acquire() {
        release();
        wanted = true;
        ask();
    }

    /** Drop the handle, so the next server start asks (and logs) afresh. */
    public static synchronized void release() {
        handle = null;
        wanted = false;
        deniedLogged = false;
        walletSpend = walletCredit = walletBalance = null;
        resultIsOk = resultMessage = handleStatus = statusSymbol = null;
    }

    private static synchronized void ask() {
        lastAskMillis = System.currentTimeMillis();
        if (handle != null || !Loader.isModLoaded(SUM_MOD_ID)) {
            return;
        }
        try {
            Class<?> sum = Class.forName(API + "SumEconomy");
            if (!(Boolean) sum.getMethod("isCompatible", int.class).invoke(null, API_VERSION)) {
                logOnce("SUM is installed but older than its economy API revision " + API_VERSION
                    + "; bike-share rides are paid in items instead.");
                return;
            }
            Optional<?> maybe = (Optional<?>) sum.getMethod("acquire", String.class).invoke(null, INTEGRATION_ID);
            if (!maybe.isPresent()) {
                Object why = sum.getMethod("describeDenial", String.class).invoke(null, INTEGRATION_ID);
                logOnce("SUM's economy is not open to LDIB, so bike-share rides are paid in items instead. "
                    + "To bill through SUM, add 'ldib=wallet_write' to SUM's economy_integration.allowedMods "
                    + "and run '/sum econ api reload'. SUM says: " + why);
                return;
            }
            Class<?> handleClass = Class.forName(API + "EconomyHandle");
            Class<?> resultClass = Class.forName(API + "EconomyResult");
            walletSpend = handleClass.getMethod("walletSpend", EntityPlayer.class, double.class, String.class);
            walletCredit = handleClass.getMethod("walletCredit", EntityPlayer.class, double.class, String.class);
            resultIsOk = resultClass.getMethod("isOk");
            resultMessage = resultClass.getMethod("getMessage");
            // A balance and a currency symbol are nice to have; neither is needed to bill.
            walletBalance = optionalMethod(handleClass, "getWalletBalance", EntityPlayer.class);
            handleStatus = optionalMethod(handleClass, "getStatus");
            Class<?> statusClass = optionalClass(API + "EconomyStatus");
            statusSymbol = statusClass == null ? null : optionalMethod(statusClass, "getCurrencySymbol");
            handle = maybe.get();
            Ldib.LOGGER.info("Bike-share rides are billed through SUM's economy (as '{}').", INTEGRATION_ID);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            release();
            logOnce("Could not reach SUM's economy API (" + e + "); bike-share rides are paid in items instead.");
        }
    }

    /**
     * Whether money moves through SUM right now. While SUM refuses, it is asked again every half minute.
     */
    public static boolean isAvailable() {
        if (handle == null && wanted && System.currentTimeMillis() - lastAskMillis >= ASK_AGAIN_MILLIS) {
            ask();
        }
        return handle != null;
    }

    /** The player's wallet balance, or empty when it cannot be read. */
    public static OptionalDouble balance(EntityPlayer player) {
        Object h = handle;
        Method m = walletBalance;
        if (h == null || m == null) {
            return OptionalDouble.empty();
        }
        try {
            Object result = m.invoke(h, player);
            return result instanceof OptionalDouble ? (OptionalDouble) result : OptionalDouble.empty();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return OptionalDouble.empty();
        }
    }

    /** Take {@code amount} from the player's wallet; {@code null} on success, otherwise SUM's reason. */
    public static String spend(EntityPlayer player, double amount, String reason) {
        return call(walletSpend, player, amount, reason);
    }

    /** Give {@code amount} to the player's wallet; {@code null} on success, otherwise SUM's reason. */
    public static String credit(EntityPlayer player, double amount, String reason) {
        return call(walletCredit, player, amount, reason);
    }

    /** SUM's currency symbol, {@code "$"} unless its bank says otherwise. */
    public static String currencySymbol() {
        Object h = handle;
        if (h != null && handleStatus != null && statusSymbol != null) {
            try {
                Object symbol = statusSymbol.invoke(handleStatus.invoke(h));
                if (symbol != null && !symbol.toString().isEmpty()) {
                    return symbol.toString();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Fall back to the dollar sign SUM uses itself.
            }
        }
        return "$";
    }

    /** {@code amount} as SUM shows money: the symbol and two decimals. */
    public static String format(String symbol, double amount) {
        return symbol + String.format(Locale.ROOT, "%.2f", amount);
    }

    private static String call(Method method, EntityPlayer player, double amount, String reason) {
        Object h = handle;
        if (h == null || method == null) {
            return "SUM's economy is not available";
        }
        try {
            Object result = method.invoke(h, player, amount, reason);
            if ((Boolean) resultIsOk.invoke(result)) {
                return null;
            }
            Object message = resultMessage.invoke(result);
            return message == null ? "refused" : message.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "SUM's economy failed: " + e;
        }
    }

    private static synchronized void logOnce(String message) {
        if (!deniedLogged) {
            deniedLogged = true;
            Ldib.LOGGER.warn(message);
        }
    }

    private static Method optionalMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            return owner.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Class<?> optionalClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
