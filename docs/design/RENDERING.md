# Rendering the rideables

How a bike gets onto the screen, why it is built the way it is, and how to check a change to it.

## The shape of it

```
client/render/mesh/      the models. Pure Java: no Minecraft client type.
  MeshBuilder            quads under a transform stack; boxes, bars, surfaces of revolution
  Mesh                   one rigid group's finished quads
  Wheels                 round wheels: spoked (bicycle), disc (scooter), fat (one-wheel)
  BikeModel …            a model = a few meshes + the transforms that pose them
  Skin / Material        which rectangle of the skin atlas is which material
  Canvas                 what a model draws onto
  RideableModels         variant → model (the one class here that names a Minecraft-side type)

client/render/
  GlCanvas               Canvas in OpenGL: display lists, and the light pass
  RenderBike             the entity renderer
  TileEntityBike*Renderer  bikes parked in racks and docks
```

A model draws onto a `Canvas`. In the game that is `GlCanvas`; in the test sources there is a second
one, `MeshPreview`, that rasterises to a PNG with no game running. That split is the reason the models
are pure Java: a change to a model can be looked at in seconds, and its geometry can be unit-tested.

## A model is a few rigid groups

A bicycle has only a handful of things that move relative to one another: the frame, the steering
assembly, two wheels, the crank and two pedals. Each is one `Mesh`, compiled into one display list the
first time it is drawn and replayed with one call after that. The two wheels are the same list drawn
under two transforms.

The first models were vanilla-style, one `ModelRenderer` per box, each drawn with its own matrix push,
translate, rotate and list call: about sixty per bicycle, thirty-eight of them in the two wheels. On
this client the frame is limited by the CPU cost of issuing draw calls, not by how many triangles the
GPU is handed, so that count was the whole cost of a bike. Measured with 256 parked rideables in view
(854×480, vsync off, old and new paths toggled in one session, A/B/A):

| | frame rate | time drawing the 256 | per rideable |
| --- | --- | --- | --- |
| one call per box | 76 fps | 12.2 ms | 48 µs |
| one call per group | 310 fps | 2.5 ms | 10 µs |

The new bicycle has roughly three times the quads of the old one. It is five times cheaper to draw.
**Detail is nearly free; draw calls are not.** `RideableMeshTest` holds every model to eight calls.

## Round wheels

A wheel is built from surfaces of revolution (`MeshBuilder.revolve`): the tread, its shoulders, the
sidewalls and the rim are each a ring of 32 segments with normals that follow the curve, so it is lit
as one round surface. A perfectly round wheel shows no sign of turning, so every wheel carries
something that goes round with it: spokes, a valve and two amber reflectors on a bicycle wheel,
alternating light and dark sectors on a solid one, and tread lugs on all of them.

The entity turns one wheel angle for every rideable, at the rate of a 0.4-block wheel. Each model
scales that by `0.4 / its own wheel radius`, so every tread moves at ground speed. The crank is geared
off the unscaled angle and is the same for every model, which keeps the rider's legs on the pedals.

## The rider comes first

A rider is placed at the entity's origin, with their hips about 13.6 px above the ground, and that
placement is gameplay (camera height, dismounting) and not this package's to move. So the bicycle is
built around it: saddle under the hips, grips where the hands come down, bottom bracket under the
saddle where a straight leg can follow the pedals, and the wheels wherever that leaves them — the
rear one just behind the saddle and the front one well out ahead, as on a real bike. The first model
was centred on the origin instead, which put its saddle 5 px behind and 5 px above the rider.

Because the bicycle is not centred on its own origin, a parked one is slid back by
`RideableModel.parkingShift()` so that the midpoint between its axles sits on the rack or dock slot.

The rider's own pose is `client/RiderPoseHandler`. Rideables never use the vanilla sitting pose
(`EntityBike.shouldRiderSit` is false): that pose is a minecart passenger's, legs straight out in
front. The handler turns the legs with the crank and puts the hands on the grips. It does this by
swapping the player model's limbs for stand-ins during that player's render, because limb angles set
from a render event are overwritten before the model draws. Armour is drawn by models it cannot
reach, so leggings and sleeves stay in the standing pose.

## The skin

Skins are painted per material, not per box: a few rectangles on a 128×64 atlas, listed in `Skin` and
painted by `tools/gen_skins.py`. Those two tables must agree. Every face samples inside the rectangle
of the material it is made of, so a new livery is a new set of colours and no model changes. To add a
material, add the rectangle to both, in unpainted space at least 4 px from its neighbours, and re-run
the script; it rewrites every skin, and existing rectangles come out byte-identical.

## Lights, and the bug they are suspected of causing

A lit lens is drawn full-bright and its halo is added to the scene. Both need GL state that ordinary
entity drawing does not: lighting off, additive blending, no depth writes.

`GlStateManager` skips any call its own cache says would change nothing. That cache is only right
while every renderer goes through it. In a modded game it can already disagree with the real GL state
by the time a bike is drawn: with CSM blocks beside a dock, a probe found the dock renderer entered
with the cached and real blend factors different. The old light pass changed state *through* the
cache, and only on frames where a light was lit, and the same probe saw it rewrite the cache on lit
frames and leave it alone on dark ones. Anything drawn next would then see a different cache from
frame to frame. That is the suspected cause of issue #1 (blocks near a bike flashing in time with its
tail light). An earlier fix that restored the blend function afterwards did not cure it, which fits:
it restored the state, not the cache.

What is not established: the flashing was only seen in the Alto pack, which runs OptiFine, and it did
not reproduce in a development client with either the old or the new light pass. So the mechanism is
measured, and its link to the visible symptom is an inference.

So the light pass in `GlCanvas` never touches `GlStateManager`. It saves the real state with
`glPushAttrib`, changes it with raw GL calls, and restores it with `glPopAttrib`. Afterwards the state
and the cache are both exactly as they were found, lit frame or not — which removes this pass as a
possible cause whatever the full story turns out to be. **Do not convert those calls to
`GlStateManager`.**

## Checking a change

- `./gradlew test` runs `RideableMeshTest`: winding, texels, roundness, draw-call budget.
- `MeshPreview` (see its class comment for the two commands) writes a six-view contact sheet per
  model to `tools/preview/`. Use it for shape, proportion and texel placement.
- In game, measure with MCMCP's `client_frame_stats` (`renderWork`) and `client_profile_rendering`,
  the way the sibling CSM mod does: vsync off and no frame cap, a fixed camera, six seconds of warm-up,
  and both versions in the same session, because a restart alone moves the numbers by several percent.
- After any change to a bicycle's layout, look at a ridden one from the side and the front (F5):
  hips on the saddle, hands on the grips, feet at the pedals. Then look at one in a dock and one in
  each rack style; `parkingShift()` is what keeps those where they were.
