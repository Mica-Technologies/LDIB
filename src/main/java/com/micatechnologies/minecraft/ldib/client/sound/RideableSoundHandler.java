package com.micatechnologies.minecraft.ldib.client.sound;

import com.micatechnologies.minecraft.ldib.LdibSounds;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Drives the LDIB ride sounds for every bike within earshot — the local player's own and anyone
 * else's. Keeps one looping {@link BikeRideSound} alive per bike that is ridden or still rolling (each
 * self-updates volume/pitch from speed), and fires a one-shot brake scuff the moment a bike starts
 * braking at speed. Client-only, installed from {@link
 * com.micatechnologies.minecraft.ldib.LdibClientProxy}.
 *
 * <p>This used to cover only the bike the local player was sitting on, so a bike ridden past you was
 * silent. Everything it needs is synced — the speed for the hum, the braking flag for the scuff — so
 * an observer's client can play exactly what the rider hears.</p>
 */
public class RideableSoundHandler {

    /**
     * Blocks from the listener within which bikes get a sound. A bit past the 16 blocks a sound at
     * these volumes carries, so a bike rolling into range is already playing when it becomes audible.
     */
    private static final double HEARING_RANGE = 24.0D;

    /** Brake scuffs only for a bike braking at more than this speed (blocks/s). */
    private static final double BRAKE_SOUND_MIN_SPEED = 0.5D;

    private final Map<Integer, BikeRideSound> rideSounds = new HashMap<>();
    private final Set<Integer> braking = new HashSet<>();

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || mc.world == null) {
            rideSounds.clear();
            braking.clear();
            return;
        }

        Set<Integer> seen = new HashSet<>();
        for (EntityBike bike : mc.world.getEntitiesWithinAABB(EntityBike.class,
                player.getEntityBoundingBox().grow(HEARING_RANGE))) {
            int id = bike.getEntityId();
            seen.add(id);

            // Keep the ride loop going; the sound ends itself once the bike stops riderless, so start
            // a fresh one whenever it is moving again.
            if (BikeRideSound.isAudible(bike)) {
                BikeRideSound sound = rideSounds.get(id);
                if (sound == null || sound.isDonePlaying()) {
                    sound = new BikeRideSound(bike);
                    rideSounds.put(id, sound);
                    mc.getSoundHandler().playSound(sound);
                }
            }

            boolean nowBraking = bike.isBraking();
            if (nowBraking && !braking.contains(id) && Math.abs(bike.speed()) > BRAKE_SOUND_MIN_SPEED) {
                mc.world.playSound(bike.posX, bike.posY, bike.posZ, LdibSounds.BRAKE,
                    SoundCategory.NEUTRAL, 0.4F, 1.0F, false);
            }
            if (nowBraking) {
                braking.add(id);
            } else {
                braking.remove(id);
            }
        }

        // Forget a sound only once it has ended, not when its bike drifts out of range: dropping it
        // earlier would start a second loop on top of the first if the bike came back while it still
        // played. A bike the client stops tracking is marked dead, which ends its sound.
        braking.retainAll(seen);
        for (Iterator<Map.Entry<Integer, BikeRideSound>> it = rideSounds.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().isDonePlaying()) {
                it.remove();
            }
        }
    }
}
