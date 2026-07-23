package com.micatechnologies.minecraft.ldib.item;

import com.micatechnologies.minecraft.ldib.LdibConstants;
import com.micatechnologies.minecraft.ldib.LdibTab;
import com.micatechnologies.minecraft.ldib.entity.BikeVariant;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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

    @Override
    public void addInformation(ItemStack stack, World world, java.util.List<String> tooltip,
                               net.minecraft.client.util.ITooltipFlag flag) {
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
