package com.micatechnologies.minecraft.ldib.integration;

import com.micatechnologies.minecraft.ldib.Ldib;
import com.micatechnologies.minecraft.ldib.LdibConfig;
import java.util.Locale;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * The bike-share's item currency — what rides are paid in when SUM is not in use. Emeralds by default;
 * any item by registry name through {@code bikeshare.currencyItem}. Fares are taken from, and
 * rebalancing credit given to, the rider's own inventory. Server side only.
 *
 * <p>Only the item is matched, not its damage or NBT, so a renamed emerald is still an emerald.</p>
 */
public final class ItemCurrency {

    private static String resolvedFor;
    private static Item resolved;

    private ItemCurrency() {
        throw new AssertionError("No instances.");
    }

    /** The configured currency item; emeralds if the configured name is not an item. */
    public static Item item() {
        String name = LdibConfig.shareCurrencyItem;
        if (!name.equals(resolvedFor)) {
            Item item = Item.getByNameOrId(name);
            if (item == null || item == Items.AIR) {
                Ldib.LOGGER.warn("bikeshare.currencyItem '{}' is not an item; bike-share rides are paid in emeralds.",
                    name);
                item = Items.EMERALD;
            }
            resolved = item;
            resolvedFor = name;
        }
        return resolved;
    }

    /** The currency's name for prices and messages, lower case and singular: "emerald". */
    @SuppressWarnings("deprecation") // I18n on the server side is what vanilla's own commands use
    public static String singularName() {
        return new ItemStack(item()).getDisplayName().toLowerCase(Locale.ROOT);
    }

    /** How many of the currency item {@code player} carries. */
    public static int count(EntityPlayer player) {
        Item item = item();
        int total = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Take up to {@code amount} of the currency item from {@code player}; return how many were taken. */
    public static int take(EntityPlayer player, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int taken = player.inventory.clearMatchingItems(item(), -1, amount, null);
        player.inventoryContainer.detectAndSendChanges();
        return taken;
    }

    /** Give {@code amount} of the currency item to {@code player}, dropping what does not fit. */
    public static int give(EntityPlayer player, int amount) {
        Item item = item();
        int left = amount;
        while (left > 0) {
            int stackSize = Math.min(left, item.getItemStackLimit());
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(item, stackSize));
            left -= stackSize;
        }
        return amount;
    }
}
