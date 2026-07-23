package com.micatechnologies.minecraft.ldib.network;

import com.micatechnologies.minecraft.ldib.Ldib;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server → client: open the kiosk screen for a station, carrying the player's current session state
 * so the screen knows whether to offer "check out" or show an active rental.
 *
 * <p>The handler routes through {@code Ldib.proxy.openKioskGui} rather than referencing a client GUI
 * type, so this class stays loadable on a dedicated server (the common proxy's method is a no-op).</p>
 */
public class PacketOpenKiosk implements IMessage {

    private BlockPos kiosk;
    private boolean hasSession;
    private long startTick;

    public PacketOpenKiosk() {
    }

    public PacketOpenKiosk(BlockPos kiosk, boolean hasSession, long startTick) {
        this.kiosk = kiosk;
        this.hasSession = hasSession;
        this.startTick = startTick;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.kiosk = BlockPos.fromLong(buf.readLong());
        this.hasSession = buf.readBoolean();
        this.startTick = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(kiosk.toLong());
        buf.writeBoolean(hasSession);
        buf.writeLong(startTick);
    }

    public static class Handler implements IMessageHandler<PacketOpenKiosk, IMessage> {
        @Override
        public IMessage onMessage(PacketOpenKiosk msg, MessageContext ctx) {
            Ldib.proxy.openKioskGui(msg.kiosk, msg.hasSession, msg.startTick);
            return null;
        }
    }
}
