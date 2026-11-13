package com.micatechnologies.minecraft.ldib.client.render;

import com.micatechnologies.minecraft.ldib.client.render.mesh.Canvas;
import com.micatechnologies.minecraft.ldib.client.render.mesh.Mesh;
import java.nio.FloatBuffer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Draws {@link Mesh}es with OpenGL: the game's side of {@link Canvas}. Client-only.
 *
 * <p><b>Meshes are display lists.</b> Each is compiled the first time it is drawn and replayed with
 * one call from then on, the way vanilla's own entity model parts are — only here a list holds a
 * whole rigid group rather than a single box. A list records no matrix, so the one wheel list is
 * replayed under two transforms. They are never freed: there are a couple of dozen of them, they
 * belong to models that live as long as the game does, and nothing about them depends on the world
 * or on resource packs.</p>
 *
 * <p><b>The light pass does not touch {@code GlStateManager}, on purpose.</b> {@code GlStateManager}
 * skips any call that its own cache says would change nothing. That cache is only right while every
 * renderer goes through it, and in a modded game the cache and the real GL state can already disagree
 * by the time a bike is drawn (measured: with other mods' blocks beside a dock, the blend factors
 * did). The old light pass enabled blending, changed the blend function and so on <i>through</i> the
 * cache, and only on frames where a light was lit — and was measured rewriting the cache on those
 * frames and not on dark ones. That is the suspected cause of issue #1, blocks near a bike flashing
 * in time with its tail light: whatever drew next would see a different cache from frame to frame.
 * Restoring the blend function afterwards (the first fix) did not cure it, which fits: that put the
 * state back, not the cache. The flashing itself was only ever seen with OptiFine and was not
 * reproduced in a development client, so treat the diagnosis as likely rather than proven.</p>
 *
 * <p>So {@link #beginEmissive} saves the real state with {@code glPushAttrib}, the pass changes it
 * with raw GL calls that the cache never hears about, and {@link #endEmissive} puts the real state
 * back with {@code glPopAttrib}. Afterwards both the state and the cache are exactly as they were
 * found, whatever that was, lit frame or not. Do not "tidy" these into {@code GlStateManager} calls.</p>
 */
public final class GlCanvas implements Canvas {

    public static final GlCanvas INSTANCE = new GlCanvas();

    /** Model pixels to blocks. Baked into the lists, and applied to translations here. */
    private static final float PX = 0.0625F;

    /** How bright a halo is at full intensity; it is added to the scene, so 1 would wash it out. */
    private static final float GLOW_STRENGTH = 0.8F;

    private static final int EMISSIVE_STATE = GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
        | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_FOG_BIT;

    private static final FloatBuffer BLACK = GLAllocation.createDirectFloatBuffer(16);

    static {
        BLACK.put(0.0F).put(0.0F).put(0.0F).put(1.0F);
        BLACK.position(0).limit(4);
    }

    private float savedLightmapX;
    private float savedLightmapY;

    private GlCanvas() {
    }

    @Override
    public void push() {
        GlStateManager.pushMatrix();
    }

    @Override
    public void pop() {
        GlStateManager.popMatrix();
    }

    @Override
    public void translate(float x, float y, float z) {
        GlStateManager.translate(x * PX, y * PX, z * PX);
    }

    @Override
    public void rotate(float degrees, float x, float y, float z) {
        GlStateManager.rotate(degrees, x, y, z);
    }

    @Override
    public void draw(Mesh mesh) {
        int list = mesh.handle;
        if (list == 0) {
            list = compile(mesh);
        }
        GlStateManager.callList(list);
    }

    private static int compile(Mesh mesh) {
        int list = GLAllocation.generateDisplayLists(1);
        GlStateManager.glNewList(list, GL11.GL_COMPILE);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        // The same vertex format vanilla's model boxes use, so these light like any other entity.
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL);
        float[] d = mesh.data();
        for (int i = 0; i < d.length; i += Mesh.FLOATS_PER_VERTEX) {
            buffer.pos(d[i] * PX, d[i + 1] * PX, d[i + 2] * PX)
                .tex(d[i + 3], d[i + 4])
                .normal(d[i + 5], d[i + 6], d[i + 7])
                .endVertex();
        }
        tessellator.draw();
        GlStateManager.glEndList();
        mesh.handle = list;
        return list;
    }

    @Override
    public void beginEmissive(float intensity) {
        float i = Math.max(0.0F, Math.min(1.0F, intensity));
        // The lightmap coordinate is not something the cache tracks, but OpenGlHelper remembers the
        // last one set, so that goes back through OpenGlHelper rather than with the attributes.
        savedLightmapX = OpenGlHelper.lastBrightnessX;
        savedLightmapY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(EMISSIVE_STATE);
        GL11.glDisable(GL11.GL_LIGHTING);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GL11.glColor4f(i, i, i, 1.0F);
    }

    @Override
    public void beginGlow(float intensity) {
        float g = GLOW_STRENGTH * Math.max(0.0F, Math.min(1.0F, intensity));
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        // Fog is mixed in before blending. A halo's black surround adds nothing — unless fog has
        // tinted it, when the whole square would be added as a faint grey tile at a distance. Black
        // fog fades the halo out with distance instead.
        GL11.glFog(GL11.GL_FOG_COLOR, BLACK);
        GL11.glColor4f(g, g, g, 1.0F);
    }

    @Override
    public void endEmissive() {
        GL11.glPopAttrib();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, savedLightmapX, savedLightmapY);
    }
}
