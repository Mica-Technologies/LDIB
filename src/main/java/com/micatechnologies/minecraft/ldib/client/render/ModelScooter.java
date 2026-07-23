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
    private final ModelRenderer stemLug;
    private final ModelRenderer headHousing;
    private final ModelRenderer headLens;
    private final ModelRenderer headGlow;
    private final ModelRenderer brakeHousing;
    private final ModelRenderer brakeLens;
    private final ModelRenderer brakeGlow;

    /** Thin the stem ~33% from 2 px: −0.33 px on each face → ~1.34 px. */
    private static final float THIN = -0.33F;
    /** The handlebar is thinner still (~1 px) so it is strictly narrower than the 1.34 px stem it meets
     *  — their faces never coincide at the joint, so no z-fighting there. */
    private static final float HANDLEBAR_THIN = -0.5F;

    /**
     * @param performance {@code true} for the fast/performance scooter variant (chunkier front head
     *                    box), {@code false} for the standard scooter (no accessory) — the only
     *                    difference from the base scooter geometry.
     */
    public ModelScooter(boolean performance) {
        this.textureWidth = ATLAS_W;
        this.textureHeight = ATLAS_H;

        // Per-material UV (see ModelRideable's atlas map): wheels = TYRE (black), stem/fork/lug =
        // FRAME (stem colour), deck grip-tape + handlebar = ACCENT (secondary), fender = METAL (grey).

        // Small wheels (radius 3 = 6 px): front toward −z, rear toward +z, centred at model_y = +3 so
        // the bottom sits on the ground (+6). Thin 1 px tyres.
        frontWheel = buildWheel(3.0F, -6.0F, 3, 1, TYRE_U, TYRE_V);
        rearWheel = buildWheel(3.0F, 6.0F, 3, 1, TYRE_U, TYRE_V);

        // Low deck the rider stands on. The performance scooter gets a wider deck (6 px vs 4) — a
        // clean, clip-free way to make it read as beefier than the standard scooter.
        int deckWidth = performance ? 6 : 4;
        deck = new ModelRenderer(this, ACCENT_U, ACCENT_V);
        deck.addBox(-deckWidth / 2.0F, 4.0F, -3.0F, deckWidth, 1, 9);
        deck.setRotationPoint(0.0F, 0.0F, 0.0F);

        // Tall front stem up to the handlebars (slimmed ~33%), plus a short fork to the front wheel.
        stem = tube(4.0F, -6.0F, -14.0F, -6.0F, 2, THIN, FRAME_U, FRAME_V);
        fork = tube(4.0F, -6.0F, 3.0F, -6.0F, 2, FRAME_U, FRAME_V);

        handlebar = new ModelRenderer(this, ACCENT_U, ACCENT_V);
        handlebar.addBox(-5.0F, -1.0F, -1.0F, 10, 2, 2, HANDLEBAR_THIN);
        handlebar.setRotationPoint(0.0F, -14.0F, -6.0F);

        // Bury the stem/fork base overlap so their faces don't fight.
        stemLug = lug(4.0F, -6.0F, 3, FRAME_U, FRAME_V);

        // Rear fender over the back wheel.
        fender = new ModelRenderer(this, METAL_U, METAL_V);
        fender.addBox(-2.0F, 0.0F, 4.0F, 4, 1, 4);
        fender.setRotationPoint(0.0F, 0.0F, 0.0F);

        // Lights = permanent grey housing (render) + emissive lens + additive glow (renderLights).
        // Headlight on the stem front (−z), brake light on the rear fender (+z).
        headHousing = box(-1.5F, -9.0F, -8.0F, 3, 3, 2);
        headLens = box(-1.0F, -8.5F, -8.5F, 2, 2, 1);
        headGlow = box(-3.0F, -10.0F, -10.5F, 6, 6, 3);

        brakeHousing = box(-1.5F, -2.0F, 8.0F, 3, 3, 2);
        brakeLens = box(-1.0F, -1.5F, 10.0F, 2, 2, 1);
        brakeGlow = box(-2.5F, -2.5F, 10.0F, 5, 5, 2);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        renderGroup(frontWheel, scale);
        renderGroup(rearWheel, scale);
        stemLug.render(scale);
        deck.render(scale);
        stem.render(scale);
        fork.render(scale);
        handlebar.render(scale);
        fender.render(scale);
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
