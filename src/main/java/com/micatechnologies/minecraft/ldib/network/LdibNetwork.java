package com.micatechnologies.minecraft.ldib.network;

import com.micatechnologies.minecraft.ldib.LdibConstants;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * LDIB's SimpleImpl network channel — the mod's first, added for the bike-share kiosk GUI. Server →
 * client opens the kiosk screen ({@link PacketOpenKiosk}); client → server performs a kiosk action
 * such as checking out ({@link PacketKioskAction}).
 *
 * <p>The client-bound packet's handler routes through {@code Ldib.proxy} rather than touching a
 * {@code net.minecraft.client} type directly, so this class and its handlers stay loadable on a
 * dedicated server.</p>
 */
public final class LdibNetwork {

    public static SimpleNetworkWrapper CHANNEL;

    private LdibNetwork() {
        throw new AssertionError("No instances.");
    }

    public static void init() {
        CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(LdibConstants.MOD_NAMESPACE);
        CHANNEL.registerMessage(PacketKioskAction.Handler.class, PacketKioskAction.class, 0, Side.SERVER);
        CHANNEL.registerMessage(PacketOpenKiosk.Handler.class, PacketOpenKiosk.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(PacketGrabBike.Handler.class, PacketGrabBike.class, 2, Side.SERVER);
        CHANNEL.registerMessage(PacketSyncConfig.Handler.class, PacketSyncConfig.class, 3, Side.CLIENT);
    }
}
