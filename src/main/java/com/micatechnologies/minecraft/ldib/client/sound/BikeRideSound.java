package com.micatechnologies.minecraft.ldib.client.sound;

import com.micatechnologies.minecraft.ldib.LdibSounds;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.SoundCategory;

/**
 * The looping ride hum of one bike — the local player's own, or anyone else's rolling past. A {@link
 * MovingSound} so it follows the bike and can update its volume/pitch every tick from the bike's
 * speed — quiet and low-pitched when crawling, louder and higher when moving. It ends itself once the
 * bike is gone, or nobody is riding it and it has stopped; {@link RideableSoundHandler} starts a new
 * one if it gets going again. Client-only.
 *
 * <p>Other players' bikes can be heard because the speed it reads is the synced one an observing
 * client dead-reckons from (see {@code EntityBike.SPEED}), not anything only the rider's client has.</p>
 */
public class BikeRideSound extends MovingSound {

    /** Below this (blocks/s) a riderless bike counts as stopped and its sound ends. */
    static final double AUDIBLE_SPEED = 0.05D;

    private final EntityBike bike;

    public BikeRideSound(EntityBike bike) {
        super(LdibSounds.RIDE, SoundCategory.NEUTRAL);
        this.bike = bike;
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 0.0F;
        this.xPosF = (float) bike.posX;
        this.yPosF = (float) bike.posY;
        this.zPosF = (float) bike.posZ;
    }

    @Override
    public void update() {
        if (this.bike.isDead || !isAudible(this.bike)) {
            this.donePlaying = true;
            return;
        }
        this.xPosF = (float) this.bike.posX;
        this.yPosF = (float) this.bike.posY;
        this.zPosF = (float) this.bike.posZ;
        double speed = Math.abs(this.bike.speed());
        this.volume = (float) Math.min(0.5D, 0.06D + speed * 0.055D);
        this.pitch = (float) (0.7D + Math.min(1.0D, speed / 8.0D) * 0.6D);
    }

    /** Whether a bike should be making its ride sound: someone is on it, or it is still rolling. */
    static boolean isAudible(EntityBike bike) {
        return bike.isBeingRidden() || Math.abs(bike.speed()) > AUDIBLE_SPEED;
    }
}
