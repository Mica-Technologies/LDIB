package com.micatechnologies.minecraft.ldib;

import com.micatechnologies.minecraft.ldib.client.hud.RideHud;
import com.micatechnologies.minecraft.ldib.client.render.RenderBike;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import com.micatechnologies.minecraft.ldib.item.LdibItems;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Client-side proxy. Entity renderers, item-model binding and (later) the ride camera all get
 * installed from here.
 *
 * <p>This class and everything it reaches may reference {@code net.minecraft.client}. Nothing
 * outside {@code LdibClientProxy}'s reachable graph may — a single stray client import in common
 * code fails the CI dedicated-server smoke test, which is exactly what that test exists to catch.</p>
 */
public class LdibClientProxy extends LdibCommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        RenderingRegistry.registerEntityRenderingHandler(EntityBike.class, RenderBike::new);
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
            com.micatechnologies.minecraft.ldib.block.TileEntityBikeRack.class,
            new com.micatechnologies.minecraft.ldib.client.render.TileEntityBikeRackRenderer());
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
            com.micatechnologies.minecraft.ldib.block.TileEntityBikeDock.class,
            new com.micatechnologies.minecraft.ldib.client.render.TileEntityBikeDockRenderer());
        MinecraftForge.EVENT_BUS.register(new RideHud());
        // ModelRegistryEvent is on the MOD bus; register this proxy to the Forge bus is not enough,
        // so subscribe it explicitly to receive the model-registration event.
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Binds item models. Must run on {@code ModelRegistryEvent}: models bake before {@code init},
     * so registering a variant any later leaves the item rendering as the missing-model cube.
     */
    @SubscribeEvent
    public void registerModels(ModelRegistryEvent event) {
        bindModel(LdibItems.bike);
        bindModel(LdibItems.ebike);
        bindModel(LdibItems.scooter);
        bindModel(LdibItems.scooterFast);
        for (com.micatechnologies.minecraft.ldib.block.BlockBikeRack rack
                : com.micatechnologies.minecraft.ldib.block.LdibBlocks.racks()) {
            bindModel(Item.getItemFromBlock(rack));
        }
        bindModel(Item.getItemFromBlock(com.micatechnologies.minecraft.ldib.block.LdibBlocks.dock));
    }

    private static void bindModel(Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0,
            new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
