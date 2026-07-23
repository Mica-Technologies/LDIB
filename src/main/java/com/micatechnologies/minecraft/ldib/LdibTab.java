package com.micatechnologies.minecraft.ldib;

import com.micatechnologies.minecraft.ldib.item.LdibItems;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * Creative inventory tab for all LDIB content.
 */
public final class LdibTab {

    public static final CreativeTabs LDIB_TAB = new CreativeTabs(LdibConstants.MOD_NAMESPACE) {
        @Override
        public ItemStack createIcon() {
            return ICON;
        }
    };

    /**
     * Tab icon. Deliberately a mutable static rather than an inline {@code new ItemStack(...)} in
     * {@code createIcon()}: {@link CreativeTabs} is constructed during class-load, long before item
     * registration, so referencing an LDIB item directly there yields an air stack.
     * {@link #initTabElements()} swaps in the real icon once registration is done.
     */
    private static ItemStack ICON = new ItemStack(Items.LEAD);

    private LdibTab() {
        throw new AssertionError("No instances.");
    }

    /**
     * Called from {@code preInit} after {@link LdibItems} has populated the registry. Points
     * {@link #ICON} at the bike item so the creative tab shows a bike rather than a placeholder.
     */
    public static void initTabElements() {
        if (LdibItems.bike != null) {
            ICON = new ItemStack(LdibItems.bike);
        }
    }
}
