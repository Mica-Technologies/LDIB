package com.micatechnologies.minecraft.ldib.client.render.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Guards the things about the rideable models that nothing else would notice going wrong: a face
 * wound inside-out (invisible from one side in game, and the compiler has no opinion), a face
 * sampling outside any painted part of the skin (a transparent hole), a wheel that is not round, and
 * a model quietly growing back toward one draw call per box.
 */
class RideableMeshTest {

    /** Every painted rectangle of the skin, as {@code {u, v, w, h}} in texels. */
    private static final Material[] PAINTED = {
        Skin.TYRE, Skin.FRAME, Skin.ACCENT, Skin.METAL, Skin.FATTYRE, Skin.ALLOY, Skin.GLOW_WHITE,
        Skin.GLOW_RED, Skin.SHELL, Skin.RAIL, Skin.HEAD_LENS_OFF, Skin.HEAD_LENS_ON, Skin.REAR_LENS_OFF,
        Skin.REAR_LENS_ON, Skin.REFLECTOR, Skin.HARDWARE};

    /** A canvas that draws nothing and remembers what it was asked to. */
    private static class Recorder implements Canvas {
        final Map<Mesh, Integer> drawn = new IdentityHashMap<Mesh, Integer>();
        int draws;
        int depth;
        int maxDepth;
        boolean emissive;

        @Override
        public void push() {
            depth++;
            maxDepth = Math.max(maxDepth, depth);
        }

        @Override
        public void pop() {
            depth--;
        }

        @Override
        public void translate(float x, float y, float z) {
        }

        @Override
        public void rotate(float degrees, float x, float y, float z) {
        }

        @Override
        public void draw(Mesh mesh) {
            draws++;
            Integer n = drawn.get(mesh);
            drawn.put(mesh, n == null ? 1 : n + 1);
        }

        @Override
        public void beginEmissive(float intensity) {
            assertTrue(!emissive, "emissive pass started twice");
            emissive = true;
        }

        @Override
        public void beginGlow(float intensity) {
            assertTrue(emissive, "glow outside an emissive pass");
        }

        @Override
        public void endEmissive() {
            assertTrue(emissive, "emissive pass ended without starting");
            emissive = false;
        }
    }

    private static List<RideableModel> allModels() {
        List<RideableModel> models = new ArrayList<RideableModel>();
        models.add(new BikeModel(false, true));
        models.add(new BikeModel(true, false));
        models.add(new BikeModel(true, true));
        models.add(new ScooterModel(false));
        models.add(new ScooterModel(true));
        models.add(new OnewheelModel());
        return models;
    }

    /** Every mesh a model can draw, in any pose and with the lights in every state. */
    private static List<Mesh> meshesOf(RideableModel model) {
        Recorder r = new Recorder();
        model.render(r, 1.0F, 1.0F, 10.0F, true);
        model.renderLights(r, 10.0F, false, false, 1.0F);
        model.renderLights(r, 10.0F, true, true, 1.0F);
        return new ArrayList<Mesh>(r.drawn.keySet());
    }

    @Test
    void everyFaceIsWoundToMatchItsNormal() {
        for (RideableModel model : allModels()) {
            for (Mesh mesh : meshesOf(model)) {
                float[] d = mesh.data();
                int s = Mesh.FLOATS_PER_VERTEX;
                for (int q = 0; q < mesh.quadCount(); q++) {
                    int a = q * 4 * s;
                    // Geometric normal from the winding, using whichever corner triple is not
                    // degenerate (the centre of a disc repeats a corner).
                    double[] n = cross(d, a, a + s, a + 2 * s);
                    if (n[0] * n[0] + n[1] * n[1] + n[2] * n[2] < 1.0e-9) {
                        n = cross(d, a, a + 2 * s, a + 3 * s);
                    }
                    double agree = 0;
                    for (int k = 0; k < 4; k++) {
                        int o = a + k * s;
                        agree += n[0] * d[o + 5] + n[1] * d[o + 6] + n[2] * d[o + 7];
                    }
                    assertTrue(agree > 0, model.getClass().getSimpleName() + ": quad " + q
                        + " is wound against its own normal — it would be culled from the side it faces");
                }
            }
        }
    }

    @Test
    void everyFaceSamplesAPaintedPartOfTheSkin() {
        for (RideableModel model : allModels()) {
            for (Mesh mesh : meshesOf(model)) {
                float[] d = mesh.data();
                for (int i = 0; i < d.length; i += Mesh.FLOATS_PER_VERTEX) {
                    double u = d[i + 3] * Skin.ATLAS_W;
                    double v = d[i + 4] * Skin.ATLAS_H;
                    boolean painted = false;
                    for (Material m : PAINTED) {
                        painted |= u >= m.u && u <= m.u + m.w && v >= m.v && v <= m.v + m.h;
                    }
                    assertTrue(painted, model.getClass().getSimpleName() + ": a vertex samples texel ("
                        + u + ", " + v + "), which no material paints");
                }
            }
        }
    }

    @Test
    void normalsAreUnitLength() {
        for (RideableModel model : allModels()) {
            for (Mesh mesh : meshesOf(model)) {
                float[] d = mesh.data();
                for (int i = 0; i < d.length; i += Mesh.FLOATS_PER_VERTEX) {
                    double len = Math.sqrt(d[i + 5] * d[i + 5] + d[i + 6] * d[i + 6] + d[i + 7] * d[i + 7]);
                    assertEquals(1.0, len, 1.0e-3);
                }
            }
        }
    }

    @Test
    void aBicycleWheelIsRound() {
        double radius = 5.5;
        Mesh wheel = Wheels.spoked(radius, 32, 12);
        float[] d = wheel.data();
        int s = Mesh.FLOATS_PER_VERTEX;
        double farthest = 0;
        double flattest = radius;
        for (int q = 0; q < wheel.quadCount(); q++) {
            // The tread is the set of quads whose corners all lie on the outer radius. A polygon's
            // flats dip inside the circle its corners are on; how far is how un-round it is.
            double midY = 0;
            double midZ = 0;
            boolean tread = true;
            for (int k = 0; k < 4; k++) {
                int o = (q * 4 + k) * s;
                double r = Math.hypot(d[o + 1], d[o + 2]);
                farthest = Math.max(farthest, r);
                tread &= Math.abs(r - radius) < 1.0e-4;
                midY += d[o + 1] / 4;
                midZ += d[o + 2] / 4;
            }
            if (tread) {
                flattest = Math.min(flattest, Math.hypot(midY, midZ));
            }
        }
        assertEquals(radius, farthest, 1.0e-4, "nothing on the wheel may stick out past the tread");
        // 1/16 of a pixel is 1/256 of a block: well under what can be seen at any riding distance.
        assertTrue(radius - flattest < 0.0625, "tread flats dip " + (radius - flattest)
            + " px inside the circle — add segments");
    }

    @Test
    void modelsDrawAsAHandfulOfGroupsNotABoxAtATime() {
        // The budget that pays for the detail: a bicycle is the frame, the steering assembly, two
        // wheels, the crank, two pedals and a kickstand. If a change pushes a model past this it has
        // started drawing per part again, and that cost — not polygon count — is what limits how
        // many bikes a busy station can show.
        for (RideableModel model : allModels()) {
            Recorder r = new Recorder();
            model.render(r, 1.0F, 1.0F, 10.0F, true);
            assertTrue(r.draws <= 8, model.getClass().getSimpleName() + " took " + r.draws + " draw calls");
            assertEquals(0, r.depth, "unbalanced push/pop");
            model.renderLights(r, 10.0F, true, true, 1.0F);
            assertEquals(0, r.depth, "unbalanced push/pop in the light pass");
            assertTrue(!r.emissive, "light pass left open");
        }
    }

    @Test
    void bothWheelsOfABikeAreTheSameMesh() {
        Recorder r = new Recorder();
        new BikeModel(false, false).render(r, 0.0F, 0.0F, 0.0F, false);
        boolean twice = false;
        for (int n : r.drawn.values()) {
            twice |= n == 2;
        }
        assertTrue(twice, "the two wheels should replay one mesh, not each carry their own copy");
    }

    @Test
    void anUnlitModelNeverEntersTheEmissivePass() {
        // The pass that changes drawing state must not run at all when no light is lit.
        for (RideableModel model : allModels()) {
            final boolean[] entered = {false};
            Recorder r = new Recorder() {
                @Override
                public void beginEmissive(float intensity) {
                    entered[0] = true;
                    super.beginEmissive(intensity);
                }
            };
            model.renderLights(r, 0.0F, false, false, 1.0F);
            assertTrue(!entered[0], model.getClass().getSimpleName() + " entered the emissive pass unlit");
        }
    }

    private static double[] cross(float[] d, int a, int b, int c) {
        double ux = d[b] - d[a];
        double uy = d[b + 1] - d[a + 1];
        double uz = d[b + 2] - d[a + 2];
        double vx = d[c] - d[a];
        double vy = d[c + 1] - d[a + 1];
        double vz = d[c + 2] - d[a + 2];
        return new double[] {uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }
}
