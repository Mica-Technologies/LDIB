package com.micatechnologies.minecraft.ldib.entity;

import com.micatechnologies.minecraft.ldib.block.BlockBikeDock;
import com.micatechnologies.minecraft.ldib.block.BlockBikeRack;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Where to put a rideable down when a dock lets one go.
 *
 * <p>The naive answer — the block in front of the dock, pointing out along its facing — is what
 * shipped first, and it looks wrong for a reason that is easy to miss: a bike is <b>longer than the
 * block it stands in</b>. Its wheels sit ±0.875 blocks from its centre, so a bike centred one block
 * out from a dock has its rear wheel buried a third of a block inside the dock post it just left. Put
 * a row of docks side by side, as every real share station does, and the released bikes clip their
 * own dock, each other, and whatever wall the row is backed against.</p>
 *
 * <p>So placement is a search rather than an offset, and it measures the bike people <i>see</i>
 * ({@link #LENGTH} × {@link #WIDTH} × {@link #HEIGHT}) rather than the 0.8-wide collision box the
 * physics uses — a spot that merely avoids trapping the entity is not the same as a spot the bike
 * looks parked in. Candidates are tried nearest-and-straightest first, and the search has three ways
 * to say no that the caller must respect (see {@link #findReleaseSpot}).</p>
 *
 * <p>This is the dispensing counterpart to {@code EntityBike.rescueStuckDismount}, which does the
 * same job for a rider stepping off. Common code — a dedicated server runs it.</p>
 */
public final class RideablePlacement {

    /**
     * Front-to-back extent of a rideable <b>as drawn</b>, in blocks. The models put wheel centres at
     * ±7 px with a 7 px radius (see {@code ModelBike}), i.e. ±0.875 blocks, plus a little margin.
     * Deliberately larger than {@code EntityBike}'s 0.8-wide collision box: this search is about the
     * bike not <i>looking</i> like it is inside something.
     */
    public static final double LENGTH = 1.8D;

    /** Side-to-side extent of a rideable as drawn, in blocks — handlebar width plus margin. */
    public static final double WIDTH = 0.9D;

    /** Height of a rideable as drawn, in blocks: bars and saddle stand above the 1.0 entity box. */
    public static final double HEIGHT = 1.2D;

    /**
     * Where to try, as {@code {blocks forward, blocks right, quarter-turns clockwise}} in the dock's
     * own frame. Forward starts at <b>1.5</b>, not 1: half a block clears the dock's own post and
     * {@link #LENGTH}/2 clears the bike's rear wheel, and anything less puts them inside each other no
     * matter how empty the world is.
     *
     * <p>Order is what a station attendant would do — roll it straight out, then further out, then
     * into the next lane along, and only once the whole lane is unusable turn it sideways to stand it
     * beside the dock.</p>
     */
    private static final double[][] CANDIDATES = {
        {1.5D, 0.0D, 0.0D},
        {2.0D, 0.0D, 0.0D},
        {1.5D, 1.0D, 0.0D}, {1.5D, -1.0D, 0.0D},
        {2.5D, 0.0D, 0.0D},
        {2.0D, 1.0D, 0.0D}, {2.0D, -1.0D, 0.0D},
        {1.5D, 2.0D, 0.0D}, {1.5D, -2.0D, 0.0D},
        {1.0D, 1.5D, 1.0D}, {1.0D, -1.5D, 3.0D},
        {0.0D, 1.5D, 1.0D}, {0.0D, -1.5D, 3.0D},
    };

    /** Vertical offsets tried, in order: the dock's own level first, then a step down, up, or a drop. */
    private static final int[] HEIGHTS = {0, -1, 1, -2};

    private RideablePlacement() {
        throw new AssertionError("No instances.");
    }

    /** A spot a rideable fits in: where to put it and which way to point it. */
    public static final class Spot {
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;

        private Spot(double x, double y, double z, float yaw) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }
    }

    /**
     * The first clear spot around {@code dock} to release a rideable into, or {@code null} if the dock
     * is walled in tightly enough that there is nowhere to put one.
     *
     * <p><b>Callers must check for {@code null} before taking the bike out of the dock.</b> Undocking
     * first and discovering afterwards that there is nowhere to put the result is how a bike leaves
     * the fleet without arriving anywhere — better to refuse the check-out and tell the player to
     * clear some space.</p>
     *
     * <p>Spots with something solid underfoot are preferred, but a dock built on the lip of a drop
     * still dispenses (second pass) rather than refusing forever — the bike has gravity and will find
     * the floor, which is more than can be said for a bike that was never spawned.</p>
     *
     * @param world  the world to test against
     * @param dock   the dock block's position
     * @param facing the dock's facing, i.e. the direction a bike is rolled out
     */
    public static Spot findReleaseSpot(World world, BlockPos dock, EnumFacing facing) {
        Spot footed = search(world, dock, facing, true);
        return footed != null ? footed : search(world, dock, facing, false);
    }

    private static Spot search(World world, BlockPos dock, EnumFacing facing, boolean requireFooting) {
        EnumFacing right = facing.rotateY();
        for (int dy : HEIGHTS) {
            double y = dock.getY() + dy;
            for (double[] candidate : CANDIDATES) {
                double x = dock.getX() + 0.5D
                    + facing.getXOffset() * candidate[0] + right.getXOffset() * candidate[1];
                double z = dock.getZ() + 0.5D
                    + facing.getZOffset() * candidate[0] + right.getZOffset() * candidate[1];
                float yaw = quarterTurn(facing, (int) candidate[2]).getHorizontalAngle();
                if (requireFooting && !hasFooting(world, x, y, z)) {
                    continue;
                }
                if (isClear(world, footprint(x, y, z, yaw))) {
                    return new Spot(x, y, z, yaw);
                }
            }
        }
        return null;
    }

    /** {@code facing} turned {@code turns} quarter-turns clockwise. */
    private static EnumFacing quarterTurn(EnumFacing facing, int turns) {
        EnumFacing result = facing;
        for (int i = 0; i < turns; i++) {
            result = result.rotateY();
        }
        return result;
    }

    /**
     * The box a rideable of this yaw occupies, centred on {@code (x, z)} and standing on {@code y}.
     *
     * <p>Axis-aligned around the rotated rectangle: at the cardinal yaws a dock ever produces this is
     * exact (the length lands wholly on one axis), and at any other angle it is a slight over-estimate,
     * which errs the safe way.</p>
     */
    private static AxisAlignedBB footprint(double x, double y, double z, float yaw) {
        double yawRad = Math.toRadians(yaw);
        double sin = Math.abs(Math.sin(yawRad));
        double cos = Math.abs(Math.cos(yawRad));
        // Minecraft forward for a yaw is (-sin, cos), so the length runs along z at yaw 0.
        double halfX = (LENGTH * sin + WIDTH * cos) / 2.0D;
        double halfZ = (LENGTH * cos + WIDTH * sin) / 2.0D;
        return new AxisAlignedBB(x - halfX, y, z - halfZ, x + halfX, y + HEIGHT, z + halfZ);
    }

    /** Whether {@code box} is free of world geometry, of other rideables, and of racks and docks. */
    private static boolean isClear(World world, AxisAlignedBB box) {
        if (world.collidesWithAnyBlock(box)) {
            return false;
        }
        for (EntityBike other : world.getEntitiesWithinAABB(EntityBike.class, box)) {
            if (!other.isDead) {
                return false;
            }
        }
        return !overlapsStation(world, box);
    }

    /**
     * Whether {@code box} reaches into any rack or dock block.
     *
     * <p>A separate test from the block-collision one because neither block is as tall as what it
     * holds: a rack's collision box stops at 0.6 so you can step over it, and both draw a bike
     * standing well above and around themselves. Collision alone would happily release a bike through
     * a neighbouring dock's parked one, which is the exact overlap this whole class exists to stop, so
     * for placement purposes a rack or dock counts as a full block.</p>
     */
    private static boolean overlapsStation(World world, AxisAlignedBB box) {
        BlockPos min = new BlockPos(Math.floor(box.minX), Math.floor(box.minY), Math.floor(box.minZ));
        BlockPos max = new BlockPos(Math.ceil(box.maxX) - 1, Math.ceil(box.maxY) - 1,
            Math.ceil(box.maxZ) - 1);
        for (BlockPos pos : BlockPos.getAllInBoxMutable(min, max)) {
            Block block = world.getBlockState(pos).getBlock();
            if (block instanceof BlockBikeDock || block instanceof BlockBikeRack) {
                return true;
            }
        }
        return false;
    }

    /** Whether there is a solid top face (or liquid) directly under a rideable standing here. */
    private static boolean hasFooting(World world, double x, double y, double z) {
        BlockPos below = new BlockPos(x, y - 0.5D, z);
        IBlockState state = world.getBlockState(below);
        return state.isSideSolid(world, below, EnumFacing.UP) || state.getMaterial().isLiquid();
    }
}
