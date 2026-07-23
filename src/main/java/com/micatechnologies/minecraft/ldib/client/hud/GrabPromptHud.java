package com.micatechnologies.minecraft.ldib.client.hud;

import com.micatechnologies.minecraft.ldib.block.BlockBikeDock;
import com.micatechnologies.minecraft.ldib.block.BlockBikeRack;
import com.micatechnologies.minecraft.ldib.client.LdibKeyHandler;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
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
        // Only while actually riding a rideable — never when the player is on foot.
        if (!(mc.player.getRidingEntity() instanceof EntityBike)) {
            return null;
        }
        EntityBike bike = (EntityBike) mc.player.getRidingEntity();
        String key = LdibKeyHandler.GRAB.getDisplayName();
        int kind = nearestStationKind(mc.world, bike);
        // Match the bike's kind to the station: share bikes return to docks, personal bikes lock to racks.
        if (bike.isShare() && kind == DOCK) {
            return "Press " + key + " to return to the dock";
        }
        if (!bike.isShare() && kind == RACK) {
            return "Press " + key + " to lock to the rack";
        }
        return null;
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
