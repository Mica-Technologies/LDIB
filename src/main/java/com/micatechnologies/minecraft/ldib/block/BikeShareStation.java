package com.micatechnologies.minecraft.ldib.block;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.api.BikeShareBilling;
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

    /** Bikes currently docked (available to take) at the station anchored by {@code kiosk}. */
    public static int countBikesAvailable(World world, BlockPos kiosk) {
        return countDocks(world, kiosk, true);
    }

    /** Free (empty) docks at the station anchored by {@code kiosk} (spots to return a bike). */
    public static int countFreeDocks(World world, BlockPos kiosk) {
        return countDocks(world, kiosk, false);
    }

    private static int countDocks(World world, BlockPos kiosk, boolean occupied) {
        int r = radius();
        int count = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.setPos(kiosk.getX() + dx, kiosk.getY() + dy, kiosk.getZ() + dz);
                    if (isDock(world, p)) {
                        TileEntity te = world.getTileEntity(p);
                        if (te instanceof TileEntityBikeDock
                            && ((TileEntityBikeDock) te).isOccupied() == occupied) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
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
        if (!BikeShareBilling.active().canCheckOut(player)) {
            status(player, "You can't start a rental right now (insufficient balance).");
            return;
        }
        network.startSession(player.getUniqueID(), kiosk, world.getTotalWorldTime());
        status(player, "Checked out — take a bike or scooter from any dock at this station.");
    }

    static void status(EntityPlayer player, String message) {
        player.sendStatusMessage(new TextComponentString(message), false);
    }
}
