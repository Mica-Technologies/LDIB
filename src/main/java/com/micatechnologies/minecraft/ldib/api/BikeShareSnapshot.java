package com.micatechnologies.minecraft.ldib.api;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything a bike-share app needs, for one player, in one object: the stations in their dimension,
 * the price list (rebalancing policy included), what the prices are counted in, and the player's own
 * rental and history. Built on the server ({@link BikeShareApi#snapshotFor}) and sent whole to the
 * client ({@link BikeShareApi#requestSnapshot}); immutable on both sides.
 */
public final class BikeShareSnapshot {

    /** Stations sent at most, nearest first, so a sprawling network cannot make a huge packet. */
    public static final int MAX_STATIONS = 256;

    /** Longest currency name sent, in UTF-8 bytes. */
    private static final int MAX_CURRENCY_BYTES = 256;

    /** World time the snapshot was taken (server clock), for elapsed-time displays. */
    public final long worldTime;
    public final int dimension;
    /** Prices and the rebalancing policy in effect; {@link ShareTariff#FREE} when rides are free. */
    public final ShareTariff tariff;
    /**
     * What amounts are counted in, for display: empty for an economy's own currency, otherwise an
     * item's plural name ("emeralds"). See {@link BikeShareApi#formatAmount}.
     */
    public final String currency;
    /** Nearest first, at most {@link #MAX_STATIONS}. */
    public final List<StationInfo> stations;
    public final RiderStatus rider;

    public BikeShareSnapshot(long worldTime, int dimension, ShareTariff tariff, String currency,
                             List<StationInfo> stations, RiderStatus rider) {
        this.worldTime = worldTime;
        this.dimension = dimension;
        this.tariff = tariff;
        this.currency = currency == null ? "" : currency;
        this.stations = Collections.unmodifiableList(new ArrayList<>(stations));
        this.rider = rider;
    }

    public void toBytes(ByteBuf buf) {
        buf.writeLong(worldTime);
        buf.writeInt(dimension);
        tariff.toBytes(buf);
        byte[] currencyBytes = currency.getBytes(StandardCharsets.UTF_8);
        int currencyLength = Math.min(currencyBytes.length, MAX_CURRENCY_BYTES); // same cap the reader applies
        buf.writeShort(currencyLength);
        buf.writeBytes(currencyBytes, 0, currencyLength);
        int n = Math.min(stations.size(), MAX_STATIONS);
        buf.writeShort(n);
        for (int i = 0; i < n; i++) {
            stations.get(i).toBytes(buf);
        }
        rider.toBytes(buf);
    }

    public static BikeShareSnapshot fromBytes(ByteBuf buf) {
        long worldTime = buf.readLong();
        int dimension = buf.readInt();
        ShareTariff tariff = ShareTariff.fromBytes(buf);
        int currencyLength = buf.readUnsignedShort();
        if (currencyLength > MAX_CURRENCY_BYTES) {
            throw new IllegalArgumentException("bike-share snapshot currency name too long: " + currencyLength);
        }
        byte[] currencyBytes = new byte[currencyLength];
        buf.readBytes(currencyBytes);
        int n = buf.readUnsignedShort();
        if (n > MAX_STATIONS) {
            throw new IllegalArgumentException("bike-share snapshot has too many stations: " + n);
        }
        List<StationInfo> stations = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            stations.add(StationInfo.fromBytes(buf));
        }
        RiderStatus rider = RiderStatus.fromBytes(buf);
        return new BikeShareSnapshot(worldTime, dimension, tariff,
            new String(currencyBytes, StandardCharsets.UTF_8), stations, rider);
    }
}
