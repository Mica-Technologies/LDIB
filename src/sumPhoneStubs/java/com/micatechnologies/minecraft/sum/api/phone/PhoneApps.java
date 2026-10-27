// COMPILE-ONLY COPY of SUM's PhoneApps (uia-server-utility-mod, src/main/java/.../sum/api/phone).
// Never packaged into LDIB; at runtime the real class comes from SUM. Keep in step with SUM
// whenever its phone API changes -- see addon.gradle (sumPhoneStubs).
package com.micatechnologies.minecraft.sum.api.phone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The registry of {@link PhoneApp}s other mods add to the SUM phone, and a way to open the phone.
 * Documented in {@code docs/PHONE_APP_API.md}.
 *
 * <p>Register from client-side setup (a client proxy's init), guarded by
 * {@code Loader.isModLoaded("sum")}: SUM is optional for most mods, and an older SUM has no phone API
 * at all, so also be ready for a {@code NoClassDefFoundError} and treat it as "no phone".</p>
 *
 * <pre>{@code
 * if (Loader.isModLoaded("sum") && PhoneApps.isCompatible(1)) {
 *     PhoneApps.register(new MyApp());
 * }
 * }</pre>
 */
public final class PhoneApps {

    /** Bumped only when something here changes incompatibly. */
    public static final int API_VERSION = 1;

    private static final List<PhoneApp> APPS = new ArrayList<>();
    private static Runnable opener;

    private PhoneApps() {
        throw new AssertionError("No instances.");
    }

    /** Whether this SUM's phone API is the {@code version} a caller was written against, or newer. */
    public static boolean isCompatible(int version) {
        return version >= 1 && version <= API_VERSION;
    }

    /**
     * Add an app to the phone's home screen, after SUM's own apps, in registration order. Client side.
     *
     * @throws IllegalArgumentException if an app with the same {@link PhoneApp#id} is registered
     */
    public static synchronized void register(PhoneApp app) {
        for (PhoneApp existing : APPS) {
            if (existing.id().equals(app.id())) {
                throw new IllegalArgumentException("A phone app with id '" + app.id() + "' is already registered");
            }
        }
        APPS.add(app);
    }

    /** Every registered app, in registration order. */
    public static synchronized List<PhoneApp> registered() {
        return Collections.unmodifiableList(new ArrayList<>(APPS));
    }

    /**
     * Open the SUM phone for the local player, as if they had used their phone item. Client side.
     * Returns false if the phone cannot be opened here (a dedicated server, or before SUM's client
     * setup has run).
     */
    public static boolean openPhone() {
        Runnable r = opener;
        if (r == null) {
            return false;
        }
        r.run();
        return true;
    }

    /** Called once by SUM's own client setup to say how the phone opens. Not for other mods. */
    public static void setOpener(Runnable openPhone) {
        opener = openPhone;
    }
}
