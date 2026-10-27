// COMPILE-ONLY COPY of SUM's PhoneScreen (uia-server-utility-mod, src/main/java/.../sum/api/phone).
// Never packaged into LDIB; at runtime the real class comes from SUM. Keep in step with SUM
// whenever its phone API changes -- see addon.gradle (sumPhoneStubs).
package com.micatechnologies.minecraft.sum.api.phone;

import net.minecraft.entity.player.EntityPlayer;

/**
 * What an open {@link PhoneApp} can see and do: the rectangle it owns on the phone's screen, the
 * player holding the phone, and the phone's own drawing primitives — so an app looks like SUM's
 * apps without reaching into the phone GUI. Implemented by SUM; not meant to be implemented by apps.
 *
 * <p>Deliberately free of client-only types, so this package stays loadable on a dedicated server.
 * The drawing methods only work while the phone is rendering.</p>
 */
public interface PhoneScreen {

    /** The app's area: left edge (inclusive). */
    int left();

    /** The app's area: top edge, just below the phone's status bar. */
    int top();

    /** The app's area: right edge (exclusive). */
    int right();

    /** The app's area: bottom edge (exclusive), just above the home button. */
    int bottom();

    /** The player looking at the phone. */
    EntityPlayer player();

    /** Whether this is a shared desk phone rather than the player's own. */
    boolean isDeskPhone();

    /** Fill a rectangle, ARGB. */
    void fill(int x1, int y1, int x2, int y2, int argb);

    /** Draw text at {@code (x, y)}, ARGB. */
    void drawText(String text, int x, int y, int argb);

    /** Draw text centred on {@code centreX}. */
    void drawCenteredText(String text, int centreX, int y, int argb);

    /** Width of {@code text} in pixels. */
    int textWidth(String text);

    /** {@code text} cut to fit {@code width} pixels, with an ellipsis when cut. */
    String trimToWidth(String text, int width);

    /** Height of a line of text, pixels. */
    int lineHeight();

    /** Leave the app and show the home screen. */
    void goHome();
}
