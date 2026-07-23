package com.micatechnologies.minecraft.ldib.client.hud;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * A tiny speed readout drawn above the hotbar while the local player is riding a bike. Client-only —
 * reached exclusively from {@link com.micatechnologies.minecraft.ldib.LdibClientProxy}, never from
 * common code. Config-gated ({@link LdibConfig#enableRideHud}) and never synced; pure convenience.
 */
public class RideHud {

    /**
     * Exact metres-per-second → miles-per-hour factor, copied verbatim from the sibling SUM mod's
     * {@code HudFormat.MPS_TO_MPH} so both mods report the same number. A block is one metre, and
     * {@link EntityBike#speed()} is already in blocks/second (= m/s), so mph is just this factor
     * applied directly — SUM derives the same value as {@code perTick * 20 * MPS_TO_MPH}.
     */
    private static final double MPS_TO_MPH = 2.2369362920544;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.HOTBAR || !LdibConfig.enableRideHud) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null) {
            return;
        }
        Entity vehicle = player.getRidingEntity();
        if (!(vehicle instanceof EntityBike)) {
            return;
        }

        double blocksPerSecond = ((EntityBike) vehicle).speed();
        double mph = blocksPerSecond * MPS_TO_MPH;
        String text = String.format("%.1f mph  (%.1f blocks/s)", mph, blocksPerSecond);
        ScaledResolution res = new ScaledResolution(mc);
        int x = res.getScaledWidth() / 2 - mc.fontRenderer.getStringWidth(text) / 2;
        int y = res.getScaledHeight() - 55;
        mc.fontRenderer.drawStringWithShadow(text, x, y, 0xFFFFFF);
    }
}
