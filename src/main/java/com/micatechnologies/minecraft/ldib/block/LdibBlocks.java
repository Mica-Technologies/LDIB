package com.micatechnologies.minecraft.ldib.block;

import com.micatechnologies.minecraft.ldib.LdibRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.item.ItemBlock;

/**
 * Every block LDIB adds, created once and handed to {@link LdibRegistry}. Currently the bike racks —
 * one {@link BlockBikeRack} per {@link RackStyle}, each with its matching {@link ItemBlock}.
 *
 * <p>Instantiated from {@code preInit}; the {@code RegistryEvent.Register} handlers in the main mod
 * class drain {@link LdibRegistry} afterwards (blocks first, then items — which include these
 * blocks' item forms). The bike-share dock blocks will join this list in Phase 7.1.</p>
 */
public final class LdibBlocks {

    /** One rack block per style, in {@link RackStyle} order. */
    public static final List<BlockBikeRack> racks = new ArrayList<>();

    private LdibBlocks() {
        throw new AssertionError("No instances.");
    }

    public static void init() {
        for (RackStyle style : RackStyle.values()) {
            BlockBikeRack block = LdibRegistry.addBlock(new BlockBikeRack(style));
            racks.add(block);
            ItemBlock item = new ItemBlock(block);
            item.setRegistryName(block.getRegistryName());
            LdibRegistry.addItem(item);
        }
    }

    public static List<BlockBikeRack> racks() {
        return Collections.unmodifiableList(racks);
    }
}
