package com.micatechnologies.minecraft.ldib;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Sided-proxy contract. The only sanctioned bridge from common code into client-only classes —
 * see the side-discipline note on {@link Ldib}.
 */
public interface LdibProxy {

    void preInit(FMLPreInitializationEvent event);

    void init(FMLInitializationEvent event);

    void postInit(FMLPostInitializationEvent event);

    /**
     * Open the kiosk screen for the station at {@code kiosk}. Called from the client-bound network
     * packet handler; a no-op on the server so the handler stays side-safe.
     */
    void openKioskGui(BlockPos kiosk, boolean hasSession, long startTick,
                      com.micatechnologies.minecraft.ldib.api.ShareTariff tariff);

    /**
     * Apply movement config values pushed from the server on join (client only; a no-op on the
     * server). The client snapshots its own values first and restores them on disconnect.
     */
    void applySyncedConfig(double[] values);
}
