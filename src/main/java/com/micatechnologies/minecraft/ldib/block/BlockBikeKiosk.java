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
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A bike-share station kiosk — the check-out point. Right-clicking it opens a small screen (via
 * {@link PacketOpenKiosk}) from which a player checks out a rental; once checked out they may take a
 * bike from any {@link BlockBikeDock} at this station (the docks within
 * {@link BikeShareStation#radius()}). The kiosk itself is stateless: a station is defined by proximity
 * (see {@link BikeShareStation}), so there is no tile entity to coordinate.
 */
public class BlockBikeKiosk extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    private static final AxisAlignedBB KIOSK_AABB = new AxisAlignedBB(0.25D, 0.0D, 0.25D, 0.75D, 1.0D, 0.75D);

    public BlockBikeKiosk() {
        super(Material.IRON);
        setHardness(2.0F);
        setResistance(10.0F);
        setSoundType(SoundType.METAL);
        setRegistryName(LdibConstants.MOD_NAMESPACE, "bike_kiosk");
        setTranslationKey(LdibConstants.MOD_NAMESPACE + ".bike_kiosk");
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
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
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

    // --- Interaction: open the kiosk screen --------------------------------------------------

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY,
                                    float hitZ) {
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            BikeShareNetwork.Session session = BikeShareNetwork.get(world).getSession(player.getUniqueID());
            long startTick = session != null ? session.startTick : 0L;
            LdibNetwork.CHANNEL.sendTo(
                new PacketOpenKiosk(pos, session != null, startTick), (EntityPlayerMP) player);
        }
        return true;
    }
}
