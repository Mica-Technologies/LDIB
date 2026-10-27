// COMPILE-ONLY COPY of SUM's PhoneApp (uia-server-utility-mod, src/main/java/.../sum/api/phone).
// Never packaged into LDIB; at runtime the real class comes from SUM. Keep in step with SUM
// whenever its phone API changes -- see addon.gradle (sumPhoneStubs).
package com.micatechnologies.minecraft.sum.api.phone;

/**
 * An app another mod adds to the SUM phone. It gets a tile on the home grid and, once opened, the
 * phone's screen to draw in — the same space SUM's own Notes or Pay app has, inside the same bezel,
 * status bar and home button. Register it with {@link PhoneApps#register}.
 *
 * <p><b>Client side only.</b> Every method is called on the client thread while the phone screen is
 * open. Nothing here is called on a server, so an app may freely use client classes in its own
 * implementation — but it must only be <i>registered</i> on the client (from a client proxy).</p>
 *
 * <p>Coordinates are screen pixels in the phone GUI's scaled space, the same space as
 * {@code mouseX}/{@code mouseY}. Draw inside {@link PhoneScreen#left()}..{@link PhoneScreen#right()}
 * and {@link PhoneScreen#top()}..{@link PhoneScreen#bottom()}; nothing clips for you.</p>
 *
 * <p>Only {@link #id}, {@link #title}, {@link #iconColor}, {@link #iconGlyph} and {@link #draw} are
 * required. New methods may be added to this interface as defaults without bumping
 * {@link PhoneApps#API_VERSION}.</p>
 */
public interface PhoneApp {

    /** A stable, unique id, namespaced by mod: {@code "ldib:bikeshare"}. */
    String id();

    /** The label under the home tile, already localised. Keep it short: the tile is 50 px wide. */
    String title();

    /** The icon square's colour, ARGB ({@code 0xFF6DB6E0}). */
    int iconColor();

    /** One or two characters drawn on the icon square, as SUM's own apps do ("$", "N"). */
    String iconGlyph();

    /** Whether to show this app on this phone. Desk phones are shared, so a personal app may hide. */
    default boolean isAvailable(PhoneScreen screen) {
        return true;
    }

    /** The app was opened from its home tile. */
    default void onOpen(PhoneScreen screen) {
    }

    /** Called once per client tick (20 a second) while the app is open. */
    default void tick(PhoneScreen screen) {
    }

    /** Draw the app's content. Called every frame while it is open. */
    void draw(PhoneScreen screen, int mouseX, int mouseY, float partialTicks);

    /** A mouse click inside the phone. Return true if the app used it. */
    default boolean mouseClicked(PhoneScreen screen, int mouseX, int mouseY, int button) {
        return false;
    }

    /** The mouse wheel turned while the app is open: positive is up. Return true if the app used it. */
    default boolean mouseScrolled(PhoneScreen screen, int mouseX, int mouseY, int amount) {
        return false;
    }

    /** A key. Return true if the app used it; Escape is never delivered (it closes the phone). */
    default boolean keyTyped(PhoneScreen screen, char typedChar, int keyCode) {
        return false;
    }

    /** Whether the phone shows its back button ("&lt;") for the app's current screen. */
    default boolean showsBackButton(PhoneScreen screen) {
        return false;
    }

    /**
     * The back button was pressed. Return true if the app went up a level itself; false sends the
     * phone home.
     */
    default boolean onBack(PhoneScreen screen) {
        return false;
    }

    /** The app is being left: the home button, or the phone closing. */
    default void onClose(PhoneScreen screen) {
    }
}
