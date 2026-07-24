package com.micatechnologies.minecraft.ldib.block;

import com.micatechnologies.minecraft.ldib.LdibConstants;
import com.micatechnologies.minecraft.ldib.LdibTab;
import com.micatechnologies.minecraft.ldib.network.LdibNetwork;
import com.micatechnologies.minecraft.ldib.network.PacketOpenKiosk;
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
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A bike-share station kiosk — the rental check-out point. A tall, chunky solar-topped pillar modelled
 * on a real bike-share kiosk: it stands <b>two blocks tall</b> (a lower body with the screen and an
 * upper body capped by a solar panel). Placing the item lands the lower block and auto-adds the upper
 * one; breaking either part removes the whole kiosk. Right-clicking either part opens the station
 * screen ({@link PacketOpenKiosk}); a station is the docks within {@link BikeShareStation#radius()} of
 * the kiosk (proximity, no tile entity to coordinate).
 */
public class BlockBikeKiosk extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    /** 0 = lower body (the master, handles interaction); 1 = upper body with the solar cap. */
    public static final PropertyInteger PART = PropertyInteger.create("part", 0, 1);

    private static final AxisAlignedBB KIOSK_AABB = new AxisAlignedBB(0.25D, 0.0D, 0.25D, 0.75D, 1.0D, 0.75D);

    /** Re-entrancy guard so breaking one part (which air-sets the other) doesn't recurse forever. */
    private static final ThreadLocal<Boolean> BREAKING = ThreadLocal.withInitial(() -> false);

    public BlockBikeKiosk() {
        super(Material.IRON);
        setHardness(2.0F);
        setResistance(10.0F);
        setSoundType(SoundType.METAL);
        setRegistryName(LdibConstants.MOD_NAMESPACE, "bike_kiosk");
        setTranslationKey(LdibConstants.MOD_NAMESPACE + ".bike_kiosk");
        setCreativeTab(LdibTab.LDIB_TAB);
        setDefaultState(this.blockState.getBaseState()
            .withProperty(FACING, EnumFacing.NORTH).withProperty(PART, 0));
    }

    // --- Block state / metadata --------------------------------------------------------------

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, PART);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState()
            .withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3))
            .withProperty(PART, (meta >> 2) & 1);
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
            .withProperty(FACING, placer.getHorizontalFacing().getOpposite()).withProperty(PART, 0);
    }

    /** After the lower block lands, stack the upper (solar) block on top if there's room. */
    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
                                ItemStack stack) {
        if (world.isRemote) {
            return;
        }
        BlockPos above = pos.up();
        if (world.isAirBlock(above) || world.getBlockState(above).getBlock().isReplaceable(world, above)) {
            world.setBlockState(above,
                getDefaultState().withProperty(FACING, state.getValue(FACING)).withProperty(PART, 1), 3);
        }
    }

    /** The lower (master) block position for either part of this kiosk. */
    private static BlockPos masterPos(IBlockState state, BlockPos pos) {
        return state.getValue(PART) == 1 ? pos.down() : pos;
    }

    // --- Shape -------------------------------------------------------------------------------

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, net.minecraft.world.IBlockAccess source,
                                        BlockPos pos) {
        return KIOSK_AABB;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    // --- Interaction: open the kiosk screen (anchored on the master) -------------------------

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY,
                                    float hitZ) {
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            BlockPos master = masterPos(state, pos);
            BikeShareNetwork.Session session = BikeShareNetwork.get(world).getSession(player.getUniqueID());
            long startTick = session != null ? session.startTick : 0L;
            com.micatechnologies.minecraft.ldib.api.ShareTariff tariff =
                com.micatechnologies.minecraft.ldib.api.BikeShareBilling.activeTariff();
            LdibNetwork.CHANNEL.sendTo(
                new PacketOpenKiosk(master, session != null, startTick, tariff), (EntityPlayerMP) player);
        }
        return true;
    }

    /** Breaking either part removes the whole kiosk (guarded so it happens once). */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!BREAKING.get() && !world.isRemote) {
            BREAKING.set(true);
            try {
                BlockPos master = masterPos(state, pos);
                BlockPos upper = master.up();
                if (!master.equals(pos) && world.getBlockState(master).getBlock() == this) {
                    world.setBlockToAir(master);
                }
                if (!upper.equals(pos) && world.getBlockState(upper).getBlock() == this) {
                    world.setBlockToAir(upper);
                }
            } finally {
                BREAKING.set(false);
            }
        }
        super.breakBlock(world, pos, state);
    }
}
