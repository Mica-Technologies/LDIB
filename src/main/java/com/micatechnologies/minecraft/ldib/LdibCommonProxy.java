package com.micatechnologies.minecraft.ldib;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Server-side (and shared) proxy. {@link LdibClientProxy} extends this, so anything put here
 * runs on both sides. Nothing here may reference a {@code net.minecraft.client} type.
 */
public class LdibCommonProxy implements LdibProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
    }

    @Override
    public void init(FMLInitializationEvent event) {
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
    }

    @Override
    public void openKioskGui(BlockPos kiosk, boolean hasSession, long startTick,
                             com.micatechnologies.minecraft.ldib.api.ShareTariff tariff) {
        // No GUI on a dedicated server.
    }

    @Override
    public void applySyncedConfig(double[] values) {
        // The server owns the authoritative config; nothing to apply on the server side.
    }
}
