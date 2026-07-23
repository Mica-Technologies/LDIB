package com.micatechnologies.minecraft.ldib.client.render;

import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;

/**
 * Draws an {@link EntityBike}. Client-only — this class and {@link ModelBike} are the only bike code
 * that touches {@code net.minecraft.client}, reached exclusively through {@code LdibClientProxy}.
 *
 * <p>The transform mirrors the vanilla minecart/boat renderers: translate to the interpolated
 * entity position, yaw to face travel, flip into model space, then draw. Exact vertical placement of
 * the wheels on the ground is a known play-test tuning task (see the master plan) — the geometry is
 * correct; the offset just wants an in-game eye.</p>
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

        // Spin the wheels by the interpolated angle accumulated on the entity (see
        // EntityBike#wheelRotation) so it reads as continuous rolling rather than snapping when speed
        // changes.
        ModelRideable model = RideableModels.forVariant(entity.variant());
        model.setWheelSpin(entity.wheelRotation(partialTicks));

        bindEntityTexture(entity);
        model.render(entity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F);

        // Emissive lights (e-bike / scooter). Headlight: on when it's dark at the bike. Rear light: a
        // running tail light that always flashes (like a real bike-share bike) and goes solid while
        // braking — so it's visible day or night, not only when braking in the dark.
        if (entity.variant().hasLights()) {
            BlockPos pos = new BlockPos(entity.posX, entity.posY + 0.5D, entity.posZ);
            // Headlight comes on when it's dark at the bike. Stored sky light stays 15 under open sky
            // regardless of time (day/night dimming is a render-only effect), so darkness is judged from
            // the synced time of day, plus block light for caves/shade. On when: it's night, or the spot
            // has little sky access — and no bright block light nearby.
            long timeOfDay = ((entity.world.getWorldTime() % 24000L) + 24000L) % 24000L;
            boolean night = timeOfDay >= 13000L && timeOfDay <= 23000L;
            int blockLight = entity.world.getLightFor(EnumSkyBlock.BLOCK, pos);
            int skyLight = entity.world.getLightFor(EnumSkyBlock.SKY, pos);
            boolean dark = blockLight < 8 && (night || skyLight < 8);
            // Rear light: solid when braking (S) or stopped, otherwise a fast flash (period 10 ticks).
            boolean solid = entity.isBraking() || Math.abs(entity.speed()) < 0.1D;
            boolean rearOn = solid || (entity.ticksExisted % 10 < 5);
            model.renderLights(0.0625F, dark, rearOn, 1.0F);
        }

        GlStateManager.popMatrix();
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityBike entity) {
        return entity.texture(); // muted fleet livery for share bikes, the variant skin otherwise
    }
}
