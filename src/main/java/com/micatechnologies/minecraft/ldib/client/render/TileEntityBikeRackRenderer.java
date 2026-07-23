package com.micatechnologies.minecraft.ldib.client.render;

import com.micatechnologies.minecraft.ldib.block.RackStyle;
import com.micatechnologies.minecraft.ldib.block.TileEntityBikeRack;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/**
 * Draws the bikes locked into a {@link TileEntityBikeRack} at their slot positions, so players can
 * see where their bike is parked — spread along the whole (possibly multi-block) rack. Client-only.
 *
 * <p>The rack's slots are defined in a local frame (local +X = along the length, local +Z = across);
 * this rotates that frame by the block's facing so the layout follows however the rack was placed,
 * then draws each locked bike scaled down with the same wheels-on-ground transform as
 * {@link RenderBike}. The frame rotation is the negative of the blockstate's y-rotation for that
 * facing (blockstate y is clockwise; {@code GlStateManager.rotate} is counter-clockwise).</p>
 */
public class TileEntityBikeRackRenderer extends TileEntitySpecialRenderer<TileEntityBikeRack> {

    /**
     * Parked bikes are drawn close to a ridden bike's size (which is 1.0) so a bike on a rack doesn't
     * look like a toy next to one out on the road — just a touch under full size for a little
     * breathing room between neighbours. Slot spacing in {@link RackStyle} is matched to this.
     */
    private static final float BIKE_SCALE = 0.9F;

    /** Rotation that maps the local +X axis onto the block's facing (see class javadoc). */
    private static float frameRotation(EnumFacing facing) {
        // if/else, not switch, to avoid a synthetic switch-map class (see BikeVariant#tuning()).
        if (facing == EnumFacing.SOUTH) {
            return -90.0F;
        }
        if (facing == EnumFacing.WEST) {
            return -180.0F;
        }
        if (facing == EnumFacing.NORTH) {
            return -270.0F;
        }
        return 0.0F; // EAST
    }

    @Override
    public void render(TileEntityBikeRack rack, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        RackStyle.Slot[] slots = rack.style().slots();

        GlStateManager.pushMatrix();
        // Centre on the master block, at ground level, then rotate into the rack's local frame.
        GlStateManager.translate(x + 0.5D, y, z + 0.5D);
        GlStateManager.rotate(frameRotation(rack.facing()), 0.0F, 1.0F, 0.0F);

        for (int i = 0; i < slots.length; i++) {
            TileEntityBikeRack.LockedBike bike = rack.getSlot(i);
            if (bike == null) {
                continue;
            }
            RackStyle.Slot slot = slots[i];
            ModelRideable model = RideableModels.forVariant(bike.variant, false); // racked bikes are personal
            model.setWheelSpin(0.0F);

            GlStateManager.pushMatrix();
            // Local: +X along the length, +Z across it. Ground lift scales with the bike.
            GlStateManager.translate(slot.along, 0.375D * BIKE_SCALE, slot.across);
            GlStateManager.rotate(180.0F - slot.yaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.scale(-BIKE_SCALE, -BIKE_SCALE, BIKE_SCALE);
            GlStateManager.enableRescaleNormal();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            bindTexture(bike.variant.texture());
            model.render(null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F);

            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
    }
}
