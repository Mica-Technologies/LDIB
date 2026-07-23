package com.micatechnologies.minecraft.ldib.network;

import com.micatechnologies.minecraft.ldib.block.BikeShareStation;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client → server: the player pressed a button on a kiosk screen (currently only "check out").
 * Handled on the server thread; all the validation and session mutation lives in
 * {@link BikeShareStation}.
 */
public class PacketKioskAction implements IMessage {

    public static final int CHECK_OUT = 0;

    private BlockPos kiosk;
    private int action;

    public PacketKioskAction() {
    }

    public PacketKioskAction(BlockPos kiosk, int action) {
        this.kiosk = kiosk;
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.kiosk = BlockPos.fromLong(buf.readLong());
        this.action = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(kiosk.toLong());
        buf.writeByte(action);
    }

    public static class Handler implements IMessageHandler<PacketKioskAction, IMessage> {
        @Override
        public IMessage onMessage(PacketKioskAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.action == CHECK_OUT) {
                    BikeShareStation.checkOut(player, msg.kiosk);
                }
            });
            return null;
        }
    }
}
