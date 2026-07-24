package com.micatechnologies.minecraft.ldib.client;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Leans the rider's camera with the bike they are riding.
 *
 * <p>The bike's <i>model</i> has leaned into turns since the MVP, but the rider's view stayed
 * stubbornly level — you could watch the bike bank underneath you while the horizon refused to move,
 * which reads as the bike being a prop rather than something you are sitting on. This closes that
 * gap: the same lean angle the renderer already uses is applied to the camera.</p>
 *
 * <p><b>This needed no coremod</b>, which is worth stating plainly because the master plan had it
 * pencilled in as the mod's first one. Vanilla 1.12.2 has no camera roll of its own, but Forge's
 * {@code EntityViewRenderEvent.CameraSetup} carries a roll field that {@code EntityRenderer} applies
 * as a Z-axis rotation immediately after firing the event — the same hook the sibling RCMC mod rolls
 * its coaster riders with. So {@code usesMixins} stays {@code false}, and LDIB keeps out of the one
 * area (the camera path) where a mixin would be most likely to fight Optifine and shader mods.</p>
 *
 * <p><b>Client-only</b>, reached exclusively from {@code LdibClientProxy}. Config-gated, and
 * deliberately gated <i>on by default but at partial strength</i>: camera roll is a real
 * motion-sickness trigger for some players, so {@link LdibConfig#cameraLeanStrength} scales it and
 * setting it to 0 turns it off entirely. Riders who disable it still get the leaning model.</p>
 */
@SideOnly(Side.CLIENT)
public final class RiderCamera {

    @SubscribeEvent
    public void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        if (LdibConfig.cameraLeanStrength <= 0.0D) {
            return;
        }
        Entity viewer = Minecraft.getMinecraft().getRenderViewEntity();
        if (viewer == null || !(viewer.getRidingEntity() instanceof EntityBike)) {
            return;
        }
        EntityBike bike = (EntityBike) viewer.getRidingEntity();

        // The entity already eases this angle toward its target and the accessor interpolates between
        // ticks, so there is nothing left to smooth here — unlike RCMC, whose track frame can snap
        // into a transition. Taking the model's own lean also guarantees the camera and the bike
        // under it agree exactly, which is the whole illusion.
        //
        // SIGN: not yet confirmed in-game. The renderer's lean and this roll are separate rotations
        // and the cosmetic steer angle already needed its sign flipped once for exactly this reason
        // (a Y-steer and a Z-lean have opposite handedness under the renderer's scale(-1,-1,1)). If
        // the horizon tips the wrong way on the first ride, negate here — not in EntityBike, whose
        // lean is verified correct.
        float lean = bike.bikeLean((float) event.getRenderPartialTicks());
        event.setRoll(lean * (float) LdibConfig.cameraLeanStrength);
    }
}
