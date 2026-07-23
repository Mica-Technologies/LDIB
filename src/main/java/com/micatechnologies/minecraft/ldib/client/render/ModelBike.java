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
    private final ModelRenderer bbLug;
    private final ModelRenderer htLug;
    private final ModelRenderer stLug;
    private final ModelRenderer raLug;
    private final ModelRenderer headHousing;
    private final ModelRenderer headLens;
    private final ModelRenderer headGlow;
    private final ModelRenderer brakeHousing;
    private final ModelRenderer brakeLens;
    private final ModelRenderer brakeGlow;

    public ModelBike() {
        this.textureWidth = ATLAS_W;
        this.textureHeight = ATLAS_H;

        // Per-material UV (see ModelRideable's atlas map): wheels = TYRE (near-black), frame tubes +
        // lugs = FRAME (variant colour), saddle + handlebar grips = ACCENT (secondary colour).

        // Wheels (radius 7 = 14 px ≈ 0.875 block): front toward −z, rear toward +z. Thin 1 px tyres.
        frontWheel = buildWheel(-1.0F, -7.0F, 7, 1, TYRE_U, TYRE_V);
        rearWheel = buildWheel(-1.0F, 7.0F, 7, 1, TYRE_U, TYRE_V);

        // Frame nodes (model space, +y down, front −z):
        //   bottom bracket BB(+2, 0), head tube HT(−9, −6), seat top ST(−11, +5),
        //   front axle FA(−1, −7), rear axle RA(−1, +7).
        downTube = tube(2.0F, 0.0F, -9.0F, -6.0F, 2, FRAME_U, FRAME_V);   // BB → head tube
        seatTube = tube(2.0F, 0.0F, -11.0F, 5.0F, 2, FRAME_U, FRAME_V);   // BB → seat
        topTube = tube(-9.0F, -6.0F, -11.0F, 5.0F, 2, FRAME_U, FRAME_V);  // head tube → seat
        fork = tube(-9.0F, -6.0F, -1.0F, -7.0F, 2, FRAME_U, FRAME_V);     // head tube → front axle
        chainStay = tube(2.0F, 0.0F, -1.0F, 7.0F, 2, FRAME_U, FRAME_V);   // BB → rear axle
        seatStay = tube(-11.0F, 5.0F, -1.0F, 7.0F, 2, FRAME_U, FRAME_V);  // seat → rear axle
        stem = tube(-9.0F, -6.0F, -12.0F, -6.0F, 2, FRAME_U, FRAME_V);    // head tube → handlebar

        handlebar = new ModelRenderer(this, ACCENT_U, ACCENT_V);
        handlebar.addBox(-5.0F, -1.0F, -1.0F, 10, 2, 2);
        handlebar.setRotationPoint(0.0F, -12.0F, -6.0F);

        // Saddle: 4 px wide so it is not coplanar with the 3 px seat lug it sits over.
        saddle = new ModelRenderer(this, ACCENT_U, ACCENT_V);
        saddle.addBox(-2.0F, -1.0F, -3.0F, 4, 1, 6);
        saddle.setRotationPoint(0.0F, -12.0F, 5.0F);

        // Joint lugs bury the overlapping tube ends at each convergence (see ModelRideable).
        bbLug = lug(2.0F, 0.0F, 3, FRAME_U, FRAME_V);   // bottom bracket: down/seat/chain
        htLug = lug(-9.0F, -6.0F, 3, FRAME_U, FRAME_V); // head tube: down/top/fork/stem
        stLug = lug(-11.0F, 5.0F, 3, FRAME_U, FRAME_V); // seat cluster: seat/top/seat-stay
        raLug = lug(-1.0F, 7.0F, 3, FRAME_U, FRAME_V);  // rear axle: chain + seat stays

        // Lights = permanent grey housing (drawn in render) + emissive lens + additive glow (drawn in
        // renderLights). Headlight at the front (−z), brake at the rear (+z).
        headHousing = box(-1.5F, -9.0F, -11.0F, 3, 3, 2);
        headLens = box(-1.0F, -8.5F, -11.5F, 2, 2, 1);
        headGlow = box(-3.0F, -10.0F, -13.0F, 6, 6, 3);

        brakeHousing = box(-1.5F, -10.0F, 8.0F, 3, 3, 2);
        brakeLens = box(-1.0F, -9.5F, 10.0F, 2, 2, 1);
        brakeGlow = box(-2.5F, -10.5F, 10.0F, 5, 5, 2);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        renderGroup(frontWheel, scale);
        renderGroup(rearWheel, scale);
        bbLug.render(scale);
        htLug.render(scale);
        stLug.render(scale);
        raLug.render(scale);
        downTube.render(scale);
        seatTube.render(scale);
        topTube.render(scale);
        fork.render(scale);
        chainStay.render(scale);
        seatStay.render(scale);
        stem.render(scale);
        handlebar.render(scale);
        saddle.render(scale);
        renderHardware(scale, headHousing, brakeHousing);
    }

    @Override
    public void setWheelSpin(float wheelAngle) {
        spinWheel(frontWheel, wheelAngle);
        spinWheel(rearWheel, wheelAngle);
    }

    @Override
    public void renderLights(float scale, boolean headlightOn, boolean brakeLightOn, float intensity) {
        renderLightFixtures(scale, headlightOn, headLens, headGlow,
            brakeLightOn, brakeLens, brakeGlow, intensity);
    }
}
