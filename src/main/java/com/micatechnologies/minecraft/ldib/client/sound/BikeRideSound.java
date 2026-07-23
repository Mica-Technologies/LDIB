package com.micatechnologies.minecraft.ldib.client.sound;

import com.micatechnologies.minecraft.ldib.LdibSounds;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;

/**
 * The looping ride hum for a bike the local player is riding. A {@link MovingSound} so it follows the
 * bike and can update its volume/pitch every tick from the bike's speed — quiet and low-pitched when
 * crawling, louder and higher when moving. It ends itself when the bike dies or the player dismounts.
 * Client-only.
 */
public class BikeRideSound extends MovingSound {

    private final EntityBike bike;
    private final EntityPlayer rider;

    public BikeRideSound(EntityBike bike, EntityPlayer rider) {
        super(LdibSounds.RIDE, SoundCategory.NEUTRAL);
        this.bike = bike;
        this.rider = rider;
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 0.0F;
    }

    @Override
    public void update() {
        if (this.bike.isDead || this.rider.getRidingEntity() != this.bike) {
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
}
