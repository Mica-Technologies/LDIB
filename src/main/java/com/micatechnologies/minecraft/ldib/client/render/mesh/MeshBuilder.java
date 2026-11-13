package com.micatechnologies.minecraft.ldib.client.render.mesh;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Accumulates quads into a {@link Mesh}. Geometry is described in model pixels under a small
 * transform stack (the usual push / translate / rotate / pop), which is applied here, once, as the
 * mesh is built — so the finished mesh carries no transforms of its own and is drawn with a single
 * call.
 *
 * <p><b>Winding is never the caller's problem.</b> Every primitive says which way its faces should
 * point (away from a box's middle, outward from a wheel's axle…) and each quad is wound to match, so
 * back-face culling can stay on and there is no way to author an inside-out face by listing corners
 * in the wrong order.</p>
 *
 * <p>Model space follows the rest of the mod: <b>+y is down, the front faces −z</b>, x runs across
 * the axles. Angles are radians here.</p>
 */
public final class MeshBuilder {

    /** Picks the texels a ring's {@code segment}-th quad samples; returns {@code {u0, v0, u1, v1}}. */
    public interface RingUv {
        float[] uv(int segment);
    }

    /**
     * How far inside its texels a face samples. Textures here are drawn unfiltered, and a coordinate
     * sitting exactly on a texel boundary can round to the texel on the wrong side of it; this keeps
     * every sample safely inside the texels the face was given.
     */
    private static final float UV_INSET = 0.02F;

    private float[] data = new float[Mesh.FLOATS_PER_VERTEX * 4 * 128];
    private int size;

    /** Current transform, a row-major 3×4 matrix. */
    private double[] m = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
    private final ArrayDeque<double[]> stack = new ArrayDeque<double[]>();

    public Mesh build() {
        return new Mesh(Arrays.copyOf(data, size));
    }

    // --- Transform stack -------------------------------------------------------------------

    public MeshBuilder push() {
        stack.push(m.clone());
        return this;
    }

    public MeshBuilder pop() {
        m = stack.pop();
        return this;
    }

    public MeshBuilder translate(double x, double y, double z) {
        for (int r = 0; r < 3; r++) {
            m[r * 4 + 3] += m[r * 4] * x + m[r * 4 + 1] * y + m[r * 4 + 2] * z;
        }
        return this;
    }

    /** Rotate about the x axis (the axle direction) — what spins a wheel or aims a frame tube. */
    public MeshBuilder rotateX(double radians) {
        return rotate(1, 2, radians);
    }

    public MeshBuilder rotateY(double radians) {
        return rotate(2, 0, radians);
    }

    public MeshBuilder rotateZ(double radians) {
        return rotate(0, 1, radians);
    }

    /** Post-multiply by a rotation in the plane of axes {@code a} → {@code b}. */
    private MeshBuilder rotate(int a, int b, double radians) {
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        for (int r = 0; r < 3; r++) {
            double ca = m[r * 4 + a];
            double cb = m[r * 4 + b];
            m[r * 4 + a] = c * ca + s * cb;
            m[r * 4 + b] = -s * ca + c * cb;
        }
        return this;
    }

    // --- Primitives ------------------------------------------------------------------------

    /** An axis-aligned box between two opposite corners. */
    public MeshBuilder box(double x0, double y0, double z0, double x1, double y1, double z1, Material mat) {
        // Run the "length" of the box along its longest axis, so the material's grain follows it.
        double dx = Math.abs(x1 - x0);
        double dy = Math.abs(y1 - y0);
        double dz = Math.abs(z1 - z0);
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) {
            int s = i & 1;
            int t = (i >> 1) & 1;
            int l = (i >> 2) & 1;
            double x;
            double y;
            double z;
            if (dz >= dx && dz >= dy) {
                x = s == 0 ? x0 : x1;
                y = t == 0 ? y0 : y1;
                z = l == 0 ? z0 : z1;
            } else if (dy >= dx) {
                x = s == 0 ? x0 : x1;
                z = t == 0 ? z0 : z1;
                y = l == 0 ? y0 : y1;
            } else {
                y = s == 0 ? y0 : y1;
                z = t == 0 ? z0 : z1;
                x = l == 0 ? x0 : x1;
            }
            c[i] = new double[] {x, y, z};
        }
        return hexa(c, mat);
    }

    /**
     * A straight square-section bar from {@code a} to {@code b} — a frame tube, a spoke, a crank arm.
     * {@code w} is its thickness across the bike (x, or as near as the bar's direction allows) and
     * {@code h} its thickness the other way.
     */
    public MeshBuilder beam(double ax, double ay, double az, double bx, double by, double bz,
                            double w, double h, Material mat) {
        double[] a = {ax, ay, az};
        double[] b = {bx, by, bz};
        double[] d = normalize(sub(b, a));
        // "Across" is the x axis with any component along the bar removed; a bar that runs along x
        // itself falls back to y.
        double[] ref = Math.abs(d[0]) > 0.9 ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
        double along = dot(ref, d);
        double[] s = normalize(new double[] {ref[0] - along * d[0], ref[1] - along * d[1], ref[2] - along * d[2]});
        double[] t = cross(d, s);
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) {
            double ss = ((i & 1) == 0 ? -0.5 : 0.5) * w;
            double tt = (((i >> 1) & 1) == 0 ? -0.5 : 0.5) * h;
            double[] o = ((i >> 2) & 1) == 0 ? a : b;
            c[i] = new double[] {
                o[0] + s[0] * ss + t[0] * tt, o[1] + s[1] * ss + t[1] * tt, o[2] + s[2] * ss + t[2] * tt};
        }
        return hexa(c, mat);
    }

    /**
     * A six-faced solid from eight corners indexed {@code s + 2t + 4l}: {@code s} and {@code t} pick
     * the side across the section, {@code l} the end along the length. Faces are wound to point away
     * from the solid's middle.
     */
    private MeshBuilder hexa(double[][] c, Material mat) {
        double[] mid = new double[3];
        for (double[] p : c) {
            mid[0] += p[0] / 8;
            mid[1] += p[1] / 8;
            mid[2] += p[2] / 8;
        }
        double sLen = length(sub(c[1], c[0]));
        double tLen = length(sub(c[2], c[0]));
        double lLen = length(sub(c[4], c[0]));
        float[] cap = mat.patch(sLen, tLen);
        float[] sideS = mat.patch(tLen, lLen);
        float[] sideT = mat.patch(sLen, lLen);
        face(c[0], c[1], c[3], c[2], mid, cap);
        face(c[4], c[5], c[7], c[6], mid, cap);
        face(c[0], c[4], c[6], c[2], mid, sideS);
        face(c[1], c[5], c[7], c[3], mid, sideS);
        face(c[0], c[4], c[5], c[1], mid, sideT);
        face(c[2], c[6], c[7], c[3], mid, sideT);
        return this;
    }

    private void face(double[] a, double[] b, double[] c, double[] d, double[] solidMid, float[] uv) {
        double[] out = {
            (a[0] + b[0] + c[0] + d[0]) / 4 - solidMid[0],
            (a[1] + b[1] + c[1] + d[1]) / 4 - solidMid[1],
            (a[2] + b[2] + c[2] + d[2]) / 4 - solidMid[2]};
        quad(a, b, c, d, out, uv, null, null, null, null);
    }

    /**
     * A flat rectangle seen from both sides is two quads; this is one of them, facing {@code out}.
     * Corners run a → b → c → d around the rectangle, and the texture patch is stretched over it with
     * {@code a} at its top-left.
     */
    public MeshBuilder quad(double[] a, double[] b, double[] c, double[] d, double[] out, float[] uv) {
        quad(a, b, c, d, out, uv, null, null, null, null);
        return this;
    }

    /**
     * A surface of revolution about the x axis (the axle): the straight profile from
     * {@code (xA, rA)} to {@code (xB, rB)} swept from angle {@code from} to {@code to} in {@code n}
     * flat segments. Angle 0 is straight up; it increases toward the rear (+z).
     *
     * <p>{@code outX} / {@code outR} say which way the surface faces, as a direction in the profile's
     * own (along-axle, away-from-axle) plane: {@code (0, 1)} is a tyre's tread, {@code (1, 0)} a
     * right-hand sidewall, {@code (0, -1)} the inside of a rim. Normals follow that direction around
     * the sweep, so the result is lit as one smooth round surface rather than {@code n} flats.</p>
     */
    public MeshBuilder revolve(double xA, double rA, double xB, double rB, double from, double to, int n,
                               double outX, double outR, RingUv ringUv) {
        double step = (to - from) / n;
        for (int i = 0; i < n; i++) {
            double t0 = from + i * step;
            double t1 = t0 + step;
            double tm = t0 + step / 2;
            quad(ringPoint(xA, rA, t0), ringPoint(xB, rB, t0), ringPoint(xB, rB, t1), ringPoint(xA, rA, t1),
                ringPoint(outX, outR, tm), ringUv.uv(i),
                ringPoint(outX, outR, t0), ringPoint(outX, outR, t0),
                ringPoint(outX, outR, t1), ringPoint(outX, outR, t1));
        }
        return this;
    }

    /** As {@link #revolve} with every segment sampling the same texels. */
    public MeshBuilder revolve(double xA, double rA, double xB, double rB, double from, double to, int n,
                               double outX, double outR, final float[] uv) {
        return revolve(xA, rA, xB, rB, from, to, n, outX, outR, new RingUv() {
            @Override
            public float[] uv(int segment) {
                return uv;
            }
        });
    }

    /** The point {@code r} from the axle at angle {@code t} (0 = up, toward the rear as it grows). */
    public static double[] ringPoint(double x, double r, double t) {
        return new double[] {x, -r * Math.cos(t), r * Math.sin(t)};
    }

    // --- Emission --------------------------------------------------------------------------

    private void quad(double[] a, double[] b, double[] c, double[] d, double[] out, float[] uv,
                      double[] na, double[] nb, double[] nc, double[] nd) {
        double[] flat = cross(sub(b, a), sub(c, a));
        if (length(flat) < 1.0e-12) {
            flat = cross(sub(c, a), sub(d, a)); // a and b coincide (the centre of a disc)
        }
        boolean flip = dot(flat, out) < 0;
        if (flip) {
            flat = new double[] {-flat[0], -flat[1], -flat[2]};
        }
        flat = normalize(flat);
        float u0 = uv[0] + UV_INSET;
        float v0 = uv[1] + UV_INSET;
        float u1 = uv[2] - UV_INSET;
        float v1 = uv[3] - UV_INSET;
        // a(u0,v0) b(u0,v1) c(u1,v1) d(u1,v0); reversing the winding swaps b and d, texels and all.
        vertex(a, na == null ? flat : na, u0, v0);
        if (flip) {
            vertex(d, nd == null ? flat : nd, u1, v0);
            vertex(c, nc == null ? flat : nc, u1, v1);
            vertex(b, nb == null ? flat : nb, u0, v1);
        } else {
            vertex(b, nb == null ? flat : nb, u0, v1);
            vertex(c, nc == null ? flat : nc, u1, v1);
            vertex(d, nd == null ? flat : nd, u1, v0);
        }
    }

    private void vertex(double[] p, double[] n, float u, float v) {
        if (size + Mesh.FLOATS_PER_VERTEX > data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        double[] nn = normalize(n);
        for (int r = 0; r < 3; r++) {
            data[size + r] = (float) (m[r * 4] * p[0] + m[r * 4 + 1] * p[1] + m[r * 4 + 2] * p[2] + m[r * 4 + 3]);
            data[size + 5 + r] = (float) (m[r * 4] * nn[0] + m[r * 4 + 1] * nn[1] + m[r * 4 + 2] * nn[2]);
        }
        data[size + 3] = u / Skin.ATLAS_W;
        data[size + 4] = v / Skin.ATLAS_H;
        size += Mesh.FLOATS_PER_VERTEX;
    }

    // --- Vector helpers --------------------------------------------------------------------

    private static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double length(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    private static double[] normalize(double[] a) {
        double len = length(a);
        return len < 1.0e-12 ? new double[] {0, -1, 0} : new double[] {a[0] / len, a[1] / len, a[2] / len};
    }
}
