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

## Kerbs, slabs and whole blocks

The physics world is flat, but the world the entity moves through is not, and "flat" must not mean
"stops dead at a 1/16-block lip". `EntityBike` sets vanilla's `stepHeight` (config `physics.stepHeight`,
default **1.0**), so a rideable rolls over kerbs, slabs, the shallow graded blocks road mods such as
Furenikus' Roads build hills out of — and, at a full block, the ordinary one-block rises a survival
world is made of. That is the difference between a machine for roads someone built for you and one you
can actually get about on. It costs the tidy old justification for the number (0.6 was the player's own
step height, so "a bike goes wherever its rider could walk"); a bike now climbs things its rider
cannot, which is a fair description of a bike.

The climb itself is `Entity.move()`'s own step-up, not new physics: the bounding box simply appears on
top of the obstacle. It is also why the wall-collision speed penalty is only applied on a *real* stop —
a lip that gets stepped over never sets `collidedHorizontally` at all.

`stepHeight` answers **reachability**: can the bike get up this at all. Effort is a separate question
with two separate answers, because the world offers two different kinds of climb.

### Steps cost energy; slopes cost force

A vanilla step-up is silent and instantaneous — you arrive on top at the speed you arrived at the
bottom. Free at 0.6, absurd at 1.0: a rideable that clears a block for nothing is a rideable that
ignores terrain. So a step is billed, by `BikePhysics.afterStepUp`, and **it is deliberately not part
of `step()`**. Everything in there is a rate acting over `dt`; a lip is an *event* that happens in
whatever fraction of a tick the wheel meets the face, and folding it into the per-second terms would
make the answer depend on `physicsSubSteps`.

The charge is kinetic energy — `v² -= stepClimbSpeed² · rise` — and choosing energy rather than speed
is what makes two things riders expect fall out of one number instead of a table:

- **A taller lip costs disproportionately more.** The cost lands in `v²` and the speed comes back out
  through a square root, so a bicycle at its 7 blocks/s cruise gives up about an eighth of its speed to
  a slab and about a third to a whole block — not twice as much, *more* than twice as much. That
  asymmetry is the whole point of the mechanic.
- **Momentum helps you over a kerb.** A fixed number of joules is a small tax on a rider with speed and
  a wall to one crawling at the lip, exactly as on a real bicycle.

`stepClimbSpeed` is deliberately larger than the `√(2gh)` a frictionless ramp would ask for: a wheel
striking a vertical face is a collision, and most of what it takes goes to heat rather than to height.
`stepClimbRetain` floors the result at a fraction of the incoming speed, and that floor is not a
rounding detail — a scooter at 5.4 blocks/s has nowhere near a full block's worth of energy to spend,
so without it *every* block-high rise would halt it and a hillside would be a series of standing
starts. In practice `stepClimbSpeed` decides how a bicycle and an e-bike climb and `stepClimbRetain`
decides how a scooter and a one-wheel do; at bicycle speeds the floor is never consulted.

### Telling a step from a slope

Both live on `EntityBike`, and they must not bill for the same centimetre. A grade is charged
continuously inside `step()` via `Terrain.grade`, measured by `updateGrade()` from *this same rise* —
so charging the whole tick's rise as a step would double-bill every graded road, and the deployment
target grades its hills in sixteenths of a block.

The split falls straight out of the clamp `updateGrade()` already applies. The steepest slope the model
is ever told about is `maxGrade`, so `maxGrade · run` is the most rise a *slope* can account for over
the distance just travelled, and everything above that line arrived as a step:

```java
stepRise = dy - maxGrade * run          // EntityBike.applyStepClimbCost()
```

One constant, two meanings, no double-billing, and — like `updateGrade()` itself — no need to ask a
single block how tall it thinks it is. Consequences worth knowing rather than rediscovering:

- A Fureniku 1/16 grade snap is fully absorbed by the allowance at every riding speed, so graded roads
  stay free. `MIN_STEP_RISE` (0.15) holds that true for a rider crawling up one, where there is barely
  any run to allow against.
- A vanilla staircase is a 1-in-2 grade, steeper than `maxGrade`, so part of every stair is billed as a
  step. That is the intended reading: a staircase is not a road.
- The faster you go the more run there is, so the more of a lip the allowance absorbs. Carrying speed
  at a kerb helps twice over, here and in the energy sum, and both point the way a real bicycle does.
- Rise is measured over a whole tick, so two lips climbed in one tick are charged as one taller one.
  Energy adds, so that is the same answer rather than an approximation of it.

Charged only where the handling model is actually run — a spectator's copy owns none of its speed and
must not invent a kerb the server never charged for.

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
