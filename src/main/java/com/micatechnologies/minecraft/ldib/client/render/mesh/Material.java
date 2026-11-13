package com.micatechnologies.minecraft.ldib.client.render.mesh;

/**
 * One painted rectangle of the skin atlas — a material (tyre rubber, frame paint, alloy…). Every
 * face made of that material samples somewhere inside the rectangle, so a skin is recoloured by
 * repainting a handful of rectangles and never by re-cutting UVs. Sizes and offsets are in texels;
 * the rectangles themselves are listed in {@link Skin} and painted by {@code tools/gen_skins.py}.
 */
public final class Material {

    final int u;
    final int v;
    final int w;
    final int h;

    public Material(int u, int v, int w, int h) {
        this.u = u;
        this.v = v;
        this.w = w;
        this.h = h;
    }

    /**
     * A patch for a face {@code faceW × faceH} pixels in size, taken from the rectangle's top-left
     * corner at one texel per pixel and clamped to the rectangle. That keeps the painted detail (a
     * tube's highlight down its left edge, a saddle's stitching) at the scale it was painted for.
     *
     * @return {@code {u0, v0, u1, v1}} in texels
     */
    public float[] patch(double faceW, double faceH) {
        int pw = Math.max(1, Math.min(w, (int) Math.ceil(faceW - 1.0e-6)));
        int ph = Math.max(1, Math.min(h, (int) Math.ceil(faceH - 1.0e-6)));
        return new float[] {u, v, u + pw, v + ph};
    }

    /** The single texel at ({@code col}, {@code row}), wrapping inside the rectangle. */
    public float[] texel(int col, int row) {
        int c = Math.floorMod(col, w);
        int r = Math.floorMod(row, h);
        return new float[] {u + c, v + r, u + c + 1, v + r + 1};
    }

    /** The whole rectangle. */
    public float[] all() {
        return new float[] {u, v, u + w, v + h};
    }

    /** A smaller material cut out of this one — a swatch within a shared rectangle. */
    public Material sub(int du, int dv, int subW, int subH) {
        return new Material(u + du, v + dv, subW, subH);
    }
}
