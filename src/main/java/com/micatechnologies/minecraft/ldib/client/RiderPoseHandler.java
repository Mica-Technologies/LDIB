package com.micatechnologies.minecraft.ldib.client;

import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import com.micatechnologies.minecraft.ldib.entity.RiderPose;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Animates a rider's limbs while they ride an LDIB rideable so the player reads as "person riding"
 * rather than "prop glued to a seat": legs pedal in sync with the wheels on a seated bike, and take a
 * planted standing stance on a scooter.
 *
 * <p>Purely cosmetic and client-only — reached exclusively from
 * {@link com.micatechnologies.minecraft.ldib.LdibClientProxy}, never from common code, so its
 * {@code net.minecraft.client} model imports never reach a dedicated server. It touches neither the
 * {@code physics} package nor {@link EntityBike}'s movement; the pedal phase is derived entirely from
 * the ridden bike's existing interpolated {@link EntityBike#wheelRotation(float)}, which already
 * accumulates with distance rolled and freezes when the bike stops.</p>
 *
 * <p>The renderer's {@link ModelBiped} is shared and reused across every player it draws (and armour
 * layers read these same angles), so a mutated pose must never leak onto a non-riding player: the
 * {@code Pre} handler snapshots exactly the angles it overwrites and the {@code Post} handler writes
 * them back, guarded so it only restores the player it actually posed.</p>
 */
public class RiderPoseHandler {

    /** Forward-down bias (radians) so a seated rider's thighs angle toward the pedals and clear the frame. */
    private static final float PEDAL_FORWARD_BIAS = 0.9F;

    /** Pedal-stroke amplitude (radians, ~40°) swung about the forward bias, 180° out of phase per leg. */
    private static final float PEDAL_AMPLITUDE = 0.7F;

    /** Outward thigh splay (radians) so the legs straddle the top tube instead of clipping it. */
    private static final float LEG_SPLAY = 0.12F;

    /** Forward arm reach (radians) toward the handlebars; negative rotateAngleX swings the arms forward. */
    private static final float ARM_REACH = -0.8F;

    /** Slight inward arm tuck (radians) so the reaching hands meet at the bars. */
    private static final float ARM_TUCK = 0.1F;

    /** Near-straight leg with a small fore/aft split (radians) for a planted standing scooter stance. */
    private static final float STAND_SPLIT = 0.15F;

    /** The player this handler posed in {@code Pre}; {@code null} unless a snapshot is pending restore. */
    private EntityPlayer posed;

    private float savedLeftLegX;
    private float savedLeftLegZ;
    private float savedRightLegX;
    private float savedRightLegZ;
    private float savedLeftArmX;
    private float savedLeftArmZ;
    private float savedRightArmX;
    private float savedRightArmZ;

    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        // Guard against a Post that never got its Pre: if a previous pose is somehow still pending,
        // drop it rather than risk restoring stale angles onto the wrong model.
        this.posed = null;

        EntityPlayer player = event.getEntityPlayer();
        Entity vehicle = player.getRidingEntity();
        if (!(vehicle instanceof EntityBike)) {
            return;
        }
        ModelBiped model = event.getRenderer().getMainModel();
        EntityBike bike = (EntityBike) vehicle;

        ModelRenderer leftLeg = model.bipedLeftLeg;
        ModelRenderer rightLeg = model.bipedRightLeg;
        ModelRenderer leftArm = model.bipedLeftArm;
        ModelRenderer rightArm = model.bipedRightArm;

        // Snapshot exactly what we overwrite so Post restores the shared model precisely.
        this.savedLeftLegX = leftLeg.rotateAngleX;
        this.savedLeftLegZ = leftLeg.rotateAngleZ;
        this.savedRightLegX = rightLeg.rotateAngleX;
        this.savedRightLegZ = rightLeg.rotateAngleZ;
        this.savedLeftArmX = leftArm.rotateAngleX;
        this.savedLeftArmZ = leftArm.rotateAngleZ;
        this.savedRightArmX = rightArm.rotateAngleX;
        this.savedRightArmZ = rightArm.rotateAngleZ;
        this.posed = player;

        if (bike.variant().pose() == RiderPose.STANDING) {
            applyStandingStance(leftLeg, rightLeg);
        } else {
            applyPedalStroke(bike, event.getPartialRenderTick(), leftLeg, rightLeg, leftArm, rightArm);
        }
    }

    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (this.posed == null || this.posed != event.getEntityPlayer()) {
            return;
        }
        ModelBiped model = event.getRenderer().getMainModel();
        model.bipedLeftLeg.rotateAngleX = this.savedLeftLegX;
        model.bipedLeftLeg.rotateAngleZ = this.savedLeftLegZ;
        model.bipedRightLeg.rotateAngleX = this.savedRightLegX;
        model.bipedRightLeg.rotateAngleZ = this.savedRightLegZ;
        model.bipedLeftArm.rotateAngleX = this.savedLeftArmX;
        model.bipedLeftArm.rotateAngleZ = this.savedLeftArmZ;
        model.bipedRightArm.rotateAngleX = this.savedRightArmX;
        model.bipedRightArm.rotateAngleZ = this.savedRightArmZ;
        this.posed = null;
    }

    /**
     * Pedals a seated rider's legs about {@link #PEDAL_FORWARD_BIAS}, the two legs a half-cycle apart,
     * driven by the ridden bike's interpolated wheel angle so the crank turns exactly as fast as the
     * wheels roll and holds still when the bike is stopped. The thighs splay outward to clear the frame,
     * and the arms reach forward to the bars.
     */
    private void applyPedalStroke(EntityBike bike, float partialTicks, ModelRenderer leftLeg,
                                  ModelRenderer rightLeg, ModelRenderer leftArm, ModelRenderer rightArm) {
        float phase = bike.wheelRotation(partialTicks);

        leftLeg.rotateAngleX = PEDAL_FORWARD_BIAS + PEDAL_AMPLITUDE * (float) Math.sin(phase);
        rightLeg.rotateAngleX = PEDAL_FORWARD_BIAS + PEDAL_AMPLITUDE * (float) Math.sin(phase + Math.PI);
        leftLeg.rotateAngleZ = LEG_SPLAY;
        rightLeg.rotateAngleZ = -LEG_SPLAY;

        leftArm.rotateAngleX = ARM_REACH;
        rightArm.rotateAngleX = ARM_REACH;
        leftArm.rotateAngleZ = ARM_TUCK;
        rightArm.rotateAngleZ = -ARM_TUCK;
    }

    /**
     * Plants a standing (scooter) rider on the deck: both legs near-straight with a small fore/aft
     * split so one foot sits slightly back, rather than pedalling in mid-air. Arms are left alone so the
     * rider can hold the stem naturally under vanilla posing.
     */
    private void applyStandingStance(ModelRenderer leftLeg, ModelRenderer rightLeg) {
        leftLeg.rotateAngleX = -STAND_SPLIT;
        rightLeg.rotateAngleX = STAND_SPLIT;
        leftLeg.rotateAngleZ = 0.0F;
        rightLeg.rotateAngleZ = 0.0F;
    }
}
