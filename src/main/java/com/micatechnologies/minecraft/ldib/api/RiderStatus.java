package com.micatechnologies.minecraft.ldib.api;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.math.BlockPos;

/**
 * One rider's side of the bike-share: the rental they have open, if any, and their history —
 * rides taken, what they have paid, and the rebalancing credit they have earned. Immutable, and the
 * same object on both sides of the wire.
 */
public final class RiderStatus {

    /** No rental and no history. */
    public static final RiderStatus NONE =
        new RiderStatus(false, false, 0L, null, 0, 0.0D, 0, 0.0D, Collections.emptyList());

    public final boolean hasRental;
    /** Whether the rental's bike has been taken from a dock yet. */
    public final boolean bikeTaken;
    /** World time the billing clock started (bike taken, or check-out until then); 0 with no rental. */
    public final long rentalClockStart;
    /** The kiosk the rental was checked out at; null with no rental. */
    public final BlockPos rentalKiosk;

    public final int rides;
    public final double charged;
    public final int rebalancingRides;
    public final double credited;
    /** Newest first. */
    public final List<CreditEntry> recentCredits;

    /** One rebalancing credit. Either station may be null (no station at that end). */
    public static final class CreditEntry {
        public final long tick;
        public final double amount;
        public final BlockPos from;
        public final BlockPos to;

        public CreditEntry(long tick, double amount, BlockPos from, BlockPos to) {
            this.tick = tick;
            this.amount = amount;
            this.from = from;
            this.to = to;
        }
    }

    public RiderStatus(boolean hasRental, boolean bikeTaken, long rentalClockStart, BlockPos rentalKiosk,
                       int rides, double charged, int rebalancingRides, double credited,
                       List<CreditEntry> recentCredits) {
        this.hasRental = hasRental;
        this.bikeTaken = bikeTaken;
        this.rentalClockStart = rentalClockStart;
        this.rentalKiosk = rentalKiosk;
        this.rides = rides;
        this.charged = charged;
        this.rebalancingRides = rebalancingRides;
        this.credited = credited;
        this.recentCredits = Collections.unmodifiableList(new ArrayList<>(recentCredits));
    }

    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(hasRental);
        buf.writeBoolean(bikeTaken);
        buf.writeLong(rentalClockStart);
        writePos(buf, rentalKiosk);
        buf.writeInt(rides);
        buf.writeDouble(charged);
        buf.writeInt(rebalancingRides);
        buf.writeDouble(credited);
        buf.writeByte(recentCredits.size());
        for (CreditEntry entry : recentCredits) {
            buf.writeLong(entry.tick);
            buf.writeDouble(entry.amount);
            writePos(buf, entry.from);
            writePos(buf, entry.to);
        }
    }

    public static RiderStatus fromBytes(ByteBuf buf) {
        boolean hasRental = buf.readBoolean();
        boolean bikeTaken = buf.readBoolean();
        long clock = buf.readLong();
        BlockPos kiosk = readPos(buf);
        int rides = buf.readInt();
        double charged = buf.readDouble();
        int rebalancing = buf.readInt();
        double credited = buf.readDouble();
        int n = buf.readUnsignedByte();
        List<CreditEntry> recent = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            recent.add(new CreditEntry(buf.readLong(), buf.readDouble(), readPos(buf), readPos(buf)));
        }
        return new RiderStatus(hasRental, bikeTaken, clock, kiosk, rides, charged, rebalancing, credited, recent);
    }

    private static void writePos(ByteBuf buf, BlockPos pos) {
        buf.writeBoolean(pos != null);
        if (pos != null) {
            buf.writeLong(pos.toLong());
        }
    }

    private static BlockPos readPos(ByteBuf buf) {
        return buf.readBoolean() ? BlockPos.fromLong(buf.readLong()) : null;
    }
}
