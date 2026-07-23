# Bike handling model

The whole point of `com.micatechnologies.minecraft.ldib.physics` is that it is **plain Java with no
Minecraft types**, so the part of the mod most likely to be subtly wrong — how a bike accelerates,
coasts, brakes and turns — is unit-tested on a bare JVM in milliseconds. This document is the "why"
behind that code.

## State

A bike's simulated state is two scalars, held immutably in `BikeState`:

- **`speed`** — forward ground speed along the current heading, blocks/second, never negative.
- **`headingDegrees`** — a Minecraft yaw (0 = south, clockwise-positive).

Everything else the game shows — world position, `motionX/Z`, the rider's seat, wheel spin — is a
downstream function of those two, computed by `EntityBike` at the tick boundary. There is no separate
"position" in the physics model; position is the integral of `(speed, heading)`.

Why so little state? Because a bike ridden with WASD has, at most, two things the rider controls:
how fast, and which way. Modelling only those keeps the simulation **deterministic** — the same
inputs give the same outputs on the client and the server, bit for bit — which is what lets the
riding client predict its own motion smoothly under Minecraft's netcode. A rigid-body model with 6
degrees of freedom would be nondeterministic across machines and would fight that prediction.

## Forces (per step)

`BikePhysics.step(state, throttle, steer, tuning, dt)`:

1. **Longitudinal input.** `throttle > 0` adds `pedalAcceleration · throttle · dt`. `throttle < 0`
   adds `brakeDeceleration · throttle · dt` (negative, so it subtracts) — braking is a separate,
   stronger authority than coasting, and it can never push a stopped bike backwards.
2. **Rolling resistance.** `speed ·= exp(−rollingResistance · dt)`. Exponential decay so the result
   is **timestep-independent** — sub-stepping does not change the coast-down curve.
3. **Air drag.** `speed −= airDrag · speed² · dt`. Quadratic, so it dominates near the top end and is
   what makes top speed finite and a bit soft rather than a hard clamp.
4. **Clamp** to `[0, maxSpeed]`. `maxSpeed` is a safety ceiling, not the design target — the natural
   top speed is the powered equilibrium where pedal thrust balances drag
   (`BikePhysics.poweredEquilibriumSpeed`).
5. **Steering.** Only if moving. Achievable steer rate is
   `maxSteerRateDegPerSec · falloff / (falloff + speed)` — full authority when crawling, and it
   tapers as you speed up, so fast riding is shallow and stable and slow riding is nimble. A parked
   bike does not turn on the spot.

## Integrator

Semi-implicit (symplectic) Euler, the same choice RCMC makes: update **speed first**, then heading,
then let `EntityBike` derive the position step from the *new* speed. Explicit Euler pumps energy into
oscillating systems; symplectic does not. It also matters for feel — braking takes effect this tick,
not next.

`physicsSubSteps` (config, default 2) splits the 50 ms tick into finer `dt` slices. One 50 ms step is
coarse for steering at speed; sub-stepping is the cheap fix and costs integrator time only, never
bandwidth.

## Tuning and variants

`BikeTuning` is the seven numbers that make one rideable feel like another. `BikeTuning.defaultBicycle()`
is the MVP baseline and the fixed point the test suite pins behaviour against. Variants are **data,
not code**:

| Variant | Intuition | Which knobs move |
| --- | --- | --- |
| Pedal bicycle | baseline | — |
| E-bike | higher assisted top speed, brisker off the line | ↑ `maxSpeed`, ↑ `pedalAcceleration` |
| Bird/Lime scooter | lower top speed, twitchier, weaker brakes | ↓ `maxSpeed`, ↑ `maxSteerRate`, ↓ `brakeDeceleration` |

Adding a variant should mean adding a `BikeTuning` factory (and later reading it from the entity/item),
not a new movement code path. See the master plan, "Variants are data".

## What is deliberately **not** modelled (yet)

- **Slopes / gravity along the ride.** The physics world is flat; vertical motion is the entity's job
  (`EntityBike` applies gravity and lets `move()` follow terrain). Speed does not yet bleed uphill or
  build downhill. That is a named later phase.
- **Reverse.** Throttle below zero brakes; it does not drive backwards.
- **Lean / countersteer.** Purely cosmetic for now, and it belongs in the renderer, not here.
