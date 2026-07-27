# Bike handling model

The whole point of `com.micatechnologies.minecraft.ldib.physics` is that it is **plain Java with no
Minecraft types**, so the part of the mod most likely to be subtly wrong — how a bike accelerates,
coasts, brakes and turns — is unit-tested on a bare JVM in milliseconds. This document is the "why"
behind that code.

## State

A bike's simulated state is two scalars, held immutably in `BikeState`:

- **`speed`** — ground speed along the current heading, blocks/second. **Signed**: negative is the
  rider walking the thing backwards, capped hard by `maxReverseSpeed`.
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

1. **Longitudinal input.** What W and S mean depends on which way the bike is already rolling, which
   is how one key does two jobs. Pushed *against* the direction of travel, throttle is the **brake**
   (`brakeDeceleration`, a separate and stronger authority than coasting), clamped so it stops
   *exactly* at zero rather than sailing through. Pushed from rest it **drives**: forward at
   `pedalAcceleration`, backward at the much weaker `reverseAcceleration`. So holding S is two felt
   phases — brake to a halt, then start shuffling backwards — not one lurch through the middle.
2. **Rolling resistance.** `speed ·= exp(−rollingResistance · dt)`. Exponential decay so the result
   is **timestep-independent** — sub-stepping does not change the coast-down curve. Decaying toward
   zero is already correct for a reversing bike.
3. **Air drag.** `speed −= airDrag · speed · |speed| · dt`. Quadratic, so it dominates near the top
   end and is what makes top speed finite and a bit soft rather than a hard clamp. Written with
   `v·|v|` rather than `v²` so it stays a *resistance*: the squared form is positive whichever way
   you are going and would shove a reversing bike ever faster backwards.
4. **Clamp** to `[−maxReverseSpeed, maxSpeed]`. `maxSpeed` is a safety ceiling, not the design target
   — the natural top speed is the powered equilibrium where pedal thrust balances drag
   (`BikePhysics.poweredEquilibriumSpeed`). `maxReverseSpeed` *is* a design target: backing up is a
   shuffle and is meant to feel like one.
5. **Steering.** Only if moving. Achievable steer rate is
   `maxSteerRateDegPerSec · falloff / (falloff + |speed|)` — full authority when crawling, and it
   tapers as you speed up, so fast riding is shallow and stable and slow riding is nimble. A parked
   bike does not turn on the spot. Reversing flips the sign: bars left swings the rear right, the
   same way a car reverses.

## Integrator

Semi-implicit (symplectic) Euler, the same choice RCMC makes: update **speed first**, then heading,
then let `EntityBike` derive the position step from the *new* speed. Explicit Euler pumps energy into
oscillating systems; symplectic does not. It also matters for feel — braking takes effect this tick,
not next.

`physicsSubSteps` (config, default 2) splits the 50 ms tick into finer `dt` slices. One 50 ms step is
coarse for steering at speed; sub-stepping is the cheap fix and costs integrator time only, never
bandwidth.

## Tuning and variants

`BikeTuning` is the nine numbers that make one rideable feel like another. `BikeTuning.defaultBicycle()`
is the MVP baseline and the fixed point the test suite pins behaviour against. Variants are **data,
not code**:

| Variant | Intuition | Which knobs move |
| --- | --- | --- |
| Pedal bicycle | baseline | — |
| E-bike | higher assisted top speed, brisker off the line | ↑ `maxSpeed`, ↑ `pedalAcceleration` |
| Bird/Lime scooter | lower top speed, twitchier, weaker brakes | ↓ `maxSpeed`, ↑ `maxSteerRate`, ↓ `brakeDeceleration` |

Adding a variant should mean adding a `BikeTuning` factory (and later reading it from the entity/item),
not a new movement code path. See the master plan, "Variants are data".

Note the two reverse numbers are **shared across every variant** (one pair of config values, threaded
through all five factories) and are deliberately excluded from `withAssist`. Backing up is the rider
shuffling the thing with their feet on all of them; a motor does not help with that, and a flat
battery does not make you worse at it.

## Kerbs, slabs and road hills

The physics world is flat, but the world the entity moves through is not, and "flat" must not mean
"stops dead at a 1/16-block lip". `EntityBike` sets vanilla's `stepHeight` (config `physics.stepHeight`,
default **0.6** — the player's own value), so a rideable rolls over anything its rider could have
walked over: kerbs, slabs, and the shallow graded blocks road mods such as Furenikus' Roads build
hills out of. Those were previously walls, which is a bad look on a road bike on a road.

That is `Entity.move()`'s own step-up doing the work, not new physics — the handling model never learns
about it, and speed is unchanged by the climb. It is also why the wall-collision speed penalty is only
applied on a *real* stop: a lip that gets stepped over never sets `collidedHorizontally` at all.

## What is deliberately **not** modelled (yet)

- **Gravity along the ride.** Vertical motion is the entity's job (`EntityBike` applies gravity and
  lets `move()` follow terrain). Speed does not yet bleed uphill or build downhill — a graded road is
  climbed at the same effort as a flat one. That is a named later phase, and it is a different thing
  from the step-up above, which is about *reachability*, not effort.
- **Lean / countersteer.** Purely cosmetic for now, and it belongs in the renderer, not here.
