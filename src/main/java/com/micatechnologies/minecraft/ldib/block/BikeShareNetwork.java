package com.micatechnologies.minecraft.ldib.block;

import net.minecraft.nbt.NBTTagCompound;
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

    // --- Persistence -------------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.available = nbt.getInteger("Available");
        this.dockCount = nbt.getInteger("DockCount");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("Available", this.available);
        nbt.setInteger("DockCount", this.dockCount);
        return nbt;
    }
}
