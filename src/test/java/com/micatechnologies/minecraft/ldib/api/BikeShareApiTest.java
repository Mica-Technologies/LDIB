package com.micatechnologies.minecraft.ldib.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.micatechnologies.minecraft.ldib.entity.BikeVariant;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Collections;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * The bike-share API's pure parts: the direction maths other mods will point arrows with, and the wire
 * format the snapshot travels in. A wire format that does not round-trip, or reads a different number
 * of bytes than it wrote, corrupts every field after it, so each DTO is pinned here.
 */
class BikeShareApiTest {

    private static final double EPS = 1.0e-9D;

    @Test
    void bearingsUseMinecraftYaw() {
        assertEquals(0.0D, StationMath.bearing(0, 0, 0, 10), EPS, "+Z is south, yaw 0");
        assertEquals(90.0D, StationMath.bearing(0, 0, -10, 0), EPS, "-X is west, yaw 90");
        assertEquals(-180.0D, StationMath.bearing(0, 0, 0, -10), EPS, "-Z is north, yaw 180 (wrapped)");
        assertEquals(-90.0D, StationMath.bearing(0, 0, 10, 0), EPS, "+X is east, yaw -90");
    }

    @Test
    void relativeBearingIsPositiveToTheRight() {
        // Facing south (0); a station to the west (90) is on a south-facing player's right.
        assertEquals(90.0D, StationMath.relativeBearing(0.0D, 90.0D), EPS);
        assertEquals(-90.0D, StationMath.relativeBearing(0.0D, -90.0D), EPS);
        assertEquals(-180.0D, StationMath.relativeBearing(0.0D, 180.0D), EPS, "dead behind");
    }

    @Test
    void compassNamesTheEightPoints() {
        assertEquals("S", StationMath.compass(0.0D));
        assertEquals("W", StationMath.compass(90.0D));
        assertEquals("N", StationMath.compass(180.0D));
        assertEquals("E", StationMath.compass(-90.0D));
        assertEquals("SW", StationMath.compass(45.0D));
        assertEquals("NE", StationMath.compass(-135.0D));
    }

    @Test
    void distanceIgnoresHeight() {
        assertEquals(5.0D, StationMath.distance(0, 0, 3, 4), EPS);
    }

    @Test
    void stationInfoCountsByTypeAndFill() {
        int[] byVariant = new int[BikeVariant.values().length];
        byVariant[BikeVariant.EBIKE.ordinal()] = 2;
        StationInfo s = new StationInfo(new BlockPos(1, 2, 3), 2, 6, byVariant, 100L);
        assertEquals(8, s.docks());
        assertEquals(0.25D, s.fill(), EPS);
        assertEquals(2, s.bikes(BikeVariant.EBIKE));
        assertEquals(0, s.bikes(BikeVariant.SCOOTER));
    }

    @Test
    void snapshotRoundTripsAndConsumesExactlyWhatItWrote() {
        int[] byVariant = new int[BikeVariant.values().length];
        byVariant[BikeVariant.BICYCLE.ordinal()] = 3;
        StationInfo station = new StationInfo(new BlockPos(-40, 64, 900), 3, 1, byVariant, 1234L);
        RiderStatus rider = new RiderStatus(true, true, 5000L, new BlockPos(10, 64, 10), 7, 12.5D, 2, 1.5D,
            Collections.singletonList(new RiderStatus.CreditEntry(4000L, 0.5D, new BlockPos(1, 2, 3), null)));
        ShareTariff tariff = new ShareTariff(1.0D, 0.2D, 0.3D, 0.25D, 0.5D, 0.75D, 0.25D);
        BikeShareSnapshot original = new BikeShareSnapshot(6000L, 0, tariff, "emeralds",
            Arrays.asList(station, station), rider);

        ByteBuf buf = Unpooled.buffer();
        original.toBytes(buf);
        buf.writeByte(0x5A); // a sentinel: the reader must stop exactly before it
        BikeShareSnapshot copy = BikeShareSnapshot.fromBytes(buf);

        assertEquals(0x5A, buf.readByte(), "reader consumed a different number of bytes than were written");
        assertEquals(6000L, copy.worldTime);
        assertEquals("emeralds", copy.currency);
        assertEquals(0.5D, copy.tariff.rebalanceCreditPerEnd, EPS);
        assertEquals(2, copy.stations.size());
        assertEquals(new BlockPos(-40, 64, 900), copy.stations.get(0).kiosk);
        assertEquals(3, copy.stations.get(0).bikes(BikeVariant.BICYCLE));
        assertEquals(7, copy.rider.rides);
        assertEquals(new BlockPos(10, 64, 10), copy.rider.rentalKiosk);
        assertEquals(1, copy.rider.recentCredits.size());
        assertNull(copy.rider.recentCredits.get(0).to);
    }

    @Test
    void oversizedStationCountIsRejectedNotTrusted() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeLong(0L);
        buf.writeInt(0);
        ShareTariff.FREE.toBytes(buf);
        buf.writeShort(0);
        buf.writeShort(BikeShareSnapshot.MAX_STATIONS + 1);
        assertThrows(IllegalArgumentException.class, () -> BikeShareSnapshot.fromBytes(buf));
    }

    @Test
    void amountsFormatAsWholeItemsOrDecimals() {
        BikeShareSnapshot items = new BikeShareSnapshot(0L, 0, ShareTariff.FREE.withCurrency("emerald", true),
            "emeralds", Collections.emptyList(), RiderStatus.NONE);
        BikeShareSnapshot money = new BikeShareSnapshot(0L, 0, ShareTariff.FREE.withCurrency("$", false),
            "$", Collections.emptyList(), RiderStatus.NONE);
        assertEquals("3 emeralds", BikeShareApi.formatAmount(items, 3.0D));
        assertEquals("1 emerald", BikeShareApi.formatAmount(items, 1.0D));
        assertEquals("$2.50", BikeShareApi.formatAmount(money, 2.5D));
    }
}
