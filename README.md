# LDIB — Limitless Development: Immersive Biking

A Minecraft **1.12.2 Forge** mod that adds **rider-controlled bikes**: mount one and pedal, brake
and steer it with WASD, using the vanilla riding mechanic rather than a retextured pig or minecart.

The first release is a single blocky, voxel-style **bicycle** done properly — real acceleration,
coasting, braking and speed-dependent steering. Planned from there: a couple of bike models,
**e-bike** variants, and dockless-scooter rideables in the spirit of **Bird** and **Lime**. The
scope stays deliberately focused: a handful of rideables that feel good, not a general vehicle
framework.

> **Status: pre-alpha.** The repository is scaffolded, **`./gradlew build` is green** (compiles
> against Forge 1.12.2, all handling-model unit tests pass, jar produced), and a first rideable
> vertical slice (item → entity → renderer) is in place. It has **not yet been run in-game** — the
> next step is a `runClient` play-test to tune the renderer and confirm the ride. See
> `docs/AGENT-PLANS/MASTER_PLAN.md` (local only, gitignored) for the phased roadmap and the exact
> "done / not done" state.

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
├── Ldib, LdibConfig, LdibRegistry, LdibTab, Ldib*Proxy   # Forge plumbing
├── physics/     # bike handling model — pure Java, ZERO Minecraft types
│   ├── BikeState                  # immutable (speed, heading)
│   ├── BikeTuning                 # per-variant handling constants
│   └── BikePhysics                # semi-implicit Euler step; testable on a bare JVM
├── entity/
│   └── EntityBike                 # the rider-controlled vehicle (common; server loads it)
├── item/
│   └── ItemBike, LdibItems        # places the bike, boat-style
└── client/render/                 # client-ONLY: RenderBike + ModelBike (blocky)
```

**The load-bearing constraint:** `physics` contains no Minecraft types. That keeps the part most
likely to be subtly wrong — acceleration, braking, steering feel — testable on a bare JVM
(`./gradlew test` runs in seconds), with assertions like "braking stops sooner than coasting" and
"a parked bike does not turn on the spot". Convert to Minecraft types at the entity boundary only.

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
