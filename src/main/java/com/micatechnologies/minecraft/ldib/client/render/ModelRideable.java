package com.micatechnologies.minecraft.ldib.client.render;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;

/**
 * Common client-side base for a rideable's model — a {@link ModelBase} whose wheels can be spun from
 * ground speed and whose lights can glow. {@link ModelBike} and {@link ModelScooter} both extend it so
 * the entity renderer and the rack / dock tile-entity renderers can hold one reference and draw
 * whichever variant is present (see {@link RideableModels}). Client-only, like everything in this
 * package.
 */
public abstract class ModelRideable extends ModelBase {

    /** Spin the wheels by {@code wheelAngle} radians — called by renderers from ground speed. */
    public abstract void setWheelSpin(float wheelAngle);

    /**
     * Draw this rideable's emissive light fixtures — a white headlight and a red brake light — each at
     * full brightness so they glow regardless of ambient light. Variants without lights (the pedal
     * bicycle) are simply never asked to draw them; see {@link
     * com.micatechnologies.minecraft.ldib.entity.BikeVariant#hasLights()}.
     */
    public abstract void renderLights(float scale, boolean headlightOn, boolean brakeLightOn);

    /**
     * Render the given light parts emissively: texture off (so they show as flat colour on the
     * placeholder skins), lighting off, and the lightmap forced to full bright, then restored. A
     * headlight is warm white; a brake light is red.
     */
    protected static void renderLightFixtures(float scale, boolean headlightOn, ModelRenderer headlight,
                                              boolean brakeLightOn, ModelRenderer brakeLight) {
        if (!headlightOn && !brakeLightOn) {
            return;
        }
        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

        if (headlightOn) {
            GlStateManager.color(1.0F, 1.0F, 0.85F, 1.0F);
            headlight.render(scale);
        }
        if (brakeLightOn) {
            GlStateManager.color(1.0F, 0.05F, 0.05F, 1.0F);
            brakeLight.render(scale);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
    }
}
