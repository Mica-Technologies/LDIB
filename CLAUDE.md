# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

LDIB ("Limitless Development: Immersive Biking") is a **Minecraft 1.12.2 Forge mod** (mod id:
`ldib`) that adds **rider-controlled bikes** — you mount one and WASD pedals, brakes and steers it
using the vanilla riding mechanic. The MVP is one blocky bicycle; e-bike variants and Bird/Lime-style
scooters (plus their docks) come later. It is **not** a general vehicle framework — a small number of
purpose-built rideables done well.

Build system is GregTechCEu Buildscripts (a RetroFuturaGradle wrapper), the same as the sibling mods
`RCMC`, `minecraft-city-super-mod` (CSM) and `uia-server-utility-mod` (SUM). RCMC in particular is
close prior art — it is also a rideable-vehicle physics mod — and this repo was scaffolded from it.

## Build Commands

Set `JAVA_HOME` to a **JDK 17–22** install before each `./gradlew` invocation. **21 is the
recommended sweet spot** (and what CI uses): RetroFuturaGradle wants the Gradle process on Java 21+,
and the pinned Gradle 8.9 officially supports running only on Java ≤ 22. The compiler and mod code
target **Java 8** via Jabel regardless — only the JVM that runs Gradle changes.

```bash
JAVA_HOME="..." ./gradlew build      # compile + unit tests + jar
JAVA_HOME="..." ./gradlew test       # unit tests only — pure JVM, seconds not minutes
JAVA_HOME="..." ./gradlew runClient  # dev client
JAVA_HOME="..." ./gradlew runServer  # dev dedicated server
JAVA_HOME="..." ./gradlew clean

# Apple Silicon: use the Rosetta path (see addon.gradle for the one-time setup it needs):
JAVA_HOME="..." ./gradlew runClient -Prosetta
```

Heap is `-Xmx3G` in `gradle.properties` for decompilation.

## Architecture

### The central design decision

**A bike ridden with WASD has two controllable degrees of freedom: forward speed and heading.** The
handling model simulates exactly those two scalars; world position, motion and the rider's seat are
downstream functions of them, applied at the tick boundary. It is a kinematic model, not a
rigid-body one — which keeps it deterministic across client and server (the ride needs that to feel
smooth) and cheap enough to step every ridden bike every tick.

### Layers

```
physics/     Handling. Pure Java, ZERO Minecraft types.
  BikeState                immutable (speed, heading)
  BikeTuning               per-variant constants (maxSpeed, accel, brake, drag, steer)
  BikePhysics              semi-implicit (symplectic) Euler step

entity/      EntityBike — the rider-controlled vehicle. Common code; a server loads it.
item/        ItemBike / LdibItems — places the bike, boat-style.
client/      RenderBike + ModelBike — client-ONLY, reached via LdibClientProxy.
Ldib*.java   Forge plumbing: @Mod class, config, registry, creative tab, proxies.
```

### Rules that are load-bearing — do not break these

1. **`physics` must never import a Minecraft type.** That is what makes the handling model
   unit-testable on a bare JVM (`./gradlew test` runs in seconds with no game instance). Convert at
   the entity boundary — if you need a `Vec3d`, build it in `EntityBike`, not in `physics`.

2. **Common code must never reach client-only classes.** `RenderBike` and `ModelBike` are reached
   only through `LdibClientProxy`. A stray `net.minecraft.client` import in common code compiles
   perfectly and only fails when a dedicated server boots — which is exactly what the CI server
   smoke test exists to catch. (SUM shipped three such bugs at once and took a server down.)

3. **`EntityBike` returns its rider as the controlling passenger, on purpose.** That is what makes
   the client send `CPacketVehicleMove` so WASD reaches the vehicle — the exact opposite of RCMC's
   coaster car, whose passive riders must *not* be controlling. It is also what exposes the bike to
   the server's "Vehicle moved too quickly!" kick, so keep bike speeds sane (the MVP tops out well
   under the threshold). See the platform-constraints appendix in the master plan before raising a
   variant's top speed.

4. **The integrator is symplectic on purpose.** `BikePhysics.step` updates speed first, then
   heading, then the caller derives position from the *new* speed. Keep it that way.

### Config

`LdibConfig` reads Forge `Configuration` into static fields at load time — never query
`Configuration` per-tick. Values under `physics` change movement *results*, so on a server the
server's copy is authoritative and must be synced to clients (a Phase-2 task); values under `client`
are presentation only and are never synced.

### Mixins — deferred

`usesMixins = false`. The MVP needs no coremod: a rider-controlled vehicle is entirely public-hook
territory. The first thing that may justify a mixin is client-side camera lean / a first-person
handlebar view. When that lands, flip `usesMixins` on, set `coreModClass` + `mixinsPackage`, and
restore the `reobfJar` manifest guard that RCMC/SUM carry (`addon.gradle` already has the guarded
`MixinConfigs` manifest hook). Until then, don't add a coremod.

## Conventions

- Package root: `com.micatechnologies.minecraft.ldib`
- Feature code lives in its own subpackage; event handlers register on `MinecraftForge.EVENT_BUS` in
  `Ldib.preInit()`.
- Registry names use the `ldib:` prefix.
- Never hard-code the mod id/name/version — use `LdibConstants`, which reads the build-generated
  `Tags` class. The version is derived from the latest git tag (`YYYY.MM.DD` for releases).
- Blocks are metres; time is seconds inside `physics`, converted at the tick boundary via
  `LdibConstants.SECONDS_PER_TICK`.

## Planning docs

`docs/AGENT-PLANS/` is **gitignored** — it holds the phased implementation plan and agent working
notes. `docs/AGENT-PLANS/MASTER_PLAN.md` is the 0→100 roadmap; read it before starting substantial
work, and update the phase checkboxes as things land. `docs/design/` holds the committed design notes
(the handling model, the ride/netcode model).

## CI

- `test-mod-build-pr.yml` — compile + unit tests, then a **dedicated-server smoke test** that boots a
  real server and greps for `Done (`. The second job is the one that catches side violations.
- `build-mod-release-pre-release-main.yml` — on push to `main`, tags and publishes a pre-release with
  checksums; `workflow_dispatch` with `release=true` cuts a full release.
- `cleanup-mod-pre-releases.yml` — prunes pre-releases older than 90 days.
