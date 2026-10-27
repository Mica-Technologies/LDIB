package com.micatechnologies.minecraft.ldib.block;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * The world-level registry behind the bike-share dock network — the one piece of new infrastructure
 * the docks need (flagged up front in the master plan, Phase 7.1). It tracks the shared fleet: how
 * many bikes are currently sitting in docks and thus available to check out anywhere, plus how many
 * dock points exist. Persisted as {@link WorldSavedData} so "return at any dock" and any future
 * fleet cap survive a reload without scanning the world for docks.
 *
 * <p>This first cut runs an <b>infinite fleet</b>: every check-out spawns a fresh bike and every
 * return removes one, so {@link #available()} is purely informational and check-out is never blocked.
 * A fixed-size fleet (seed N bikes, refuse check-out when none are free) is a drop-in on top of this —
 * the counters it would gate on already live here. All mutation happens server-side from
 * {@link BlockBikeDock}.</p>
 */
public class BikeShareNetwork extends WorldSavedData {

    /** Per-world storage key. Stable — changing it orphans existing saved data. */
    private static final String DATA_NAME = "ldib_bikeshare";

    /** Bikes currently docked and available to check out (the shared free-bike count). */
    private int available;

    /** How many dock points exist in this world. Informational for now; a fleet cap would use it. */
    private int dockCount;

    /**
     * An active rental: the player, when it started (for per-minute billing), the kiosk they checked
     * out at (its station is where they may take a bike), and whether they've taken their bike yet.
     */
    public static final class Session {
        public final UUID player;
        public final long startTick;
        public final BlockPos kiosk;
        public boolean bikeTaken;
        /** World time the bike was actually taken from a dock; the billing clock starts here (0 until taken). */
        public long takenTick;
        /**
         * How full the station the bike was taken from was, counting that bike (see {@code
         * ShareTariff#fill}); {@code -1} until taken, for a standalone dock, and for sessions saved
         * before rebalancing existed — none of which earn pickup credit.
         */
        public double pickupFill = -1.0D;

        public Session(UUID player, long startTick, BlockPos kiosk, boolean bikeTaken, long takenTick) {
            this.player = player;
            this.startTick = startTick;
            this.kiosk = kiosk;
            this.bikeTaken = bikeTaken;
            this.takenTick = takenTick;
        }
    }

    /** Active rentals by player. A player has at most one open session. */
    private final Map<UUID, Session> sessions = new HashMap<>();

    /**
     * One station as last counted: a kiosk (keyed by its lower, master block) and the docks within its
     * radius. Cached so the bike-share API can describe every station in the world, including ones
     * whose chunks are not loaded — before this, a station existed only as a radius scan around a
     * kiosk someone was standing at. Recounted on every event that can change it (see
     * {@link #dockChanged}), never on a timer.
     */
    public static final class StationRecord {
        public final BlockPos kiosk;
        public int bikes;
        public int freeDocks;
        /** Docked bikes per variant, indexed by {@code BikeVariant.ordinal()}. */
        public int[] bikesByVariant = new int[0];
        /** World time of the last recount. */
        public long countedTick;

        StationRecord(BlockPos kiosk) {
            this.kiosk = kiosk;
        }
    }

    /** Every known station, by master kiosk position. Insertion-ordered so listings are stable. */
    private final Map<BlockPos, StationRecord> stations = new LinkedHashMap<>();

    /** One rebalancing credit paid to a rider, for the rewards history. */
    public static final class Credit {
        public final long tick;
        public final double amount;
        /** The kiosk the ride started at, and the one it was returned near; either may be null. */
        public final BlockPos from;
        public final BlockPos to;

        Credit(long tick, double amount, BlockPos from, BlockPos to) {
            this.tick = tick;
            this.amount = amount;
            this.from = from;
            this.to = to;
        }
    }

    /**
     * A rider's bike-share history. SUM keeps no transaction history and the item currency has none
     * to keep, so LDIB records what its rewards screen needs itself.
     */
    public static final class Ledger {
        /** How many recent credits are kept; older ones fall off. */
        public static final int RECENT_LIMIT = 10;

        public int rides;
        public double charged;
        public int rebalancingRides;
        public double credited;
        /** Newest first. */
        public final Deque<Credit> recent = new ArrayDeque<>();
    }

    private final Map<UUID, Ledger> ledgers = new HashMap<>();

    // WorldSavedData is instantiated reflectively via its (String) constructor by getOrLoadData.
    public BikeShareNetwork(String name) {
        super(name);
    }

    /** Fetch (or lazily create) the network for {@code world}. Server-side only. */
    public static BikeShareNetwork get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        BikeShareNetwork data =
            (BikeShareNetwork) storage.getOrLoadData(BikeShareNetwork.class, DATA_NAME);
        if (data == null) {
            data = new BikeShareNetwork(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    // --- Fleet accounting --------------------------------------------------------------------

    /** A dock point was placed. */
    public void registerDock() {
        dockCount++;
        markDirty();
    }

    /** A dock point was removed. Any bike it held is dropped as an item by the caller. */
    public void unregisterDock() {
        dockCount = Math.max(0, dockCount - 1);
        markDirty();
    }

    /** A bike was returned to a dock: it re-joins the available fleet. */
    public void bikeReturned() {
        available++;
        markDirty();
    }

    /** A bike was checked out of a dock: it leaves the available fleet. */
    public void bikeCheckedOut() {
        available = Math.max(0, available - 1);
        markDirty();
    }

    /** Bikes sitting in docks right now, available to check out anywhere. */
    public int available() {
        return available;
    }

    /** Total dock points in this world. */
    public int dockCount() {
        return dockCount;
    }

    // --- Rental sessions ---------------------------------------------------------------------

    /** Start a rental for {@code player}, checked out at {@code kiosk} at {@code startTick}. */
    public void startSession(UUID player, BlockPos kiosk, long startTick) {
        sessions.put(player, new Session(player, startTick, kiosk, false, 0L));
        markDirty();
    }

    /** {@code player}'s active rental, or {@code null} if they have none. */
    public Session getSession(UUID player) {
        return sessions.get(player);
    }

    public boolean hasSession(UUID player) {
        return sessions.containsKey(player);
    }

    /**
     * Record that the player has taken their one bike out of a dock this session, at {@code tick}, from
     * a station {@code pickupFill} full ({@code -1} for no station).
     */
    public void markBikeTaken(UUID player, long tick, double pickupFill) {
        Session s = sessions.get(player);
        if (s != null) {
            s.bikeTaken = true;
            s.takenTick = tick;
            s.pickupFill = pickupFill;
            markDirty();
        }
    }

    /** End {@code player}'s rental and return it (for billing), or {@code null} if none was open. */
    public Session endSession(UUID player) {
        Session s = sessions.remove(player);
        if (s != null) {
            markDirty();
        }
        return s;
    }

    // --- Station registry --------------------------------------------------------------------

    /** Every known station in this world. */
    public Collection<StationRecord> stations() {
        return Collections.unmodifiableCollection(stations.values());
    }

    /** The station keyed by master kiosk {@code kiosk}, or {@code null} if it is not registered. */
    public StationRecord station(BlockPos kiosk) {
        return stations.get(kiosk);
    }

    /**
     * Make sure the kiosk at {@code pos} (either half) is registered, and recount it. Called when a
     * kiosk is placed or used — the latter is how kiosks that predate the registry join it.
     */
    public StationRecord ensureStation(World world, BlockPos pos) {
        BlockPos kiosk = BikeShareStation.master(world, pos);
        StationRecord record = stations.get(kiosk);
        if (record == null) {
            record = new StationRecord(kiosk);
            stations.put(kiosk, record);
        }
        recount(world, record);
        return record;
    }

    /** The kiosk at master position {@code kiosk} is gone. */
    public void removeStation(BlockPos kiosk) {
        if (stations.remove(kiosk) != null) {
            markDirty();
        }
    }

    /**
     * A dock at {@code dockPos} was placed, removed, filled or emptied: recount every station it belongs
     * to. A dock near a kiosk no station knows about yet registers that kiosk, so an existing world's
     * stations fill in as they are used.
     */
    public void dockChanged(World world, BlockPos dockPos) {
        boolean found = false;
        for (StationRecord record : stations.values()) {
            if (BikeShareStation.withinStation(record.kiosk, dockPos)) {
                recount(world, record);
                found = true;
            }
        }
        if (!found) {
            BlockPos kiosk = BikeShareStation.findKioskNear(world, dockPos);
            if (kiosk != null) {
                ensureStation(world, kiosk);
            }
        }
    }

    /**
     * Drop stations whose kiosk is in a loaded chunk but no longer there — removed by something that
     * skips block breaking, such as a world editor. Kiosks in unloaded chunks are left alone: absence
     * of evidence. Returns how many were dropped.
     */
    public int pruneMissing(World world) {
        int before = stations.size();
        stations.values().removeIf(r -> world.isBlockLoaded(r.kiosk) && !BikeShareStation.isKiosk(world, r.kiosk));
        int dropped = before - stations.size();
        if (dropped > 0) {
            markDirty();
        }
        return dropped;
    }

    private void recount(World world, StationRecord record) {
        BikeShareStation.Counts counts = BikeShareStation.count(world, record.kiosk);
        record.bikes = counts.bikes;
        record.freeDocks = counts.freeDocks;
        record.bikesByVariant = counts.bikesByVariant;
        record.countedTick = world.getTotalWorldTime();
        markDirty();
    }

    // --- Rewards ledger ----------------------------------------------------------------------

    /** {@code player}'s history, or {@code null} if they have never finished a ride. */
    public Ledger ledger(UUID player) {
        return ledgers.get(player);
    }

    /** Record a finished ride: what it cost, and any rebalancing credit paid back for it. */
    public void recordRide(UUID player, double charged, double credit, BlockPos from, BlockPos to, long tick) {
        Ledger ledger = ledgers.computeIfAbsent(player, k -> new Ledger());
        ledger.rides++;
        ledger.charged += charged;
        if (credit > 0.0D) {
            ledger.rebalancingRides++;
            ledger.credited += credit;
            ledger.recent.addFirst(new Credit(tick, credit, from, to));
            while (ledger.recent.size() > Ledger.RECENT_LIMIT) {
                ledger.recent.removeLast();
            }
        }
        markDirty();
    }

    // --- Persistence -------------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.available = nbt.getInteger("Available");
        this.dockCount = nbt.getInteger("DockCount");
        sessions.clear();
        NBTTagList list = nbt.getTagList("Sessions", 10); // 10 = compound
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            UUID player = tag.getUniqueId("Player");
            BlockPos kiosk = BlockPos.fromLong(tag.getLong("Kiosk"));
            Session session = new Session(player, tag.getLong("Start"), kiosk,
                tag.getBoolean("BikeTaken"), tag.getLong("Taken"));
            // Absent in sessions saved before rebalancing existed; those stay at -1 (no pickup credit).
            if (tag.hasKey("PickupFill")) {
                session.pickupFill = tag.getDouble("PickupFill");
            }
            sessions.put(player, session);
        }

        // Stations and ledgers are absent from saves that predate them; both simply start empty.
        stations.clear();
        NBTTagList stationList = nbt.getTagList("Stations", 10);
        for (int i = 0; i < stationList.tagCount(); i++) {
            NBTTagCompound tag = stationList.getCompoundTagAt(i);
            StationRecord record = new StationRecord(BlockPos.fromLong(tag.getLong("Kiosk")));
            record.bikes = tag.getInteger("Bikes");
            record.freeDocks = tag.getInteger("Free");
            record.bikesByVariant = tag.getIntArray("ByVariant");
            record.countedTick = tag.getLong("Counted");
            stations.put(record.kiosk, record);
        }
        ledgers.clear();
        NBTTagList ledgerList = nbt.getTagList("Ledgers", 10);
        for (int i = 0; i < ledgerList.tagCount(); i++) {
            NBTTagCompound tag = ledgerList.getCompoundTagAt(i);
            Ledger ledger = new Ledger();
            ledger.rides = tag.getInteger("Rides");
            ledger.charged = tag.getDouble("Charged");
            ledger.rebalancingRides = tag.getInteger("RebalancingRides");
            ledger.credited = tag.getDouble("Credited");
            NBTTagList recent = tag.getTagList("Recent", 10);
            for (int j = 0; j < recent.tagCount(); j++) {
                NBTTagCompound c = recent.getCompoundTagAt(j);
                ledger.recent.addLast(new Credit(c.getLong("Tick"), c.getDouble("Amount"),
                    c.hasKey("From") ? BlockPos.fromLong(c.getLong("From")) : null,
                    c.hasKey("To") ? BlockPos.fromLong(c.getLong("To")) : null));
            }
            ledgers.put(tag.getUniqueId("Player"), ledger);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("Available", this.available);
        nbt.setInteger("DockCount", this.dockCount);
        NBTTagList list = new NBTTagList();
        for (Session s : sessions.values()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("Player", s.player);
            tag.setLong("Start", s.startTick);
            tag.setLong("Taken", s.takenTick);
            tag.setLong("Kiosk", s.kiosk.toLong());
            tag.setBoolean("BikeTaken", s.bikeTaken);
            tag.setDouble("PickupFill", s.pickupFill);
            list.appendTag(tag);
        }
        nbt.setTag("Sessions", list);

        NBTTagList stationList = new NBTTagList();
        for (StationRecord record : stations.values()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("Kiosk", record.kiosk.toLong());
            tag.setInteger("Bikes", record.bikes);
            tag.setInteger("Free", record.freeDocks);
            tag.setIntArray("ByVariant", record.bikesByVariant);
            tag.setLong("Counted", record.countedTick);
            stationList.appendTag(tag);
        }
        nbt.setTag("Stations", stationList);

        NBTTagList ledgerList = new NBTTagList();
        for (Map.Entry<UUID, Ledger> entry : ledgers.entrySet()) {
            Ledger ledger = entry.getValue();
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("Player", entry.getKey());
            tag.setInteger("Rides", ledger.rides);
            tag.setDouble("Charged", ledger.charged);
            tag.setInteger("RebalancingRides", ledger.rebalancingRides);
            tag.setDouble("Credited", ledger.credited);
            NBTTagList recent = new NBTTagList();
            for (Credit credit : ledger.recent) {
                NBTTagCompound c = new NBTTagCompound();
                c.setLong("Tick", credit.tick);
                c.setDouble("Amount", credit.amount);
                if (credit.from != null) {
                    c.setLong("From", credit.from.toLong());
                }
                if (credit.to != null) {
                    c.setLong("To", credit.to.toLong());
                }
                recent.appendTag(c);
            }
            tag.setTag("Recent", recent);
            ledgerList.appendTag(tag);
        }
        nbt.setTag("Ledgers", ledgerList);
        return nbt;
    }
}
