# The ride model: control ownership and netcode

This is the half of the mod that is *not* pure math — how a player mounts a bike, how their WASD
reaches the vehicle, and who is authoritative over where the bike actually is. Getting this wrong is
how rideable-vehicle mods end up rubber-banding or getting players kicked, so it is written down.

## Mounting

`ItemBike` casts a bike out in front of the player (a raytrace + `world.spawnEntity`, the vanilla boat
gesture). `EntityBike.processInitialInteract` puts a right-clicking, non-sneaking player on the bike
via `player.startRiding(this)` — server-side only; Forge replicates the mount to clients.

## Control ownership — the one decision everything hangs on

`EntityBike.getControllingPassenger()` returns the rider. **This is deliberate and it is the opposite
of RCMC's coaster car**, which leaves that method returning `null` because a coaster rider has no
control input.

Returning the rider has two consequences, both of which we *want*:

1. The vanilla client recognises the local player as driving a vehicle and starts sending
   **`CPacketVehicleMove`** — the client simulates the bike each frame/tick and tells the server
   where it ended up. That is how WASD (`rider.moveForward` / `moveStrafing`, which are only
   authoritative on the controlling client) actually moves the bike.
2. The server runs its **"Vehicle moved too quickly!"** check on the controlling passenger's vehicle.
   If a vehicle-move packet reports too large a delta, the server rejects it and snaps the vehicle
   back — visible as a hard rubber-band.

Both client and server run `EntityBike.onUpdate` with the *same deterministic* `BikePhysics`, so the
server's own simulation and the client's reported position agree, and the correction is a no-op in
the common case. The determinism from the handling model is what makes this work.

## The "moved too quickly" trap, and why the MVP is safe

The server's threshold is on the **per-packet** movement delta. A bike tops out around **7 blocks/s ≈
0.35 blocks/tick**, an order of magnitude under anything that trips the check, so the MVP needs no
mitigation. This is *not* free for future variants: a fast e-bike or a launched scooter approaching
the threshold would start rubber-banding. The escape hatches (documented at length in the master
plan's platform-constraints appendix, inherited from RCMC's analysis of the same 1.12.2 code) are, in
order of preference: keep top speeds sane; sub-step the *entity* move so no single packet is large; or,
only if truly needed, the coaster car's trick of not being the controlling passenger — which a
*driven* vehicle cannot use without losing its controls.

## Authority and sync, staged

- **MVP:** the controlling client is authoritative over its own bike via `CPacketVehicleMove`; other
  clients see it through the entity tracker (`tracker(80, 3, true)` — boat-class range, velocity on,
  so non-riders interpolate smoothly). Good enough to ride and to watch someone ride.
- **Phase 2 — config sync:** `LdibConfig.physics` values change simulation *results*. If a client's
  gravity/drag/accel differ from the server's, its prediction diverges and it rubber-bands. The
  server must push its `physics` config to clients on join. Until that ships, servers and clients must
  run identical configs.
- **Later — ridden-entity rendering:** at speed, vanilla's per-render-chunk culling can skip the
  entity you are riding *on*; RCMC hit this and fixed it with a `RenderWorldLastEvent` redraw. Expect
  to need the same once bikes get fast or long.

## Client-side discipline

`RenderBike` and `ModelBike` are the only bike classes that touch `net.minecraft.client`, and they are
reached exclusively through `LdibClientProxy`. A dedicated server loads `EntityBike`, `ItemBike` and
the physics package with no client type anywhere on the path — the CI server smoke test boots a real
server to prove exactly that, because this class of bug compiles cleanly and only dies at server boot.
