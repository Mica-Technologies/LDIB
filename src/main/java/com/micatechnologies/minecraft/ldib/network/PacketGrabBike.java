package com.micatechnologies.minecraft.ldib.network;

import com.micatechnologies.minecraft.ldib.RideableActions;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client → server: the player pressed the "grab / dock" key. The server decides what that means from
 * the player's context (riding vs on foot, near a station or not) — see {@link RideableActions}. No
 * payload: everything needed is the sending player's position and what they're riding.
 */
public class PacketGrabBike implements IMessage {

    public PacketGrabBike() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketGrabBike, IMessage> {
        @Override
        public IMessage onMessage(PacketGrabBike msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> RideableActions.grabOrDock(player));
            return null;
        }
    }
}
