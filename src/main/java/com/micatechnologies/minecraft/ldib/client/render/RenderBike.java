package com.micatechnologies.minecraft.ldib.client.render;

import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
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

    /** Degrees of cosmetic lean per degree/tick of heading change, capped by {@link #MAX_LEAN_DEG}. */
    private static final float LEAN_PER_YAW_RATE = 3.0F;
    private static final float MAX_LEAN_DEG = 22.0F;

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

        // Cosmetic lean into turns: roll about the travel axis by how fast the heading is changing.
        // Purely visual — the physics model doesn't know about it. Sign verified in-game 2026-07-22:
        // the model-space roll runs opposite the turn direction, so negate to lean INTO the turn
        // (A/left leans left, D/right leans right).
        float yawRate = MathHelper.wrapDegrees(entity.rotationYaw - entity.prevRotationYaw);
        float lean = MathHelper.clamp(-yawRate * LEAN_PER_YAW_RATE, -MAX_LEAN_DEG, MAX_LEAN_DEG);
        GlStateManager.rotate(lean, 0.0F, 0.0F, 1.0F);

        GlStateManager.scale(-1.0F, -1.0F, 1.0F);

        // Spin the wheels proportionally to ground speed so the rideable reads as moving.
        float wheelAngle = (float) (entity.speed() * (partialTicks + entity.ticksExisted) * 0.4D);
        ModelRideable model = RideableModels.forVariant(entity.variant());
        model.setWheelSpin(wheelAngle);

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
        return entity.variant().texture();
    }
}
