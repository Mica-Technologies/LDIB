package com.micatechnologies.minecraft.ldib.client.sound;

import com.micatechnologies.minecraft.ldib.LdibSounds;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Drives the LDIB ride sounds for the local player: keeps one looping {@link BikeRideSound} alive
 * while they're riding a bike (it self-updates volume/pitch from speed), and fires a one-shot brake
 * blip the moment they start braking at speed. Client-only, installed from {@link
 * com.micatechnologies.minecraft.ldib.LdibClientProxy}.
 */
public class RideableSoundHandler {

    private BikeRideSound rideSound;
    private boolean wasBraking;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || mc.world == null || !(player.getRidingEntity() instanceof EntityBike)) {
            rideSound = null;
            wasBraking = false;
            return;
        }
        EntityBike bike = (EntityBike) player.getRidingEntity();

        // Keep the ride loop going; the sound ends itself on dismount/death, so restart when needed.
        if (rideSound == null || rideSound.isDonePlaying()) {
            rideSound = new BikeRideSound(bike, player);
            mc.getSoundHandler().playSound(rideSound);
        }

        boolean braking = bike.isBraking();
        if (braking && !wasBraking && Math.abs(bike.speed()) > 0.5D) {
            mc.world.playSound(player, bike.getPosition(), LdibSounds.BRAKE,
                SoundCategory.NEUTRAL, 0.4F, 1.0F);
        }
        wasBraking = braking;
    }
}
