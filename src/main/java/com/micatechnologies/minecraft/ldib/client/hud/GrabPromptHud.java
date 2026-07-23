package com.micatechnologies.minecraft.ldib.client.hud;

import com.micatechnologies.minecraft.ldib.block.BlockBikeDock;
import com.micatechnologies.minecraft.ldib.block.BlockBikeRack;
import com.micatechnologies.minecraft.ldib.client.LdibKeyHandler;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * A contextual hint drawn above the hotbar telling the player they can press the grab/dock key —
 * "Press G to return to the dock" / "…lock to the rack" / "…pick up the bike" — whenever the relevant
 * action is available (a bike they're riding, or one placed right next to them, is near a station).
 * Makes the {@link LdibKeyHandler} keybind discoverable instead of hidden in the Controls menu.
 *
 * <p>Client-only. The station scan is throttled (a few times a second, not every frame) and the bound
 * key name is read live from the keybind, so a rebind is reflected immediately.</p>
 */
public class GrabPromptHud {

    private static final double BIKE_RADIUS = 3.5D;
    private static final int STATION_RADIUS = 4;

    private String cachedPrompt;
    private int frame;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.HOTBAR) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (frame++ % 5 == 0) { // recompute a few times a second, not every frame
            cachedPrompt = computePrompt(mc);
        }
        if (cachedPrompt == null) {
            return;
        }
        ScaledResolution res = new ScaledResolution(mc);
        int x = res.getScaledWidth() / 2 - mc.fontRenderer.getStringWidth(cachedPrompt) / 2;
        int y = res.getScaledHeight() - 68; // just above the ride speed HUD
        mc.fontRenderer.drawStringWithShadow(cachedPrompt, x, y, 0xFFEE66);
    }

    private String computePrompt(Minecraft mc) {
        EntityPlayer player = mc.player;
        World world = mc.world;
        boolean riding = player.getRidingEntity() instanceof EntityBike;
        EntityBike bike = riding ? (EntityBike) player.getRidingEntity() : nearestBike(world, player);
        if (bike == null) {
            return null;
        }
        String key = LdibKeyHandler.GRAB.getDisplayName();
        int kind = nearestStationKind(world, bike);
        if (kind == DOCK) {
            return "Press " + key + " to return to the dock";
        }
        if (kind == RACK) {
            return "Press " + key + " to lock to the rack";
        }
        // No station nearby: offer pick-up only when on foot (while riding, the ride HUD suffices).
        return riding ? null : "Press " + key + " to pick up the bike";
    }

    private static EntityBike nearestBike(World world, EntityPlayer player) {
        AxisAlignedBB box = player.getEntityBoundingBox().grow(BIKE_RADIUS);
        List<EntityBike> bikes = world.getEntitiesWithinAABB(EntityBike.class, box);
        EntityBike best = null;
        double bestSq = BIKE_RADIUS * BIKE_RADIUS;
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

    private static final int NONE = 0, DOCK = 1, RACK = 2;

    /** Which station kind, if any, is nearest to {@code bike} within {@link #STATION_RADIUS}. */
    private static int nearestStationKind(World world, EntityBike bike) {
        int kind = NONE;
        double bestSq = (STATION_RADIUS + 1) * (STATION_RADIUS + 1);
        BlockPos origin = new BlockPos(bike.posX, bike.posY, bike.posZ);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -STATION_RADIUS; dx <= STATION_RADIUS; dx++) {
            for (int dy = -STATION_RADIUS; dy <= STATION_RADIUS; dy++) {
                for (int dz = -STATION_RADIUS; dz <= STATION_RADIUS; dz++) {
                    p.setPos(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    Block block = world.getBlockState(p).getBlock();
                    int here = block instanceof BlockBikeDock ? DOCK
                        : block instanceof BlockBikeRack ? RACK : NONE;
                    if (here == NONE) {
                        continue;
                    }
                    double sq = p.distanceSq(bike.posX, bike.posY, bike.posZ);
                    if (sq < bestSq) {
                        bestSq = sq;
                        kind = here;
                    }
                }
            }
        }
        return kind;
    }
}
