package com.micatechnologies.minecraft.ldib.client.render;

import com.micatechnologies.minecraft.ldib.client.render.mesh.RideableModel;
import com.micatechnologies.minecraft.ldib.client.render.mesh.RideableModels;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

/**
 * Draws an {@link EntityBike}. Client-only — this package is the only bike code that touches {@code
 * net.minecraft.client}, reached exclusively through {@code LdibClientProxy}.
 *
 * <p>The transform mirrors the vanilla minecart/boat renderers: translate to the interpolated entity
 * position, yaw to face travel, flip into model space, then draw. What is drawn, and how cheaply, is
 * {@link RideableModel}'s business; this class only poses it from the entity.</p>
 *
 * <p><b>Nothing here asks the world a question per frame.</b> Whether the headlight should be on is
 * answered by the entity, which re-checks a couple of times a second (see {@link
 * EntityBike#isDarkAround}); the skin is a constant held by the variant. With a station's worth of
 * bikes in view, per-frame lookups were a measurable part of drawing them.</p>
 */
public class RenderBike extends Render<EntityBike> {

    public RenderBike(RenderManager renderManager) {
        super(renderManager);
        this.shadowSize = 0.5F;
    }

    @Override
    public void doRender(EntityBike entity, double x, double y, double z, float entityYaw,
                         float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + 0.375F, (float) z);
        GlStateManager.rotate(180.0F - entityYaw, 0.0F, 1.0F, 0.0F);

        // Cosmetic lean into turns — eased and interpolated on the entity (see EntityBike#bikeLean)
        // so it neither snaps at tick boundaries nor is renderer-local (one RenderBike serves every
        // bike). Purely visual — the physics model doesn't know about it.
        GlStateManager.rotate(entity.bikeLean(partialTicks), 0.0F, 0.0F, 1.0F);

        GlStateManager.scale(-1.0F, -1.0F, 1.0F);

        // The pose, all interpolated on the entity so it reads as continuous motion: how far the
        // wheels and pedals have turned, and how far the front assembly is turned into the corner.
        // Purely visual — the physics heading is already in entityYaw above.
        RideableModel model = RideableModels.forVariant(entity.variant(), entity.isShare());
        float steer = entity.steerAngle(partialTicks);

        bindEntityTexture(entity);
        model.render(GlCanvas.INSTANCE, entity.wheelRotation(partialTicks), entity.crankRotation(partialTicks),
            steer, !entity.isBeingRidden());

        // Lights (e-bike / scooter / one-wheel). Headlight: on when it is dark at the bike. Rear light:
        // a running tail light that always flashes (like a real bike-share bike) and goes solid while
        // braking or stopped — so it is visible day or night, not only when braking in the dark.
        if (entity.variant().hasLights()) {
            boolean solid = entity.isBraking() || Math.abs(entity.speed()) < 0.1D;
            boolean rearOn = solid || (entity.ticksExisted % 10 < 5);
            model.renderLights(GlCanvas.INSTANCE, steer, entity.isDarkAround(), rearOn, 1.0F);
        }

        GlStateManager.popMatrix();
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityBike entity) {
        return entity.texture(); // muted fleet livery for share bikes, the variant skin otherwise
    }
}
