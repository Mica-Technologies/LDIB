package com.micatechnologies.minecraft.ldib.client.render.mesh;

/**
 * A self-balancing one-wheel board: one fat round tyre standing in a slot in the middle of a
 * matte-black shell, a grip pad fore and aft for the rider's feet, an anodised rail down each side,
 * and a bumper with a light bar capping each end.
 *
 * <p>The tyre is {@value #WHEEL_RADIUS} px in radius and a full {@value #WHEEL_WIDTH} px across — by
 * some distance the widest wheel in the mod — because that squat tyre is the whole silhouette of the
 * thing.</p>
 *
 * <p><b>Nothing on this model steers.</b> A board has no fork, stem or bars and is turned by leaning
 * it over, so the steer angle is ignored and the lean the renderer applies to every rideable carries
 * the turn by itself. Two draw calls.</p>
 */
public final class OnewheelModel extends RideableModel {

    private static final float WHEEL_RADIUS = 4.0F;
    private static final float WHEEL_WIDTH = 5.0F;
    private static final float AXLE_Y = 6.0F - WHEEL_RADIUS;

    private static final float SPIN_SCALE = NOMINAL_WHEEL_RADIUS / (WHEEL_RADIUS / 16.0F);

    private final Mesh body;
    private final Mesh wheel;

    public OnewheelModel() {
        this.wheel = Wheels.fat(WHEEL_RADIUS, WHEEL_WIDTH, 24, 6);
        this.body = new MeshBuilder()
            // Deck shells fore and aft of the tyre, stopping half a pixel short of it.
            .box(-3.5, 2.0, -9.5, 3.5, 5.0, -4.5, Skin.SHELL)
            .box(-3.5, 2.0, 4.5, 3.5, 5.0, 9.5, Skin.SHELL)
            // Side rails bridging the wheel slot, outboard of the tyre and shallower than the shells,
            // so the tyre stands proud above them and shows below — the one view that has to read as
            // "a board with a fat wheel through it".
            .box(-4.0, 2.25, -5.0, -3.0, 4.25, 5.0, Skin.RAIL)
            .box(3.0, 2.25, -5.0, 4.0, 4.25, 5.0, Skin.RAIL)
            // End bumpers, narrower than the shells and a quarter-pixel below the pads' top.
            .box(-3.0, 1.75, -11.5, 3.0, 5.75, -8.5, Skin.SHELL)
            .box(-3.0, 1.75, 8.5, 3.0, 5.75, 11.5, Skin.SHELL)
            // Grip pads. Their top at y = 1.5 is what the rider's standing height is measured from.
            .box(-2.5, 1.5, -9.0, 2.5, 2.5, -5.0, Skin.ACCENT)
            .box(-2.5, 1.5, 5.0, 2.5, 2.5, 9.0, Skin.ACCENT)
            // Light bar housings across each bumper.
            .box(-2.5, 2.0, -12.0, 2.5, 4.0, -11.0, Skin.HARDWARE)
            .box(-2.5, 2.0, 11.0, 2.5, 4.0, 12.0, Skin.HARDWARE)
            .build();

        headLensOff = lens(-2.0, 2.5, -12.5, 2.0, 3.5, -12.0, Skin.HEAD_LENS_OFF);
        headLensOn = lens(-2.0, 2.5, -12.5, 2.0, 3.5, -12.0, Skin.HEAD_LENS_ON);
        headGlow = halo(0, 3.0, -13.4, 3.0, Skin.GLOW_WHITE);
        rearLensOff = lens(-2.0, 2.5, 12.0, 2.0, 3.5, 12.5, Skin.REAR_LENS_OFF);
        rearLensOn = lens(-2.0, 2.5, 12.0, 2.0, 3.5, 12.5, Skin.REAR_LENS_ON);
        rearGlow = halo(0, 3.0, 13.3, 2.8, Skin.GLOW_RED);
    }

    @Override
    public void render(Canvas c, float wheelAngle, float crankAngle, float steer, boolean parked) {
        c.draw(body);
        drawWheel(c, wheel, AXLE_Y, 0.0F, (float) Math.toDegrees(wheelAngle * SPIN_SCALE));
    }
}
