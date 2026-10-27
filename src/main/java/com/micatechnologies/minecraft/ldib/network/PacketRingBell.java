package com.micatechnologies.minecraft.ldib.network;

import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client → server: the rider pressed the bell key. The server rings the bell of whatever the sender is
 * riding — so everyone in earshot hears it, not just the rider — and ignores the press if they are not
 * riding anything (see {@link EntityBike#ringBell}, which also rate-limits it). No payload: the
 * sending player says everything needed.
 */
public class PacketRingBell implements IMessage {

    public PacketRingBell() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketRingBell, IMessage> {
        @Override
        public IMessage onMessage(PacketRingBell msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.getRidingEntity() instanceof EntityBike) {
                    ((EntityBike) player.getRidingEntity()).ringBell();
                }
            });
            return null;
        }
    }
}
