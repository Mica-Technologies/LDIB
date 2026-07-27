package com.micatechnologies.minecraft.ldib.client;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Owns where a riding player is <b>looking</b>: it follows the bike round a turn, stops dead at the
 * limit, and drifts back toward straight ahead as you pick up speed.
 *
 * <p><b>Why this is a per-frame job, and what went wrong when it wasn't.</b> The first cut did all of
 * this in {@code EntityBike.updatePassenger}, which runs on the game tick — 20 Hz. Mouse look does
 * not: {@code EntityRenderer.updateCameraAndRender} calls {@code player.turn(...)} once per
 * <i>frame</i>, and then renders the world in the same frame. So the two ran at different rates and
 * fought, in two separate ways the owner spotted immediately in-game:</p>
 *
 * <ul>
 *   <li><b>Recentring juddered.</b> A pull applied 20 times a second, to a camera being redrawn 100+
 *       times a second, is 20 visible steps rather than a drift — and it read as stutter next to the
 *       mouse's own smoothness.</li>
 *   <li><b>The look limit bounced.</b> Pushing against it, the mouse carried the view past the stop
 *       every frame and the tick-rate clamp yanked it back 20 times a second. That is an oscillator,
 *       not a wall.</li>
 * </ul>
 *
 * <p>The fix is not to smooth either one but to move the whole job onto the frame, into
 * {@code EntityViewRenderEvent.CameraSetup} — which fires from {@code orientCamera}, <b>after</b> that
 * frame's mouse turn and before the camera rotation is applied. Nothing then happens between the
 * rider's input and the clamp, so the limit is a hard stop with nothing to bounce against, and the
 * recentring is a continuous exponential decay in real seconds rather than a staircase in ticks.</p>
 *
 * <p><b>The offset is the state, not the yaw.</b> The rider's angle away from the bike's heading is
 * kept here in {@link #lookOffset} and the absolute yaw is derived from it every frame, rather than
 * the other way round. That is what makes the view follow a turn: re-deriving the offset from the
 * absolute yaw each frame would have it shrink by exactly the bike's heading change, i.e. leave the
 * rider staring at a fixed point in the world while the bike turned underneath them. So only the
 * <i>mouse's</i> contribution — the difference between the yaw now and the yaw this class wrote last
 * frame — is folded in, and the bike's own heading comes in already interpolated for the frame, which
 * is also what keeps a turn perfectly smooth.</p>
 *
 * <p>Client-only, reached exclusively from {@code LdibClientProxy}. It writes to the local player and
 * nothing else; a rider on someone else's screen is drawn from the yaw the server sent, and the server
 * enforces {@link EntityBike#MAX_LOOK_YAW} itself.</p>
 */
@SideOnly(Side.CLIENT)
public final class RiderLook {

    /**
     * Recentring rate in e-folds per second at {@code viewRecenterStrength == 1}, which the config
     * value scales. 20 puts the default 0.05 at one e-fold per second — a drift you can ride against —
     * and 1.0 at a 50 ms time constant, which is the "snaps forward the moment you stop turning"
     * end the config describes.
     */
    private static final double MAX_RECENTER_RATE = 20.0D;

    /**
     * Speed (blocks/s) at which recentring reaches full strength; below it the pull scales down
     * linearly, and at a standstill there is none at all. That is the whole feel: stopped at a junction
     * you look wherever you like and stay there, and the faster you ride the more insistently your eyes
     * come back to the road.
     */
    private static final double RECENTER_FULL_SPEED = 4.0D;

    /**
     * Longest frame the recentring will integrate over, in seconds. A frame that took longer than this
     * was not a frame — it was a lag spike, a world load, or a pause — and letting one decay a rider's
     * view by however long they spent in a menu would be a nasty surprise on the way out.
     */
    private static final double MAX_FRAME_SECONDS = 0.25D;

    /** The bike being ridden last frame, or {@code null}; a change means a fresh mount, so recentre. */
    private EntityBike bike;

    /** Degrees the rider is looking away from the bike's heading. The state everything derives from. */
    private float lookOffset;

    /** The yaw this class wrote last frame, so this frame's mouse movement can be read back out. */
    private float lastAppliedYaw;

    /** {@code System.nanoTime()} at the last frame, or 0 when there isn't one to measure against. */
    private long lastFrameNanos;

    @SubscribeEvent
    public void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity viewer = mc.getRenderViewEntity();
        // Only ever the local player's own view: in spectator mode the render view entity is somebody
        // else entirely, and writing a yaw onto them is not this class's business.
        if (viewer != mc.player || !(viewer instanceof EntityPlayer)
            || !(viewer.getRidingEntity() instanceof EntityBike)) {
            this.bike = null;
            this.lastFrameNanos = 0L;
            return;
        }
        EntityPlayer rider = (EntityPlayer) viewer;
        EntityBike ridden = (EntityBike) viewer.getRidingEntity();
        float partialTicks = (float) event.getRenderPartialTicks();
        float bikeYaw = interpolateYaw(ridden.prevRotationYaw, ridden.rotationYaw, partialTicks);

        if (this.bike != ridden) {
            // Just got on. Start looking where the bike is pointing rather than wherever you happened
            // to be facing when you right-clicked it, which is what riding one has always done.
            this.bike = ridden;
            this.lookOffset = 0.0F;
            this.lastAppliedYaw = bikeYaw;
            this.lastFrameNanos = 0L;
        }

        // Whatever the mouse did since the last frame is the rider's own input, and is the only thing
        // that adds to the offset.
        this.lookOffset = MathHelper.wrapDegrees(
            this.lookOffset + MathHelper.wrapDegrees(rider.rotationYaw - this.lastAppliedYaw));

        // A hard stop. Nothing runs between here and the camera rotation below, so there is nothing
        // left to push past it and nothing to bounce back from.
        this.lookOffset = MathHelper.clamp(this.lookOffset,
            -EntityBike.MAX_LOOK_YAW, EntityBike.MAX_LOOK_YAW);

        this.lookOffset *= recenterDecay(ridden);

        float yaw = MathHelper.wrapDegrees(bikeYaw + this.lookOffset);
        // prev moves with it: this angle is already correct for this exact frame, so there is nothing
        // for the renderer to interpolate toward and interpolating would only add lag. It is what
        // Entity.turn does with mouse input for the same reason.
        rider.prevRotationYaw = yaw;
        rider.rotationYaw = yaw;
        rider.prevRotationYawHead = yaw;
        rider.rotationYawHead = yaw;
        this.lastAppliedYaw = yaw;

        // The event was built from the yaw as it stood a moment ago, so hand it the corrected one —
        // otherwise the camera would spend every frame showing the overshoot we just took out.
        event.setYaw(yaw + 180.0F);
    }

    /**
     * The factor to multiply the look offset by this frame, from an exponential decay in real seconds
     * — so the drift home is the same speed at 30 fps and at 300, and is a smooth curve rather than a
     * staircase. Returns 1 (no pull at all) when the rider is stopped or has turned recentring off.
     */
    private float recenterDecay(EntityBike ridden) {
        long now = System.nanoTime();
        long previous = this.lastFrameNanos;
        this.lastFrameNanos = now;
        if (previous == 0L || LdibConfig.viewRecenterStrength <= 0.0D) {
            return 1.0F;
        }
        double seconds = Math.min((now - previous) / 1.0e9D, MAX_FRAME_SECONDS);
        double speedFraction = Math.min(1.0D, Math.abs(ridden.speed()) / RECENTER_FULL_SPEED);
        double rate = LdibConfig.viewRecenterStrength * MAX_RECENTER_RATE * speedFraction;
        if (rate <= 0.0D || seconds <= 0.0D) {
            return 1.0F;
        }
        return (float) Math.exp(-rate * seconds);
    }

    /** Interpolate between two yaws the short way round, so a wrap past ±180 doesn't spin the view. */
    private static float interpolateYaw(float previous, float current, float partialTicks) {
        return previous + MathHelper.wrapDegrees(current - previous) * partialTicks;
    }
}
