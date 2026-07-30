package com.micatechnologies.minecraft.ldib.client.render;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

/**
 * A blocky self-balancing one-wheel board: one fat slick tyre standing in a slot in the middle of a
 * matte-black shell, a grip pad fore and aft of it for the rider's feet, an anodised rail down each
 * side, and a bumper capping each end with a light bar in it.
 *
 * <p>Same conventions as {@link ModelRideable}: <b>+y is down</b>, the tyre touches the ground at
 * {@code model_y = +6}, and the <b>front faces −z</b>. The tyre is radius 4 and a full 5 px across —
 * by some distance the widest wheel here (the scooter's are 1 px) — because that squat, fat tyre is
 * the entire silhouette of the thing. It needs its own {@code FATTYRE} atlas region for exactly that
 * reason; nothing that wide fits the shared {@code TYRE} rect.</p>
 *
 * <p><b>Nothing on this model steers.</b> Every other rideable here has a front assembly that swings
 * about a head tube, and {@link #beginSteer} exists to turn it; a board has no fork, no stem and no
 * bars, and is turned by leaning it over. So {@link ModelRideable#setSteerAngle} is quietly ignored
 * here and the cosmetic lean {@code RenderBike} already applies to the whole model carries the turn on
 * its own — which is not a shortcut but the more accurate answer for this vehicle.</p>
 */
public class ModelOnewheel extends ModelRideable {

    private final ModelRenderer[] wheel;
    private final ModelRenderer shellFront;
    private final ModelRenderer shellRear;
    private final ModelRenderer railLeft;
    private final ModelRenderer railRight;
    private final ModelRenderer bumperFront;
    private final ModelRenderer bumperRear;
    private final ModelRenderer padFront;
    private final ModelRenderer padRear;
    private final ModelRenderer headHousing;
    private final ModelRenderer headLens;
    private final ModelRenderer headGlow;
    private final ModelRenderer brakeHousing;
    private final ModelRenderer brakeLens;
    private final ModelRenderer brakeGlow;

    /** Tyre radius in px. Ground is at {@code y = +6}, so the axle sits at {@code y = +2}. */
    private static final int WHEEL_RADIUS = 4;

    /** Tyre width in px across the axle — a go-kart slick, not a bicycle tyre. */
    private static final int WHEEL_WIDTH = 5;

    /**
     * Correction from the shared wheel-spin rate to this wheel's actual size.
     *
     * <p>{@code EntityBike.WHEEL_RADIANS_PER_BLOCK} is a single constant for every rideable, derived
     * from a ~0.4-block wheel. This tyre is {@link #WHEEL_RADIUS} px = 0.25 blocks, so rolling it at
     * the shared rate would visibly under-spin it — and on a board whose one wheel is the whole
     * silhouette, that reads as slipping. Scaling here rather than making the shared constant
     * per-variant keeps the fix local: the bike and both scooters keep the spin rate their owner has
     * already eyeballed.</p>
     */
    private static final float SPIN_SCALE = 0.4F / (WHEEL_RADIUS / 16.0F);

    public ModelOnewheel() {
        this.textureWidth = ATLAS_W;
        this.textureHeight = ATLAS_H;

        // Per-material UV (see ModelRideable's atlas map): tyre = FATTYRE, deck shells + bumpers =
        // SHELL (matte black plastic), side rails = RAIL (anodised metal), foot pads = ACCENT (grip).

        // The tyre, axle across x at (y=+2, z=0): occupies y −2..+6, z −4..+4, x ±2.5. No spokes — a
        // one-wheel tyre is a slick, and there is nothing to see through anyway.
        wheel = buildWheel(2.0F, 0.0F, WHEEL_RADIUS, WHEEL_WIDTH, FATTYRE_U, FATTYRE_V);

        // Deck shells fore and aft of the tyre. They stop 0.5 px short of the tyre's z extent so no
        // shell face lands on the plane a rim segment sweeps through.
        shellFront = box(-3.5F, 2.0F, -9.5F, 7, 3, 5, SHELL_U, SHELL_V);
        shellRear = box(-3.5F, 2.0F, 4.5F, 7, 3, 5, SHELL_U, SHELL_V);

        // Side rails bridging the wheel slot, outboard of the 5 px tyre (x ±3..±4, clearing it by
        // 0.5 px) and offset a quarter-pixel from the shells they bury into (y 2.25..4.25 vs 2..5), so
        // the overlap has no coplanar faces to fight over — the lug trick from ModelRideable.
        //
        // They sit strictly INSIDE the shell's height rather than spanning it. A rail as deep as the
        // shell hid the bottom third of the tyre from side-on, which is the one view that has to read
        // as "board with a fat wheel through it"; keeping it shallow leaves the tyre proud above and
        // exposed below, which is what the real thing looks like.
        railLeft = box(-4.0F, 2.25F, -5.0F, 1, 2, 10, RAIL_U, RAIL_V);
        railRight = box(3.0F, 2.25F, -5.0F, 1, 2, 10, RAIL_U, RAIL_V);

        // End bumpers, 1 px into each shell and narrower than it (x ±3 vs ±3.5) for the same reason.
        bumperFront = box(-3.0F, 1.5F, -11.5F, 6, 4, 3, SHELL_U, SHELL_V);
        bumperRear = box(-3.0F, 1.5F, 8.5F, 6, 4, 3, SHELL_U, SHELL_V);

        // Grip pads, sunk 0.5 px into the shells so they stand proud without sharing the shell's top
        // face. Their top at y = 1.5 is what RiderPose.BOARD's mount height is measured from.
        padFront = box(-3.0F, 1.5F, -9.0F, 6, 1, 4, ACCENT_U, ACCENT_V);
        padRear = box(-3.0F, 1.5F, 5.0F, 6, 1, 4, ACCENT_U, ACCENT_V);

        // Lights = permanent grey housing (render) + emissive lens + additive glow (renderLights). A
        // wide, low bar across each bumper rather than the bikes' round lamp — white ahead, red behind.
        headHousing = box(-2.5F, 2.0F, -12.0F, 5, 2, 1);
        headLens = box(-2.0F, 2.5F, -12.5F, 4, 1, 1);
        headGlow = box(-3.5F, 1.5F, -14.0F, 7, 4, 3);

        brakeHousing = box(-2.5F, 2.0F, 11.0F, 5, 2, 1);
        brakeLens = box(-2.0F, 2.5F, 12.0F, 4, 1, 1);
        brakeGlow = box(-3.5F, 1.5F, 12.0F, 7, 4, 2);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        // One flat pass: no beginSteer anywhere, because nothing on a board steers (see class docs).
        renderGroup(wheel, scale);
        shellFront.render(scale);
        shellRear.render(scale);
        railLeft.render(scale);
        railRight.render(scale);
        bumperFront.render(scale);
        bumperRear.render(scale);
        padFront.render(scale);
        padRear.render(scale);
        renderHardware(scale, headHousing, brakeHousing);
    }

    @Override
    public void setWheelSpin(float wheelAngle) {
        spinWheel(wheel, wheelAngle * SPIN_SCALE);
    }

    @Override
    public void renderLights(float scale, boolean headlightOn, boolean brakeLightOn, float intensity) {
        // Both fixtures are fixed to the board, so unlike the bike and scooter there is no steered
        // front pass to split out — one call draws the lot.
        renderLightFixtures(scale, headlightOn, headLens, headGlow,
            brakeLightOn, brakeLens, brakeGlow, intensity);
    }
}
