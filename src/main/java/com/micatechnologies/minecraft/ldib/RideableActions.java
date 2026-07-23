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
 * intuitive thing from context, whether you're riding a bike or standing next to a placed one:
 *
 * <ul>
 *   <li><b>The bike (ridden, or the nearest placed one) is near a rack/dock</b> → return/lock it there
 *       — you just have to be near the station, no precise aiming or right-clicking.</li>
 *   <li><b>No station near the bike</b> → pick it up into your inventory (hopping off if you're on it).</li>
 * </ul>
 *
 * <p>Server-authoritative. Reuses {@link BlockBikeRack#tryLockBike}/{@link BlockBikeDock#tryDockBike}
 * for the park (they work on any bike, ridden or placed) and {@link EntityBike#giveAsItem} for pick-up.</p>
 */
public final class RideableActions {

    /** How close (blocks) a rack/dock must be to the bike to return/park it there. */
    private static final double DOCK_RADIUS = 4.0D;
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
            status(player, "Nothing here — look at or stand near a bike, or ride one up to a rack or dock.");
            return;
        }
        // First choice: return/park the bike at the nearest rack or dock.
        if (parkAtNearestStation(world, player, bike)) {
            return;
        }
        // No station near the bike → pocket it.
        bike.giveAsItem(player);
        bike.setDead();
        status(player, riding ? "Picked up your bike." : "Picked up the bike.");
    }

    /** Return/lock {@code bike} at the nearest available rack/dock within {@link #DOCK_RADIUS}. */
    private static boolean parkAtNearestStation(World world, EntityPlayer player, EntityBike bike) {
        List<BlockPos> stations = new ArrayList<>();
        int r = (int) Math.ceil(DOCK_RADIUS);
        BlockPos origin = new BlockPos(bike.posX, bike.posY, bike.posZ);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.setPos(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    Block block = world.getBlockState(p).getBlock();
                    if ((block instanceof BlockBikeDock || block instanceof BlockBikeRack)
                        && p.distanceSq(bike.posX, bike.posY, bike.posZ) <= DOCK_RADIUS * DOCK_RADIUS) {
                        stations.add(p.toImmutable());
                    }
                }
            }
        }
        if (stations.isEmpty()) {
            return false;
        }
        // Try nearest-first; tryDock/LockBike return false when occupied/full, so we skip to the next.
        stations.sort(Comparator.comparingDouble(s -> s.distanceSq(bike.posX, bike.posY, bike.posZ)));
        for (BlockPos pos : stations) {
            Block block = world.getBlockState(pos).getBlock();
            if (block instanceof BlockBikeDock
                && ((BlockBikeDock) block).tryDockBike(world, pos, bike, player)) {
                return true;
            }
            if (block instanceof BlockBikeRack
                && ((BlockBikeRack) block).tryLockBike(world, pos, bike, player)) {
                return true;
            }
        }
        // A station was near but had no room; report it and don't fall through to pocketing.
        status(player, "The nearest rack or dock is full.");
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
