# LDIB Documentation

| Document | What it covers |
| --- | --- |
| [`design/HANDLING.md`](design/HANDLING.md) | The bike handling model: state, forces, integrator, tuning |
| [`design/RIDE_MODEL.md`](design/RIDE_MODEL.md) | How riding works: control ownership, netcode, the "moved too quickly" trap |
| [`AGENT-PLANS/`](AGENT-PLANS/) | **Gitignored.** Phased implementation plan and agent working notes |

## Conventions used across these docs

- **Blocks are metres.** A bike doing "7" is doing 7 blocks/second, treated as 7 m/s (~25 km/h — a
  brisk cycling pace). Minecraft's world scale is roughly 1 block ≈ 1 m for the things that matter
  here (rider eye height, vehicle length), and pretending otherwise buys nothing.
- **Speed is in blocks/second** inside `physics`; the entity converts to blocks/**tick** exactly
  once, at the tick boundary, via `LdibConstants.SECONDS_PER_TICK` (20 ticks/s).
- **Heading is a Minecraft yaw** in degrees (0 = +Z/south, increasing clockwise), so the physics and
  the entity share one angle convention with no conversion.
- **Throttle and steer are normalised** to `[-1, 1]`: throttle `+1` full pedal / `-1` full brake;
  steer `-1` hard left / `+1` hard right.
