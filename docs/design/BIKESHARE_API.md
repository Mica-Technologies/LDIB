# Bike-share API

For mods that want to show LDIB's bike-share network: a phone app, a map, a HUD, a sign. Everything is
in `com.micatechnologies.minecraft.ldib.api`, read-only, and versioned by `BikeShareApi.VERSION`
(currently `1`; bumped only on an incompatible change).

## What a station is

A station is a **kiosk** plus every **dock** within `bikeshare.stationRadius` blocks of it (a cube).
LDIB keeps a registry of stations per dimension, keyed by the kiosk's lower block, and recounts a
station whenever something there changes: a dock is placed, broken, filled, emptied or stocked, or the
kiosk is placed or used. Counts are therefore **as last seen**, which is what lets the API describe
stations in chunks that are not loaded.

Kiosks built before the registry existed join it the first time someone uses one or a dock near it
changes. An operator can also run `/ldib stations rescan` to register every station with a dock in a
loaded chunk, and `/ldib stations list` to see the registry.

## Server side

Call from the server thread.

| Method | Returns |
| --- | --- |
| `BikeShareApi.stations(World)` | Every station in that dimension, as `StationInfo` |
| `BikeShareApi.riderStatus(EntityPlayer)` | The player's open rental and history, as `RiderStatus` |
| `BikeShareApi.snapshotFor(EntityPlayer)` | All of the above plus prices, for one player, as `BikeShareSnapshot` |

## Client side

```java
BikeShareApi.requestSnapshot();                  // ask; safe to call on a timer
BikeShareSnapshot s = BikeShareApi.latestSnapshot(); // null until the first answer arrives
```

The server answers each player at most once every 10 ticks and ignores the rest, so polling every
second or two while your screen is open is fine. The cache is cleared on disconnect.

## The objects

**`StationInfo`**: `kiosk` (the station's identity), `bikes`, `freeDocks`, `docks()`,
`bikes(BikeVariant)`, `fill()` (0..1, or −1 with no docks), `countedTick`.

**`ShareTariff`** (in the snapshot): `unlockFee`, `ratePerMinute(variant)`, `isPaid()`, and the
rebalancing policy: `rebalanceCreditPerEnd`, `isBusy(fill)` (riders are paid to take bikes from it),
`isStarved(fill)` (riders are paid to return bikes to it). A free network sends `ShareTariff.FREE`,
for which neither is ever true.

**`RiderStatus`**: `hasRental`, `bikeTaken`, `rentalClockStart` (world time), `rentalKiosk`, `rides`,
`charged`, `rebalancingRides`, `credited`, `recentCredits` (newest first: tick, amount, from and to
stations).

**`BikeShareSnapshot`**: `worldTime`, `dimension`, `tariff`, `currency`, `stations` (nearest the
player first, at most 256), `rider`. Show amounts with `BikeShareApi.formatAmount(snapshot, amount)`:
`"3 emeralds"` for an item currency, `"2.50"` for an economy's own.

**`StationMath`**: `distance`, `bearing` (Minecraft yaw: 0 is south, 90 west), `relativeBearing`
(0 ahead, positive right), `compass` ("NE").

## Rebalancing

A ride earns credit for each end that helps the fleet: taking its bike from a busy station (at least
`rebalanceBusyFill` full) and returning it to a starved one (at most `rebalanceStarvedFill`). Each
qualifying end pays `rebalanceCredit`, the total is capped at what the ride cost, and it is only paid
when rides are billed. Stations are judged as the rider found them.

## LDIBike, the phone app

LDIB uses this API itself for **LDIBike**, an app on SUM's phone (`client/phone/LdibikePhoneApp`),
registered when SUM is installed and has its phone app API (`sum.api.phone`, SUM's
`docs/PHONE_APP_API.md`). LDIB compiles against copies of those few SUM types kept in
`src/sumPhoneStubs/java` and never packaged (see `addon.gradle`); keep them in step with SUM.
