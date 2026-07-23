package com.micatechnologies.minecraft.ldib.client.render;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

/**
 * A blocky stand-on scooter (Bird / Segway-style): two small round-ish wheels, a low deck the rider
 * stands on, a tall front stem with handlebars, and a rear fender. The MVP voxel look, like {@link
 * ModelBike}; per-variant higher-fidelity art is later.
 *
 * <p>Same conventions as {@link ModelRideable}: <b>+y is down</b>, wheels touch the ground at {@code
 * model_y = +6}, and the <b>front faces −z</b> (the stem/handlebars). The wheels are small (radius 3 ≈
 * 0.375 block) to match a real e-scooter's proportions — much smaller than the bike's — and the deck
 * sits low between them.</p>
 */
public class ModelScooter extends ModelRideable {

    private final ModelRenderer[] frontWheel;
    private final ModelRenderer[] rearWheel;
    private final ModelRenderer deck;
    private final ModelRenderer stem;
    private final ModelRenderer fork;
    private final ModelRenderer handlebar;
    private final ModelRenderer fender;
    private final ModelRenderer headlight;
    private final ModelRenderer brakeLight;

    public ModelScooter() {
        this.textureWidth = 64;
        this.textureHeight = 32;

        // Small wheels (radius 3 = 6 px): front toward −z, rear toward +z, centred at model_y = +3 so
        // the bottom sits on the ground (+6). Thin 1 px tyres.
        frontWheel = buildWheel(3.0F, -6.0F, 3, 1, 2);
        rearWheel = buildWheel(3.0F, 6.0F, 3, 1, 2);

        // Low deck the rider stands on, from just behind the stem back toward the rear wheel.
        deck = new ModelRenderer(this, 0, 0);
        deck.addBox(-2.0F, 4.0F, -3.0F, 4, 1, 9);
        deck.setRotationPoint(0.0F, 0.0F, 0.0F);

        // Tall front stem up to the handlebars, plus a short fork down to the front wheel.
        stem = tube(4.0F, -6.0F, -14.0F, -6.0F, 2);
        fork = tube(4.0F, -6.0F, 3.0F, -6.0F, 2);

        handlebar = new ModelRenderer(this, 0, 0);
        handlebar.addBox(-5.0F, -1.0F, -1.0F, 10, 2, 2);
        handlebar.setRotationPoint(0.0F, -14.0F, -6.0F);

        // Rear fender over the back wheel.
        fender = new ModelRenderer(this, 0, 0);
        fender.addBox(-2.0F, 0.0F, 4.0F, 4, 1, 4);
        fender.setRotationPoint(0.0F, 0.0F, 0.0F);

        // Emissive lights: headlight on the stem front (−z), brake light on the rear fender (+z).
        headlight = new ModelRenderer(this, 0, 0);
        headlight.addBox(-1.5F, -9.0F, -7.0F, 3, 2, 1);
        headlight.setRotationPoint(0.0F, 0.0F, 0.0F);

        brakeLight = new ModelRenderer(this, 0, 0);
        brakeLight.addBox(-1.5F, -1.0F, 8.0F, 3, 2, 1);
        brakeLight.setRotationPoint(0.0F, 0.0F, 0.0F);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        renderGroup(frontWheel, scale);
        renderGroup(rearWheel, scale);
        deck.render(scale);
        stem.render(scale);
        fork.render(scale);
        handlebar.render(scale);
        fender.render(scale);
    }

    @Override
    public void setWheelSpin(float wheelAngle) {
        spinWheel(frontWheel, wheelAngle);
        spinWheel(rearWheel, wheelAngle);
    }

    @Override
    public void renderLights(float scale, boolean headlightOn, boolean brakeLightOn) {
        renderLightFixtures(scale, headlightOn, headlight, brakeLightOn, brakeLight);
    }
}
