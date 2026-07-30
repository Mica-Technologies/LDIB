package com.micatechnologies.minecraft.ldib.block;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.api.BikeShareBilling;
import com.micatechnologies.minecraft.ldib.api.ShareTariff;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * A bike-share <b>station</b> is a {@link BlockBikeKiosk} plus every {@link BlockBikeDock} within
 * {@link LdibConfig#shareStationRadius} of it — proximity grouping, no rigid multiblock (the master
 * plan's "a station is just adjacent dock points"). This holds the station/session rules shared by the
 * kiosk, the docks and the network packet handler: who may check out, which docks belong to which
 * kiosk, and the live bike/dock counts a kiosk screen shows.
 *
 * <p>All server-authoritative. The count helpers are pure block reads, so a client screen can call
 * them against its own loaded world too.</p>
 */
public final class BikeShareStation {

    private BikeShareStation() {
        throw new AssertionError("No instances.");
    }

    public static int radius() {
        return LdibConfig.shareStationRadius;
    }

    public static boolean isKiosk(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof BlockBikeKiosk;
    }

    public static boolean isDock(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof BlockBikeDock;
    }

    /** Whether {@code dock} is within a kiosk's station radius (a cube around the kiosk). */
    public static boolean withinStation(BlockPos kiosk, BlockPos dock) {
        int r = radius();
        return Math.abs(kiosk.getX() - dock.getX()) <= r
            && Math.abs(kiosk.getY() - dock.getY()) <= r
            && Math.abs(kiosk.getZ() - dock.getZ()) <= r;
    }

    /** The nearest kiosk within radius of {@code dock}, or {@code null} if the dock is standalone. */
    public static BlockPos findKioskNear(World world, BlockPos dock) {
        int r = radius();
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.setPos(dock.getX() + dx, dock.getY() + dy, dock.getZ() + dz);
                    if (isKiosk(world, p)) {
                        double sq = dock.distanceSq(p);
                        if (sq < bestSq) {
                            bestSq = sq;
                            best = p.toImmutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    /** How full a station is: bikes waiting to be taken, and empty docks to return one to. */
    public static final class Counts {
        public final int bikes;
        public final int freeDocks;

        Counts(int bikes, int freeDocks) {
            this.bikes = bikes;
            this.freeDocks = freeDocks;
        }
    }

    /**
     * Both counts for the station anchored by {@code kiosk}, in one pass.
     *
     * <p><b>Walks the world's loaded tile entities rather than the blocks around the kiosk</b>, which
     * is the same answer for a fraction of the work. The old form was a {@code (2r+1)³} cube of
     * {@code getBlockState} + {@code getTileEntity} calls, run <i>twice</i> (once per count) — ~9.8k
     * block reads at the default radius of 8, and {@code shareStationRadius} is configurable up to
     * <b>64</b>, where one call is 2.1 million lookups. {@code GuiKiosk} was calling both of them from
     * {@code drawScreen}, i.e. every rendered frame, so opening a kiosk on a large-radius server was a
     * hard client freeze.</p>
     *
     * <p>Docks are tile entities, so the loaded-TE list is the smallest set that can possibly contain
     * them; the cost is now proportional to how many tile entities exist nearby rather than to the
     * cube of the radius. Semantics are unchanged — {@link #withinStation} applies exactly the cube
     * test the scan did, and a TE is in that list precisely when its chunk is loaded, which is when
     * the block scan could have seen it.</p>
     */
    public static Counts count(World world, BlockPos kiosk) {
        int bikes = 0;
        int freeDocks = 0;
        // Indexed rather than for-each, and re-reading size() each step: this runs from a GUI on the
        // client and from block interaction on the server, and neither wants to throw if something
        // removes a tile entity underneath it.
        java.util.List<TileEntity> loaded = world.loadedTileEntityList;
        for (int i = 0; i < loaded.size(); i++) {
            TileEntity te = loaded.get(i);
            if (!(te instanceof TileEntityBikeDock) || te.isInvalid()) {
                continue;
            }
            if (!withinStation(kiosk, te.getPos())) {
                continue;
            }
            if (((TileEntityBikeDock) te).isOccupied()) {
                bikes++;
            } else {
                freeDocks++;
            }
        }
        return new Counts(bikes, freeDocks);
    }

    /** Bikes currently docked (available to take) at the station anchored by {@code kiosk}. */
    public static int countBikesAvailable(World world, BlockPos kiosk) {
        return count(world, kiosk).bikes;
    }

    /** Free (empty) docks at the station anchored by {@code kiosk} (spots to return a bike). */
    public static int countFreeDocks(World world, BlockPos kiosk) {
        return count(world, kiosk).freeDocks;
    }

    /** Start a rental for {@code player} at {@code kiosk}: validate, bill-gate, open a session, notify. */
    public static void checkOut(EntityPlayer player, BlockPos kiosk) {
        World world = player.world;
        if (world.isRemote || !isKiosk(world, kiosk)) {
            return;
        }
        BikeShareNetwork network = BikeShareNetwork.get(world);
        if (network.hasSession(player.getUniqueID())) {
            status(player, "You already have a bike-share session running.");
            return;
        }
        // Don't open a (billable) session at a station with nothing to ride — the fleet is bounded by
        // the bikes physically docked here, so read that ground truth rather than a separate counter
        // that could drift. This is the "can't check out when none free" rule from the master plan.
        if (countBikesAvailable(world, kiosk) <= 0) {
            status(player, "No bikes available at this station right now — try another station.");
            return;
        }
        ShareTariff tariff = BikeShareBilling.activeTariff();
        if (!BikeShareBilling.active().canCheckOut(player)) {
            status(player, String.format(
                "You can't start a rental right now — you need at least %.2f to cover the unlock fee.",
                tariff.unlockFee));
            return;
        }
        network.startSession(player.getUniqueID(), kiosk, world.getTotalWorldTime());
        if (tariff.isPaid()) {
            status(player, String.format(
                "Checked out (unlock fee %.2f) — take a bike or scooter from any dock at this station.",
                tariff.unlockFee));
        } else {
            status(player, "Checked out — take a bike or scooter from any dock at this station.");
        }
    }

    static void status(EntityPlayer player, String message) {
        player.sendStatusMessage(new TextComponentString(message), false);
    }
}
