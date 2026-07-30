# LDIB — Limitless Development: Immersive Biking

A Minecraft **1.12.2 Forge** mod that adds **rider-controlled bikes**: mount one and pedal, brake
and steer it with WASD, using the vanilla riding mechanic rather than a retextured pig or minecart.

Five rideables, each blocky and voxel-style and each done properly — real acceleration, coasting,
braking and speed-dependent steering:

- a **bicycle** and a faster **e-bike**
- a **scooter** (~12 mph) and a **performance scooter** (~22 mph), ridden standing
- a **one-wheel** (~12 mph) — one fat tyre between two foot pads, ridden *across* the board in a surf
  stance with only your head turned down the road. It carves harder than anything else here and it is
  **standalone**: no docks, no racks, no fleet. You pick it up and carry it.

Plus the infrastructure around them: **owner-locked bike racks** in five styles (multi-block, and they
show the bikes parked on them), and a **bike-share network** of docks and kiosks where you check a bike
out at one station and return it at any other — optionally billed per minute through an installed
economy mod. Powered variants carry a **battery** that runs down and fades the assist as it empties.

The scope stays deliberately focused: a handful of rideables that feel good, not a general vehicle
framework.

> **Status: alpha — released and playable.** Latest release **`2026.07.24`**; the ride, the racks, the
> share network and the visuals are all confirmed working in-game. `./gradlew build` is green (Forge
> 1.12.2, 33 unit tests, jar produced) and CI additionally boots a real dedicated server on every PR.
>
> Rough edges are visual rather than structural — several model positions and offsets are first cuts
> awaiting a tuning pass.
>
> This branch is ahead of the latest release: the **battery** and the **rider camera lean** have landed
> since `2026.07.24` and ship with the next one. Day-to-day work happens on `dev/mica-alex-changes`;
> `main` is the release line. See `docs/AGENT-PLANS/MASTER_PLAN.md` (local only, gitignored) for the
> roadmap and the exact "done / not done" state.

## What makes it different from a pig with a saddle

| Aspect | Vanilla saddled mob | LDIB |
| --- | --- | --- |
| Control | Steer toward look direction | Throttle / brake / steer as separate inputs |
| Speed | Fixed walk/sprint | Continuous: accelerates, coasts, has a real top speed |
| Braking | None | Dedicated brake that stops faster than coasting |
| Steering | Instant | Rate-limited and speed-dependent (tight when slow, shallow when fast) |
| Model | Living mob | Purpose-built rideable entity, physics in a testable core |

## Building

Requires a **JDK 17–22** (`21` is the sweet spot — see `CLAUDE.md` for the reasoning). The mod
itself targets Java 8 via Jabel regardless of which JDK runs Gradle.

```sh
./gradlew build          # compile, run unit tests, produce the jar
./gradlew test           # unit tests only (pure JVM, no game instance needed)
./gradlew runClient      # dev client
./gradlew runServer      # dev dedicated server
```

Build system is [GregTechCEu Buildscripts](https://github.com/GregTechCEu/Buildscripts)
(a RetroFuturaGradle wrapper), matching the other Mica Technologies 1.12.2 mods (RCMC, CSM, SUM).

## Architecture at a glance

```
com.micatechnologies.minecraft.ldib
├── Ldib, LdibConfig, LdibRegistry, LdibTab, LdibSounds, Ldib*Proxy   # Forge plumbing
├── RideableActions                # shared park / pocket / grab logic (server-authoritative)
├── physics/     # handling + battery models — pure Java, ZERO Minecraft types
│   ├── BikeState                  # immutable (speed, heading)
│   ├── BikeTuning                 # per-variant handling constants; blends motor assist
│   ├── BikePhysics                # semi-implicit Euler step; testable on a bare JVM
│   └── BatteryModel               # charge spend + assist taper
├── entity/
│   ├── EntityBike                 # the rider-controlled vehicle (common; server loads it)
│   ├── BikeVariant                # "variants are data": tuning + skin + pose + battery per variant
│   └── RiderPose                  # seated (bike) / standing (scooter) / across the board (one-wheel)
├── item/                          # ItemBike, LdibItems — places the bike, boat-style
├── block/                         # racks, docks, kiosks + the bike-share network (WorldSavedData)
├── network/                       # LdibNetwork + packets (kiosk GUI, grab, config sync)
├── api/                           # BikeShareBilling, ShareTariff — the economy seam
├── integration/                   # SumEconomy — optional soft dep, reflection only, no compile dep
└── client/                        # client-ONLY, reached via LdibClientProxy
    ├── render/                    # RenderBike, ModelRideable + per-variant models, rack/dock TESRs
    ├── hud/                       # ride readout (speed + battery), grab prompt
    ├── gui/                       # kiosk screen
    ├── sound/                     # looping ride sound, brake scuff
    └── RiderCamera, RiderPoseHandler, LdibKeyHandler, ClientConfigSync
```

**The load-bearing constraint:** `physics` contains no Minecraft types. That keeps the parts most
likely to be subtly wrong — acceleration, braking, steering feel, how a battery fades — testable on a
bare JVM (`./gradlew test` runs 33 tests in seconds), with assertions like "braking stops sooner than
coasting", "a parked bike does not turn on the spot" and "a flat e-bike never outruns a pedal bicycle".
Convert to Minecraft types at the entity boundary only.

**The other one:** nothing outside `client/` may touch `net.minecraft.client`. A stray client import in
common code compiles perfectly and only fails when a dedicated server boots — hence the CI smoke test
below.

## CI

- **Pull requests** — compile + unit tests, then a dedicated-server smoke test that boots a real
  server and asserts it reaches startup. That second job exists because client-only code reached
  from common code compiles perfectly and only fails at server boot — and the bike renderer/model
  sit right next to the common `EntityBike`.
- **Push to `main`** — builds and publishes a pre-release with checksums; a manual dispatch with
  `release=true` cuts a full `YYYY.MM.DD` release.
- Pre-releases older than 90 days are pruned automatically.

## License

LGPL 2.1 — see `LICENSE`.
