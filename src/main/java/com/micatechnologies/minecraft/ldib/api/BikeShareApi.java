package com.micatechnologies.minecraft.ldib.api;

import com.micatechnologies.minecraft.ldib.block.BikeShareNetwork;
import com.micatechnologies.minecraft.ldib.network.LdibNetwork;
import com.micatechnologies.minecraft.ldib.network.PacketBikeShareRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * The bike-share network, for other mods: where the stations are, what is docked at each, the prices
 * and rebalancing policy, and a rider's own rental and history. Documented for integrators in
 * {@code docs/design/BIKESHARE_API.md}.
 *
 * <p><b>Server side</b>, call {@link #stations} or {@link #snapshotFor} from the server thread.
 * <b>Client side</b> — a phone app, a HUD — call {@link #requestSnapshot()} and read
 * {@link #latestSnapshot()} once it arrives; LDIB answers on its own network channel, and limits how
 * often each player may ask.</p>
 *
 * <p>Everything returned is an immutable copy. Nothing here changes the network.</p>
 */
public final class BikeShareApi {

    /** API version; bumped only when something here changes incompatibly. */
    public static final int VERSION = 1;

    private static volatile BikeShareSnapshot latest;

    private BikeShareApi() {
        throw new AssertionError("No instances.");
    }

    // --- Server side ---------------------------------------------------------------------------

    /** Every known station in {@code world}'s dimension, as last counted. Server thread only. */
    public static List<StationInfo> stations(World world) {
        List<StationInfo> out = new ArrayList<>();
        for (BikeShareNetwork.StationRecord record : BikeShareNetwork.get(world).stations()) {
            out.add(new StationInfo(record.kiosk, record.bikes, record.freeDocks, record.bikesByVariant,
                record.countedTick));
        }
        return out;
    }

    /** {@code player}'s rental and history. Server thread only. */
    public static RiderStatus riderStatus(EntityPlayer player) {
        BikeShareNetwork network = BikeShareNetwork.get(player.world);
        BikeShareNetwork.Session session = network.getSession(player.getUniqueID());
        BikeShareNetwork.Ledger ledger = network.ledger(player.getUniqueID());
        List<RiderStatus.CreditEntry> recent = new ArrayList<>();
        if (ledger != null) {
            for (BikeShareNetwork.Credit credit : ledger.recent) {
                recent.add(new RiderStatus.CreditEntry(credit.tick, credit.amount, credit.from, credit.to));
            }
        }
        long clock = session == null ? 0L : (session.takenTick > 0L ? session.takenTick : session.startTick);
        return new RiderStatus(session != null, session != null && session.bikeTaken, clock,
            session != null ? session.kiosk : null,
            ledger != null ? ledger.rides : 0, ledger != null ? ledger.charged : 0.0D,
            ledger != null ? ledger.rebalancingRides : 0, ledger != null ? ledger.credited : 0.0D, recent);
    }

    /** Everything an app shows, for {@code player}: their dimension's stations nearest first. */
    public static BikeShareSnapshot snapshotFor(EntityPlayer player) {
        List<StationInfo> stations = stations(player.world);
        double px = player.posX;
        double pz = player.posZ;
        stations.sort(Comparator.comparingDouble(
            s -> StationMath.distance(px, pz, s.kiosk.getX() + 0.5D, s.kiosk.getZ() + 0.5D)));
        return new BikeShareSnapshot(player.world.getTotalWorldTime(), player.dimension,
            BikeShareBilling.activeTariff(), BikeShareBilling.currencyName(), stations, riderStatus(player));
    }

    // --- Client side ---------------------------------------------------------------------------

    /**
     * Ask the server for a fresh snapshot. Client only. The answer lands in {@link #latestSnapshot}
     * a moment later; asking again before then, or more than a few times a second, is harmless — the
     * server drops requests that come too fast.
     */
    public static void requestSnapshot() {
        LdibNetwork.CHANNEL.sendToServer(new PacketBikeShareRequest());
    }

    /** The last snapshot the server sent this client, or {@code null} if none has arrived yet. */
    public static BikeShareSnapshot latestSnapshot() {
        return latest;
    }

    /** Called by LDIB's packet handler when a snapshot arrives. Not for other mods. */
    public static void receive(BikeShareSnapshot snapshot) {
        latest = snapshot;
    }

    /** Forget the cached snapshot (on disconnect), so a different server's network never shows. */
    public static void clear() {
        latest = null;
    }

    // --- Formatting ----------------------------------------------------------------------------

    /**
     * An amount in the snapshot's currency, for display: "3 emeralds" for an item currency (whole
     * numbers), "2.50" for an economy's own currency.
     */
    public static String formatAmount(BikeShareSnapshot snapshot, double amount) {
        if (snapshot == null || snapshot.currency.isEmpty()) {
            return String.format("%.2f", amount);
        }
        long whole = Math.round(amount);
        return whole + " " + snapshot.currency;
    }
}
