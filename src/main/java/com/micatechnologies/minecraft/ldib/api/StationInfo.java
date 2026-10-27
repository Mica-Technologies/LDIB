package com.micatechnologies.minecraft.ldib.api;

import com.micatechnologies.minecraft.ldib.entity.BikeVariant;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;

/**
 * One bike-share station as the API describes it: where its kiosk stands, how many docks it has, and
 * what is docked there right now, by type. Immutable, and the same object on both sides of the wire.
 *
 * <p>Counts are as the station was last counted — every dock, return, check-out, stock and kiosk use
 * recounts it — so a station in an unloaded chunk is still described, as it was when last seen.</p>
 */
public final class StationInfo {

    /** The kiosk's lower block. This is the station's identity. */
    public final BlockPos kiosk;
    public final int bikes;
    public final int freeDocks;
    /** World time the counts were taken. */
    public final long countedTick;
    private final int[] bikesByVariant;

    public StationInfo(BlockPos kiosk, int bikes, int freeDocks, int[] bikesByVariant, long countedTick) {
        this.kiosk = kiosk;
        this.bikes = bikes;
        this.freeDocks = freeDocks;
        this.bikesByVariant = bikesByVariant == null ? new int[0] : bikesByVariant.clone();
        this.countedTick = countedTick;
    }

    /** Every dock point at the station, full or empty. */
    public int docks() {
        return bikes + freeDocks;
    }

    /** How full the station is, 0..1, or −1 with no docks; see {@link ShareTariff#fill}. */
    public double fill() {
        return ShareTariff.fill(bikes, freeDocks);
    }

    /** Docked bikes of one type. */
    public int bikes(BikeVariant variant) {
        int i = variant.ordinal();
        return i < bikesByVariant.length ? bikesByVariant[i] : 0;
    }

    public void toBytes(ByteBuf buf) {
        buf.writeLong(kiosk.toLong());
        buf.writeInt(bikes);
        buf.writeInt(freeDocks);
        buf.writeLong(countedTick);
        buf.writeByte(bikesByVariant.length);
        for (int count : bikesByVariant) {
            buf.writeInt(count);
        }
    }

    public static StationInfo fromBytes(ByteBuf buf) {
        BlockPos kiosk = BlockPos.fromLong(buf.readLong());
        int bikes = buf.readInt();
        int free = buf.readInt();
        long counted = buf.readLong();
        int[] byVariant = new int[buf.readUnsignedByte()];
        for (int i = 0; i < byVariant.length; i++) {
            byVariant[i] = buf.readInt();
        }
        return new StationInfo(kiosk, bikes, free, byVariant, counted);
    }
}
