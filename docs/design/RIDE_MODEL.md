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
  clients see it through the entity tracker (`tracker(80, TRACKER_UPDATE_INTERVAL, true)` —
  boat-class range). Good enough to ride; **not** good enough to watch someone ride, see below.
- **Phase 2 — config sync:** `LdibConfig.physics` values change simulation *results*. If a client's
  gravity/drag/accel differ from the server's, its prediction diverges and it rubber-bands. The
  server must push its `physics` config to clients on join. Until that ships, servers and clients must
  run identical configs.
- **Later — ridden-entity rendering:** at speed, vanilla's per-render-chunk culling can skip the
  entity you are riding *on*; RCMC hit this and fixed it with a `RenderWorldLastEvent` redraw. Expect
  to need the same once bikes get fast or long.

## Watching someone else ride

The first version of this was bad in a way worth recording, because every piece of it looked right in
isolation.

An observing client ticks a remote player's bike exactly like any other entity, so `EntityBike.onUpdate`
ran, stepped `BikePhysics`, and derived motion — from `rider.moveForward` and `rider.moveStrafing`,
**which are only ever populated on the riding client**. So the model ran with zero input on a bike whose
speed had also never been synced, concluded the bike was parked, and left it exactly where it was. Then
the tracker arrived and `Entity.setPositionAndRotationDirect` — whose base implementation is a bare
`setPosition` + `setRotation`, with none of the interpolation `EntityLivingBase` and the vanilla
vehicles override in — teleported it a full block. Six times a second, on a bike that was motionless in
between. No amount of render-side interpolation can rescue that; the entity really was standing still.

Two changes, together:

1. **Sync the speed** (`EntityBike.SPEED`, quantised by `SPEED_SYNC_STEP`). An observer can then
   integrate `(speed, heading)` exactly as the server does and keep the bike *moving* between updates.
   It also fixes things that were quietly reading zero on every screen but the rider's: wheel spin
   and the riding sound.
2. **Treat a tracker update as an error, not a destination.** `setPositionAndRotationDirect` stores
   the difference; `applyServerCorrection` folds it in over `TRACKER_UPDATE_INTERVAL` ticks, so one
   correction finishes just as the next update lands. Beyond `CORRECTION_SNAP_DISTANCE` it snaps
   instead — sliding smoothly across ten blocks would be stranger to watch than a cut.

**And a lesson the second change taught the hard way.** Deferring the correction to the bottom of
`onUpdate` moved *when* `rotationYaw` changes on an observing client, and the cosmetic lean — which
reads `rotationYaw - prevRotationYaw` and sat above the correction — silently lost its only input
there. Remote bikes stopped leaning and stopped turning their bars, and nothing failed loudly: the
number just became zero, on every screen but the rider's. The lean/steer easing now lives in
`updateCosmeticLeanAndSteer()`, called last, with the ordering requirement written on the method. Any
future code that reads a per-tick yaw delta has the same trap waiting for it.

The dividing line is `simulate = !world.isRemote || canPassengerSteer()`: the server and the rider's own
client predict, everyone else follows. `canPassengerSteer()` is vanilla's own "is this the local
player's vehicle" test and is ordinary common code (the vanilla boat uses it) — it is false on a
dedicated server for *every* bike, which is why it is paired with the `isRemote` check rather than used
alone.

## Looking around while riding — and why it lives on the frame, not the tick

`updatePassenger` originally assigned `passenger.rotationYaw = this.rotationYaw`. That tracked steering
perfectly and made looking around impossible: the assignment ran every tick and ate whatever the mouse
had done since the last one.

The obvious fix — nudge the yaw from `updatePassenger` by deltas instead of assigning it — was wrong
too, and in-game testing found it immediately. **Mouse look is per-frame**: `EntityRenderer.updateCameraAndRender`
calls `player.turn(...)` once a frame and renders the world in the same frame, while `updatePassenger`
runs on the 20 Hz tick. Two rates, in conflict, producing two distinct artefacts:

- **Recentring juddered** — a pull applied 20×/s to a camera redrawn 100+×/s is a staircase, and it
  read as stutter right next to the mouse's own smoothness.
- **The look limit bounced** — the mouse carried the view past the stop every frame and the tick-rate
  clamp yanked it back 20×/s. That is an oscillator, not a wall.

So the whole job moved onto the frame, into `client/RiderLook` on `EntityViewRenderEvent.CameraSetup`
— which fires from `orientCamera`, *after* that frame's mouse turn and before the camera rotation is
applied. Nothing runs between the rider's input and the clamp, so the limit is a genuine hard stop, and
recentring is a continuous exponential decay in real seconds (frame-rate independent) rather than a
staircase in ticks. `RiderLook` also feeds the corrected angle back via `event.setYaw`, since the event
was built from the yaw as it stood a moment earlier.

**The offset is the state, not the yaw.** `RiderLook` keeps the rider's angle away from the bike's
heading and derives the absolute yaw from it each frame. That is what makes the view follow a turn:
re-deriving the offset from the absolute yaw would have it shrink by exactly the bike's heading change,
leaving the rider staring at a fixed point in the world while the bike turned underneath them. Only the
*mouse's* contribution is folded in — the difference between the yaw now and the yaw `RiderLook` wrote
last frame — and the bike's heading arrives already interpolated for the frame, which is the other half
of why a turn is smooth.

What stays on the tick is only what genuinely belongs to the bike: `setRenderYawOffset` keeps the
rider's *body* square with it, so looking over your shoulder turns your head rather than swivelling you
out of the saddle.

Two limits shape it, and they are deliberately different kinds of thing:

- `EntityBike.MAX_LOOK_YAW` (100°, a shade under the vanilla boat's 105) is a **constant**. The riding
  client stops itself at it per frame; the **server** clamps to it in `updatePassenger` so the limit is
  enforced by the authority rather than trusted to a client. A client and server that disagreed about
  it would fight over the rider's yaw. Observing clients deliberately do *not* re-clamp — a remote
  rider's yaw arrives already clamped, and re-clamping against a bike heading a tick behind would only
  jitter their head.
- `LdibConfig.viewRecenterStrength` is **client-only** and safe to differ per player, because the drift
  only ever moves a view *toward* the heading — strictly inside the shared limit, so the server can
  never have cause to reject it. It scales with speed, reaching zero at a standstill: stopped, you look
  where you like and stay there; at speed your eyes come back to the road.

Nothing here feeds back into movement. The bike steers from `moveStrafing`, never from where the rider
is looking.

## Client-side discipline

`RenderBike` and `ModelBike` are the only bike classes that touch `net.minecraft.client`, and they are
reached exclusively through `LdibClientProxy`. A dedicated server loads `EntityBike`, `ItemBike` and
the physics package with no client type anywhere on the path — the CI server smoke test boots a real
server to prove exactly that, because this class of bug compiles cleanly and only dies at server boot.
