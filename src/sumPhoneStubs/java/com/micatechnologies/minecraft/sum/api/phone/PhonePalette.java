// COMPILE-ONLY COPY of SUM's PhonePalette (uia-server-utility-mod, src/main/java/.../sum/api/phone).
// Never packaged into LDIB; at runtime the real class comes from SUM. Keep in step with SUM
// whenever its phone API changes -- see addon.gradle (sumPhoneStubs).
package com.micatechnologies.minecraft.sum.api.phone;

/**
 * The SUM phone's colours, ARGB, so a {@link PhoneApp} matches the built-in apps. Values may be
 * retuned between versions; refer to them by name rather than copying the numbers.
 */
public final class PhonePalette {

    public static final int SCREEN_BG = 0xFF202833;
    public static final int TEXT_LIGHT = 0xFFE0E0E0;
    public static final int TEXT_DIM = 0xFF9098A0;
    public static final int TEXT_TITLE = 0xFFFFFFFF;
    public static final int TEXT_ERROR = 0xFFE07070;
    public static final int TEXT_ACCENT = 0xFF6DB6E0;
    public static final int TEXT_NUMBER = 0xFFD4B258;
    public static final int TEXT_GOOD = 0xFF6EC07A;
    public static final int LIST_ROW_BG = 0xFF2A3340;
    public static final int LIST_ROW_HOVER = 0xFF3A4658;
    public static final int BUTTON_BG = 0xFF4A5468;
    public static final int BUTTON_HOVER = 0xFF5A6680;
    public static final int BADGE_BG = 0xFFD04050;

    private PhonePalette() {
        throw new AssertionError("No instances.");
    }
}
