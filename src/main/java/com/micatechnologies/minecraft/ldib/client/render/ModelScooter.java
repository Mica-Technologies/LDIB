package com.micatechnologies.minecraft.ldib.client.render;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

/**
 * A blocky stand-on scooter (Bird / Segway-style): two small wheels, a low deck the rider stands on,
 * and a tall front stem with handlebars. The MVP voxel look, like {@link ModelBike}; per-variant
 * higher-fidelity art is later.
 *
 * <p>Same coordinate convention as {@link ModelBike} (see its class javadoc): {@link RenderBike}'s
 * transform makes <b>+y point down</b>, wheels touch the ground at {@code model_y = +6}, and the model
 * grows <i>upward</i> into <i>negative</i> y. The scooter's wheels are smaller than a bike's (radius
 * 4 px), so they are centred at {@code model_y = +2} to keep their contact point on the ground.</p>
 */
public class ModelScooter extends ModelRideable {

    private final ModelRenderer rearWheel;
    private final ModelRenderer frontWheel;
    private final ModelRenderer deck;
    private final ModelRenderer stem;
    private final ModelRenderer handlebar;
    private final ModelRenderer headlight;
    private final ModelRenderer brakeLight;

    public ModelScooter() {
        this.textureWidth = 64;
        this.textureHeight = 32;

        // Small wheels (8 px ≈ 0.5 block): centred at model_y = +2 so the bottom sits at +6 (ground).
        rearWheel = new ModelRenderer(this, 0, 0);
        rearWheel.addBox(-1.0F, -4.0F, -4.0F, 2, 8, 8);
        rearWheel.setRotationPoint(0.0F, 2.0F, -6.0F);

        frontWheel = new ModelRenderer(this, 0, 0);
        frontWheel.addBox(-1.0F, -4.0F, -4.0F, 2, 8, 8);
        frontWheel.setRotationPoint(0.0F, 2.0F, 6.0F);

        // Low deck the rider stands on, running fore–aft between the wheels just above the ground.
        deck = new ModelRenderer(this, 0, 0);
        deck.addBox(-2.0F, 3.0F, -5.0F, 4, 2, 10);
        deck.setRotationPoint(0.0F, 0.0F, 0.0F);

        // Front steering stem: a tall pole from the deck up to the handlebars (upward = negative y).
        stem = new ModelRenderer(this, 0, 0);
        stem.addBox(-1.0F, -18.0F, -1.0F, 2, 22, 2);
        stem.setRotationPoint(0.0F, 4.0F, 6.0F);

        // Handlebars across the top of the stem.
        handlebar = new ModelRenderer(this, 0, 0);
        handlebar.addBox(-5.0F, -1.0F, -1.0F, 10, 2, 2);
        handlebar.setRotationPoint(0.0F, -14.0F, 6.0F);

        // Light fixtures (drawn only by renderLights). Front is +z: headlight on the stem facing
        // forward; brake light low on the rear fender behind the back wheel (as on a Segway Max).
        headlight = new ModelRenderer(this, 0, 0);
        headlight.addBox(-1.5F, -10.0F, 7.0F, 3, 2, 1);
        headlight.setRotationPoint(0.0F, 0.0F, 0.0F);

        brakeLight = new ModelRenderer(this, 0, 0);
        brakeLight.addBox(-1.5F, -1.0F, -8.0F, 3, 2, 1);
        brakeLight.setRotationPoint(0.0F, 0.0F, 0.0F);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        rearWheel.render(scale);
        frontWheel.render(scale);
        deck.render(scale);
        stem.render(scale);
        handlebar.render(scale);
    }

    @Override
    public void setWheelSpin(float wheelAngle) {
        this.rearWheel.rotateAngleX = wheelAngle;
        this.frontWheel.rotateAngleX = wheelAngle;
    }

    @Override
    public void renderLights(float scale, boolean headlightOn, boolean brakeLightOn) {
        renderLightFixtures(scale, headlightOn, headlight, brakeLightOn, brakeLight);
    }
}
