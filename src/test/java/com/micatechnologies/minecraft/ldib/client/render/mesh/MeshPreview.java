package com.micatechnologies.minecraft.ldib.client.render.mesh;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Renders the rideable models to PNG contact sheets without starting the game — a small software
 * rasteriser standing in for the GL side of {@link Canvas}. Not a test; a tool for looking at a model
 * change in seconds instead of a client launch. It needs nothing but the {@code mesh} package and a
 * JDK:
 *
 * <pre>
 *   javac -d out src/main/java/.../client/render/mesh/[A-QS-Z]*.java \
 *                src/main/java/.../client/render/mesh/RideableModel.java \
 *                src/test/java/.../client/render/mesh/MeshPreview.java
 *   java -cp out com.micatechnologies.minecraft.ldib.client.render.mesh.MeshPreview \
 *        src/main/resources/assets/ldib/textures/entity  tools/preview
 * </pre>
 *
 * <p>(Everything in the package except {@code RideableModels}, which is the one class that names a
 * Minecraft type.) It mimics the game closely enough to judge shape, proportion, winding and texel
 * placement: the same model-to-world flip as the entity renderer, back-face culling, unfiltered
 * texture sampling, and two-light shading like the game's entity lighting. It is not pixel-exact and
 * is no substitute for a look in game.</p>
 */
public final class MeshPreview implements Canvas {

    private static final int VIEW_W = 640;
    private static final int VIEW_H = 460;
    /** Screen pixels per block. */
    private static final double ZOOM = 300.0;

    private final int[] colour = new int[VIEW_W * VIEW_H];
    private final double[] depth = new double[VIEW_W * VIEW_H];
    private final BufferedImage skin;

    /** View × entity transform: model pixels to view space (x right, y up, z toward the viewer). */
    private double[] m;
    private final ArrayDeque<double[]> stack = new ArrayDeque<double[]>();

    private boolean emissive;
    private boolean glow;
    private float intensity = 1.0F;

    private MeshPreview(BufferedImage skin, double yawDeg, double pitchDeg) {
        this.skin = skin;
        Arrays.fill(colour, 0xFF8FA9C4);
        Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        // View: orbit by yaw about the vertical, then tip by pitch. Entity: the renderer's
        // scale(-1, -1, 1) flip, the 1/16 pixel scale, and the lift that puts the wheels on y = 0.
        m = new double[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
        rotate((float) pitchDeg, 1, 0, 0);
        rotate((float) yawDeg, 0, 1, 0);
        translateRaw(0, 0.375 - 0.6, 0);
        scaleRaw(-0.0625, -0.0625, 0.0625);
    }

    // --- Canvas ----------------------------------------------------------------------------

    @Override
    public void push() {
        stack.push(m.clone());
    }

    @Override
    public void pop() {
        m = stack.pop();
    }

    @Override
    public void translate(float x, float y, float z) {
        translateRaw(x, y, z);
    }

    private void translateRaw(double x, double y, double z) {
        for (int r = 0; r < 3; r++) {
            m[r * 4 + 3] += m[r * 4] * x + m[r * 4 + 1] * y + m[r * 4 + 2] * z;
        }
    }

    private void scaleRaw(double x, double y, double z) {
        for (int r = 0; r < 3; r++) {
            m[r * 4] *= x;
            m[r * 4 + 1] *= y;
            m[r * 4 + 2] *= z;
        }
    }

    @Override
    public void rotate(float degrees, float x, float y, float z) {
        double a = Math.toRadians(degrees);
        double c = Math.cos(a);
        double s = Math.sin(a);
        double t = 1 - c;
        double[] r = {
            t * x * x + c, t * x * y - s * z, t * x * z + s * y,
            t * x * y + s * z, t * y * y + c, t * y * z - s * x,
            t * x * z - s * y, t * y * z + s * x, t * z * z + c};
        double[] n = m.clone();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                n[row * 4 + col] = m[row * 4] * r[col] + m[row * 4 + 1] * r[3 + col] + m[row * 4 + 2] * r[6 + col];
            }
        }
        m = n;
    }

    @Override
    public void beginEmissive(float intensity) {
        this.emissive = true;
        this.intensity = intensity;
    }

    @Override
    public void beginGlow(float intensity) {
        this.glow = true;
        this.intensity = intensity;
    }

    @Override
    public void endEmissive() {
        this.emissive = false;
        this.glow = false;
        this.intensity = 1.0F;
    }

    @Override
    public void draw(Mesh mesh) {
        float[] d = mesh.data();
        int stride = Mesh.FLOATS_PER_VERTEX;
        double[][] p = new double[4][3];
        double[][] n = new double[4][3];
        double[][] uv = new double[4][2];
        for (int q = 0; q < mesh.quadCount(); q++) {
            for (int k = 0; k < 4; k++) {
                int o = (q * 4 + k) * stride;
                for (int r = 0; r < 3; r++) {
                    p[k][r] = m[r * 4] * d[o] + m[r * 4 + 1] * d[o + 1] + m[r * 4 + 2] * d[o + 2] + m[r * 4 + 3];
                    n[k][r] = m[r * 4] * d[o + 5] + m[r * 4 + 1] * d[o + 6] + m[r * 4 + 2] * d[o + 7];
                }
                double len = Math.sqrt(n[k][0] * n[k][0] + n[k][1] * n[k][1] + n[k][2] * n[k][2]);
                for (int r = 0; r < 3; r++) {
                    n[k][r] /= len;
                }
                uv[k][0] = d[o + 3] * skin.getWidth();
                uv[k][1] = d[o + 4] * skin.getHeight();
            }
            triangle(p, n, uv, 0, 1, 2);
            triangle(p, n, uv, 0, 2, 3);
        }
    }

    // --- Rasteriser ------------------------------------------------------------------------

    private void triangle(double[][] p, double[][] n, double[][] uv, int a, int b, int c) {
        double ax = VIEW_W / 2.0 + p[a][0] * ZOOM;
        double ay = VIEW_H / 2.0 - p[a][1] * ZOOM;
        double bx = VIEW_W / 2.0 + p[b][0] * ZOOM;
        double by = VIEW_H / 2.0 - p[b][1] * ZOOM;
        double cx = VIEW_W / 2.0 + p[c][0] * ZOOM;
        double cy = VIEW_H / 2.0 - p[c][1] * ZOOM;
        // Screen y runs down, so a counter-clockwise (front) face has negative area here.
        double area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay);
        if (area == 0 || (area > 0 && !glow)) {
            return;
        }
        int x0 = (int) Math.max(0, Math.floor(Math.min(ax, Math.min(bx, cx))));
        int x1 = (int) Math.min(VIEW_W - 1, Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int y0 = (int) Math.max(0, Math.floor(Math.min(ay, Math.min(by, cy))));
        int y1 = (int) Math.min(VIEW_H - 1, Math.ceil(Math.max(ay, Math.max(by, cy))));
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                double wa = ((bx - px) * (cy - py) - (cx - px) * (by - py)) / area;
                double wb = ((cx - px) * (ay - py) - (ax - px) * (cy - py)) / area;
                double wc = 1 - wa - wb;
                if (wa < 0 || wb < 0 || wc < 0) {
                    continue;
                }
                double z = wa * p[a][2] + wb * p[b][2] + wc * p[c][2];
                int i = y * VIEW_W + x;
                if (z <= depth[i]) {
                    continue;
                }
                int tu = clamp((int) Math.floor(wa * uv[a][0] + wb * uv[b][0] + wc * uv[c][0]), skin.getWidth());
                int tv = clamp((int) Math.floor(wa * uv[a][1] + wb * uv[b][1] + wc * uv[c][1]), skin.getHeight());
                int texel = skin.getRGB(tu, tv);
                double shade = intensity;
                if (!emissive) {
                    double nx = wa * n[a][0] + wb * n[b][0] + wc * n[c][0];
                    double ny = wa * n[a][1] + wb * n[b][1] + wc * n[c][1];
                    double nz = wa * n[a][2] + wb * n[b][2] + wc * n[c][2];
                    // Two lights from above, one a little in front and one a little behind, over a
                    // flat ambient — the shape of the game's entity lighting.
                    double l0 = Math.max(0, (0.16 * nx + 0.81 * ny + 0.57 * nz));
                    double l1 = Math.max(0, (-0.16 * nx + 0.81 * ny - 0.57 * nz));
                    shade = Math.min(1.0, 0.4 + 0.6 * (l0 + l1));
                }
                int r = (int) (((texel >> 16) & 0xFF) * shade);
                int g = (int) (((texel >> 8) & 0xFF) * shade);
                int bl = (int) ((texel & 0xFF) * shade);
                if (glow) {
                    int old = colour[i];
                    r = Math.min(255, r + ((old >> 16) & 0xFF));
                    g = Math.min(255, g + ((old >> 8) & 0xFF));
                    bl = Math.min(255, bl + (old & 0xFF));
                } else {
                    depth[i] = z;
                }
                colour[i] = 0xFF000000 | (r << 16) | (g << 8) | bl;
            }
        }
    }

    private static int clamp(int v, int size) {
        return Math.max(0, Math.min(size - 1, v));
    }

    // --- Contact sheets --------------------------------------------------------------------

    private static void sheet(RideableModel model, File skinFile, File out, boolean lights) throws IOException {
        BufferedImage skin = ImageIO.read(skinFile);
        double[][] views = {{90, 8}, {40, 18}, {145, 18}, {180, 12}, {0, 12}, {70, 55}};
        BufferedImage sheet = new BufferedImage(VIEW_W * 3, VIEW_H * 2, BufferedImage.TYPE_INT_ARGB);
        for (int v = 0; v < views.length; v++) {
            MeshPreview view = new MeshPreview(skin, views[v][0], views[v][1]);
            float steer = v == 1 || v == 4 ? 20.0F : 0.0F;
            boolean parked = v == 2 || v == 5;
            model.render(view, 0.6F, 0.9F, steer, parked);
            if (lights) {
                model.renderLights(view, steer, v != 3, v != 4, 1.0F);
            }
            sheet.setRGB((v % 3) * VIEW_W, (v / 3) * VIEW_H, VIEW_W, VIEW_H, view.colour, 0, VIEW_W);
        }
        ImageIO.write(sheet, "png", out);
        System.out.println("wrote " + out);
    }

    public static void main(String[] args) throws IOException {
        File skins = new File(args[0]);
        File out = new File(args[1]);
        out.mkdirs();
        sheet(new BikeModel(false, true), new File(skins, "bike.png"), new File(out, "model_bike.png"), false);
        sheet(new BikeModel(true, false), new File(skins, "ebike.png"), new File(out, "model_ebike.png"), true);
        sheet(new BikeModel(true, true), new File(skins, "share_ebike.png"), new File(out, "model_share_ebike.png"), true);
        sheet(new ScooterModel(false), new File(skins, "scooter.png"), new File(out, "model_scooter.png"), true);
        sheet(new ScooterModel(true), new File(skins, "scooter_fast.png"), new File(out, "model_scooter_fast.png"), true);
        sheet(new OnewheelModel(), new File(skins, "onewheel.png"), new File(out, "model_onewheel.png"), true);
    }
}
