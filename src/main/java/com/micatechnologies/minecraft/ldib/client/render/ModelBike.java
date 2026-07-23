package com.micatechnologies.minecraft.ldib.client.render;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

/**
 * A deliberately blocky bicycle — two wheels, a frame, a seat and handlebars, all box primitives.
 * This is the MVP "voxel-style" look; a higher-fidelity model (and per-variant models for the
 * e-bike and scooters) is a later phase. All boxes sample a single 64×32 texture, so a flat
 * placeholder skin renders fine until real art exists.
 *
 * <p><b>Coordinate convention (important — an earlier version of this model rendered underground).</b>
 * {@link RenderBike} draws with the vanilla minecart/boat transform: {@code translate(y + 0.375)}
 * then {@code scale(-1, -1, 1)}, at {@code 0.0625} (1/16) model scale. Under that transform a model
 * coordinate maps to the world as {@code world_y = entity.y + 0.375 − model_y·0.0625}, i.e. <b>+y is
 * down</b> and the wheels touch the ground at {@code model_y = +6}. So the bike is authored around an
 * axle at {@code model_y = 0} (0.375 block above ground = wheel radius), growing <i>upward</i> into
 * <i>negative</i> y toward the seat. Do not use the biped "feet at y = 24" convention here.</p>
 *
 * <p>Units are texture pixels; a 16-pixel span is one block. Wheels are 12 px ≈ 0.75 block.</p>
 */
public class ModelBike extends ModelBase {

    private final ModelRenderer rearWheel;
    private final ModelRenderer frontWheel;
    private final ModelRenderer frame;
    private final ModelRenderer seatPost;
    private final ModelRenderer seat;
    private final ModelRenderer handlebar;

    public ModelBike() {
        this.textureWidth = 64;
        this.textureHeight = 32;

        // Wheels: thin in X (the axle runs sideways), a 12×12 disc in the Y–Z plane, centred on the
        // axle at model_y = 0 so wheel-spin (rotateAngleX) turns them in place. Bottom sits at
        // model_y = +6 → exactly ground level.
        rearWheel = new ModelRenderer(this, 0, 0);
        rearWheel.addBox(-1.0F, -6.0F, -6.0F, 2, 12, 12);
        rearWheel.setRotationPoint(0.0F, 0.0F, -7.0F);

        frontWheel = new ModelRenderer(this, 0, 0);
        frontWheel.addBox(-1.0F, -6.0F, -6.0F, 2, 12, 12);
        frontWheel.setRotationPoint(0.0F, 0.0F, 7.0F);

        // Frame: a low bar running fore–aft between the wheels, just above the axle line.
        frame = new ModelRenderer(this, 0, 0);
        frame.addBox(-1.0F, -1.0F, -8.0F, 2, 2, 16);
        frame.setRotationPoint(0.0F, -1.0F, 0.0F);

        // Seat post rises from the rear of the frame (upward = negative y).
        seatPost = new ModelRenderer(this, 0, 0);
        seatPost.addBox(-1.0F, -8.0F, -1.0F, 2, 8, 2);
        seatPost.setRotationPoint(0.0F, -2.0F, -6.0F);

        seat = new ModelRenderer(this, 0, 0);
        seat.addBox(-2.0F, -1.0F, -3.0F, 4, 1, 6);
        seat.setRotationPoint(0.0F, -10.0F, -6.0F);

        // Handlebars up front.
        handlebar = new ModelRenderer(this, 0, 0);
        handlebar.addBox(-5.0F, -1.0F, -1.0F, 10, 2, 2);
        handlebar.setRotationPoint(0.0F, -9.0F, 7.0F);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        rearWheel.render(scale);
        frontWheel.render(scale);
        frame.render(scale);
        seatPost.render(scale);
        seat.render(scale);
        handlebar.render(scale);
    }

    /** Spin the wheels by {@code wheelAngle} radians — called by the renderer from bike speed. */
    public void setWheelSpin(float wheelAngle) {
        this.rearWheel.rotateAngleX = wheelAngle;
        this.frontWheel.rotateAngleX = wheelAngle;
    }
}
