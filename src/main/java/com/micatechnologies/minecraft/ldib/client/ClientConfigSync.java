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

    /** The client's own surface table, saved alongside {@link #localSnapshot}. */
    private String[] localSurfaces;

    private ClientConfigSync() {
    }

    /**
     * Apply the server's config, snapshotting the client's own once so it can be restored later.
     *
     * <p>Both snapshots are taken under the one {@code localSnapshot == null} guard so they can never
     * come from different moments — restoring a numeric set from before a server override alongside a
     * surface table from after it would leave the client running a config that never existed.</p>
     *
     * @param serverSurfaces the server's surface table, or {@code null} from a server too old to send
     *                       one — in which case the client keeps its own, matching how the numeric
     *                       array treats a short payload
     */
    public void apply(double[] serverValues, String[] serverSurfaces) {
        if (localSnapshot == null) {
            localSnapshot = LdibConfig.captureSyncable();
            localSurfaces = LdibConfig.captureSyncableSurfaces();
        }
        LdibConfig.applySyncable(serverValues);
        if (serverSurfaces != null) {
            LdibConfig.applySyncableSurfaces(serverSurfaces);
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        if (localSnapshot != null) {
            LdibConfig.applySyncable(localSnapshot);
            LdibConfig.applySyncableSurfaces(localSurfaces);
            localSnapshot = null;
            localSurfaces = null;
        }
    }
}
