package com.micatechnologies.minecraft.ldib.client;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Widens the rider's field of view with speed, the way sprinting does on foot.
 *
 * <p>Before this, the camera lean was the only thing telling a rider how fast they were going, and
 * it only says anything in a turn. A slightly wider view at speed is the cue every racing game uses,
 * and vanilla already has the machinery: {@link FOVUpdateEvent} is the hook sprinting and bows go
 * through, and the renderer eases toward whatever it returns, so a change in speed never snaps the
 * view.</p>
 *
 * <p>Client-only, reached from {@code LdibClientProxy}. {@link LdibConfig#speedFovStrength} sets
 * how much (0 turns it off), and it is never synced: like the camera lean it is a matter of taste.</p>
 */
@SideOnly(Side.CLIENT)
public final class RiderFov {

    /**
     * Speed, blocks/second, at which the full {@link LdibConfig#speedFovStrength} applies — about the
     * fast scooter's top speed. Below it the widening scales linearly, so a crawl changes nothing.
     */
    private static final double FULL_EFFECT_SPEED = 10.0D;

    @SubscribeEvent
    public void onFovUpdate(FOVUpdateEvent event) {
        if (LdibConfig.speedFovStrength <= 0.0D
            || !(event.getEntity().getRidingEntity() instanceof EntityBike)) {
            return;
        }
        EntityBike bike = (EntityBike) event.getEntity().getRidingEntity();
        double fraction = Math.min(1.0D, Math.abs(bike.speed()) / FULL_EFFECT_SPEED);
        event.setNewfov(event.getNewfov() * (float) (1.0D + LdibConfig.speedFovStrength * fraction));
    }
}
