package com.micatechnologies.minecraft.ldib.network;

import com.micatechnologies.minecraft.ldib.block.BikeShareStation;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client → server: the player pressed a button on a kiosk screen. Handled on the server thread; all
 * the validation and session mutation lives in {@link BikeShareStation}.
 */
public class PacketKioskAction implements IMessage {

    public static final int CHECK_OUT = 0;
    /** End an open rental from the kiosk — the only way out of one that doesn't involve docking. */
    public static final int END_RENTAL = 1;

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

    /**
     * How far (squared) a player may be from the kiosk they claim to be pressing buttons on.
     *
     * <p>The position in this packet is supplied by the client and was previously passed straight
     * through to {@link BikeShareStation}, which only checked that the block there <i>is</i> a kiosk —
     * so a modified client could start or end a rental at any kiosk in the world without going near
     * one. Vanilla block reach is about 5 blocks; 8 leaves room for latency and for standing back from
     * a two-block kiosk without being exploitable.</p>
     */
    private static final double MAX_REACH_SQ = 8.0D * 8.0D;

    public static class Handler implements IMessageHandler<PacketKioskAction, IMessage> {
        @Override
        public IMessage onMessage(PacketKioskAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                // Never trust a client-supplied position: verify the player is actually standing at
                // the kiosk before acting on it.
                if (player.getDistanceSq(msg.kiosk) > MAX_REACH_SQ) {
                    return;
                }
                if (msg.action == CHECK_OUT) {
                    BikeShareStation.checkOut(player, msg.kiosk);
                } else if (msg.action == END_RENTAL) {
                    BikeShareStation.endRental(player, msg.kiosk);
                }
            });
            return null;
        }
    }
}
