package com.micatechnologies.minecraft.ldib.network;

import com.micatechnologies.minecraft.ldib.Ldib;
import com.micatechnologies.minecraft.ldib.LdibConfig;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server → client on join: the server's movement-affecting config values, so a connecting client
 * predicts with the server's tuning instead of its own local config (avoiding rubber-band when the two
 * differ). The client applies them and restores its own on disconnect (see the client handler reached
 * through {@code Ldib.proxy.applySyncedConfig}).
 *
 * <p>The payload is just {@link LdibConfig#captureSyncable()}'s array, so adding a synced value is a
 * one-line change there — this packet never needs touching.</p>
 */
public class PacketSyncConfig implements IMessage {

    private double[] values;

    public PacketSyncConfig() {
    }

    private PacketSyncConfig(double[] values) {
        this.values = values;
    }

    /** The packet carrying this server's current config, to send to a joining player. */
    public static PacketSyncConfig current() {
        return new PacketSyncConfig(LdibConfig.captureSyncable());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = buf.readInt();
        this.values = new double[n];
        for (int i = 0; i < n; i++) {
            this.values[i] = buf.readDouble();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(values.length);
        for (double v : values) {
            buf.writeDouble(v);
        }
    }

    public static class Handler implements IMessageHandler<PacketSyncConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketSyncConfig msg, MessageContext ctx) {
            Ldib.proxy.applySyncedConfig(msg.values);
            return null;
        }
    }
}
