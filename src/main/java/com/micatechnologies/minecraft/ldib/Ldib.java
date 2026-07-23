package com.micatechnologies.minecraft.ldib;

import com.micatechnologies.minecraft.ldib.block.LdibBlocks;
import com.micatechnologies.minecraft.ldib.block.TileEntityBikeDock;
import com.micatechnologies.minecraft.ldib.block.TileEntityBikeRack;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import com.micatechnologies.minecraft.ldib.item.LdibItems;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main mod class for LDIB (Limitless Development: Immersive Biking).
 *
 * <p>LDIB adds rider-controlled bikes to Minecraft 1.12.2 — you mount one, and WASD pedals, brakes
 * and steers it, using the vanilla riding mechanic rather than a retextured pig or minecart. The
 * MVP is a single blocky bicycle; e-bike and scooter (Bird/Lime-style) variants and their docks
 * come later. See docs/AGENT-PLANS/MASTER_PLAN.md for the phased roadmap.</p>
 *
 * <p>Lifecycle contract for the rest of the mod:</p>
 * <ul>
 *   <li>{@code preInit} — config, network channel, event-bus registrations, item/entity setup.
 *       Nothing here may touch a {@code World}.</li>
 *   <li>{@code init} — GUI/inter-mod wiring.</li>
 *   <li>{@code postInit} — anything that must observe other mods' completed registries.</li>
 * </ul>
 *
 * <p><b>Side discipline.</b> Everything reachable from this class must be loadable on a dedicated
 * server. Client-only code (the bike renderer/model, and later the rider camera) is reached
 * exclusively through {@link LdibClientProxy}, never directly — a single stray import of a
 * {@code net.minecraft.client} type in common code fails the CI server smoke test.</p>
 */
@Mod(modid = LdibConstants.MOD_NAMESPACE,
     version = LdibConstants.MOD_VERSION,
     name = LdibConstants.MOD_NAME,
     acceptedMinecraftVersions = "[1.12.2]",
     // SUM is an OPTIONAL economy integration for paid bike-share — load-order only, never required.
     dependencies = "after:sum")
public class Ldib {

    public static final Logger LOGGER = LogManager.getLogger(LdibConstants.MOD_NAMESPACE);

    @SidedProxy(clientSide = "com.micatechnologies.minecraft.ldib.LdibClientProxy",
                serverSide = "com.micatechnologies.minecraft.ldib.LdibCommonProxy")
    public static LdibProxy proxy;

    @Mod.Instance(LdibConstants.MOD_NAMESPACE)
    public static Ldib instance;

    /** Entity network ids, allocated in registration order. Must be stable across versions. */
    private static int entityId = 0;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LdibConfig.init(event.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(this);
        com.micatechnologies.minecraft.ldib.network.LdibNetwork.init();
        LdibBlocks.init();
        LdibItems.init();
        GameRegistry.registerTileEntity(TileEntityBikeRack.class,
            new ResourceLocation(LdibConstants.MOD_NAMESPACE, "bike_rack"));
        GameRegistry.registerTileEntity(TileEntityBikeDock.class,
            new ResourceLocation(LdibConstants.MOD_NAMESPACE, "bike_dock"));
        LdibTab.initTabElements();
        proxy.preInit(event);
        LOGGER.info("I am {} at version {}", LdibConstants.MOD_NAME, LdibConstants.MOD_VERSION);
    }

    @SubscribeEvent
    public void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().register(
            EntityEntryBuilder.create()
                .entity(EntityBike.class)
                .id(new ResourceLocation(LdibConstants.MOD_NAMESPACE, "bike"), entityId++)
                .name("bike")
                // Boat-class tracking: seen from a modest distance, updated every 3 ticks with
                // velocity so non-riding clients interpolate the bike smoothly. The controlling
                // client is authoritative over its own position via the vanilla vehicle-move packet.
                .tracker(80, 3, true)
                .build());
    }

    @SubscribeEvent
    public void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(LdibRegistry.getBlocks().toArray(new Block[0]));
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(LdibRegistry.getItems().toArray(new Item[0]));
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}
