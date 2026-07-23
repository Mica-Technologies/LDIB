package com.micatechnologies.minecraft.ldib.client.render;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

/**
 * A blocky bicycle with a proper diamond frame and round-ish (12-gon) wheels — two wheels, a
 * double-triangle frame (down tube, seat tube, top tube, chain stay, seat stay), a fork, stem +
 * handlebars and a saddle. Still the voxel MVP look, just a recognisable bike rather than a
 * bike-shaped box; per-variant higher-fidelity art is later.
 *
 * <p><b>Coordinate convention</b> (see {@link ModelRideable}): {@link RenderBike}'s transform makes
 * <b>+y point down</b> and the <b>front face −z</b>. Wheels touch the ground at {@code model_y = +6};
 * the axle sits at {@code model_y = −1} (radius 7). Tyres are 1 px across while the frame tubes are
 * 2 px, so the wheels are narrower than the frame and their side faces never coincide (no z-fighting).</p>
 */
public class ModelBike extends ModelRideable {

    private final ModelRenderer[] frontWheel;
    private final ModelRenderer[] rearWheel;
    private final ModelRenderer downTube;
    private final ModelRenderer seatTube;
    private final ModelRenderer topTube;
    private final ModelRenderer fork;
    private final ModelRenderer chainStay;
    private final ModelRenderer seatStay;
    private final ModelRenderer stem;
    private final ModelRenderer handlebar;
    private final ModelRenderer saddle;
    private final ModelRenderer headlight;
    private final ModelRenderer brakeLight;

    public ModelBike() {
        this.textureWidth = 64;
        this.textureHeight = 32;

        // Wheels (radius 7 = 14 px ≈ 0.875 block): front toward −z, rear toward +z. Thin 1 px tyres.
        frontWheel = buildWheel(-1.0F, -7.0F, 7, 1, 2);
        rearWheel = buildWheel(-1.0F, 7.0F, 7, 1, 2);

        // Frame nodes (model space, +y down, front −z):
        //   bottom bracket BB(+2, 0), head tube HT(−9, −6), seat top ST(−11, +5),
        //   front axle FA(−1, −7), rear axle RA(−1, +7).
        downTube = tube(2.0F, 0.0F, -9.0F, -6.0F, 2);   // BB → head tube
        seatTube = tube(2.0F, 0.0F, -11.0F, 5.0F, 2);   // BB → seat
        topTube = tube(-9.0F, -6.0F, -11.0F, 5.0F, 2);  // head tube → seat
        fork = tube(-9.0F, -6.0F, -1.0F, -7.0F, 2);     // head tube → front axle
        chainStay = tube(2.0F, 0.0F, -1.0F, 7.0F, 2);   // BB → rear axle
        seatStay = tube(-11.0F, 5.0F, -1.0F, 7.0F, 2);  // seat → rear axle
        stem = tube(-9.0F, -6.0F, -12.0F, -6.0F, 2);    // head tube → handlebar

        handlebar = new ModelRenderer(this, 0, 0);
        handlebar.addBox(-5.0F, -1.0F, -1.0F, 10, 2, 2);
        handlebar.setRotationPoint(0.0F, -12.0F, -6.0F);

        saddle = new ModelRenderer(this, 0, 0);
        saddle.addBox(-1.5F, -1.0F, -3.0F, 3, 1, 6);
        saddle.setRotationPoint(0.0F, -12.0F, 5.0F);

        // Emissive lights (drawn only by renderLights): headlight at the front (−z), brake at rear (+z).
        headlight = new ModelRenderer(this, 0, 0);
        headlight.addBox(-1.5F, -9.0F, -9.0F, 3, 2, 1);
        headlight.setRotationPoint(0.0F, 0.0F, 0.0F);

        brakeLight = new ModelRenderer(this, 0, 0);
        brakeLight.addBox(-1.5F, -12.0F, 8.0F, 3, 2, 1);
        brakeLight.setRotationPoint(0.0F, 0.0F, 0.0F);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        renderGroup(frontWheel, scale);
        renderGroup(rearWheel, scale);
        downTube.render(scale);
        seatTube.render(scale);
        topTube.render(scale);
        fork.render(scale);
        chainStay.render(scale);
        seatStay.render(scale);
        stem.render(scale);
        handlebar.render(scale);
        saddle.render(scale);
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
