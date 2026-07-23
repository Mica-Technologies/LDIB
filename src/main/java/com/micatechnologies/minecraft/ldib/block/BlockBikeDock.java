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

        // Is the player presenting a bike at all (used only for the "occupied" message below)?
        ItemStack held = player.getHeldItem(hand);
        BikeVariant presenting = null;
        if (held.getItem() instanceof ItemBike) {
            presenting = ((ItemBike) held.getItem()).variant();
        } else if (player.getRidingEntity() instanceof EntityBike) {
            presenting = ((EntityBike) player.getRidingEntity()).variant();
        }

        if (dock.isOccupied()) {
            if (presenting != null) {
                status(player, "This dock is occupied. Return your bike at a free dock.");
                return true;
            }
            return checkOutFromDock(world, pos, state, player, dock, network);
        }

        // Dock is free.
        // Op setup: sneak-right-click a free dock with a bike item to stock the fleet with a NEW
        // public bike-share bike of that variant.
        if (player.isSneaking() && held.getItem() instanceof ItemBike && player.canUseCommand(2, "")) {
            dock.dock(((ItemBike) held.getItem()).variant());
            if (!player.capabilities.isCreativeMode) {
                held.shrink(1);
            }
            network.bikeReturned();
            status(player, "Added a bike-share bike to the network.");
            return true;
        }
        // Return a ridden share bike (docks are for the public fleet only).
        if (player.getRidingEntity() instanceof EntityBike) {
            EntityBike bike = (EntityBike) player.getRidingEntity();
            if (!bike.isShare()) {
                status(player, "Docks are for bike-share bikes — lock a personal bike to a rack instead.");
                return true;
            }
            dock.dock(bike.variant());
            bike.removePassengers();
            bike.setDead();
            network.bikeReturned();
            completeSessionOnReturn(world, player, network);
            return true;
        }
        if (held.getItem() instanceof ItemBike) {
            status(player, "Docks only take bike-share bikes. (Op: sneak-right-click to stock this dock.)");
            return true;
        }
        status(player, "This dock is empty. Ride a bike-share bike here to return it.");
        return true;
    }

    /**
     * Take a bike out of an occupied dock. At a station (a kiosk is nearby) this requires an active
     * rental checked out at that station, one bike per session; a standalone dock (no kiosk) still
     * allows a quick self-serve check-out.
     */
    private boolean checkOutFromDock(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                     TileEntityBikeDock dock, BikeShareNetwork network) {
        BikeShareNetwork.Session session = network.getSession(player.getUniqueID());
        if (session != null) {
            if (session.bikeTaken) {
                status(player, "You already have a bike out — return it before taking another.");
                return true;
            }
            if (!BikeShareStation.isKiosk(world, session.kiosk)
                || !BikeShareStation.withinStation(session.kiosk, pos)) {
                status(player, "Take your bike from a dock at the station where you checked out.");
                return true;
            }
            BikeVariant variant = dock.undock();
            spawnInFront(world, pos, state, variant);
            network.bikeCheckedOut();
            network.markBikeTaken(player.getUniqueID());
            status(player, "Enjoy your ride — return it at any station dock when you're done.");
            return true;
        }
        // No session: station docks send you to the kiosk; standalone docks self-serve.
        if (BikeShareStation.findKioskNear(world, pos) != null) {
            status(player, "Check out at the station kiosk first.");
            return true;
        }
        BikeVariant variant = dock.undock();
        spawnInFront(world, pos, state, variant);
        network.bikeCheckedOut();
        status(player, "Checked out a bike. (" + network.available() + " available in the network)");
        return true;
    }

    /** If the returning player had an open rental, close it, bill for the minutes, and chat them. */
    private static void completeSessionOnReturn(World world, EntityPlayer player, BikeShareNetwork network) {
        BikeShareNetwork.Session session = network.endSession(player.getUniqueID());
        if (session == null) {
            status(player, "Returned your bike to the dock. ("
                + network.available() + " available in the network)");
            return;
        }
        long elapsed = Math.max(0L, world.getTotalWorldTime() - session.startTick);
        int minutes = (int) Math.max(1L, (elapsed + 1199L) / 1200L); // ceil to whole minutes, min 1
        double charged = com.micatechnologies.minecraft.ldib.api.BikeShareBilling.active().charge(player, minutes);
        String message = "Your bike-share session is complete — " + minutes
            + (minutes == 1 ? " minute." : " minutes.");
        if (charged > 0.0D) {
            message += String.format(" You were charged %.2f.", charged);
        }
        player.sendMessage(new TextComponentString(message));
    }

    /**
     * Dock the bike {@code player} is riding into this dock, if it's free — the "ride up and park"
     * path called from {@link EntityBike} when the click landed on the bike rather than the dock.
     * Returns true if the bike was docked.
     */
    /** Clip a specific bike into this dock (ridden or already placed) on {@code player}'s behalf. */
    public boolean tryDockBike(World world, BlockPos pos, EntityBike bike, EntityPlayer player) {
        if (world.isRemote || bike == null || bike.isDead || !bike.isShare()) {
            return false; // docks only take public bike-share bikes; personal bikes go on racks
        }
        TileEntityBikeDock dock = dockTE(world, pos);
        if (dock == null || dock.isOccupied()) {
            return false;
        }
        dock.dock(bike.variant());
        if (bike.isBeingRidden()) {
            bike.removePassengers();
        }
        bike.setDead();
        BikeShareNetwork network = BikeShareNetwork.get(world);
        network.bikeReturned();
        completeSessionOnReturn(world, player, network);
        return true;
    }

    /** The bike {@code player} is riding, docked here — the "ride up and right-click" convenience. */
    public boolean tryDockRidden(World world, BlockPos pos, EntityPlayer player) {
        return player.getRidingEntity() instanceof EntityBike
            && tryDockBike(world, pos, (EntityBike) player.getRidingEntity(), player);
    }

    /** Spawn a checked-out bike in the block in front of the dock, pointing out along its facing. */
    private static void spawnInFront(World world, BlockPos pos, IBlockState state, BikeVariant variant) {
        EnumFacing f = state.getValue(FACING);
        BlockPos front = pos.offset(f);
        EntityBike bike = new EntityBike(world, variant, true); // dispensed bikes are public-fleet bikes
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
