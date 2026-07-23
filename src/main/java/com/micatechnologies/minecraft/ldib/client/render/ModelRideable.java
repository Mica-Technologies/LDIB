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
 *
 * <p><b>Orientation.</b> All models here are authored with their <b>front toward −z</b>. {@link
 * RenderBike}'s minecart/boat transform (rotate {@code 180 − yaw}, then {@code scale(-1,-1,1)}) maps
 * model −z onto the world direction the rider faces, so front-at−z renders forward. (Earlier models
 * had the handlebars at +z, i.e. facing backwards — invisible in first person on the seated bike, but
 * glaring on the standing scooter.)</p>
 *
 * <p><b>Round-ish wheels.</b> {@link #buildWheel} fakes a round wheel from six thin planks that each
 * span the full diameter, rotated in 30° steps about the axle — a twelve-sided silhouette. Spinning
 * the wheel is then just adding an angle to every plank ({@link #spinWheel}).</p>
 */
public abstract class ModelRideable extends ModelBase {

    /** Number of planks per wheel; each contributes two rim points → a 12-gon. */
    private static final int WHEEL_PLANKS = 6;
    private static final double PLANK_STEP = Math.PI / WHEEL_PLANKS; // 30°

    /** Spin the wheels by {@code wheelAngle} radians — called by renderers from ground speed. */
    public abstract void setWheelSpin(float wheelAngle);

    /**
     * Draw this rideable's emissive light fixtures — a white headlight and a red brake light — each at
     * full brightness so they glow regardless of ambient light. Variants without lights (the pedal
     * bicycle) are simply never asked to draw them; see {@link
     * com.micatechnologies.minecraft.ldib.entity.BikeVariant#hasLights()}.
     */
    public abstract void renderLights(float scale, boolean headlightOn, boolean brakeLightOn);

    // --- Model-building helpers --------------------------------------------------------------

    /**
     * A tube lying in the bike's centre plane (x = 0), from {@code (ay, az)} to {@code (by, bz)} in
     * model space, {@code w} pixels thick. Used for every frame member; since they all lie in the
     * centre plane, a single rotation about the axle (X) aims each one.
     */
    protected ModelRenderer tube(float ay, float az, float by, float bz, int w) {
        float dy = by - ay;
        float dz = bz - az;
        int len = Math.max(1, Math.round((float) Math.sqrt(dy * dy + dz * dz)));
        ModelRenderer t = new ModelRenderer(this, 0, 0);
        t.addBox(-w / 2.0F, 0.0F, -w / 2.0F, w, len, w);
        t.setRotationPoint(0.0F, ay, az);
        t.rotateAngleX = (float) Math.atan2(dz, dy);
        return t;
    }

    /**
     * A round-ish wheel centred on the axle at {@code (ay, az)}: {@code radius} px, {@code tireWidth}
     * px across (the axle direction — keep this thinner than the frame to avoid z-fighting), each
     * plank {@code rimThickness} px. Returns the six planks; render them as a group and spin with
     * {@link #spinWheel}.
     */
    protected ModelRenderer[] buildWheel(float ay, float az, int radius, int tireWidth, int rimThickness) {
        ModelRenderer[] planks = new ModelRenderer[WHEEL_PLANKS];
        for (int i = 0; i < WHEEL_PLANKS; i++) {
            ModelRenderer p = new ModelRenderer(this, 0, 0);
            p.addBox(-tireWidth / 2.0F, -radius, -rimThickness / 2.0F, tireWidth, radius * 2, rimThickness);
            p.setRotationPoint(0.0F, ay, az);
            p.rotateAngleX = (float) (i * PLANK_STEP);
            planks[i] = p;
        }
        return planks;
    }

    /** Re-aim a wheel's planks so the whole wheel is rotated by {@code spin} radians. */
    protected static void spinWheel(ModelRenderer[] wheel, float spin) {
        for (int i = 0; i < wheel.length; i++) {
            wheel[i].rotateAngleX = (float) (i * PLANK_STEP) + spin;
        }
    }

    /** Render every part in a group (e.g. a wheel's planks). */
    protected static void renderGroup(ModelRenderer[] parts, float scale) {
        for (ModelRenderer part : parts) {
            part.render(scale);
        }
    }

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
