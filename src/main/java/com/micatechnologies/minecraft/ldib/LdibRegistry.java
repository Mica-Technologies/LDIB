package com.micatechnologies.minecraft.ldib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

/**
 * Central collection point for blocks and items, populated by the feature packages and drained by
 * the {@code RegistryEvent.Register} handlers in {@link Ldib}.
 *
 * <p>Registry-event ordering in 1.12.2 is the reason this indirection exists: blocks must be
 * registered before their {@link net.minecraft.item.ItemBlock}s, and both must exist before models
 * are baked. Collecting into lists and registering in one place keeps that ordering in a single
 * readable spot. The MVP ships no blocks yet — the bike rack, charging dock and scooter dock in the
 * master plan will be the first entries in {@code BLOCKS}.</p>
 */
public final class LdibRegistry {

    private static final List<Block> BLOCKS = new ArrayList<>();
    private static final List<Item> ITEMS = new ArrayList<>();

    private LdibRegistry() {
        throw new AssertionError("No instances.");
    }

    public static <T extends Block> T addBlock(T block) {
        BLOCKS.add(block);
        return block;
    }

    public static <T extends Item> T addItem(T item) {
        ITEMS.add(item);
        return item;
    }

    public static List<Block> getBlocks() {
        return Collections.unmodifiableList(BLOCKS);
    }

    public static List<Item> getItems() {
        return Collections.unmodifiableList(ITEMS);
    }
}
