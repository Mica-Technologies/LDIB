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
 * A bike-share dock: a public post you clip one rideable to, checked out at one dock and returned at
 * any other. Unlike a {@link BlockBikeRack} — which is your own owner-locked storage — a dock is
 * shared infrastructure: a docked bike is fungible, so the dock stores only the {@link BikeVariant},
 * never an owner. The shared fleet is tracked in a world-level {@link BikeShareNetwork}.
 *
 * <ul>
 *   <li><b>Check out</b> — right-click an occupied dock empty-handed: its bike undocks as an
 *       {@link EntityBike} spawned just in front of the dock, and the dock goes empty.</li>
 *   <li><b>Return</b> — ride or carry a bike up to a free dock and right-click: the bike is consumed
 *       into the dock, which goes occupied.</li>
 * </ul>
 *
 * <p>This first cut runs an <b>infinite fleet</b> (check-out is never blocked); the network's counters
 * make a fixed-size fleet a drop-in later. All mutation is server-side.</p>
 */
public class BlockBikeDock extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    private static final AxisAlignedBB DOCK_AABB = new AxisAlignedBB(0.1D, 0.0D, 0.1D, 0.9D, 0.5D, 0.9D);

    public BlockBikeDock() {
        super(Material.IRON);
        setHardness(1.5F);
        setResistance(6.0F);
        setSoundType(SoundType.METAL);
        setRegistryName(LdibConstants.MOD_NAMESPACE, "bike_dock");
        setTranslationKey(LdibConstants.MOD_NAMESPACE + ".bike_dock");
        setCreativeTab(LdibTab.LDIB_TAB);
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    // --- Block state / metadata --------------------------------------------------------------

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX,
                                            float hitY, float hitZ, int meta, EntityLivingBase placer,
                                            EnumHand hand) {
        // Face the dock toward the player who placed it, so a docked bike points out at them.
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
                                ItemStack stack) {
        if (!world.isRemote) {
            BikeShareNetwork.get(world).registerDock();
        }
    }

    // --- Tile entity -------------------------------------------------------------------------

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityBikeDock();
    }

    private static TileEntityBikeDock dockTE(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileEntityBikeDock ? (TileEntityBikeDock) te : null;
    }

    // --- Shape / rendering -------------------------------------------------------------------

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, net.minecraft.world.IBlockAccess source,
                                        BlockPos pos) {
        return DOCK_AABB;
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
        TileEntityBikeDock dock = dockTE(world, pos);
        if (dock == null) {
            return false;
        }
        BikeShareNetwork network = BikeShareNetwork.get(world);

        // What bike, if any, is the player presenting to return?
        ItemStack held = player.getHeldItem(hand);
        BikeVariant presenting = null;
        boolean fromRiding = false;
        if (held.getItem() instanceof ItemBike) {
            presenting = ((ItemBike) held.getItem()).variant();
        } else if (player.getRidingEntity() instanceof EntityBike) {
            presenting = ((EntityBike) player.getRidingEntity()).variant();
            fromRiding = true;
        }

        if (dock.isOccupied()) {
            if (presenting != null) {
                status(player, "This dock is occupied. Return your bike at a free dock.");
                return true;
            }
            // Check out: undock the bike and spawn it in front of the dock.
            BikeVariant variant = dock.undock();
            spawnInFront(world, pos, state, variant);
            network.bikeCheckedOut();
            status(player, "Checked out a bike. (" + network.available() + " available in the network)");
            return true;
        }

        // Dock is free.
        if (presenting != null) {
            dock.dock(presenting);
            if (fromRiding) {
                EntityBike bike = (EntityBike) player.getRidingEntity();
                bike.removePassengers();
                bike.setDead();
            } else if (!player.capabilities.isCreativeMode) {
                held.shrink(1);
            }
            network.bikeReturned();
            status(player, "Returned your bike to the dock. ("
                + network.available() + " available in the network)");
            return true;
        }

        status(player, "This dock is empty. Ride or carry a bike here to return it.");
        return true;
    }

    /** Spawn a checked-out bike in the block in front of the dock, pointing out along its facing. */
    private static void spawnInFront(World world, BlockPos pos, IBlockState state, BikeVariant variant) {
        EnumFacing f = state.getValue(FACING);
        BlockPos front = pos.offset(f);
        EntityBike bike = new EntityBike(world, variant);
        bike.setPositionAndRotation(
            front.getX() + 0.5D, pos.getY(), front.getZ() + 0.5D, f.getHorizontalAngle(), 0.0F);
        world.spawnEntity(bike);
    }

    /** Breaking a dock drops its bike (if any) so it is never lost, and updates the network. */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntityBikeDock dock = dockTE(world, pos);
            BikeShareNetwork network = BikeShareNetwork.get(world);
            if (dock != null && dock.isOccupied()) {
                BikeVariant variant = dock.undock();
                ItemStack stack = new ItemStack(LdibItems.forVariant(variant));
                net.minecraft.inventory.InventoryHelper.spawnItemStack(world,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
                network.bikeCheckedOut();
            }
            network.unregisterDock();
        }
        super.breakBlock(world, pos, state);
    }

    private static void status(EntityPlayer player, String message) {
        player.sendStatusMessage(new TextComponentString(message), true);
    }
}
