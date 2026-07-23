package com.micatechnologies.minecraft.ldib.client;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Client side of the config sync (see {@code PacketSyncConfig}). When a server pushes its movement
 * config on join, {@link #apply} first snapshots the client's own values, then overwrites the live
 * {@link LdibConfig} fields with the server's — so the client predicts with the server's tuning. On
 * disconnect it restores the snapshot, so singleplayer and the next server start from the player's own
 * config again. Client-only; installed from {@link com.micatechnologies.minecraft.ldib.LdibClientProxy}.
 */
public class ClientConfigSync {

    public static final ClientConfigSync INSTANCE = new ClientConfigSync();

    /** The client's own config, saved before the first server override; null when not overridden. */
    private double[] localSnapshot;

    private ClientConfigSync() {
    }

    /** Apply the server's values, snapshotting the client's own once so they can be restored later. */
    public void apply(double[] serverValues) {
        if (localSnapshot == null) {
            localSnapshot = LdibConfig.captureSyncable();
        }
        LdibConfig.applySyncable(serverValues);
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        if (localSnapshot != null) {
            LdibConfig.applySyncable(localSnapshot);
            localSnapshot = null;
        }
    }
}
