package com.micatechnologies.minecraft.ldib.client.render.mesh;

/**
 * A rideable's shape: a few rigid {@link Mesh} groups and the handful of transforms that pose them.
 * The entity renderer and the rack and dock renderers all draw through this, onto a {@link Canvas}.
 *
 * <p><b>Why groups and not boxes.</b> The first models were vanilla-style: every box its own part,
 * each drawn with its own matrix push, translate, rotate and call — some sixty of them for a bicycle,
 * thirty-eight in the wheels alone. A bicycle only has a handful of things that move relative to one
 * another (the frame, two wheels, the crank, the steering assembly), so each of those is now one mesh
 * and one call, and what a wheel is made of stopped mattering to the frame rate. That is what paid
 * for round wheels.</p>
 *
 * <p><b>Conventions</b>, shared by every model: <b>+y is down</b>, the <b>front faces −z</b>, x runs
 * along the axles, the ground is at {@code y = +6}, and a unit is a pixel (1/16 block). The renderers'
 * minecart-style transform maps that onto the world.</p>
 *
 * <p>Models hold no per-frame state — the pose arrives as arguments — so one instance serves every
 * bike of its kind, ridden or parked, without one's steering or wheel spin bleeding into the next.</p>
 */
public abstract class RideableModel {

    /**
     * How far a wheel-spin angle from the entity is scaled for this model's wheel. The entity turns
     * its wheel angle at one rate for every rideable, as if all wheels were {@value
     * #NOMINAL_WHEEL_RADIUS} blocks in radius; a model with a different wheel multiplies by {@code
     * NOMINAL / actual} so the tread moves at ground speed and the wheel visibly rolls, not skids.
     */
    protected static final float NOMINAL_WHEEL_RADIUS = 0.4F;

    /** Light fixtures; any may be {@code null} on a model without that light. */
    protected Mesh headLensOff;
    protected Mesh headLensOn;
    protected Mesh headGlow;
    protected Mesh rearLensOff;
    protected Mesh rearLensOn;
    protected Mesh rearGlow;

    /** Whether the headlight turns with the steering (bar-mounted) or stays with the frame. */
    protected boolean headlightSteers;

    /** Model z of the vertical steering axis (at x = 0) the front assembly turns about. */
    protected float steerPivotZ;

    /**
     * Draw the rideable.
     *
     * @param wheelAngle how far the wheels have turned, radians, at the entity's nominal rate
     * @param crankAngle how far the pedals have turned, radians (ignored by models with none)
     * @param steer      front-assembly steer angle, degrees
     * @param parked     nobody is riding it — a bicycle puts its kickstand down
     */
    public abstract void render(Canvas c, float wheelAngle, float crankAngle, float steer, boolean parked);

    /**
     * How far back (+z, px) to slide this model when it is parked in a rack or dock, so that what the
     * rack holds — the wheels — sits centred on the slot. Zero for anything already centred on its
     * own origin; a bicycle is built around its rider instead and is not.
     */
    public float parkingShift() {
        return 0.0F;
    }

    /**
     * Draw the lights: each lens either dark, as an ordinary part, or lit and glowing. Only for
     * variants that have lights at all.
     *
     * @param intensity 0–1; a bike idling in a dock glows dimmer than one on the road
     */
    public final void renderLights(Canvas c, float steer, boolean headlightOn, boolean rearLightOn,
                                   float intensity) {
        if (!headlightOn) {
            drawHead(c, steer, headLensOff);
        }
        if (!rearLightOn) {
            draw(c, rearLensOff);
        }
        if (!headlightOn && !rearLightOn) {
            return;
        }
        c.beginEmissive(intensity);
        if (headlightOn) {
            drawHead(c, steer, headLensOn);
        }
        if (rearLightOn) {
            draw(c, rearLensOn);
        }
        c.beginGlow(intensity);
        if (headlightOn) {
            drawHead(c, steer, headGlow);
        }
        if (rearLightOn) {
            draw(c, rearGlow);
        }
        c.endEmissive();
    }

    private void drawHead(Canvas c, float steer, Mesh mesh) {
        if (mesh == null) {
            return;
        }
        if (headlightSteers) {
            beginSteer(c, steer);
            c.draw(mesh);
            c.pop();
        } else {
            c.draw(mesh);
        }
    }

    private static void draw(Canvas c, Mesh mesh) {
        if (mesh != null) {
            c.draw(mesh);
        }
    }

    /** Push a transform turning what follows by {@code steer} degrees about the steering axis. */
    protected final void beginSteer(Canvas c, float steer) {
        c.push();
        c.translate(0.0F, 0.0F, steerPivotZ);
        c.rotate(steer, 0.0F, 1.0F, 0.0F);
        c.translate(0.0F, 0.0F, -steerPivotZ);
    }

    /** Draw {@code wheel} on an axle at {@code (y, z)}, turned by {@code degrees}. */
    protected static void drawWheel(Canvas c, Mesh wheel, float y, float z, float degrees) {
        c.push();
        c.translate(0.0F, y, z);
        c.rotate(degrees, 1.0F, 0.0F, 0.0F);
        c.draw(wheel);
        c.pop();
    }

    // --- Shared light-fixture geometry -------------------------------------------------------

    /** A lens: a small box, in the given swatch. Build it twice, once dark and once lit. */
    protected static Mesh lens(double x0, double y0, double z0, double x1, double y1, double z1, Material swatch) {
        return new MeshBuilder().box(x0, y0, z0, x1, y1, z1, swatch).build();
    }

    /**
     * A halo: three squares of side {@code 2 × half} crossed at right angles on {@code (x, y, z)},
     * each carrying the round fade-to-black glow texture. Whichever way it is seen from, at least one
     * is close to face-on, so it reads as a soft ball of light rather than the solid block the old
     * glow was — and it is three quads.
     */
    protected static Mesh halo(double x, double y, double z, double half, Material glow) {
        MeshBuilder b = new MeshBuilder();
        float[] uv = glow.all();
        double h = half;
        b.quad(p(x - h, y - h, z), p(x - h, y + h, z), p(x + h, y + h, z), p(x + h, y - h, z), p(0, 0, 1), uv);
        b.quad(p(x, y - h, z - h), p(x, y + h, z - h), p(x, y + h, z + h), p(x, y - h, z + h), p(1, 0, 0), uv);
        b.quad(p(x - h, y, z - h), p(x - h, y, z + h), p(x + h, y, z + h), p(x + h, y, z - h), p(0, -1, 0), uv);
        return b.build();
    }

    private static double[] p(double x, double y, double z) {
        return new double[] {x, y, z};
    }
}
