package com.micatechnologies.minecraft.ldib.block;

import com.micatechnologies.minecraft.ldib.entity.BikeVariant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * The state behind one {@link BlockBikeRack}: the bikes locked into its slots, each with the owner
 * who may take it back. A rack holds up to {@link RackStyle#capacity()} bikes, one per slot, and each
 * slot can belong to a different player — a rack is shared street furniture, but each bike on it is
 * private to whoever locked it.
 *
 * <p>Synced to clients (via the standard tile-entity update packet) so the tile-entity renderer can
 * draw the locked bikes where they sit. All mutation happens server-side from
 * {@link BlockBikeRack#onBlockActivated}.</p>
 */
public class TileEntityBikeRack extends TileEntity {

    /** One locked bike: which variant, and who owns it. */
    public static final class LockedBike {
        public final BikeVariant variant;
        public final UUID owner;
        public final String ownerName;

        public LockedBike(BikeVariant variant, UUID owner, String ownerName) {
            this.variant = variant;
            this.owner = owner;
            this.ownerName = ownerName == null ? "" : ownerName;
        }
    }

    /** slot index -> locked bike. Absent key = empty slot. */
    private final Map<Integer, LockedBike> locked = new HashMap<>();

    /** The style of the block at this position (its capacity + slot layout), or HOOP as a fallback. */
    public RackStyle style() {
        if (this.world != null) {
            Block block = this.world.getBlockState(this.pos).getBlock();
            if (block instanceof BlockBikeRack) {
                return ((BlockBikeRack) block).style();
            }
        }
        return RackStyle.HOOP;
    }

    public int capacity() {
        return style().capacity();
    }

    public int lockedCount() {
        return locked.size();
    }

    public boolean isEmpty() {
        return locked.isEmpty();
    }

    public boolean isFull() {
        return locked.size() >= capacity();
    }

    public LockedBike getSlot(int slot) {
        return locked.get(slot);
    }

    /** First empty slot in {@code [0, capacity)}, or {@code -1} if the rack is full. */
    public int firstFreeSlot() {
        int cap = capacity();
        for (int i = 0; i < cap; i++) {
            if (!locked.containsKey(i)) {
                return i;
            }
        }
        return -1;
    }

    /** First slot owned by {@code player}, or {@code -1} if they have no bike here. */
    public int firstSlotOwnedBy(UUID player) {
        for (Map.Entry<Integer, LockedBike> e : locked.entrySet()) {
            LockedBike b = e.getValue();
            if (b.owner != null && b.owner.equals(player)) {
                return e.getKey();
            }
        }
        return -1;
    }

    /** A name to show in the "locked by …" message for any occupied slot. */
    public String anyOwnerName() {
        for (LockedBike b : locked.values()) {
            if (!b.ownerName.isEmpty()) {
                return b.ownerName;
            }
        }
        return "another player";
    }

    public void lock(int slot, UUID owner, String ownerName, BikeVariant variant) {
        locked.put(slot, new LockedBike(variant, owner, ownerName));
        sync();
    }

    /** Empty a slot and return the variant that was there (for handing the item back), or null. */
    public BikeVariant unlock(int slot) {
        LockedBike removed = locked.remove(slot);
        sync();
        return removed == null ? null : removed.variant;
    }

    /** Mark dirty and push a block update so clients re-read the locked bikes and redraw them. */
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
        NBTTagList list = new NBTTagList();
        for (Map.Entry<Integer, LockedBike> e : locked.entrySet()) {
            LockedBike b = e.getValue();
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Slot", e.getKey());
            tag.setInteger("Variant", b.variant.id());
            tag.setString("OwnerName", b.ownerName);
            if (b.owner != null) {
                tag.setUniqueId("Owner", b.owner);
            }
            list.appendTag(tag);
        }
        compound.setTag("Locked", list);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        locked.clear();
        NBTTagList list = compound.getTagList("Locked", 10); // 10 = compound
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int slot = tag.getInteger("Slot");
            BikeVariant variant = BikeVariant.byId(tag.getInteger("Variant"));
            UUID owner = tag.hasUniqueId("Owner") ? tag.getUniqueId("Owner") : null;
            locked.put(slot, new LockedBike(variant, owner, tag.getString("OwnerName")));
        }
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

    /** Expand the render box so the bikes, which overhang the rack block, are not frustum-culled. */
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(this.pos).grow(1.0D);
    }
}
