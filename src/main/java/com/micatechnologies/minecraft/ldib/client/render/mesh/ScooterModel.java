package com.micatechnologies.minecraft.ldib.client.render.mesh;

/**
 * A stand-on scooter (Bird / Segway-style): two small round wheels, a low deck, a tall stem with
 * handlebars, and a rear fender. The body is the same blocky shape it has always been; the wheels are
 * round, with sectored discs that show them turning.
 *
 * <p>Wheels are {@value #WHEEL_RADIUS} px in radius — real e-scooter proportions, far smaller than a
 * bicycle's — and the deck sits low between them. Three draw calls plus the wheels.</p>
 */
public final class ScooterModel extends RideableModel {

    private static final float WHEEL_RADIUS = 3.0F;
    private static final float AXLE_Y = 6.0F - WHEEL_RADIUS;
    private static final float FRONT_Z = -6.0F;
    private static final float REAR_Z = 6.0F;

    private static final float SPIN_SCALE = NOMINAL_WHEEL_RADIUS / (WHEEL_RADIUS / 16.0F);

    private final Mesh body;
    private final Mesh front;
    private final Mesh wheel;

    /**
     * @param performance {@code true} for the fast scooter, which gets a wider deck so it reads as the
     *                    beefier of the two
     */
    public ScooterModel(boolean performance) {
        this.steerPivotZ = -6.0F; // the stem
        this.headlightSteers = true;
        this.wheel = Wheels.disc(WHEEL_RADIUS, 20, 5);

        double deckHalf = performance ? 3.0 : 2.0;
        this.body = new MeshBuilder()
            .box(-deckHalf, 4.0, -3.0, deckHalf, 5.0, 6.0, Skin.ACCENT)       // deck
            .box(-2.0, 0.0, 4.0, 2.0, 1.0, 8.0, Skin.METAL)                   // rear fender
            .box(-1.5, -2.0, 8.0, 1.5, 1.0, 10.0, Skin.HARDWARE)              // tail light housing
            .build();

        // The whole steering column is one rigid unit. The stem and the bar are each a different
        // thickness from what they meet, so no two faces share a plane at a joint.
        this.front = new MeshBuilder()
            .box(-1.5, 2.5, -7.5, 1.5, 5.5, -4.5, Skin.FRAME)                 // base lug
            .box(-0.67, -13.67, -6.67, 0.67, 3.67, -5.33, Skin.FRAME)         // stem
            .box(-1.0, 3.0, -7.0, 1.0, 4.0, -5.0, Skin.FRAME)                 // fork
            .box(-4.5, -14.5, -6.5, 4.5, -13.5, -5.5, Skin.ACCENT)            // handlebar
            .box(-1.5, -9.0, -8.0, 1.5, -6.0, -6.0, Skin.HARDWARE)            // headlight housing
            .build();

        headLensOff = lens(-1.0, -8.5, -8.5, 1.0, -6.5, -8.0, Skin.HEAD_LENS_OFF);
        headLensOn = lens(-1.0, -8.5, -8.5, 1.0, -6.5, -8.0, Skin.HEAD_LENS_ON);
        headGlow = halo(0, -7.5, -9.3, 3.0, Skin.GLOW_WHITE);
        rearLensOff = lens(-1.0, -1.5, 10.0, 1.0, 0.5, 10.5, Skin.REAR_LENS_OFF);
        rearLensOn = lens(-1.0, -1.5, 10.0, 1.0, 0.5, 10.5, Skin.REAR_LENS_ON);
        rearGlow = halo(0, -0.5, 11.0, 2.5, Skin.GLOW_RED);
    }

    @Override
    public void render(Canvas c, float wheelAngle, float crankAngle, float steer, boolean parked) {
        float wheelDeg = (float) Math.toDegrees(wheelAngle * SPIN_SCALE);
        c.draw(body);
        drawWheel(c, wheel, AXLE_Y, REAR_Z, wheelDeg);
        beginSteer(c, steer);
        c.draw(front);
        drawWheel(c, wheel, AXLE_Y, FRONT_Z, wheelDeg);
        c.pop();
    }
}
