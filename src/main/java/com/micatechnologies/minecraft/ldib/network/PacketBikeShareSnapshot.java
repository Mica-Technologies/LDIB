package com.micatechnologies.minecraft.ldib.network;

import com.micatechnologies.minecraft.ldib.api.BikeShareApi;
import com.micatechnologies.minecraft.ldib.api.BikeShareSnapshot;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server → client: a bike-share snapshot, answering a {@link PacketBikeShareRequest}. Stored straight
 * into {@link BikeShareApi#latestSnapshot()} from the network thread: the snapshot is immutable and the
 * field volatile, so there is nothing to hand to the client thread, and no client-only class is
 * touched — this handler is safe to load on a dedicated server.
 */
public class PacketBikeShareSnapshot implements IMessage {

    private BikeShareSnapshot snapshot;

    public PacketBikeShareSnapshot() {
    }

    public PacketBikeShareSnapshot(BikeShareSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.snapshot = BikeShareSnapshot.fromBytes(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        snapshot.toBytes(buf);
    }

    public static class Handler implements IMessageHandler<PacketBikeShareSnapshot, IMessage> {
        @Override
        public IMessage onMessage(PacketBikeShareSnapshot msg, MessageContext ctx) {
            BikeShareApi.receive(msg.snapshot);
            return null;
        }
    }
}
