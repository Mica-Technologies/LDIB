package com.micatechnologies.minecraft.ldib.client.render;

import com.micatechnologies.minecraft.ldib.block.RackStyle;
import com.micatechnologies.minecraft.ldib.block.TileEntityBikeRack;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

/**
 * Draws the bikes locked into a {@link TileEntityBikeRack} at their slot positions, so players can
 * see where their bike is parked. Client-only — reached solely from {@code LdibClientProxy}.
 *
 * <p>Each occupied slot renders a {@link ModelBike} (with the locked bike's variant texture) standing
 * on the ground at the slot, using the same wheels-on-ground transform as the ridden-bike renderer.</p>
 */
public class TileEntityBikeRackRenderer extends TileEntitySpecialRenderer<TileEntityBikeRack> {

    private final ModelBike model = new ModelBike();

    @Override
    public void render(TileEntityBikeRack rack, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        RackStyle.Slot[] slots = rack.style().slots();
        this.model.setWheelSpin(0.0F);

        for (int i = 0; i < slots.length; i++) {
            TileEntityBikeRack.LockedBike bike = rack.getSlot(i);
            if (bike == null) {
                continue;
            }
            RackStyle.Slot slot = slots[i];

            GlStateManager.pushMatrix();
            // Same wheels-on-ground transform as RenderBike, applied at the slot within the block.
            GlStateManager.translate(x + slot.x, y + 0.375D, z + slot.z);
            GlStateManager.rotate(180.0F - slot.yaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.scale(-1.0F, -1.0F, 1.0F);
            GlStateManager.enableRescaleNormal();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            bindTexture(bike.variant.texture());
            this.model.render(null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F);

            GlStateManager.popMatrix();
        }
    }
}
