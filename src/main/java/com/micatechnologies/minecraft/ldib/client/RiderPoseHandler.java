package com.micatechnologies.minecraft.ldib.client;

import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import com.micatechnologies.minecraft.ldib.entity.RiderPose;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Poses a rider's limbs while they ride an LDIB rideable so the player reads as "person riding"
 * rather than "prop glued to a seat": on a bicycle the legs reach down to the pedals and turn with
 * them and the hands rest on the grips; on a one-wheel the feet plant along the board and the arms
 * come out for balance.
 *
 * <p>Purely cosmetic and client-only — reached exclusively from
 * {@link com.micatechnologies.minecraft.ldib.LdibClientProxy}, never from common code. The pedal
 * phase comes from the ridden bike's interpolated {@link EntityBike#crankRotation(float)}, which is
 * what the bike's own crank is drawn from, so feet and pedals cannot drift apart.</p>
 *
 * <p><b>Why limbs are swapped rather than rotated.</b> The obvious way to pose a player is to set
 * the model's limb angles in {@code RenderPlayerEvent.Pre}. That does nothing: the player model
 * recomputes every limb angle from scratch inside its own {@code render}, immediately before it
 * draws, and there is no event between the two. (An earlier version of this class did exactly that,
 * and riders simply showed the vanilla pose.) So for the length of one player's render, each limb we
 * pose is replaced in the model by a {@link PosedLimb} — a part with the same boxes that sets its own
 * angles at the last possible moment, when it is asked to draw. {@code Post} puts the originals back,
 * so the shared model never carries a rider's pose onto anyone else.</p>
 *
 * <p>Known limit: armour is drawn by separate models this cannot reach, so a rider's leggings and
 * sleeves stay in the plain standing pose while the limbs under them move.</p>
 */
public class RiderPoseHandler {

    /** How far the legs swing either side of straight down as the pedals go round, radians. */
    private static final float PEDAL_AMPLITUDE = 0.3F;

    /** The pedals turn about a point a little ahead of the hips, so the legs hang slightly forward. */
    private static final float PEDAL_FORWARD_BIAS = -0.08F;

    /** Outward lean of each leg, radians: the pedals are wider apart than the hips. */
    private static final float LEG_SPLAY = 0.09F;

    /** Forward reach of the arms, radians, from the shoulders down to the grips. */
    private static final float ARM_REACH = -0.92F;

    /** Inward lean of each arm, radians: the grips are closer together than the shoulders. */
    private static final float ARM_TUCK = 0.13F;

    /**
     * How far apart a board rider's feet plant along the deck, radians. The body has already been
     * turned 90° across the board ({@link RiderPose#bodyYawOffset}), so a sideways splay of the legs
     * in the rider's own frame is a fore-and-aft stagger along the board.
     */
    private static final float BOARD_SPLAY = 0.22F;

    /** How far a board rider's arms hang out from their sides, radians — the balancing posture. */
    private static final float BOARD_ARM_OUT = 0.45F;

    /** A limb that draws at angles of our choosing, whatever the model computed for it. */
    private static final class PosedLimb extends ModelRenderer {
        private float poseX;
        private float poseZ;

        PosedLimb(ModelBase model, ModelRenderer original) {
            super(model);
            this.cubeList.addAll(original.cubeList);
        }

        @Override
        public void render(float scale) {
            this.rotateAngleX = this.poseX;
            this.rotateAngleY = 0.0F;
            this.rotateAngleZ = this.poseZ;
            super.render(scale);
        }
    }

    /** One stand-in per original limb, made the first time it is needed and reused. */
    private final Map<ModelRenderer, PosedLimb> standIns = new IdentityHashMap<ModelRenderer, PosedLimb>();

    /** The model whose limbs are currently swapped, and the originals to put back. */
    private ModelPlayer swapped;
    private final ModelRenderer[] originals = new ModelRenderer[8];

    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        restore(); // a Post that never came must not leave stand-ins in the shared model

        EntityPlayer player = event.getEntityPlayer();
        Entity vehicle = player.getRidingEntity();
        if (!(vehicle instanceof EntityBike)) {
            return;
        }
        EntityBike bike = (EntityBike) vehicle;
        RiderPose pose = bike.variant().pose();
        if (pose == RiderPose.STANDING) {
            return; // a scooter rider stands as the game already draws them
        }

        float leftLegX = 0.0F;
        float rightLegX = 0.0F;
        float legOut;
        float armX;
        float armOut;
        if (pose == RiderPose.BOARD) {
            legOut = BOARD_SPLAY;
            armX = 0.0F;
            armOut = BOARD_ARM_OUT;
        } else {
            // The player's right foot is on the bike's left-hand pedal as the bike model counts
            // sides, which is half a turn round from the crank angle itself.
            float stroke = PEDAL_AMPLITUDE * (float) Math.sin(bike.crankRotation(event.getPartialRenderTick()));
            rightLegX = PEDAL_FORWARD_BIAS + stroke;
            leftLegX = PEDAL_FORWARD_BIAS - stroke;
            legOut = LEG_SPLAY;
            armX = ARM_REACH;
            armOut = -ARM_TUCK;
        }

        // Positive Z leans a limb toward the model's −x side, which is the player's right.
        ModelPlayer model = event.getRenderer().getMainModel();
        this.originals[0] = model.bipedRightLeg;
        this.originals[1] = model.bipedLeftLeg;
        this.originals[2] = model.bipedRightArm;
        this.originals[3] = model.bipedLeftArm;
        this.originals[4] = model.bipedRightLegwear;
        this.originals[5] = model.bipedLeftLegwear;
        this.originals[6] = model.bipedRightArmwear;
        this.originals[7] = model.bipedLeftArmwear;
        model.bipedRightLeg = standIn(model, this.originals[0], rightLegX, legOut);
        model.bipedLeftLeg = standIn(model, this.originals[1], leftLegX, -legOut);
        model.bipedRightArm = standIn(model, this.originals[2], armX, armOut);
        model.bipedLeftArm = standIn(model, this.originals[3], armX, -armOut);
        model.bipedRightLegwear = standIn(model, this.originals[4], rightLegX, legOut);
        model.bipedLeftLegwear = standIn(model, this.originals[5], leftLegX, -legOut);
        model.bipedRightArmwear = standIn(model, this.originals[6], armX, armOut);
        model.bipedLeftArmwear = standIn(model, this.originals[7], armX, -armOut);
        this.swapped = model;
    }

    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        restore();
    }

    private PosedLimb standIn(ModelBase model, ModelRenderer original, float x, float z) {
        PosedLimb limb = this.standIns.get(original);
        if (limb == null) {
            limb = new PosedLimb(model, original);
            this.standIns.put(original, limb);
        }
        // The model moves a limb's pivot itself in some poses (sneaking), and does so on whatever
        // part is in the slot — but start from where the original was.
        limb.rotationPointX = original.rotationPointX;
        limb.rotationPointY = original.rotationPointY;
        limb.rotationPointZ = original.rotationPointZ;
        limb.showModel = original.showModel;
        limb.isHidden = original.isHidden;
        limb.poseX = x;
        limb.poseZ = z;
        return limb;
    }

    private void restore() {
        ModelPlayer model = this.swapped;
        if (model == null) {
            return;
        }
        this.swapped = null;
        model.bipedRightLeg = this.originals[0];
        model.bipedLeftLeg = this.originals[1];
        model.bipedRightArm = this.originals[2];
        model.bipedLeftArm = this.originals[3];
        model.bipedRightLegwear = this.originals[4];
        model.bipedLeftLegwear = this.originals[5];
        model.bipedRightArmwear = this.originals[6];
        model.bipedLeftArmwear = this.originals[7];
    }
}
