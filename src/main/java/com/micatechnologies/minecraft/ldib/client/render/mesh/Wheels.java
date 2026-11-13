package com.micatechnologies.minecraft.ldib.client.render.mesh;

/**
 * Round wheels. Each is one {@link Mesh} centred on its axle, lying in the bike's centre plane, and
 * spun by rotating the whole thing about x — one draw call per wheel however much detail it has.
 *
 * <p>A wheel is built as surfaces of revolution (see {@link MeshBuilder#revolve}), so "round" is a
 * segment count rather than a stack of boxes: the tread, its shoulders, the sidewalls and the rim are
 * each one ring. <b>A perfectly round wheel shows no sign of turning</b>, so every style here carries
 * something that goes round with it — spokes, a valve and reflectors on a bicycle wheel, alternating
 * sectors on a solid one, and tread lugs on all of them.</p>
 */
public final class Wheels {

    private static final double TURN = 2.0 * Math.PI;

    private Wheels() {
        throw new AssertionError("No instances.");
    }

    /**
     * A bicycle wheel: a slim tyre on an alloy rim, laced to a small hub with thin spokes, with a
     * valve and a pair of amber reflectors. {@code radius} is to the tread; the tyre is 1 px across.
     */
    public static Mesh spoked(double radius, int segments, int spokes) {
        MeshBuilder b = new MeshBuilder();
        double tyreIn = radius - 1.15;
        double rimIn = radius - 1.75;
        double rimHalf = 0.34;
        tyre(b, radius, 0.5, 0.28, 0.35, tyreIn, rimHalf, segments, Skin.TYRE);

        // Rim: a bright side wall each side and a darker well facing the hub.
        float[] rimSide = Skin.ALLOY.texel(1, 1);
        b.revolve(rimHalf, tyreIn, rimHalf, rimIn, 0, TURN, segments, 1, 0, rimSide);
        b.revolve(-rimHalf, tyreIn, -rimHalf, rimIn, 0, TURN, segments, -1, 0, rimSide);
        b.revolve(-rimHalf, rimIn, rimHalf, rimIn, 0, TURN, segments, 0, -1, Skin.ALLOY.texel(2, 4));

        // Hub: a short barrel across the axle with a flange face at each end.
        double hubR = 0.6;
        double hubHalf = 0.9;
        float[] hub = Skin.ALLOY.texel(3, 2);
        b.revolve(-hubHalf, hubR, hubHalf, hubR, 0, TURN, 10, 0, 1, hub);
        b.revolve(hubHalf, 0, hubHalf, hubR, 0, TURN, 10, 1, 0, hub);
        b.revolve(-hubHalf, 0, -hubHalf, hubR, 0, TURN, 10, -1, 0, hub);

        // Spokes run from alternate hub flanges to the middle of the rim, like a real dished wheel.
        Material spoke = Skin.ALLOY.sub(4, 3, 1, 1);
        for (int k = 0; k < spokes; k++) {
            double t = k * TURN / spokes;
            double[] in = MeshBuilder.ringPoint((k % 2 == 0) ? 0.55 : -0.55, hubR * 0.8, t);
            double[] out = MeshBuilder.ringPoint(0, rimIn + 0.06, t);
            b.beam(in[0], in[1], in[2], out[0], out[1], out[2], 0.17, 0.17, spoke);
        }

        // Valve stem, half a spoke gap round from a spoke so it is never hidden behind one.
        double gap = TURN / spokes;
        double valveAt = gap / 2;
        double[] v0 = MeshBuilder.ringPoint(0, rimIn + 0.05, valveAt);
        double[] v1 = MeshBuilder.ringPoint(0, rimIn - 0.95, valveAt);
        b.beam(v0[0], v0[1], v0[2], v1[0], v1[1], v1[2], 0.26, 0.26, Skin.HARDWARE);

        // Two reflectors, opposite each other and about a quarter turn from the valve — a whole
        // number of spoke gaps round from it, so they too sit between spokes (spokes come in pairs,
        // so the one opposite does as well).
        for (int k = 0; k < 2; k++) {
            b.push();
            b.rotateX(valveAt + Math.round(spokes / 4.0) * gap + k * Math.PI);
            b.box(-0.16, -(rimIn - 0.35), -0.62, 0.16, -(rimIn - 1.25), 0.62, Skin.REFLECTOR);
            b.pop();
        }
        return b.build();
    }

    /**
     * A small solid wheel — a scooter's. A tyre around a disc that alternates light and dark sectors,
     * which is what shows it turning. The tyre is 1 px across.
     */
    public static Mesh disc(double radius, int segments, final int sectors) {
        MeshBuilder b = new MeshBuilder();
        double tyreIn = radius - 0.95;
        double faceHalf = 0.4;
        tyre(b, radius, 0.5, 0.26, 0.3, tyreIn, faceHalf, segments, Skin.TYRE);
        sectorDisc(b, faceHalf, tyreIn, segments, sectors, Skin.ALLOY.texel(1, 2), Skin.METAL.texel(3, 3));
        // Axle cap, standing a little proud of the disc.
        double capR = Math.min(0.75, tyreIn * 0.4);
        float[] cap = Skin.HARDWARE.texel(0, 1);
        for (int side = -1; side <= 1; side += 2) {
            b.revolve(side * faceHalf, capR, side * 0.62, capR, 0, TURN, 10, 0, 1, cap);
            b.revolve(side * 0.62, 0, side * 0.62, capR, 0, TURN, 10, side, 0, cap);
        }
        return b.build();
    }

    /**
     * The one-wheel's tyre: {@code width} px across with broad rounded shoulders, around a sectored
     * hub face each side.
     */
    public static Mesh fat(double radius, double width, int segments, int sectors) {
        MeshBuilder b = new MeshBuilder();
        double half = width / 2.0;
        double tyreIn = radius - 1.8;
        double faceHalf = half - 0.25;
        tyre(b, radius, half, half - 0.85, 0.75, tyreIn, faceHalf, segments, Skin.FATTYRE);
        sectorDisc(b, faceHalf, tyreIn, segments, sectors, Skin.RAIL.texel(2, 3), Skin.METAL.texel(3, 3));
        return b.build();
    }

    /**
     * The rubber: a flat tread {@code 2 × treadHalf} wide at {@code radius}, a chamfered shoulder each
     * side dropping {@code shoulder} px to the sidewall at {@code ±half}, the sidewalls down to
     * {@code tyreIn}, and the ledge from there in to {@code ±innerHalf} where the rim or disc starts.
     * The tread alternates a raised lug and a groove from segment to segment.
     */
    private static void tyre(MeshBuilder b, double radius, double half, double treadHalf, double shoulder,
                             double tyreIn, double innerHalf, int segments, final Material rubber) {
        final float[] lug = rubber.texel(0, 3);
        final float[] groove = rubber.texel(1, 1);
        b.revolve(-treadHalf, radius, treadHalf, radius, 0, TURN, segments, 0, 1, new MeshBuilder.RingUv() {
            @Override
            public float[] uv(int segment) {
                return segment % 2 == 0 ? lug : groove;
            }
        });
        double shoulderR = radius - shoulder;
        float[] wall = rubber.texel(1, 5);
        for (int side = -1; side <= 1; side += 2) {
            b.revolve(side * treadHalf, radius, side * half, shoulderR, 0, TURN, segments, side, 1, rubber.texel(2, 2));
            b.revolve(side * half, shoulderR, side * half, tyreIn, 0, TURN, segments, side, 0, wall);
            b.revolve(side * half, tyreIn, side * innerHalf, tyreIn, 0, TURN, segments, 0, -1, wall);
        }
    }

    /** A flat disc face each side at {@code ±faceHalf}, out to {@code r}, in alternating sectors. */
    private static void sectorDisc(MeshBuilder b, double faceHalf, double r, final int segments,
                                   final int sectors, final float[] light, final float[] dark) {
        MeshBuilder.RingUv uv = new MeshBuilder.RingUv() {
            @Override
            public float[] uv(int segment) {
                // Each sector is a light spoke followed by a narrower dark gap.
                double within = (segment * (double) sectors / segments) % 1.0;
                return within < 0.6 ? light : dark;
            }
        };
        b.revolve(faceHalf, 0, faceHalf, r, 0, TURN, segments, 1, 0, uv);
        b.revolve(-faceHalf, 0, -faceHalf, r, 0, TURN, segments, -1, 0, uv);
    }
}
