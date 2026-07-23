package com.micatechnologies.minecraft.ldib.client.render;

import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

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

    private final ModelBike model = new ModelBike();

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

        // Spin the wheels proportionally to ground speed so the bike reads as moving.
        float wheelAngle = (float) (entity.speed() * (partialTicks + entity.ticksExisted) * 0.4D);
        this.model.setWheelSpin(wheelAngle);

        bindEntityTexture(entity);
        this.model.render(entity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F);

        GlStateManager.popMatrix();
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityBike entity) {
        return entity.variant().texture();
    }
}
