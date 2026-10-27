package com.micatechnologies.minecraft.ldib.network;

import com.micatechnologies.minecraft.ldib.api.BikeShareApi;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client → server: send me a bike-share snapshot ({@link BikeShareApi#requestSnapshot}). The server
 * answers with a {@link PacketBikeShareSnapshot} for the sender only. No payload.
 *
 * <p>Rate-limited per player: a phone app refreshing on a timer, or a buggy one asking every frame,
 * costs at most one snapshot per {@link #MIN_TICKS_BETWEEN} ticks. A snapshot is cheap — it reads the
 * station registry, never the world — but it is still a packet per request.</p>
 */
public class PacketBikeShareRequest implements IMessage {

    /** Fewest ticks between two answered requests from one player. */
    static final long MIN_TICKS_BETWEEN = 10L;

    /** World time each player was last answered. Server thread only. */
    private static final Map<UUID, Long> LAST_ANSWERED = new HashMap<>();

    public PacketBikeShareRequest() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketBikeShareRequest, IMessage> {
        @Override
        public IMessage onMessage(PacketBikeShareRequest msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                long now = player.world.getTotalWorldTime();
                Long last = LAST_ANSWERED.get(player.getUniqueID());
                if (last != null && now - last < MIN_TICKS_BETWEEN && now >= last) {
                    return;
                }
                LAST_ANSWERED.put(player.getUniqueID(), now);
                LdibNetwork.CHANNEL.sendTo(new PacketBikeShareSnapshot(BikeShareApi.snapshotFor(player)), player);
            });
            return null;
        }
    }
}
