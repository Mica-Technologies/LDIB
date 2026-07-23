package com.micatechnologies.minecraft.ldib.client.render;

import com.micatechnologies.minecraft.ldib.block.TileEntityBikeDock;
import com.micatechnologies.minecraft.ldib.entity.BikeVariant;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/**
 * Draws the bike clipped into a {@link TileEntityBikeDock}, so a docked bike is visible before you
 * check it out. Client-only.
 *
 * <p>Mirrors {@link TileEntityBikeRackRenderer}: the bike is centred on the dock and rotated by the
 * block's facing (using the same frame-rotation convention as the rack, so a docked bike and a racked
 * bike sit consistently relative to their block's facing), then drawn with the wheels-on-ground
 * transform from {@link RenderBike}.</p>
 */
public class TileEntityBikeDockRenderer extends TileEntitySpecialRenderer<TileEntityBikeDock> {

    /** Same near-full size as a racked bike (see {@link TileEntityBikeRackRenderer}). */
    private static final float BIKE_SCALE = 0.9F;

    /** Rotation that maps the local frame onto the block's facing (matches the rack renderer). */
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
    public void render(TileEntityBikeDock dock, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        BikeVariant variant = dock.variant();
        if (variant == null) {
            return;
        }
        ModelRideable model = RideableModels.forVariant(variant, true); // docked bikes are share bikes
        model.setWheelSpin(0.0F);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + 0.375D * BIKE_SCALE, z + 0.5D);
        GlStateManager.rotate(frameRotation(dock.facing()), 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(-BIKE_SCALE, -BIKE_SCALE, BIKE_SCALE);
        GlStateManager.enableRescaleNormal();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        bindTexture(variant.shareTexture()); // docked bikes are always public-fleet bikes
        model.render(null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F);

        // A bike idling in a share dock: headlight off, rear light dimly flashing (a calm slow blink).
        if (variant.hasLights() && dock.getWorld() != null) {
            boolean rearOn = (dock.getWorld().getTotalWorldTime() % 20L) < 10L;
            model.renderLights(0.0625F, false, rearOn, 0.4F);
        }

        GlStateManager.popMatrix();
    }
}
