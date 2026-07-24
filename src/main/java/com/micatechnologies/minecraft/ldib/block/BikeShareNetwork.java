package com.micatechnologies.minecraft.ldib.block;

import java.util.HashMap;
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

    /** Record that the player has taken their one bike out of a dock this session, at {@code tick}. */
    public void markBikeTaken(UUID player, long tick) {
        Session s = sessions.get(player);
        if (s != null) {
            s.bikeTaken = true;
            s.takenTick = tick;
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
            sessions.put(player, new Session(player, tag.getLong("Start"), kiosk,
                tag.getBoolean("BikeTaken"), tag.getLong("Taken")));
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
            list.appendTag(tag);
        }
        nbt.setTag("Sessions", list);
        return nbt;
    }
}
