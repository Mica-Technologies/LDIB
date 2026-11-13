package com.micatechnologies.minecraft.ldib.client.render.mesh;

/**
 * The bicycle and the e-bike: a diamond frame on two round, spoked wheels, with the parts that make
 * a bike read as a bike — twin fork blades and stays either side of the wheels, a chainring, chain
 * and sprocket, mudguards, a shaped saddle, grips and brake levers, pedals that stay level as the
 * crank turns, and a kickstand that comes down when nobody is riding. The frame tubes stay
 * square-section: that is the Minecraft in it.
 *
 * <p><b>The bike is built around the rider, not the other way round.</b> A rider sits at the
 * entity's origin (model z = 0) with their hips about 13.6 px above the ground (model y ≈ −7.6), and
 * their hands come down about 8 px ahead of that. Those are facts of the mounting code and the
 * player model, verified in play, and nothing here may move them. So the saddle is put under the
 * hips, the handlebar under the hands, and the bottom bracket below and a little ahead of the saddle
 * where the feet come down (the rider's legs cannot bend, so the pedals have to turn about a point
 * a straight leg can follow) — and the wheels go where a bicycle's wheels then have to be: the rear
 * one just behind the saddle, the front one well out ahead. That is why the bike is not centred on
 * its own origin; a real rider is not centred between the wheels either. The first model put the
 * saddle 5 px behind and 5 px above where the rider actually was.</p>
 *
 * <p>The wheels are {@value #WHEEL_RADIUS} px in radius, 16 px apart, so they do not touch each other
 * and the frame does not pass through them: the down tube clears the front tyre and the seat tube
 * the rear, as on a real frame.</p>
 *
 * <p><b>Parked</b>, there is no rider to fit, and racks and docks hold a bike by its wheels; {@link
 * #parkingShift()} slides it back so the wheels sit either side of the slot's centre.</p>
 *
 * <p>Five rigid groups, seven draw calls: the frame, the steering assembly, one wheel drawn twice,
 * the crank, and one pedal drawn twice (plus the kickstand when parked).</p>
 */
public final class BikeModel extends RideableModel {

    private static final double TURN = 2.0 * Math.PI;

    /** Tyre radius, px. The ground is at y = +6, so the axles sit at {@link #AXLE_Y}. */
    private static final float WHEEL_RADIUS = 5.5F;
    private static final float AXLE_Y = 6.0F - WHEEL_RADIUS;
    private static final float FRONT_Z = -11.0F;
    private static final float REAR_Z = 6.0F;

    /** Bottom bracket — the crank's axle. */
    private static final float BB_Y = 2.0F;
    private static final float BB_Z = -1.0F;
    /** Crank length, px. Short enough that a pedal at the bottom of its stroke clears the ground. */
    private static final float CRANK = 3.5F;

    /** The vertical steering axis: the head tube. */
    private static final float HEAD_Z = -8.0F;

    /** Where the down tube leaves the head tube. */
    private static final double DOWN_TUBE_Y = -7.6;
    private static final double DOWN_TUBE_Z = -7.4;

    private static final float SPIN_SCALE = NOMINAL_WHEEL_RADIUS / (WHEEL_RADIUS / 16.0F);

    private final Mesh body;
    private final Mesh front;
    private final Mesh wheel;
    private final Mesh crank;
    private final Mesh pedalRight;
    private final Mesh pedalLeft;
    private final Mesh kickstand;

    /**
     * @param electric {@code true} for an e-bike: a battery on the down tube, a hub motor, lights
     * @param basket   {@code true} to fit a front basket (the pedal bicycle, and the share e-bike)
     */
    public BikeModel(boolean electric, boolean basket) {
        this.steerPivotZ = HEAD_Z;
        this.wheel = Wheels.spoked(WHEEL_RADIUS, 32, 12);
        this.body = buildBody(electric, basket);
        this.front = buildFront(electric && !basket);
        this.crank = buildCrank();
        this.pedalRight = new MeshBuilder().box(2.25, -0.3, -0.9, 3.75, 0.3, 0.9, Skin.HARDWARE).build();
        this.pedalLeft = new MeshBuilder().box(-3.75, -0.3, -0.9, -2.25, 0.3, 0.9, Skin.HARDWARE).build();
        this.kickstand = new MeshBuilder()
            .beam(-1.5, 1.4, 4.4, -3.3, 6.0, 5.4, 0.4, 0.4, Skin.HARDWARE).build();

        if (electric) {
            if (basket) {
                // A share bike's lamp hangs under the front of its basket, and the basket is bolted
                // to the frame, so the beam does not swing with the bars.
                headlightSteers = false;
                headLensOff = lens(-0.6, -8.8, -14.95, 0.6, -8.0, -14.7, Skin.HEAD_LENS_OFF);
                headLensOn = lens(-0.6, -8.8, -14.95, 0.6, -8.0, -14.7, Skin.HEAD_LENS_ON);
                headGlow = halo(0, -8.4, -15.7, 3.0, Skin.GLOW_WHITE);
            } else {
                headlightSteers = true;
                headLensOff = lens(-0.6, -9.8, -10.45, 0.6, -8.8, -10.2, Skin.HEAD_LENS_OFF);
                headLensOn = lens(-0.6, -9.8, -10.45, 0.6, -8.8, -10.2, Skin.HEAD_LENS_ON);
                headGlow = halo(0, -9.3, -11.2, 3.0, Skin.GLOW_WHITE);
            }
            // The tail light rides on the end of the rear mudguard.
            rearLensOff = lens(-0.6, -2.1, 12.9, 0.6, -1.1, 13.15, Skin.REAR_LENS_OFF);
            rearLensOn = lens(-0.6, -2.1, 12.9, 0.6, -1.1, 13.15, Skin.REAR_LENS_ON);
            rearGlow = halo(0, -1.6, 13.9, 2.5, Skin.GLOW_RED);
        }
    }

    /** Everything bolted to the frame: tubes, stays, saddle, drivetrain, rear mudguard, accessories. */
    private static Mesh buildBody(boolean electric, boolean basket) {
        MeshBuilder b = new MeshBuilder();

        // The diamond. Tubes that meet all lie in the centre plane, so each gets its own width: two
        // of the same width would share side planes where they overlap at a joint, and flicker.
        b.box(-1.0, -10.5, -9.0, 1.0, -6.8, -7.0, Skin.FRAME);                         // head tube
        b.beam(0, -9.8, -7.2, 0, -5.5, 0.2, 1.4, 1.4, Skin.FRAME);                     // top tube
        b.beam(0, DOWN_TUBE_Y, DOWN_TUBE_Z, 0, BB_Y, BB_Z, 1.7, 1.7, Skin.FRAME);      // down tube
        b.beam(0, BB_Y, BB_Z, 0, -5.6, 0.7, 1.5, 1.5, Skin.FRAME);                     // seat tube
        b.beam(0, -5.3, 0.62, 0, -7.2, 1.08, 0.8, 0.8, Skin.ALLOY);                    // seat post

        // Bottom bracket shell, across the frame.
        b.push().translate(0, BB_Y, BB_Z);
        float[] shell = Skin.FRAME.texel(1, 2);
        b.revolve(-1.1, 0.9, 1.1, 0.9, 0, TURN, 10, 0, 1, shell);
        b.revolve(1.1, 0, 1.1, 0.9, 0, TURN, 10, 1, 0, shell);
        b.revolve(-1.1, 0, -1.1, 0.9, 0, TURN, 10, -1, 0, shell);
        b.pop();

        // Rear triangle: a chain stay and a seat stay each side of the wheel.
        for (int side = -1; side <= 1; side += 2) {
            b.beam(side * 0.95, BB_Y, BB_Z, side * 1.25, AXLE_Y, REAR_Z, 0.6, 0.8, Skin.FRAME);
            b.beam(side * 1.08, -5.2, 0.7, side * 1.25, AXLE_Y, REAR_Z, 0.6, 0.7, Skin.FRAME);
        }

        // Saddle: wide at the back, narrowing to a nose, directly under the rider's hips.
        b.box(-2.0, -7.8, 1.6, 2.0, -7.0, 4.4, Skin.ACCENT);
        b.box(-1.5, -7.8, 0.0, 1.5, -7.0, 1.6, Skin.ACCENT);
        b.box(-0.9, -7.8, -1.6, 0.9, -7.0, 0.0, Skin.ACCENT);

        // Drivetrain, on the right: sprocket on the rear axle, and the two runs of chain to the
        // chainring (which turns with the crank, so it lives in that group).
        b.push().translate(0, AXLE_Y, REAR_Z);
        float[] steel = Skin.ALLOY.texel(4, 3);
        b.revolve(1.6, 0, 1.6, 1.1, 0, TURN, 12, -1, 0, steel);
        b.revolve(1.85, 0, 1.85, 1.1, 0, TURN, 12, 1, 0, steel);
        b.revolve(1.6, 1.1, 1.85, 1.1, 0, TURN, 12, 0, 1, steel);
        b.pop();
        b.beam(1.72, BB_Y - 2.2, BB_Z, 1.72, AXLE_Y - 1.1, REAR_Z, 0.22, 0.35, Skin.HARDWARE);
        b.beam(1.72, BB_Y + 2.2, BB_Z, 1.72, AXLE_Y + 1.1, REAR_Z, 0.22, 0.35, Skin.HARDWARE);

        // Rear mudguard, from behind the seat tube round to just past the back of the wheel.
        b.push().translate(0, AXLE_Y, REAR_Z);
        mudguard(b, Math.toRadians(-35), Math.toRadians(100), 12);
        b.pop();

        if (electric) {
            // Battery pack clamped along the down tube, and a hub motor in the rear wheel.
            double ay = DOWN_TUBE_Y;
            double az = DOWN_TUBE_Z;
            double dy = BB_Y - ay;
            double dz = BB_Z - az;
            b.beam(0, ay + 0.22 * dy, az + 0.22 * dz, 0, ay + 0.78 * dy, az + 0.78 * dz, 2.6, 2.4, Skin.METAL.sub(4, 0, 9, 5));
            b.push().translate(0, AXLE_Y, REAR_Z);
            float[] motor = Skin.HARDWARE.texel(1, 1);
            b.revolve(-1.0, 1.5, 1.0, 1.5, 0, TURN, 14, 0, 1, motor);
            b.revolve(1.0, 0, 1.0, 1.5, 0, TURN, 14, 1, 0, motor);
            b.revolve(-1.0, 0, -1.0, 1.5, 0, TURN, 14, -1, 0, motor);
            b.pop();
            // Tail light housing, on the end of the rear mudguard.
            b.box(-0.8, -2.3, 11.9, 0.8, -0.9, 12.9, Skin.HARDWARE);
        }

        if (basket) {
            // An open-topped basket on a bracket off the head tube: a floor and four thin walls.
            // (The plain middle of the metal swatch: its corners carry rivets, which would repeat
            // on every wall.)
            Material wire = Skin.METAL.sub(4, 0, 9, 5);
            b.box(-2.5, -9.4, -14.5, 2.5, -9.0, -10.5, wire);
            b.box(-2.5, -12.6, -14.5, 2.5, -9.4, -14.1, wire);
            b.box(-2.5, -12.6, -10.9, 2.5, -9.4, -10.5, wire);
            b.box(-2.5, -12.6, -14.1, -2.1, -9.4, -10.9, wire);
            b.box(2.1, -12.6, -14.1, 2.5, -9.4, -10.9, wire);
            b.box(-0.5, -10.0, -10.5, 0.5, -9.2, -9.0, Skin.HARDWARE);
            if (electric) {
                b.box(-0.8, -9.0, -14.7, 0.8, -7.8, -13.7, Skin.HARDWARE); // lamp housing under it
            }
        }
        return b.build();
    }

    /** Everything that turns with the bars: stem, handlebar, fork, front mudguard. */
    private static Mesh buildFront(boolean lamp) {
        MeshBuilder b = new MeshBuilder();
        b.box(-0.6, -11.9, -8.6, 0.6, -10.5, -7.4, Skin.ALLOY);                        // stem
        b.box(-4.5, -11.9, -8.4, 4.5, -11.1, -7.6, Skin.ALLOY);                        // handlebar
        b.box(-2.4, -12.5, -8.35, -1.7, -11.9, -7.65, Skin.ALLOY);                     // bell
        b.box(-1.6, -6.8, -8.8, 1.6, -6.0, -7.2, Skin.FRAME);                          // fork crown
        for (int side = -1; side <= 1; side += 2) {
            b.box(side * 3.0, -12.05, -8.55, side * 4.7, -10.95, -7.45, Skin.ACCENT);  // grip
            b.beam(side * 3.0, -11.4, -8.5, side * 4.3, -11.2, -9.5, 0.25, 0.25, Skin.HARDWARE); // lever
            // A fork blade each side of the wheel, kinked forward to the axle.
            b.beam(side * 1.3, -6.2, -8.0, side * 1.3, -2.4, -8.7, 0.6, 0.9, Skin.FRAME);
            b.beam(side * 1.3, -2.4, -8.7, side * 1.3, AXLE_Y, FRONT_Z, 0.6, 0.9, Skin.FRAME);
        }
        // Front mudguard. It stops short at the back so it clears the down tube when the bars turn.
        b.push().translate(0, AXLE_Y, FRONT_Z);
        mudguard(b, Math.toRadians(-95), Math.toRadians(28), 11);
        b.pop();
        if (lamp) {
            b.box(-0.8, -10.0, -10.2, 0.8, -8.6, -9.0, Skin.HARDWARE);
        }
        return b.build();
    }

    /** The crank, about the bottom bracket: the chainring, and an arm each side half a turn apart. */
    private static Mesh buildCrank() {
        MeshBuilder b = new MeshBuilder();
        final float[] ring = Skin.ALLOY.texel(1, 1);
        final float[] cutout = Skin.HARDWARE.texel(0, 2);
        MeshBuilder.RingUv spider = new MeshBuilder.RingUv() {
            @Override
            public float[] uv(int segment) {
                return segment % 4 == 3 ? cutout : ring; // dark gaps, so the ring is seen to turn
            }
        };
        b.revolve(1.6, 0, 1.6, 2.2, 0, TURN, 16, -1, 0, spider);
        b.revolve(1.85, 0, 1.85, 2.2, 0, TURN, 16, 1, 0, spider);
        b.revolve(1.6, 2.2, 1.85, 2.2, 0, TURN, 16, 0, 1, ring);
        b.revolve(-2.25, 0.35, 2.25, 0.35, 0, TURN, 8, 0, 1, Skin.HARDWARE.texel(0, 1)); // spindle
        b.beam(2.1, 0, 0, 2.1, -CRANK, 0, 0.3, 0.7, Skin.ALLOY);
        b.beam(-2.1, 0, 0, -2.1, CRANK, 0, 0.3, 0.7, Skin.ALLOY);
        return b.build();
    }

    /**
     * A mudguard: a thin curved strip a little outside the tyre, about the axle the builder is
     * currently centred on, painted to match the frame.
     */
    private static void mudguard(MeshBuilder b, double from, double to, int segments) {
        double in = WHEEL_RADIUS + 0.6;
        double out = in + 0.2;
        double half = 0.7;
        float[] top = Skin.FRAME.texel(1, 3);
        float[] under = Skin.FRAME.texel(9, 15);
        b.revolve(-half, out, half, out, from, to, segments, 0, 1, top);
        b.revolve(-half, in, half, in, from, to, segments, 0, -1, under);
        b.revolve(half, in, half, out, from, to, segments, 1, 0, top);
        b.revolve(-half, in, -half, out, from, to, segments, -1, 0, top);
        // Close the two ends so the strip is not a see-through slot from in front or behind.
        for (int end = 0; end < 2; end++) {
            double t = end == 0 ? from : to;
            double sign = end == 0 ? -1 : 1;
            b.quad(MeshBuilder.ringPoint(-half, in, t), MeshBuilder.ringPoint(-half, out, t),
                MeshBuilder.ringPoint(half, out, t), MeshBuilder.ringPoint(half, in, t),
                new double[] {0, sign * Math.sin(t), sign * Math.cos(t)}, top);
        }
    }

    @Override
    public float parkingShift() {
        return -(FRONT_Z + REAR_Z) / 2.0F; // put the midpoint between the axles on the slot's centre
    }

    @Override
    public void render(Canvas c, float wheelAngle, float crankAngle, float steer, boolean parked) {
        float wheelDeg = (float) Math.toDegrees(wheelAngle * SPIN_SCALE);
        float crankDeg = (float) Math.toDegrees(crankAngle);

        c.draw(body);
        if (parked) {
            c.draw(kickstand);
        }
        drawWheel(c, wheel, AXLE_Y, REAR_Z, wheelDeg);

        // The crank turns; each pedal is carried round on the end of its arm and turned back by the
        // same angle, so it stays flat under the rider's foot instead of tumbling with the arm.
        c.push();
        c.translate(0.0F, BB_Y, BB_Z);
        c.rotate(crankDeg, 1.0F, 0.0F, 0.0F);
        c.draw(crank);
        drawPedal(c, pedalRight, -CRANK, crankDeg);
        drawPedal(c, pedalLeft, CRANK, crankDeg);
        c.pop();

        beginSteer(c, steer);
        c.draw(front);
        drawWheel(c, wheel, AXLE_Y, FRONT_Z, wheelDeg);
        c.pop();
    }

    private static void drawPedal(Canvas c, Mesh pedal, float armEnd, float crankDeg) {
        c.push();
        c.translate(0.0F, armEnd, 0.0F);
        c.rotate(-crankDeg, 1.0F, 0.0F, 0.0F);
        c.draw(pedal);
        c.pop();
    }
}
