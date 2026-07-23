package com.micatechnologies.minecraft.ldib;

import com.micatechnologies.minecraft.ldib.block.BlockBikeDock;
import com.micatechnologies.minecraft.ldib.block.BlockBikeRack;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * The forgiving "grab / dock" action behind the keybind (see {@code PacketGrabBike}). One key does the
 * intuitive thing from context, whether you're riding a bike or standing next to a placed one, and it
 * respects the public/personal split:
 *
 * <ul>
 *   <li><b>A public bike-share bike</b> → return it to the nearest dock (never pocketed — it belongs
 *       to the fleet).</li>
 *   <li><b>A personal bike near a rack</b> → lock it there.</li>
 *   <li><b>A personal bike with no rack near</b> → pick it up into your inventory.</li>
 * </ul>
 *
 * <p>Server-authoritative. Reuses {@link BlockBikeRack#tryLockBike}/{@link BlockBikeDock#tryDockBike}
 * (which themselves enforce the share/personal split) and {@link EntityBike#giveAsItem}.</p>
 */
public final class RideableActions {

    /** How close (blocks) a rack/dock must be to the bike to return/park it there. */
    private static final double STATION_RADIUS = 4.0D;
    /** How close a placed bike must be to the player to act on it when on foot. */
    private static final double GRAB_RADIUS = 3.5D;

    private RideableActions() {
        throw new AssertionError("No instances.");
    }

    /** Run the grab/dock action for {@code player} from their current context. Server-side. */
    public static void grabOrDock(EntityPlayer player) {
        World world = player.world;
        if (world.isRemote) {
            return;
        }
        boolean riding = player.getRidingEntity() instanceof EntityBike;
        EntityBike bike = riding ? (EntityBike) player.getRidingEntity() : nearestPlacedBike(world, player);
        if (bike == null) {
            status(player, "Nothing here — ride a bike, or stand next to a placed one.");
            return;
        }
        if (bike.isShare()) {
            // Public fleet bike: return to a dock only; never pocketed.
            if (park(world, player, bike, true)) {
                return;
            }
            status(player, "Ride this bike-share bike to a dock to return it.");
            return;
        }
        // Personal bike: lock to a rack if one is near, otherwise pocket it.
        if (park(world, player, bike, false)) {
            return;
        }
        bike.giveAsItem(player);
        bike.setDead();
        status(player, riding ? "Picked up your bike." : "Picked up the bike.");
    }

    /**
     * Park {@code bike} at the nearest matching station within {@link #STATION_RADIUS}: a dock if
     * {@code wantDock}, else a rack. Returns true if a station of that type was near (parked, or
     * reported full) — false only if no such station is nearby (so the caller can fall back).
     */
    private static boolean park(World world, EntityPlayer player, EntityBike bike, boolean wantDock) {
        List<BlockPos> stations = new ArrayList<>();
        int r = (int) Math.ceil(STATION_RADIUS);
        BlockPos origin = new BlockPos(bike.posX, bike.posY, bike.posZ);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.setPos(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    Block block = world.getBlockState(p).getBlock();
                    boolean match = wantDock ? block instanceof BlockBikeDock : block instanceof BlockBikeRack;
                    if (match && p.distanceSq(bike.posX, bike.posY, bike.posZ) <= STATION_RADIUS * STATION_RADIUS) {
                        stations.add(p.toImmutable());
                    }
                }
            }
        }
        if (stations.isEmpty()) {
            return false;
        }
        stations.sort(Comparator.comparingDouble(s -> s.distanceSq(bike.posX, bike.posY, bike.posZ)));
        for (BlockPos pos : stations) {
            Block block = world.getBlockState(pos).getBlock();
            if (wantDock && ((BlockBikeDock) block).tryDockBike(world, pos, bike, player)) {
                return true;
            }
            if (!wantDock && ((BlockBikeRack) block).tryLockBike(world, pos, bike, player)) {
                return true;
            }
        }
        status(player, wantDock ? "The nearest dock is full." : "The nearest rack is full.");
        return true;
    }

    /** The nearest un-ridden placed bike within {@link #GRAB_RADIUS} of {@code player}, or null. */
    private static EntityBike nearestPlacedBike(World world, EntityPlayer player) {
        AxisAlignedBB box = player.getEntityBoundingBox().grow(GRAB_RADIUS);
        List<EntityBike> bikes = world.getEntitiesWithinAABB(EntityBike.class, box);
        EntityBike best = null;
        double bestSq = GRAB_RADIUS * GRAB_RADIUS;
        for (EntityBike bike : bikes) {
            if (bike.isBeingRidden() || bike.isDead) {
                continue;
            }
            double sq = player.getDistanceSq(bike);
            if (sq <= bestSq) {
                bestSq = sq;
                best = bike;
            }
        }
        return best;
    }

    private static void status(EntityPlayer player, String message) {
        player.sendStatusMessage(new TextComponentString(message), true);
    }
}
