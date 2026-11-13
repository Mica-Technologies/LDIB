package com.micatechnologies.minecraft.ldib.client.render.mesh;

/**
 * What a {@link RideableModel} draws onto. The game's implementation issues GL calls; the offline
 * preview's rasterises to an image. Keeping the models behind this is what lets them stay free of
 * Minecraft types.
 *
 * <p>Distances are model pixels (1/16 block) and angles are degrees, as in the GL calls this mirrors.
 * The skin atlas is expected to be bound already.</p>
 */
public interface Canvas {

    void push();

    void pop();

    void translate(float x, float y, float z);

    void rotate(float degrees, float x, float y, float z);

    void draw(Mesh mesh);

    /**
     * Start drawing things that give off light: from here until {@link #endEmissive} meshes are drawn
     * at full brightness, ignoring the light level of the world around them, scaled by {@code
     * intensity} (0–1).
     */
    void beginEmissive(float intensity);

    /**
     * Within an emissive pass, switch to drawing halos: meshes from here on <i>add</i> their colour
     * to what is already on screen, scaled by {@code intensity}, are visible from both sides, and
     * hide nothing behind them.
     */
    void beginGlow(float intensity);

    /**
     * Finish the emissive pass, leaving everything about the drawing state exactly as {@link
     * #beginEmissive} found it.
     */
    void endEmissive();
}
