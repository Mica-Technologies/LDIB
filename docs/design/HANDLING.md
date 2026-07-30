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

`stepHeight` answers **reachability** — can the bike get up this at all. Effort is a separate question,
answered below by the grade; the two interact, because a step-up is a rise with almost no run and the
grade measurement has to be stopped from reading it as a cliff.

## Terrain: what the ground does to the ride

The model was written for a flat world with nothing underfoot. It no longer is, and the way that
landed is the part worth understanding: **`BikePhysics` still knows nothing about Minecraft.** The
entity looks at the world, reduces it to three plain numbers in a `Terrain`, and hands them in.

```java
BikePhysics.step(state, throttle, steer, tuning, terrain, dt)
```

The five-argument form still exists and delegates with `Terrain.FLAT`, so every caller and test that
predates hills is untouched and still passes.

| `Terrain` field | What it does |
| --- | --- |
| `grade` | rise/run **along the heading**; gravity along the slope becomes one more acceleration on `speed` |
| `rollFactor` | multiplies `rollingResistance` — the reason a made road is worth riding on |
| `gripFactor` | *not read by `step`* — folded into a derived tuning by `BikeTuning.withGrip` |

**Slope is signed against the heading, not the direction of travel.** A bike pointing up a hill reads
`+0.2` whether it is rolling forwards up it, coasting backwards down it, or stopped. That single
convention is why one gravity term covers climbing, stalling and rolling back with no special cases —
and why a rider who stops on a climb starts rolling back down for free.

**Grip goes on last, after `withAssist`.** Assist decides what the motor is offering; grip decides how
much of it the ground will accept. Composed the other way round, a fresh battery would quietly undo
the ice. Grip scales braking, steering and traction — never `maxSpeed`, because ice does not lower how
fast a bike can go, only your ability to get there and your options once you have.

**The ceilings still hold on a descent.** Letting gravity carry a rider past `maxSpeed` downhill would
be more fun, but `maxSpeed` is the margin against the server's "moved too quickly" kick and a long
hill is exactly where it would be spent. A descent means reaching top speed without pedalling. The
reverse ceiling now matters more than it did: it is what stops a bike abandoned facing uphill from
rolling away at ever-increasing speed.

### Measuring the slope, and why that is the hard part

The integrator change is half a dozen lines. The work is deciding what to tell it.

`EntityBike.updateGrade()` measures the grade from the movement each tick actually produced —
`rise / run`, with the run projected onto the heading — rather than probing block heights. That is
what makes it work on *any* terrain: vanilla slabs and stairs, a road mod's 1/16-graded hills, or
something nobody has written yet, with no knowledge of any block's height semantics. It costs one tick
of lag.

Three things stop that being naive, and all three are load-bearing:

- **Clamped to `maxGrade`.** A kerb step-up rises up to `stepHeight` in a couple of centimetres of run,
  which divides out to a cliff face. Unclamped, *every kerb in the world would stamp on the brakes*.
  The clamp is what makes "a step is not a slope" true, and it is a correctness guard rather than a
  taste knob.
- **Smoothed** (`gradeSmoothing`), because one tick of movement over slabs is a noisy signal.
- **Ignored in the air.** Falling is all rise and no run. The entity's own gravity already owns that,
  so the grade decays toward level rather than reporting a drop as an infinitely steep road.

### Surfaces

`integration/RoadSurfaces` maps a block's **registry name** to `(grip, rollFactor)` from a config
table. No mod dependency, hard or soft — which is not a compromise but the only option and the better
one: Fureniku's Roads exposes no API, no events, no IMC and no entity hooks at all, so there is nothing
to hook and nothing that can fight us, and a table of names works for road mods that do not exist yet.

Entries take one `*` wildcard, and that is necessary rather than decorative: road mods generate paint
blocks per colour and **the colour set is extensible at runtime** (Fureniku's ships white, yellow and
red; green and blue arrive from separate addons), so `furenikusroads:*_bike` is the only form that
stays correct. Markings are usually a separate non-colliding block sitting *on top* of the surface, so
the lookup tries the block underfoot and then the one below it.

The table is **synced to clients** alongside the numeric config, for the same reason the numbers are:
a client that thinks a road is gravel predicts a slower bike than the server is simulating, and
rubber-bands.

## What is deliberately **not** modelled (yet)

- **Lean / countersteer.** Purely cosmetic for now, and it belongs in the renderer, not here.
- **Weather.** Rain does not reduce grip. Nearly free now that grip exists — it is the same scalar.
- **Descent overspeed.** See the ceiling note above; revisit only alongside the fast-vehicle work.
- **Per-variant hill feel.** `slopeGravity` lives on `BikeTuning` so a variant *could* differ, but
  every factory passes the same config value today, because gravity on a slope is mass-independent.
