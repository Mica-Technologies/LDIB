package com.micatechnologies.minecraft.ldib;

import com.micatechnologies.minecraft.ldib.block.BlockBikeDock;
import com.micatechnologies.minecraft.ldib.block.BlockBikeRack;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * The forgiving "grab / dock" action behind the keybind (see {@code PacketGrabBike}). One key does the
 * intuitive thing from context, so players don't have to discover right-click gestures:
 *
 * <ul>
 *   <li><b>Riding, near a rack or dock</b> → park the bike there (lock to the rack / clip into the
 *       dock) — you just have to be <i>near</i> it, no precise aiming.</li>
 *   <li><b>Riding, not near a station</b> → hop off and pocket the bike as an item.</li>
 *   <li><b>On foot, looking at or standing near a placed bike</b> → pick it up into your inventory.</li>
 * </ul>
 *
 * <p>Server-authoritative. Reuses {@link BlockBikeRack#tryLockRidden}/{@link BlockBikeDock#tryDockRidden}
 * for the actual park, and {@link EntityBike#giveAsItem} for the pick-up.</p>
 */
public final class RideableActions {

    /** How close (blocks) a rack/dock must be to park at it when you press the key while riding. */
    private static final double DOCK_RADIUS = 3.0D;
    /** How close a placed bike must be to grab it on foot. */
    private static final double GRAB_RADIUS = 3.0D;

    private RideableActions() {
        throw new AssertionError("No instances.");
    }

    /** Run the grab/dock action for {@code player} from their current context. Server-side. */
    public static void grabOrDock(EntityPlayer player) {
        World world = player.world;
        if (world.isRemote) {
            return;
        }
        if (player.getRidingEntity() instanceof EntityBike) {
            EntityBike bike = (EntityBike) player.getRidingEntity();
            if (parkAtNearestStation(world, player)) {
                return;
            }
            bike.giveAsItem(player);
            bike.setDead();
            status(player, "Picked up your bike.");
            return;
        }
        EntityBike target = nearestPlacedBike(world, player);
        if (target != null) {
            target.giveAsItem(player);
            target.setDead();
            status(player, "Picked up the bike.");
            return;
        }
        status(player, "Nothing to grab — look at a placed bike, or ride one up to a rack or dock.");
    }

    /** Try to park the ridden bike at the nearest rack/dock within {@link #DOCK_RADIUS}; true if parked. */
    private static boolean parkAtNearestStation(World world, EntityPlayer player) {
        BlockPos origin = new BlockPos(player.posX, player.posY, player.posZ);
        int r = (int) Math.ceil(DOCK_RADIUS);
        BlockPos best = null;
        double bestSq = DOCK_RADIUS * DOCK_RADIUS;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.setPos(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    Block block = world.getBlockState(p).getBlock();
                    if (!(block instanceof BlockBikeDock) && !(block instanceof BlockBikeRack)) {
                        continue;
                    }
                    double sq = player.getDistanceSq(p);
                    if (sq <= bestSq) {
                        // Only accept it if it can actually take the bike (free dock / rack with room).
                        BlockPos here = p.toImmutable();
                        if (parkAt(world, here, player, block)) {
                            return true;
                        }
                        best = here; // remembered only to note a station was near even if full
                    }
                }
            }
        }
        if (best != null) {
            status(player, "That station is full.");
            return true; // handled (don't fall through to pocketing the bike)
        }
        return false;
    }

    private static boolean parkAt(World world, BlockPos pos, EntityPlayer player, Block block) {
        if (block instanceof BlockBikeDock) {
            return ((BlockBikeDock) block).tryDockRidden(world, pos, player);
        }
        if (block instanceof BlockBikeRack) {
            return ((BlockBikeRack) block).tryLockRidden(world, pos, player);
        }
        return false;
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
