package com.micatechnologies.minecraft.ldib.client.render.mesh;

/**
 * A finished, immutable bag of textured quads — the unit a rideable is drawn in. Each rigid group of
 * a model (the frame, one wheel, the crank…) is one {@code Mesh}, drawn with one call under whatever
 * transform that group needs. Pure Java: no Minecraft type appears here or anywhere else in this
 * package apart from {@link RideableModels}, so the geometry can be built, inspected and rendered to
 * a PNG on a bare JVM (see {@code MeshPreview} in the test sources).
 *
 * <p>Vertices are stored four to a quad, {@value #FLOATS_PER_VERTEX} floats each: position in model
 * pixels (1/16 block), texture coordinate normalised to the atlas, then the normal.</p>
 */
public final class Mesh {

    /** x, y, z, u, v, nx, ny, nz. */
    public static final int FLOATS_PER_VERTEX = 8;

    private final float[] data;

    /**
     * Slot for whoever draws this mesh to remember what it compiled it into (a GL display list id).
     * Zero means "not compiled yet". It lives here, rather than in a map on the drawing side, because
     * it is read once per group per bike per frame and a field read is the cheapest lookup there is.
     */
    public int handle;

    Mesh(float[] data) {
        this.data = data;
    }

    /** The raw vertex data; see the class docs for the layout. Not to be modified. */
    public float[] data() {
        return data;
    }

    public int vertexCount() {
        return data.length / FLOATS_PER_VERTEX;
    }

    public int quadCount() {
        return vertexCount() / 4;
    }
}
