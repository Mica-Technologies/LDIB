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
 * <p>The numeric payload is just {@link LdibConfig#captureSyncable()}'s array, so adding a synced
 * number is a one-line change there and this packet never needs touching. The surface table travels
 * alongside it as strings, because block names are the one movement-affecting setting that will not
 * fit in a {@code double[]} — and it has to travel, for exactly the same reason the numbers do: a
 * client that thinks a road is gravel predicts a slower bike than the server is simulating.</p>
 */
public class PacketSyncConfig implements IMessage {

    /**
     * Cap on surface-table entries accepted from the wire. A join packet is not a place to accept an
     * unbounded allocation from a length field, and no real table is anywhere near this size.
     */
    private static final int MAX_SURFACE_ENTRIES = 4096;

    private double[] values;
    private String[] surfaces;

    public PacketSyncConfig() {
    }

    private PacketSyncConfig(double[] values, String[] surfaces) {
        this.values = values;
        this.surfaces = surfaces;
    }

    /** The packet carrying this server's current config, to send to a joining player. */
    public static PacketSyncConfig current() {
        return new PacketSyncConfig(LdibConfig.captureSyncable(), LdibConfig.captureSyncableSurfaces());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = buf.readInt();
        this.values = new double[Math.max(0, n)];
        for (int i = 0; i < this.values.length; i++) {
            this.values[i] = buf.readDouble();
        }
        // Read defensively: a server older than the surface table sends nothing after the numbers,
        // and a joining client must not fail its login over that. Same append-only contract as the
        // numeric array — an older server is a valid prefix, and the client keeps its own table.
        if (!buf.isReadable()) {
            this.surfaces = null;
            return;
        }
        int m = buf.readInt();
        if (m < 0 || m > MAX_SURFACE_ENTRIES) {
            this.surfaces = null;
            return;
        }
        this.surfaces = new String[m];
        for (int i = 0; i < m; i++) {
            this.surfaces[i] = net.minecraftforge.fml.common.network.ByteBufUtils.readUTF8String(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(values.length);
        for (double v : values) {
            buf.writeDouble(v);
        }
        String[] table = surfaces == null ? new String[0] : surfaces;
        buf.writeInt(table.length);
        for (String s : table) {
            net.minecraftforge.fml.common.network.ByteBufUtils.writeUTF8String(buf, s == null ? "" : s);
        }
    }

    public static class Handler implements IMessageHandler<PacketSyncConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketSyncConfig msg, MessageContext ctx) {
            Ldib.proxy.applySyncedConfig(msg.values, msg.surfaces);
            return null;
        }
    }
}
