package com.micatechnologies.minecraft.ldib.block;

import com.micatechnologies.minecraft.ldib.LdibConstants;
import com.micatechnologies.minecraft.ldib.LdibTab;
import com.micatechnologies.minecraft.ldib.entity.BikeVariant;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import com.micatechnologies.minecraft.ldib.item.ItemBike;
import com.micatechnologies.minecraft.ldib.item.LdibItems;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * A bike rack: a player locks their own bike to it, and only they can unlock it. Private storage for
 * owned bikes, as opposed to the (planned) shared bike-share dock. One instance per {@link RackStyle};
 * the interaction and state are identical across styles, so they share this class and
 * {@link TileEntityBikeRack}.
 *
 * <p>All lock/unlock mutation is server-side ({@code !world.isRemote}). The client just returns
 * {@code true} from the interaction so the hand swings and no place-block happens.</p>
 */
public class BlockBikeRack extends Block {

    // A low rack footprint rather than a full cube — it is a piece of street furniture, not a wall.
    private static final AxisAlignedBB RACK_AABB = new AxisAlignedBB(0.1D, 0.0D, 0.1D, 0.9D, 0.6D, 0.9D);

    private final RackStyle style;

    public BlockBikeRack(RackStyle style) {
        super(Material.IRON);
        this.style = style;
        setHardness(1.5F);
        setResistance(6.0F);
        setSoundType(SoundType.METAL);
        setRegistryName(LdibConstants.MOD_NAMESPACE, "bike_rack_" + style.key());
        setTranslationKey(LdibConstants.MOD_NAMESPACE + ".bike_rack_" + style.key());
        setCreativeTab(LdibTab.LDIB_TAB);
    }

    public RackStyle style() {
        return style;
    }

    // --- Tile entity -------------------------------------------------------------------------

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityBikeRack();
    }

    // --- Shape / rendering -------------------------------------------------------------------

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, net.minecraft.world.IBlockAccess source,
                                        BlockPos pos) {
        return RACK_AABB;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    // --- Interaction -------------------------------------------------------------------------

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY,
                                    float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityBikeRack)) {
            return false;
        }
        TileEntityBikeRack rack = (TileEntityBikeRack) te;
        ItemStack held = player.getHeldItem(hand);

        // Work out whether the player is presenting a bike to lock, and which variant.
        BikeVariant toLock = null;
        boolean fromRiding = false;
        if (held.getItem() instanceof ItemBike) {
            toLock = ((ItemBike) held.getItem()).variant();
        } else if (player.getRidingEntity() instanceof EntityBike) {
            toLock = ((EntityBike) player.getRidingEntity()).variant();
            fromRiding = true;
        }

        // Locking takes priority when there is a free slot: fill the rack up.
        if (toLock != null && !rack.isFull()) {
            int slot = rack.firstFreeSlot();
            rack.lock(slot, player.getUniqueID(), player.getName(), toLock);
            if (fromRiding) {
                EntityBike bike = (EntityBike) player.getRidingEntity();
                bike.removePassengers();
                bike.setDead();
            } else if (!player.capabilities.isCreativeMode) {
                held.shrink(1);
            }
            status(player, "Locked your bike to the rack. Only you can unlock it. ("
                + rack.lockedCount() + "/" + rack.capacity() + ")");
            return true;
        }

        // Otherwise, try to take the player's own bike back out.
        int mySlot = rack.firstSlotOwnedBy(player.getUniqueID());
        if (mySlot >= 0) {
            BikeVariant variant = rack.unlock(mySlot);
            giveBike(player, variant);
            status(player, "Unlocked your bike from the rack.");
            return true;
        }

        // Nothing to add and nothing here of theirs — say why.
        if (toLock != null && rack.isFull()) {
            status(player, "This rack is full (" + rack.capacity() + "/" + rack.capacity() + ").");
        } else if (rack.isEmpty()) {
            status(player, "Hold or ride a bike up to the rack to lock it here.");
        } else {
            status(player, "The bikes here are locked by " + rack.anyOwnerName() + ".");
        }
        return true;
    }

    /** Breaking a rack must not eat any locked bikes — drop them all so they are recoverable. */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityBikeRack) {
            TileEntityBikeRack rack = (TileEntityBikeRack) te;
            for (int slot = 0; slot < rack.capacity(); slot++) {
                TileEntityBikeRack.LockedBike bike = rack.getSlot(slot);
                if (bike != null) {
                    ItemStack stack = new ItemStack(LdibItems.forVariant(bike.variant));
                    net.minecraft.inventory.InventoryHelper.spawnItemStack(
                        world, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    private static void giveBike(EntityPlayer player, BikeVariant variant) {
        ItemStack stack = new ItemStack(LdibItems.forVariant(variant));
        if (!player.inventory.addItemStackToInventory(stack)) {
            player.dropItem(stack, false);
        }
    }

    private static void status(EntityPlayer player, String message) {
        player.sendStatusMessage(new TextComponentString(message), true);
    }
}
