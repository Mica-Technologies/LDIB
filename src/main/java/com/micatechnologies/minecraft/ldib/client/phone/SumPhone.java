package com.micatechnologies.minecraft.ldib.client.phone;

import com.micatechnologies.minecraft.sum.api.phone.PhoneApps;

/**
 * Puts {@link LdibikePhoneApp} on SUM's phone. Client only.
 *
 * <p>This is the only class that touches SUM's phone API directly, and the caller
 * ({@code LdibClientProxy}) reaches it only after checking SUM is loaded, catching the
 * {@link NoClassDefFoundError} an older SUM — one with no phone API — would throw when this class
 * links. Keeping the reference here, out of the proxy, is what lets the proxy itself load whatever SUM
 * is or is not installed.</p>
 */
public final class SumPhone {

    private SumPhone() {
        throw new AssertionError("No instances.");
    }

    /** Register the app; returns false if this SUM's phone API is not one LDIB was written for. */
    public static boolean register() {
        if (!PhoneApps.isCompatible(1)) {
            return false;
        }
        PhoneApps.register(new LdibikePhoneApp());
        return true;
    }
}
