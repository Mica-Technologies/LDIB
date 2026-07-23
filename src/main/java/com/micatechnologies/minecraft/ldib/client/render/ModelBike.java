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
    private final ModelRenderer crankArm;
    private final ModelRenderer pedalLeft;
    private final ModelRenderer pedalRight;
    private final ModelRenderer downTube;
    private final ModelRenderer seatTube;
    private final ModelRenderer topTube;
    private final ModelRenderer fork;
    private final ModelRenderer chainStay;
    private final ModelRenderer seatStay;
    private final ModelRenderer stem;
    private final ModelRenderer handlebar;
    private final ModelRenderer saddle;
    /** Optional accessories, {@code null} when absent: a battery pack on the down tube and/or a front
     *  basket. A share e-bike carries both; a personal e-bike just the battery; a bicycle just the basket. */
    private final ModelRenderer battery;
    private final ModelRenderer basket;
    private final ModelRenderer basketBracket;
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

    /** Whether this is the e-bike (has electric lights + a battery) vs the plain pedal bicycle. */
    private final boolean electric;

    /** Crank radius (BB to pedal), px — well inside the wheel radius (7) so a pedal swinging down never
     *  pokes past the tyre. */
    private static final int CRANK_RADIUS = 4;
    /** Crank arm cross-section, px — thin, and (being 1 px like the tyres, not 2 px like the frame
     *  tubes) never coplanar with a tube face even on the rare frame it swings past one. */
    private static final int CRANK_THICK = 1;
    /** Pedal cube, px. */
    private static final int PEDAL_SIZE = 2;
    /** How far outboard (±x) each pedal sits from the centre plane the frame tubes occupy — clears the
     *  2 px-wide chain stay / down tube instead of clipping through them. */
    private static final float PEDAL_OUTBOARD = 2.5F;

    /**
     * @param hasBattery {@code true} to fit a battery pack + electric lights (any e-bike).
     * @param hasBasket  {@code true} to fit a front basket (the pedal bicycle, and the share e-bike).
     */
    public ModelBike(boolean hasBattery, boolean hasBasket) {
        this.electric = hasBattery;
        this.textureWidth = ATLAS_W;
        this.textureHeight = ATLAS_H;

        // Per-material UV (see ModelRideable's atlas map): wheels = TYRE (near-black), frame tubes +
        // lugs = FRAME (variant colour), saddle + handlebar grips = ACCENT (secondary colour).

        // Wheels (radius 7 = 14 px ≈ 0.875 block): front toward −z, rear toward +z. Thin 1 px tyres.
        // Spoked — the bike's wheels are big enough (unlike the scooter's) for spokes to read clearly.
        frontWheel = buildWheel(-1.0F, -7.0F, 7, 1, TYRE_U, TYRE_V, true);
        rearWheel = buildWheel(-1.0F, 7.0F, 7, 1, TYRE_U, TYRE_V, true);

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

        // Handlebar shrunk ~0.5 px (→ ~1 px) so it is strictly narrower than the 2 px stem it meets,
        // burying the joint instead of sharing a coplanar face (no z-fighting there).
        handlebar = new ModelRenderer(this, ACCENT_U, ACCENT_V);
        handlebar.addBox(-5.0F, -1.0F, -1.0F, 10, 2, 2, -0.5F);
        handlebar.setRotationPoint(0.0F, -12.0F, -6.0F);

        // Saddle: 4 px wide so it is not coplanar with the 3 px seat lug it sits over.
        saddle = new ModelRenderer(this, ACCENT_U, ACCENT_V);
        saddle.addBox(-2.0F, -1.0F, -3.0F, 4, 1, 6);
        saddle.setRotationPoint(0.0F, -12.0F, 5.0F);

        // Accessories. Battery: a 3 px pack straddling (burying) the 2 px down tube. Basket: a solid
        // 5x4x4 box just in FRONT of the handlebar (rear wall at z −8, clear of the z −7..−5 bar) so
        // that wall renders and you can't see through it from first person; a short 2 px bracket buries
        // into both the basket and the handlebar so it reads as mounted, not floating.
        battery = hasBattery ? box(-1.5F, -6.0F, -4.0F, 3, 5, 3, METAL_U, METAL_V) : null;
        basket = hasBasket ? box(-2.5F, -13.0F, -12.0F, 5, 4, 4, METAL_U, METAL_V) : null;
        basketBracket = hasBasket ? box(-1.0F, -12.0F, -9.0F, 2, 2, 4, METAL_U, METAL_V) : null;

        // Crank + pedals, rotating about the bottom bracket in time with the wheels (set from the same
        // wheelAngle in setWheelSpin, so the animated rider's legs, the crank and the wheels never drift
        // out of phase). The arm is a single thin diametral bar through the BB — both pedal positions at
        // once — so spinning it by one angle already keeps its two ends 180° apart; the two pedal cubes
        // are built identically (at the "up" end of the arm) but offset to opposite outboard sides
        // (clearing the 2 px chain stay / down tube) and are re-aimed with a fixed π offset between them
        // in setWheelSpin, landing each on its own arm end without duplicating the arm's geometry.
        crankArm = new ModelRenderer(this, FRAME_U, FRAME_V);
        crankArm.addBox(-CRANK_THICK / 2.0F, -CRANK_RADIUS, -CRANK_THICK / 2.0F,
            CRANK_THICK, CRANK_RADIUS * 2, CRANK_THICK);
        crankArm.setRotationPoint(0.0F, 2.0F, 0.0F);

        pedalRight = new ModelRenderer(this, METAL_U, METAL_V);
        pedalRight.addBox(PEDAL_OUTBOARD - PEDAL_SIZE / 2.0F, -CRANK_RADIUS - PEDAL_SIZE / 2.0F, -PEDAL_SIZE / 2.0F,
            PEDAL_SIZE, PEDAL_SIZE, PEDAL_SIZE);
        pedalRight.setRotationPoint(0.0F, 2.0F, 0.0F);

        pedalLeft = new ModelRenderer(this, METAL_U, METAL_V);
        pedalLeft.addBox(-PEDAL_OUTBOARD - PEDAL_SIZE / 2.0F, -CRANK_RADIUS - PEDAL_SIZE / 2.0F, -PEDAL_SIZE / 2.0F,
            PEDAL_SIZE, PEDAL_SIZE, PEDAL_SIZE);
        pedalLeft.setRotationPoint(0.0F, 2.0F, 0.0F);

        // Joint lugs bury the overlapping tube ends at each convergence (see ModelRideable).
        bbLug = lug(2.0F, 0.0F, 3, FRAME_U, FRAME_V);   // bottom bracket: down/seat/chain
        htLug = lug(-9.0F, -6.0F, 3, FRAME_U, FRAME_V); // head tube: down/top/fork/stem
        stLug = lug(-11.0F, 5.0F, 3, FRAME_U, FRAME_V); // seat cluster: seat/top/seat-stay
        raLug = lug(-1.0F, 7.0F, 3, FRAME_U, FRAME_V);  // rear axle: chain + seat stays

        // Lights = permanent grey housing (drawn in render) + emissive lens + additive glow (drawn in
        // renderLights). Headlight at the front (−z), brake at the rear (+z).
        // Headlight at head-tube height normally, but dropped to the fork on a basket bike so the
        // basket has the front to itself.
        float headY = hasBasket ? -3.0F : -9.0F;
        headHousing = box(-1.5F, headY, -11.0F, 3, 3, 2);
        headLens = box(-1.0F, headY + 0.5F, -11.5F, 2, 2, 1);
        headGlow = box(-3.0F, headY - 1.0F, -13.0F, 6, 6, 3);

        brakeHousing = box(-1.5F, -10.0F, 8.0F, 3, 3, 2);
        brakeLens = box(-1.0F, -9.5F, 10.0F, 2, 2, 1);
        brakeGlow = box(-2.5F, -10.5F, 10.0F, 5, 5, 2);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        renderGroup(frontWheel, scale);
        renderGroup(rearWheel, scale);
        crankArm.render(scale);
        pedalLeft.render(scale);
        pedalRight.render(scale);
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
        if (battery != null) {
            battery.render(scale);
        }
        if (basket != null) {
            basket.render(scale);
            basketBracket.render(scale);
        }
        // Light housings are electric hardware — only the e-bike has them; the plain bicycle shows none
        // (which also frees the front for its basket). The lenses/glow are gated the same way by
        // BikeVariant#hasLights() before renderLights is ever called.
        if (electric) {
            renderHardware(scale, headHousing, brakeHousing);
        }
    }

    @Override
    public void setWheelSpin(float wheelAngle) {
        spinWheel(frontWheel, wheelAngle);
        spinWheel(rearWheel, wheelAngle);
        // Same angle as the wheels, so the crank stays in phase with them (and with the rider's
        // already-animated pedalling legs, which are driven from the same value). The right pedal
        // shares the arm's angle; the left is a fixed half-turn (π) ahead — see the field comment.
        crankArm.rotateAngleX = wheelAngle;
        pedalRight.rotateAngleX = wheelAngle;
        pedalLeft.rotateAngleX = wheelAngle + (float) Math.PI;
    }

    @Override
    public void renderLights(float scale, boolean headlightOn, boolean brakeLightOn, float intensity) {
        renderLightFixtures(scale, headlightOn, headLens, headGlow,
            brakeLightOn, brakeLens, brakeGlow, intensity);
    }
}
