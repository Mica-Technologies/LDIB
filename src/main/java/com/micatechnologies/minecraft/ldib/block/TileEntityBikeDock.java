package com.micatechnologies.minecraft.ldib.block;

import com.micatechnologies.minecraft.ldib.entity.BikeVariant;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * The state behind one {@link BlockBikeDock}: whether a bike is clipped into this dock point and, if
 * so, which {@link BikeVariant} it is. A dock holds at most one bike and — unlike a
 * {@link TileEntityBikeRack} — has <b>no owner</b>: a docked bike is fungible public infrastructure,
 * returned at one dock and checked out at any other, so the dock stores only the variant, never who
 * left it.
 *
 * <p>Synced to clients (via the standard tile-entity update packet) so the tile-entity renderer can
 * draw the docked bike. All mutation happens server-side from {@link BlockBikeDock#onBlockActivated}.</p>
 */
public class TileEntityBikeDock extends TileEntity {

    /** The docked bike's variant, or {@code null} when the dock is empty. */
    private BikeVariant variant;

    public boolean isOccupied() {
        return variant != null;
    }

    public BikeVariant variant() {
        return variant;
    }

    /** The dock's facing, read from the block state; defaults to NORTH. Drives the renderer. */
    public EnumFacing facing() {
        if (this.world != null) {
            IBlockState state = this.world.getBlockState(this.pos);
            if (state.getBlock() instanceof BlockBikeDock) {
                return state.getValue(BlockBikeDock.FACING);
            }
        }
        return EnumFacing.NORTH;
    }

    /** Clip a bike into this dock. */
    public void dock(BikeVariant variant) {
        this.variant = variant;
        sync();
    }

    /** Release the docked bike and return the variant that was here (or {@code null} if empty). */
    public BikeVariant undock() {
        BikeVariant removed = this.variant;
        this.variant = null;
        sync();
        return removed;
    }

    /** Mark dirty and push a block update so clients re-read the docked bike and redraw it. */
    private void sync() {
        markDirty();
        if (this.world != null && !this.world.isRemote) {
            IBlockState state = this.world.getBlockState(this.pos);
            this.world.notifyBlockUpdate(this.pos, state, state, 3);
        }
    }

    // --- Persistence -------------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("Occupied", variant != null);
        if (variant != null) {
            compound.setInteger("Variant", variant.id());
        }
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.variant = compound.getBoolean("Occupied")
            ? BikeVariant.byId(compound.getInteger("Variant")) : null;
    }

    // --- Client sync -------------------------------------------------------------------------

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(this.pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }

    /** Expand the render box so the docked bike (drawn above the post) is not culled. */
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(this.pos).grow(1.0D);
    }
}
