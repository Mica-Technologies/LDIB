package com.micatechnologies.minecraft.ldib.item;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.LdibConstants;
import com.micatechnologies.minecraft.ldib.LdibTab;
import com.micatechnologies.minecraft.ldib.entity.BikeVariant;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import com.micatechnologies.minecraft.ldib.physics.BatteryModel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The in-inventory bike. Right-clicking places an {@link EntityBike} on the block the player is
 * looking at, then consumes the item — the same "cast the vehicle out in front of you" gesture the
 * vanilla boat uses, and for the same reason: it reads naturally and avoids a placement GUI.
 *
 * <p>The raytrace/spawn logic here is deliberately close to {@code net.minecraft.item.ItemBoat} so
 * it inherits that item's well-worn edge-case handling (water/entity hits, liquid checks). When
 * variants land, this stays one class parameterised by an {@code EntityBike.Variant}; see
 * docs/AGENT-PLANS/MASTER_PLAN.md, "Variants are data".</p>
 */
public class ItemBike extends Item {

    private final BikeVariant variant;

    public ItemBike(BikeVariant variant) {
        this.variant = variant;
        setMaxStackSize(1);
        setTranslationKey(LdibConstants.MOD_NAMESPACE + "." + variant.key());
        setRegistryName(LdibConstants.MOD_NAMESPACE, variant.key());
        setCreativeTab(LdibTab.LDIB_TAB);
    }

    /** The bike variant this item places — read by {@code BlockBikeRack} when locking a bike. */
    public BikeVariant variant() {
        return variant;
    }

    // --- Battery on the stack ----------------------------------------------------------------
    //
    // A pocketed bike keeps its charge, so the charge has to live on the ItemStack between being
    // picked up and put back down. ABSENCE OF THE TAG MEANS FULL, which is what makes every other
    // path correct for free: a crafted bike, a creative-tab bike, one unlocked from a rack (racks
    // charge — you plug your e-bike in at home) and one dispensed from a share dock all arrive with
    // no tag and therefore a full battery, with no code of their own.

    /** NBT key holding a stack's battery charge, {@code 0}–{@code 1}. */
    private static final String CHARGE_TAG = "Charge";

    /** The charge on this stack, or a full one if it carries no battery tag. */
    public static double chargeOf(ItemStack stack) {
        net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(CHARGE_TAG)) {
            return BatteryModel.FULL;
        }
        return BatteryModel.clamp(tag.getDouble(CHARGE_TAG));
    }

    /** Stamp a charge onto this stack. A full charge writes no tag, keeping fresh bikes stackable-clean. */
    public static void setCharge(ItemStack stack, double charge) {
        double clamped = BatteryModel.clamp(charge);
        if (clamped >= BatteryModel.FULL) {
            if (stack.hasTagCompound()) {
                stack.getTagCompound().removeTag(CHARGE_TAG);
            }
            return;
        }
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        }
        stack.getTagCompound().setDouble(CHARGE_TAG, clamped);
    }

    /**
     * Show the charge as the vanilla durability bar. It is the one at-a-glance readout the inventory
     * already has, so a part-charged e-bike reads correctly without any new HUD or model work; a
     * variant with no battery never draws one.
     */
    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return variant.hasBattery() && chargeOf(stack) < BatteryModel.FULL;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0D - chargeOf(stack);
    }

    /**
     * Right-clicking a bike-share dock with this item <b>stocks</b> the dock (adds a share bike to the
     * fleet) instead of placing a bike on top of it. This must live here, not in the dock's
     * {@code onBlockActivated}: sneaking with an item makes vanilla skip block activation and go
     * straight to the item's use, and sneaking is exactly the setup gesture. Returning SUCCESS also
     * suppresses {@link #onItemRightClick}, so no loose bike is ever spawned on the dock.
     */
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
        if (block instanceof com.micatechnologies.minecraft.ldib.block.BlockBikeDock) {
            if (!world.isRemote) {
                ((com.micatechnologies.minecraft.ldib.block.BlockBikeDock) block)
                    .stockFromItem(world, pos, player, hand, variant);
            }
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }

    /**
     * The one place a client-only type ({@code ITooltipFlag}) appears in a signature on a class the
     * dedicated server loads. That is safe — JVM verification is lazy, and vanilla's own
     * {@code Item.addInformation} is {@code @SideOnly(Side.CLIENT)} so this overrides nothing on a
     * server and is never called there — but it is safe by accident rather than by declaration. The
     * annotation makes it deliberate: Forge's side-stripping removes the method outright on a server,
     * so the unresolvable parameter type can never be reached at all.
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, World world, java.util.List<String> tooltip,
                               net.minecraft.client.util.ITooltipFlag flag) {
        if (variant.hasBattery()) {
            double charge = chargeOf(stack);
            // Amber below the reserve, red when flat — the same warning the handling is about to give.
            String colour = charge <= 0.0D ? "§c" : (charge < LdibConfig.batteryReserveFraction ? "§6" : "§a");
            tooltip.add("§7Battery: " + colour + Math.round(charge * 100.0D) + "%");
            tooltip.add("§8Lock it to a rack to charge it.");
        }
        tooltip.add("§7Right-click the ground to place and ride.");
        tooltip.add("§7Sneak-right-click or hit it to pick it back up.");
        tooltip.add("§7Ride up to a rack or dock and right-click to park it.");
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        // Ray from the player's eyes, up to 5 blocks, ignoring only non-collidable blocks.
        float pitch = player.rotationPitch;
        float yaw = player.rotationYaw;
        Vec3d eyes = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        float cosYaw = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float sinYaw = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cosPitch = -MathHelper.cos(-pitch * 0.017453292F);
        float sinPitch = MathHelper.sin(-pitch * 0.017453292F);
        double reach = 5.0D;
        Vec3d end = eyes.add(sinYaw * cosPitch * reach, sinPitch * reach, cosYaw * cosPitch * reach);
        RayTraceResult ray = world.rayTraceBlocks(eyes, end, true);

        if (ray == null || ray.typeOfHit != RayTraceResult.Type.BLOCK) {
            return new ActionResult<>(EnumActionResult.PASS, stack);
        }

        if (!world.isRemote) {
            EntityBike bike = new EntityBike(world, variant);
            bike.setCharge(chargeOf(stack));
            bike.setPositionAndRotation(
                ray.hitVec.x, ray.hitVec.y, ray.hitVec.z, player.rotationYaw, 0.0F);
            if (!world.getCollisionBoxes(bike, bike.getEntityBoundingBox().grow(-0.1D)).isEmpty()) {
                return new ActionResult<>(EnumActionResult.FAIL, stack);
            }
            world.spawnEntity(bike);
        }

        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        player.addStat(net.minecraft.stats.StatList.getObjectUseStats(this));
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
}
