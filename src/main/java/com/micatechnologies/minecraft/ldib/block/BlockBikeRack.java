package com.micatechnologies.minecraft.ldib.block;

import com.micatechnologies.minecraft.ldib.LdibConstants;
import com.micatechnologies.minecraft.ldib.LdibTab;
import com.micatechnologies.minecraft.ldib.entity.BikeVariant;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import com.micatechnologies.minecraft.ldib.item.ItemBike;
import com.micatechnologies.minecraft.ldib.item.LdibItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
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
 * A bike rack: a player locks their own bike to it, and only they can unlock it. A rack may span
 * more than one block ({@link RackStyle#length()}): placing the master block fills in the extension
 * blocks along its facing, and breaking any part breaks the whole rack. Every block of a rack shares
 * this class; the single {@link TileEntityBikeRack} lives on the master (PART 0), and all interaction
 * and rendering resolve back to it.
 *
 * <p>All lock/unlock mutation is server-side.</p>
 */
public class BlockBikeRack extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    /** 0 = master (holds the tile entity); 1..length-1 = extension blocks along {@link #FACING}. */
    public static final PropertyInteger PART = PropertyInteger.create("part", 0, 2);

    private static final AxisAlignedBB RACK_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.6D, 1.0D);

    /** Re-entrancy guard so breaking one part (which air-sets the others) drops bikes only once. */
    private static final ThreadLocal<Boolean> BREAKING = ThreadLocal.withInitial(() -> false);

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
        setDefaultState(this.blockState.getBaseState()
            .withProperty(FACING, EnumFacing.NORTH).withProperty(PART, 0));
    }

    public RackStyle style() {
        return style;
    }

    // --- Block state / metadata --------------------------------------------------------------

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, PART);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byHorizontalIndex(meta & 3);
        int part = Math.min(2, (meta >> 2) & 3);
        return getDefaultState().withProperty(FACING, facing).withProperty(PART, part);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(PART) << 2);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX,
                                            float hitY, float hitZ, int meta, EntityLivingBase placer,
                                            EnumHand hand) {
        return getDefaultState()
            .withProperty(FACING, placer.getHorizontalFacing()).withProperty(PART, 0);
    }

    /** After the master lands, fill in the extension blocks along the facing. */
    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
                                ItemStack stack) {
        if (world.isRemote) {
            return;
        }
        EnumFacing facing = state.getValue(FACING);
        for (int i = 1; i < style.length(); i++) {
            BlockPos ext = pos.offset(facing, i);
            IBlockState there = world.getBlockState(ext);
            if (world.isAirBlock(ext) || there.getBlock().isReplaceable(world, ext)) {
                world.setBlockState(ext,
                    getDefaultState().withProperty(FACING, facing).withProperty(PART, i), 3);
            }
        }
    }

    // --- Tile entity (master only) -----------------------------------------------------------

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return state.getValue(PART) == 0;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return state.getValue(PART) == 0 ? new TileEntityBikeRack() : null;
    }

    /** The master block position for any part of this rack. */
    private static BlockPos masterPos(IBlockState state, BlockPos pos) {
        return pos.offset(state.getValue(FACING).getOpposite(), state.getValue(PART));
    }

    private TileEntityBikeRack masterTE(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(masterPos(state, pos));
        return te instanceof TileEntityBikeRack ? (TileEntityBikeRack) te : null;
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

    // --- Interaction (always resolved on the master TE) --------------------------------------

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY,
                                    float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntityBikeRack rack = masterTE(world, pos, state);
        if (rack == null) {
            return false;
        }
        ItemStack held = player.getHeldItem(hand);

        // What bike, if any, is the player presenting to lock?
        BikeVariant toLock = null;
        boolean fromRiding = false;
        if (held.getItem() instanceof ItemBike) {
            toLock = ((ItemBike) held.getItem()).variant();
        } else if (player.getRidingEntity() instanceof EntityBike) {
            toLock = ((EntityBike) player.getRidingEntity()).variant();
            fromRiding = true;
        }

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

        int mySlot = rack.firstSlotOwnedBy(player.getUniqueID());
        if (mySlot >= 0) {
            BikeVariant variant = rack.unlock(mySlot);
            giveBike(player, variant);
            status(player, "Unlocked your bike from the rack.");
            return true;
        }

        if (toLock != null && rack.isFull()) {
            status(player, "This rack is full (" + rack.capacity() + "/" + rack.capacity() + ").");
        } else if (rack.isEmpty()) {
            status(player, "Hold or ride a bike up to the rack to lock it here.");
        } else {
            status(player, "The bikes here are locked by " + rack.anyOwnerName() + ".");
        }
        return true;
    }

    /**
     * Lock the bike {@code player} is riding into this rack, if there's room — the "ride up and park"
     * path called from {@link EntityBike} when the click landed on the bike rather than the rack.
     * Returns true if the bike was locked.
     */
    public boolean tryLockRidden(World world, BlockPos pos, EntityPlayer player) {
        if (world.isRemote || !(player.getRidingEntity() instanceof EntityBike)) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        TileEntityBikeRack rack = masterTE(world, pos, state);
        if (rack == null || rack.isFull()) {
            return false;
        }
        EntityBike bike = (EntityBike) player.getRidingEntity();
        int slot = rack.firstFreeSlot();
        rack.lock(slot, player.getUniqueID(), player.getName(), bike.variant());
        bike.removePassengers();
        bike.setDead();
        status(player, "Locked your bike to the rack. Only you can unlock it. ("
            + rack.lockedCount() + "/" + rack.capacity() + ")");
        return true;
    }

    /** Breaking any part breaks the whole rack and drops every locked bike (exactly once). */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!BREAKING.get() && !world.isRemote) {
            BREAKING.set(true);
            try {
                BlockPos master = masterPos(state, pos);
                EnumFacing facing = state.getValue(FACING);

                // Drop all bikes from the master TE.
                TileEntity te = world.getTileEntity(master);
                if (te instanceof TileEntityBikeRack) {
                    TileEntityBikeRack rack = (TileEntityBikeRack) te;
                    for (int slot = 0; slot < rack.capacity(); slot++) {
                        TileEntityBikeRack.LockedBike bike = rack.getSlot(slot);
                        if (bike != null) {
                            ItemStack stack = new ItemStack(LdibItems.forVariant(bike.variant));
                            net.minecraft.inventory.InventoryHelper.spawnItemStack(world,
                                master.getX() + 0.5D, master.getY() + 0.5D, master.getZ() + 0.5D, stack);
                        }
                    }
                }

                // Remove every other part of the rack (these recurse in with the guard set).
                for (int i = 0; i < style.length(); i++) {
                    BlockPos part = master.offset(facing, i);
                    if (!part.equals(pos) && world.getBlockState(part).getBlock() == this) {
                        world.setBlockToAir(part);
                    }
                }
            } finally {
                BREAKING.set(false);
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
