package com.micatechnologies.minecraft.ldib.client.phone;

import com.micatechnologies.minecraft.ldib.api.BikeShareApi;
import com.micatechnologies.minecraft.ldib.api.BikeShareSnapshot;
import com.micatechnologies.minecraft.ldib.api.RiderStatus;
import com.micatechnologies.minecraft.ldib.api.ShareTariff;
import com.micatechnologies.minecraft.ldib.api.StationInfo;
import com.micatechnologies.minecraft.ldib.api.StationMath;
import com.micatechnologies.minecraft.ldib.entity.BikeVariant;
import com.micatechnologies.minecraft.sum.api.phone.PhoneApp;
import com.micatechnologies.minecraft.sum.api.phone.PhonePalette;
import com.micatechnologies.minecraft.sum.api.phone.PhoneScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

/**
 * LDIBike, the bike-share app on SUM's phone: the nearest stations with an arrow pointing at each,
 * what is docked where, the rider's rebalancing rewards, and their open rental.
 *
 * <p>Everything shown comes from {@link BikeShareApi}: a snapshot is requested when the app opens and
 * every couple of seconds while it stays open, and the views draw from the latest one. Distances and
 * arrows are worked out here, every frame, from the player's live position and facing, so turning on
 * the spot swings the arrows straight away rather than at the next snapshot.</p>
 *
 * <p>Client only. Registered by {@link SumPhone} when SUM's phone API is present.</p>
 */
public final class LdibikePhoneApp implements PhoneApp {

    /** Ticks between snapshot requests while the app is open (the server limits this anyway). */
    private static final int REFRESH_TICKS = 40;
    /** Height of one station row in the Nearby list. */
    private static final int ROW_H = 22;
    /** Eight arrows, clockwise from straight ahead; see {@link StationMath#arrowIndex}. */
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private enum View { NEARBY, STATION, REWARDS, RIDE }

    private static final View[] TABS = {View.NEARBY, View.REWARDS, View.RIDE};
    private static final String[] TAB_LABELS = {"Nearby", "Rewards", "Ride"};

    private View view = View.NEARBY;
    /** The station open in the STATION view. */
    private BlockPos selected;
    /** First row shown in the Nearby list. */
    private int scroll;
    private int ticksOpen;

    @Override
    public String id() {
        return "ldib:bikeshare";
    }

    @Override
    public String title() {
        return "LDIBike";
    }

    @Override
    public int iconColor() {
        return 0xFF55D6C6;
    }

    @Override
    public String iconGlyph() {
        return "B";
    }

    @Override
    public void onOpen(PhoneScreen screen) {
        view = View.NEARBY;
        selected = null;
        scroll = 0;
        ticksOpen = 0;
        BikeShareApi.requestSnapshot();
    }

    @Override
    public void tick(PhoneScreen screen) {
        if (++ticksOpen % REFRESH_TICKS == 0) {
            BikeShareApi.requestSnapshot();
        }
    }

    @Override
    public boolean showsBackButton(PhoneScreen screen) {
        return view == View.STATION;
    }

    @Override
    public boolean onBack(PhoneScreen screen) {
        if (view == View.STATION) {
            view = View.NEARBY;
            selected = null;
            return true;
        }
        return false;
    }

    // --- Layout ----------------------------------------------------------------------------------

    private static int tabsTop(PhoneScreen s) {
        return s.top() + 13;
    }

    private static int contentTop(PhoneScreen s) {
        return s.top() + 28;
    }

    /**
     * Left edge of each tab, plus the right edge of the last, so the tabs fill the row with each one
     * as wide as its label plus an even share of the room left over — equal widths left "Rewards"
     * running into its neighbours on a 132 px screen.
     */
    private static int[] tabEdges(PhoneScreen s) {
        int x1 = s.left() + 2;
        int x2 = s.right() - 2;
        int labels = 0;
        for (String label : TAB_LABELS) {
            labels += s.textWidth(label);
        }
        int spare = Math.max(0, (x2 - x1) - labels - 2 * (TABS.length - 1)) / TABS.length;
        int[] edges = new int[TABS.length + 1];
        int x = x1;
        for (int i = 0; i < TABS.length; i++) {
            edges[i] = x;
            x += s.textWidth(TAB_LABELS[i]) + spare + 2;
        }
        edges[TABS.length] = x2 + 2;
        return edges;
    }

    private static int visibleRows(PhoneScreen s) {
        return Math.max(1, (s.bottom() - contentTop(s) - 2) / ROW_H);
    }

    private static boolean inside(int mx, int my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    // --- Drawing ---------------------------------------------------------------------------------

    @Override
    public void draw(PhoneScreen s, int mouseX, int mouseY, float partialTicks) {
        // The STATION view has the phone's back button in the top-left, so its title moves aside.
        int titleX = view == View.STATION ? s.left() + 34 : s.left() + 4;
        s.drawText("LDIBike", titleX, s.top() + 2, PhonePalette.TEXT_ACCENT);

        BikeShareSnapshot snap = BikeShareApi.latestSnapshot();
        if (view != View.STATION) {
            drawTabs(s, mouseX, mouseY);
        }
        if (snap == null) {
            s.drawCenteredText("Loading...", (s.left() + s.right()) / 2, contentTop(s) + 20, PhonePalette.TEXT_DIM);
            return;
        }
        switch (view) {
            case NEARBY:
                drawNearby(s, snap, mouseX, mouseY);
                break;
            case STATION:
                drawStation(s, snap);
                break;
            case REWARDS:
                drawRewards(s, snap);
                break;
            case RIDE:
                drawRide(s, snap);
                break;
            default:
                break;
        }
    }

    private void drawTabs(PhoneScreen s, int mouseX, int mouseY) {
        int y = tabsTop(s);
        int[] edges = tabEdges(s);
        for (int i = 0; i < TABS.length; i++) {
            int x = edges[i];
            int xr = edges[i + 1] - 2;
            boolean active = TABS[i] == view;
            boolean hover = inside(mouseX, mouseY, x, y, xr, y + 12);
            s.fill(x, y, xr, y + 12,
                active ? PhonePalette.BUTTON_HOVER : (hover ? PhonePalette.LIST_ROW_HOVER : PhonePalette.LIST_ROW_BG));
            s.drawCenteredText(TAB_LABELS[i], (x + xr) / 2, y + 2,
                active ? PhonePalette.TEXT_TITLE : PhonePalette.TEXT_LIGHT);
        }
    }

    private void drawNearby(PhoneScreen s, BikeShareSnapshot snap, int mouseX, int mouseY) {
        int x1 = s.left() + 2;
        int x2 = s.right() - 2;
        if (snap.stations.isEmpty()) {
            s.drawCenteredText("No stations yet.", (s.left() + s.right()) / 2, contentTop(s) + 20, PhonePalette.TEXT_DIM);
            return;
        }
        EntityPlayer player = s.player();
        int rows = visibleRows(s);
        scroll = Math.max(0, Math.min(scroll, snap.stations.size() - rows));
        for (int i = 0; i < rows && scroll + i < snap.stations.size(); i++) {
            StationInfo st = snap.stations.get(scroll + i);
            int y = contentTop(s) + i * ROW_H;
            boolean hover = inside(mouseX, mouseY, x1, y, x2, y + ROW_H - 2);
            s.fill(x1, y, x2, y + ROW_H - 2, hover ? PhonePalette.LIST_ROW_HOVER : PhonePalette.LIST_ROW_BG);

            double sx = st.kiosk.getX() + 0.5D;
            double sz = st.kiosk.getZ() + 0.5D;
            double dist = StationMath.distance(player.posX, player.posZ, sx, sz);
            double bearing = StationMath.bearing(player.posX, player.posZ, sx, sz);
            String arrow = ARROWS[StationMath.arrowIndex(StationMath.relativeBearing(player.rotationYaw, bearing))];
            s.drawText(arrow + " " + Math.round(dist) + " m " + StationMath.compass(bearing), x1 + 3, y + 2,
                PhonePalette.TEXT_LIGHT);
            String counts = st.bikes + "/" + st.docks();
            s.drawText(counts, x2 - 3 - s.textWidth(counts), y + 2, PhonePalette.TEXT_NUMBER);
            s.drawText(s.trimToWidth(subtitle(snap.tariff, st), x2 - x1 - 6), x1 + 3, y + 11, badgeColour(snap.tariff, st));
        }
        if (snap.stations.size() > rows) {
            String more = (scroll + 1) + "-" + Math.min(snap.stations.size(), scroll + rows) + " of " + snap.stations.size();
            s.drawCenteredText(more, (s.left() + s.right()) / 2, s.bottom() - s.lineHeight(), PhonePalette.TEXT_DIM);
        }
    }

    /** The second line of a station row: its rebalancing offer, or where it is. */
    private static String subtitle(ShareTariff tariff, StationInfo st) {
        if (tariff.isBusy(st.fill())) {
            return "Full - ride one away: " + tariff.format(tariff.rebalanceCreditPerEnd) + " back";
        }
        if (tariff.isStarved(st.fill())) {
            return "Low - return one here: " + tariff.format(tariff.rebalanceCreditPerEnd) + " back";
        }
        return "at " + st.kiosk.getX() + ", " + st.kiosk.getZ();
    }

    private static int badgeColour(ShareTariff tariff, StationInfo st) {
        return tariff.isBusy(st.fill()) || tariff.isStarved(st.fill()) ? PhonePalette.TEXT_GOOD : PhonePalette.TEXT_DIM;
    }

    private void drawStation(PhoneScreen s, BikeShareSnapshot snap) {
        StationInfo st = find(snap, selected);
        int x = s.left() + 4;
        int y = s.top() + 16;
        if (st == null) {
            s.drawText("This station is gone.", x, y, PhonePalette.TEXT_DIM);
            return;
        }
        EntityPlayer player = s.player();
        double sx = st.kiosk.getX() + 0.5D;
        double sz = st.kiosk.getZ() + 0.5D;
        double bearing = StationMath.bearing(player.posX, player.posZ, sx, sz);
        String arrow = ARROWS[StationMath.arrowIndex(StationMath.relativeBearing(player.rotationYaw, bearing))];
        s.drawText(arrow + " " + Math.round(StationMath.distance(player.posX, player.posZ, sx, sz)) + " m "
            + StationMath.compass(bearing), x, y, PhonePalette.TEXT_TITLE);
        s.drawText("at " + st.kiosk.getX() + ", " + st.kiosk.getY() + ", " + st.kiosk.getZ(), x, y + 11, PhonePalette.TEXT_DIM);

        y += 28;
        for (BikeVariant variant : BikeVariant.values()) {
            if (!variant.usesStations()) {
                continue;
            }
            s.drawText(label(variant), x, y, PhonePalette.TEXT_LIGHT);
            String n = Integer.toString(st.bikes(variant));
            s.drawText(n, s.right() - 6 - s.textWidth(n), y, PhonePalette.TEXT_NUMBER);
            y += 11;
        }
        s.drawText("Free docks", x, y, PhonePalette.TEXT_LIGHT);
        String free = Integer.toString(st.freeDocks);
        s.drawText(free, s.right() - 6 - s.textWidth(free), y, PhonePalette.TEXT_NUMBER);
        y += 16;

        ShareTariff t = snap.tariff;
        if (t.isBusy(st.fill())) {
            s.drawText("Station full.", x, y, PhonePalette.TEXT_GOOD);
            s.drawText("Ride a bike away:", x, y + 11, PhonePalette.TEXT_LIGHT);
            s.drawText(t.format(t.rebalanceCreditPerEnd) + " back", x, y + 22, PhonePalette.TEXT_GOOD);
        } else if (t.isStarved(st.fill())) {
            s.drawText("Station low.", x, y, PhonePalette.TEXT_GOOD);
            s.drawText("Return a bike here:", x, y + 11, PhonePalette.TEXT_LIGHT);
            s.drawText(t.format(t.rebalanceCreditPerEnd) + " back", x, y + 22, PhonePalette.TEXT_GOOD);
        }
    }

    private void drawRewards(PhoneScreen s, BikeShareSnapshot snap) {
        int x = s.left() + 4;
        int y = contentTop(s);
        ShareTariff t = snap.tariff;
        RiderStatus r = snap.rider;
        if (t.rebalanceCreditPerEnd <= 0.0D) {
            s.drawText("No rebalancing rewards", x, y, PhonePalette.TEXT_DIM);
            s.drawText("on this network.", x, y + 11, PhonePalette.TEXT_DIM);
            y += 30;
        } else {
            s.drawText("Credit earned", x, y, PhonePalette.TEXT_LIGHT);
            String credited = t.format(r.credited);
            s.drawText(credited, s.right() - 6 - s.textWidth(credited), y, PhonePalette.TEXT_GOOD);
            s.drawText("Rebalancing rides", x, y + 11, PhonePalette.TEXT_LIGHT);
            String rr = Integer.toString(r.rebalancingRides);
            s.drawText(rr, s.right() - 6 - s.textWidth(rr), y + 11, PhonePalette.TEXT_NUMBER);
            y += 26;
            int busy = 0;
            int starved = 0;
            for (StationInfo st : snap.stations) {
                if (t.isBusy(st.fill())) {
                    busy++;
                } else if (t.isStarved(st.fill())) {
                    starved++;
                }
            }
            s.drawText("Offers: " + busy + " full, " + starved + " low", x, y, PhonePalette.TEXT_ACCENT);
            s.drawText(t.format(t.rebalanceCreditPerEnd) + " back per end", x, y + 11, PhonePalette.TEXT_DIM);
            y += 26;
        }
        s.drawText("Rides", x, y, PhonePalette.TEXT_LIGHT);
        String rides = Integer.toString(r.rides);
        s.drawText(rides, s.right() - 6 - s.textWidth(rides), y, PhonePalette.TEXT_NUMBER);
        if (t.isPaid() || r.charged > 0.0D) {
            s.drawText("Paid", x, y + 11, PhonePalette.TEXT_LIGHT);
            String paid = t.format(r.charged);
            s.drawText(paid, s.right() - 6 - s.textWidth(paid), y + 11, PhonePalette.TEXT_NUMBER);
        }
        y += 26;
        if (!r.recentCredits.isEmpty()) {
            s.drawText("Recent credit", x, y, PhonePalette.TEXT_DIM);
            y += 11;
            for (int i = 0; i < r.recentCredits.size() && y < s.bottom() - s.lineHeight(); i++) {
                RiderStatus.CreditEntry c = r.recentCredits.get(i);
                long minutesAgo = Math.max(0L, (snap.worldTime - c.tick) / 1200L);
                s.drawText("+" + t.format(c.amount), x, y, PhonePalette.TEXT_GOOD);
                String ago = minutesAgo < 1 ? "now" : minutesAgo + " min ago";
                s.drawText(ago, s.right() - 6 - s.textWidth(ago), y, PhonePalette.TEXT_DIM);
                y += 10;
            }
        }
    }

    private void drawRide(PhoneScreen s, BikeShareSnapshot snap) {
        int x = s.left() + 4;
        int y = contentTop(s);
        RiderStatus r = snap.rider;
        ShareTariff t = snap.tariff;
        if (!r.hasRental) {
            s.drawText("No rental open.", x, y, PhonePalette.TEXT_LIGHT);
            s.drawText("Check out at any", x, y + 14, PhonePalette.TEXT_DIM);
            s.drawText("station's kiosk.", x, y + 25, PhonePalette.TEXT_DIM);
            if (t.isPaid()) {
                // Names left, amounts right. In an item currency the header names the item once, so
                // each amount can drop it and fit: "1 per 5 min" rather than "1 emerald / 5 min".
                s.drawText(t.wholeItems ? "Prices, in " + t.pluralCurrency() : "Prices", x, y + 44, PhonePalette.TEXT_DIM);
                priceRow(s, "Unlock", t.wholeItems ? Integer.toString(ShareTariff.itemsOwed(t.unlockFee))
                    : t.formatPrice(t.unlockFee), y + 56);
                priceRow(s, "Bike", shortRate(t, t.bikePerMinute()), y + 67);
                priceRow(s, "E-bike", shortRate(t, t.ebikePerMinute()), y + 78);
                priceRow(s, "Scooter", shortRate(t, t.scooterPerMinute()), y + 89);
            } else {
                s.drawText("Rides are free.", x, y + 44, PhonePalette.TEXT_GOOD);
            }
            return;
        }
        s.drawText("Rental open", x, y, PhonePalette.TEXT_GOOD);
        if (r.rentalKiosk != null) {
            s.drawText("from " + r.rentalKiosk.getX() + ", " + r.rentalKiosk.getZ(), x, y + 11, PhonePalette.TEXT_DIM);
        }
        if (!r.bikeTaken) {
            s.drawText("Take a bike from a", x, y + 28, PhonePalette.TEXT_LIGHT);
            s.drawText("dock at that station.", x, y + 39, PhonePalette.TEXT_LIGHT);
            return;
        }
        // The world clock ticks on the client too, so the timer runs between snapshots.
        long elapsed = Math.max(0L, s.player().world.getTotalWorldTime() - r.rentalClockStart);
        int minutes = (int) Math.max(1L, (elapsed + 1199L) / 1200L);
        s.drawText("Riding " + minutes + (minutes == 1 ? " min" : " mins"), x, y + 28, PhonePalette.TEXT_LIGHT);
        if (t.isPaid()) {
            s.drawText("So far, on a bike:", x, y + 45, PhonePalette.TEXT_DIM);
            s.drawText(t.formatPrice(t.totalFor(BikeVariant.BICYCLE, minutes)), x, y + 56, PhonePalette.TEXT_NUMBER);
            s.drawText("e-bike: " + t.formatPrice(t.totalFor(BikeVariant.EBIKE, minutes)), x, y + 67, PhonePalette.TEXT_DIM);
        }
        s.drawText("Return at any dock.", x, y + 86, PhonePalette.TEXT_DIM);
    }

    private static void priceRow(PhoneScreen s, String name, String amount, int y) {
        s.drawText(name, s.left() + 4, y, PhonePalette.TEXT_LIGHT);
        s.drawText(amount, s.right() - 6 - s.textWidth(amount), y, PhonePalette.TEXT_NUMBER);
    }

    /** A per-minute rate without the currency's name, for under a header that already says it. */
    private static String shortRate(ShareTariff t, double perMinute) {
        if (!t.wholeItems) {
            return t.formatRate(perMinute);
        }
        if (perMinute > 0.0D && perMinute < 1.0D) {
            return "1 per " + Math.max(1L, Math.round(1.0D / perMinute)) + " min";
        }
        return Math.round(perMinute) + " per min";
    }

    private static String label(BikeVariant variant) {
        if (variant == BikeVariant.EBIKE) {
            return "E-bikes";
        }
        if (variant == BikeVariant.SCOOTER) {
            return "Scooters";
        }
        if (variant == BikeVariant.SCOOTER_FAST) {
            return "Fast scooters";
        }
        return "Bikes";
    }

    private static StationInfo find(BikeShareSnapshot snap, BlockPos kiosk) {
        if (kiosk == null) {
            return null;
        }
        for (StationInfo st : snap.stations) {
            if (st.kiosk.equals(kiosk)) {
                return st;
            }
        }
        return null;
    }

    // --- Input -----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(PhoneScreen s, int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (view != View.STATION) {
            int y = tabsTop(s);
            int[] edges = tabEdges(s);
            for (int i = 0; i < TABS.length; i++) {
                if (inside(mouseX, mouseY, edges[i], y, edges[i + 1] - 2, y + 12)) {
                    view = TABS[i];
                    BikeShareApi.requestSnapshot();
                    return true;
                }
            }
        }
        BikeShareSnapshot snap = BikeShareApi.latestSnapshot();
        if (view == View.NEARBY && snap != null) {
            int rows = visibleRows(s);
            for (int i = 0; i < rows && scroll + i < snap.stations.size(); i++) {
                int y = contentTop(s) + i * ROW_H;
                if (inside(mouseX, mouseY, s.left() + 2, y, s.right() - 2, y + ROW_H - 2)) {
                    selected = snap.stations.get(scroll + i).kiosk;
                    view = View.STATION;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(PhoneScreen s, int mouseX, int mouseY, int amount) {
        if (view != View.NEARBY) {
            return false;
        }
        scroll = Math.max(0, scroll - amount);
        return true;
    }

    @Override
    public boolean keyTyped(PhoneScreen s, char typedChar, int keyCode) {
        if (view == View.NEARBY && (keyCode == Keyboard.KEY_DOWN || keyCode == Keyboard.KEY_UP)) {
            scroll = Math.max(0, scroll + (keyCode == Keyboard.KEY_DOWN ? 1 : -1));
            return true;
        }
        return false;
    }
}
