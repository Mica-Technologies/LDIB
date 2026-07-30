package com.micatechnologies.minecraft.ldib;

import com.micatechnologies.minecraft.ldib.physics.BikeTuning;
import java.io.File;
import net.minecraftforge.common.config.Configuration;

/**
 * Forge {@link Configuration}-backed settings, loaded once in
 * {@link Ldib#preInit(net.minecraftforge.fml.common.event.FMLPreInitializationEvent)}.
 *
 * <p>Values are read into static fields at load time rather than queried per-tick: the bike model
 * steps for every ridden bike every tick, and {@code Configuration.get(...)} does string lookups
 * and I/O bookkeeping that have no place in that path.</p>
 *
 * <p><b>Server authority.</b> Everything in the {@code physics} category affects movement results,
 * so on a multiplayer server it must match between client and server or a riding client will
 * visibly desync from the bike it is steering. The server's values are the truth; syncing them to
 * clients on join is a Phase-2 task (see docs/AGENT-PLANS/MASTER_PLAN.md). Client-only presentation
 * settings, when they exist, belong in a separate {@code client} category that is never synced.</p>
 */
public final class LdibConfig {

    public static final String CATEGORY_PHYSICS = "physics";
    public static final String CATEGORY_CLIENT = "client";
    public static final String CATEGORY_BIKESHARE = "bikeshare";

    /** Radius (blocks) around a kiosk within which docks belong to its station. */
    public static int shareStationRadius = 8;

    /** Flat fee to unlock a bike-share rental, in the economy's currency. 0 = no unlock fee. */
    public static double shareUnlockFee = 1.0D;

    /** Per-minute rate for a pedal bike, in the economy's currency. Default $1 / 5 min. */
    public static double shareRateBikePerMinute = 0.20D;

    /** Per-minute rate for an e-bike, in the economy's currency. Default $1 / 3 min. */
    public static double shareRateEbikePerMinute = 1.0D / 3.0D;

    /** Per-minute rate for a scooter (either speed), in the economy's currency. Default $1 / 4 min. */
    public static double shareRateScooterPerMinute = 0.25D;

    /** Whether to bill rentals through an installed economy mod (SUM) when any fee/rate is &gt; 0. */
    public static boolean shareUseEconomy = true;

    /** Top forward speed under pedal power, blocks/second. */
    public static double maxSpeed = 7.0D;

    /** Forward acceleration while pedalling/throttling, blocks/second². */
    public static double pedalAcceleration = 3.5D;

    /** Deceleration while braking, blocks/second². */
    public static double brakeDeceleration = 9.0D;

    /** Fraction of speed shed per second to rolling resistance while coasting (exponential decay). */
    public static double rollingResistance = 0.6D;

    /** Quadratic air-drag coefficient. */
    public static double airDrag = 0.010D;

    /** Maximum steering rate at low speed, degrees/second. */
    public static double maxSteerRateDegPerSec = 90.0D;

    /** Speed (blocks/s) at which steering authority has halved. */
    public static double steerSpeedFalloff = 5.0D;

    /**
     * Top backwards speed, blocks/second — shared by every variant, because backing up is a manual
     * shuffle on all of them (no bike or scooter has a reverse gear). Walking pace is 4.3, so this is
     * deliberately well under it.
     */
    public static double reverseMaxSpeed = 1.2D;

    /** Acceleration while backing up, blocks/second². Shared by every variant, for the same reason. */
    public static double reverseAcceleration = 2.0D;

    /**
     * How high a lip a rideable rolls straight over, in blocks — kerbs, slabs, and the shallow
     * height-graded blocks road mods build hills out of.
     *
     * <p>Defaults to <b>0.6</b>, which is not a bike-specific number: it is vanilla's own player step
     * height, so a bike goes over anything its rider could have walked over. That is the rule that
     * needs no explaining in-game. Drop it to 0.5 for "slabs and nothing taller", or to 0 to restore
     * the original behaviour where every lip is a wall.</p>
     */
    public static double stepHeight = 0.6D;

    /** E-bike assisted top speed, blocks/second. Faster than a pedal bike; still not a rocket. */
    public static double ebikeMaxSpeed = 11.0D;

    /** E-bike acceleration (motor assist), blocks/second². Brisker off the line than a pedal bike. */
    public static double ebikePedalAcceleration = 5.5D;

    /** Scooter top speed, blocks/second. ~12 mph (5.36 m/s) — a standard shared e-scooter. */
    public static double scooterMaxSpeed = 5.36D;

    /** Scooter acceleration, blocks/second². */
    public static double scooterAcceleration = 4.0D;

    /** Performance-scooter top speed, blocks/second. ~22 mph (9.84 m/s) — a fast private e-scooter. */
    public static double scooterFastMaxSpeed = 9.84D;

    /** Performance-scooter acceleration, blocks/second². Brisker off the line than the standard one. */
    public static double scooterFastAcceleration = 6.0D;

    /** Scooter braking, blocks/second². Small wheels = weaker brakes than a bike. */
    public static double scooterBrakeDeceleration = 6.0D;

    /** Scooter max steering rate at low speed, degrees/second. Twitchier than a bike. */
    public static double scooterMaxSteerRateDegPerSec = 120.0D;

    /** Speed (blocks/s) at which scooter steering authority has halved. Lower = twitchier at speed. */
    public static double scooterSteerSpeedFalloff = 3.5D;

    /**
     * One-wheel top speed, blocks/second. Deliberately the same ~12 mph as the standard e-scooter
     * (owner's call) rather than the ~20 mph a real self-balancing board manages — it is a city
     * rideable in the same speed class as the scooter, not a faster one.
     */
    public static double onewheelMaxSpeed = 5.36D;

    /** One-wheel acceleration, blocks/second². A hub motor directly under your feet pulls hard off the line. */
    public static double onewheelAcceleration = 5.0D;

    /**
     * One-wheel braking, blocks/second². Weaker than the scooter's: braking is the motor pushing the
     * one tyre back under you, and there is no brake lever to grab.
     */
    public static double onewheelBrakeDeceleration = 5.0D;

    /** One-wheel max steering rate at low speed, degrees/second. Nothing here turns tighter. */
    public static double onewheelMaxSteerRateDegPerSec = 150.0D;

    /**
     * Speed (blocks/s) at which one-wheel steering authority has halved. Higher than the scooter's
     * 3.5 on purpose — a board still carves at speed, where a scooter's small wheels get twitchy.
     */
    public static double onewheelSteerSpeedFalloff = 6.0D;

    /** Blocks a one-wheel travels under power on a full charge. 0 disables the battery. */
    public static double onewheelRangeBlocks = 3500.0D;

    /** Top speed of a one-wheel with a flat battery — walking it along at your side. Blocks/second. */
    public static double onewheelPushMaxSpeed = 1.6D;

    /** Acceleration of a one-wheel with a flat battery, blocks/second². */
    public static double onewheelPushAcceleration = 1.5D;

    /** Blocks an e-bike travels under power on a full charge. 0 disables the battery entirely. */
    public static double ebikeRangeBlocks = 6000.0D;

    /** Blocks a standard scooter travels under power on a full charge. 0 disables the battery. */
    public static double scooterRangeBlocks = 4000.0D;

    /** Blocks a performance scooter travels on a charge — fast and thirsty, so a shorter range. */
    public static double scooterFastRangeBlocks = 3000.0D;

    /** Charge fraction below which motor assist starts fading toward nothing. 0 = cut out abruptly. */
    public static double batteryReserveFraction = 0.15D;

    /** Top speed of a scooter with a flat battery — you can still kick it along. Blocks/second. */
    public static double scooterKickMaxSpeed = 2.2D;

    /** Acceleration of a scooter with a flat battery, blocks/second². Kicking is slow work. */
    public static double scooterKickAcceleration = 2.0D;

    // --- Terrain: slope and surface ------------------------------------------------------------

    /**
     * Gravity along a slope, blocks/second². <b>0 disables hill effort entirely</b> and restores the
     * pre-terrain behaviour where a graded road is climbed at the same effort as a flat one.
     * Deliberately not 9.81 — see {@link BikeTuning#slopeGravity}.
     */
    public static double slopeGravity = 4.5D;

    /**
     * Steepest slope the handling model will ever be told about, as rise over run. 0.35 is a 1-in-3
     * road: brutal, and far beyond anything a real one is built at.
     *
     * <p>This is a <b>correctness</b> guard, not a taste one. The grade is measured from how far the
     * bike actually rose over how far it travelled, and a kerb or slab step-up rises {@code stepHeight}
     * in a few centimetres of run — arithmetically a cliff. Without this clamp every kerb would read
     * as a mountain and stamp on the brakes.
     */
    public static double maxGrade = 0.35D;

    /**
     * How much of the way the measured slope moves toward its new reading each tick, {@code 0}–{@code 1}.
     * Lower is smoother and laggier. Terrain is sampled from one tick of movement, which is noisy over
     * slabs and stairs, so this is what turns a jittery signal into a hill you can feel.
     */
    public static double gradeSmoothing = 0.25D;

    /** Traction multiplier for any surface not named in {@link #surfaceGrip}. 1 = ordinary ground. */
    public static double defaultSurfaceGrip = 1.0D;

    /** Rolling-resistance multiplier for any surface not named in {@link #surfaceGrip}. */
    public static double defaultSurfaceRoll = 1.0D;

    /**
     * What each block feels like under a tyre: {@code registryName=grip,rollingResistanceMultiplier}.
     *
     * <p>Grip scales braking, steering and traction (not top speed); the roll multiplier scales how
     * fast you lose speed coasting, which is what makes a made road worth riding on. An entry may
     * contain one {@code *} wildcard — necessary rather than decorative, because road mods generate
     * paint blocks per colour and the colour set is extensible at runtime.</p>
     *
     * <p>Defaults name Fureniku's Roads blocks (the deployment target) alongside vanilla ones. Naming
     * a mod that isn't installed costs nothing: entries are matched by string, never resolved.</p>
     */
    public static String[] surfaceGrip = {
        "# registryName=grip,rollMultiplier   (grip: 1 = dry tarmac, lower = slippery)",
        "# One * wildcard allowed per entry. Unlisted blocks use defaultSurfaceGrip/Roll.",
        "furenikusroads:road_block_*=1.0,0.92",
        "furenikusroads:sidewalk*=1.0,1.0",
        "furenikusroads:*_bike=1.0,0.88",
        "furenikusroads:*_bike_icon=1.0,0.88",
        "minecraft:concrete=1.0,0.95",
        "minecraft:stone=1.0,1.0",
        "minecraft:stonebrick=1.0,1.0",
        "minecraft:grass_path=1.0,1.15",
        "minecraft:gravel=0.80,1.45",
        "minecraft:grass=0.90,1.55",
        "minecraft:dirt=0.90,1.45",
        "minecraft:sand=0.70,2.10",
        "minecraft:soul_sand=0.60,3.00",
        "minecraft:snow_layer=0.55,1.50",
        "minecraft:snow=0.55,1.50",
        "minecraft:ice=0.20,0.55",
        "minecraft:packed_ice=0.20,0.55",
        "minecraft:frosted_ice=0.20,0.55",
    };

    /**
     * Physics sub-steps per game tick. One 50 ms step is coarse for steering; sub-stepping is the
     * cheap fix and costs integrator time only, never bandwidth.
     */
    public static int physicsSubSteps = 2;

    /** Whether the live speed readout is drawn while riding. Pure convenience; never synced. */
    public static boolean enableRideHud = true;

    /**
     * How much of the bike's lean the rider's camera copies, {@code 0} (level horizon) to {@code 1}
     * (the full model lean). Presentation only and never synced — camera roll is a motion-sickness
     * trigger for some players, so this is exactly the kind of setting that must stay per-player.
     * Defaults to partial: enough to feel the turn, well short of the model's full 22°.
     */
    public static double cameraLeanStrength = 0.55D;

    /**
     * How hard a moving rider's view drifts back toward straight ahead, {@code 0} (never — look
     * wherever you like at any speed) to {@code 1} (snap forward the moment you stop turning, which is
     * how the bike behaved before free look existed). Scales a decay rate in <b>real seconds</b>, so
     * the drift home is the same speed whatever the frame rate; {@code client/RiderLook} applies it.
     *
     * <p>Presentation only and never synced, and it can afford to be: the pull only ever moves a
     * rider's view <i>toward</i> the heading, i.e. strictly inside the look limit
     * {@code EntityBike.MAX_LOOK_YAW} the server enforces, so a client that drifts differently from
     * its neighbours can never end up somewhere the server would reject.</p>
     */
    public static double viewRecenterStrength = 0.05D;

    private static Configuration config;

    private LdibConfig() {
        throw new AssertionError("No instances.");
    }

    public static void init(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    // --- Server → client sync ----------------------------------------------------------------
    //
    // Every value here changes movement RESULTS (or a client-side prompt derived from a server rule),
    // so on a dedicated server the server's copy is authoritative and is pushed to each client on join
    // (see PacketSyncConfig); the client restores its own values on disconnect. Keep captureSyncable()
    // and applySyncable() in lockstep — same values, same order. The client-only `enableRideHud` is
    // presentation and is deliberately NOT synced.

    /** Snapshot the syncable values (movement tuning + the station radius) in a fixed order. */
    public static double[] captureSyncable() {
        return new double[] {
            maxSpeed, pedalAcceleration, brakeDeceleration, rollingResistance, airDrag,
            maxSteerRateDegPerSec, steerSpeedFalloff, physicsSubSteps,
            ebikeMaxSpeed, ebikePedalAcceleration,
            scooterMaxSpeed, scooterAcceleration, scooterBrakeDeceleration,
            scooterMaxSteerRateDegPerSec, scooterSteerSpeedFalloff,
            scooterFastMaxSpeed, scooterFastAcceleration,
            shareStationRadius,
            ebikeRangeBlocks, scooterRangeBlocks, scooterFastRangeBlocks, batteryReserveFraction,
            scooterKickMaxSpeed, scooterKickAcceleration,
            reverseMaxSpeed, reverseAcceleration, stepHeight,
            onewheelMaxSpeed, onewheelAcceleration, onewheelBrakeDeceleration,
            onewheelMaxSteerRateDegPerSec, onewheelSteerSpeedFalloff,
            onewheelRangeBlocks, onewheelPushMaxSpeed, onewheelPushAcceleration,
            slopeGravity, maxGrade, gradeSmoothing, defaultSurfaceGrip, defaultSurfaceRoll,
        };
    }

    /**
     * The syncable <b>surface table</b>, which the {@code double[]} cannot carry.
     *
     * <p>It has to be synced for the same reason everything in {@link #captureSyncable()} does: what a
     * block does to a bike changes movement <i>results</i>, so a client whose table differs from the
     * server's predicts a different ride and rubber-bands. A client with no entry for a road the
     * server treats as fast is exactly the visible-desync case the config sync exists to prevent.</p>
     */
    public static String[] captureSyncableSurfaces() {
        return surfaceGrip.clone();
    }

    /** Apply a surface table from {@link #captureSyncableSurfaces()} and rebuild the lookup. */
    public static void applySyncableSurfaces(String[] entries) {
        if (entries == null) {
            return;
        }
        surfaceGrip = entries.clone();
        com.micatechnologies.minecraft.ldib.integration.RoadSurfaces.reload(surfaceGrip);
    }

    /**
     * Apply values captured by {@link #captureSyncable()} (same order) into the live fields.
     *
     * <p>Reads defensively through {@link #at}: a server running an older LDIB sends a shorter array,
     * and indexing straight into it would throw inside the login packet handler — turning "the server
     * has fewer settings than I do" into a failed join. Because the array is <b>append-only</b> by
     * convention, a short one is a valid prefix, so anything missing simply keeps this client's own
     * value. Never reorder {@link #captureSyncable()}; that assumption is what makes this safe.</p>
     */
    public static void applySyncable(double[] v) {
        if (v == null) {
            return;
        }
        maxSpeed = at(v, 0, maxSpeed);
        pedalAcceleration = at(v, 1, pedalAcceleration);
        brakeDeceleration = at(v, 2, brakeDeceleration);
        rollingResistance = at(v, 3, rollingResistance);
        airDrag = at(v, 4, airDrag);
        maxSteerRateDegPerSec = at(v, 5, maxSteerRateDegPerSec);
        steerSpeedFalloff = at(v, 6, steerSpeedFalloff);
        physicsSubSteps = (int) at(v, 7, physicsSubSteps);
        ebikeMaxSpeed = at(v, 8, ebikeMaxSpeed);
        ebikePedalAcceleration = at(v, 9, ebikePedalAcceleration);
        scooterMaxSpeed = at(v, 10, scooterMaxSpeed);
        scooterAcceleration = at(v, 11, scooterAcceleration);
        scooterBrakeDeceleration = at(v, 12, scooterBrakeDeceleration);
        scooterMaxSteerRateDegPerSec = at(v, 13, scooterMaxSteerRateDegPerSec);
        scooterSteerSpeedFalloff = at(v, 14, scooterSteerSpeedFalloff);
        scooterFastMaxSpeed = at(v, 15, scooterFastMaxSpeed);
        scooterFastAcceleration = at(v, 16, scooterFastAcceleration);
        shareStationRadius = (int) at(v, 17, shareStationRadius);
        ebikeRangeBlocks = at(v, 18, ebikeRangeBlocks);
        scooterRangeBlocks = at(v, 19, scooterRangeBlocks);
        scooterFastRangeBlocks = at(v, 20, scooterFastRangeBlocks);
        batteryReserveFraction = at(v, 21, batteryReserveFraction);
        scooterKickMaxSpeed = at(v, 22, scooterKickMaxSpeed);
        scooterKickAcceleration = at(v, 23, scooterKickAcceleration);
        reverseMaxSpeed = at(v, 24, reverseMaxSpeed);
        reverseAcceleration = at(v, 25, reverseAcceleration);
        stepHeight = at(v, 26, stepHeight);
        onewheelMaxSpeed = at(v, 27, onewheelMaxSpeed);
        onewheelAcceleration = at(v, 28, onewheelAcceleration);
        onewheelBrakeDeceleration = at(v, 29, onewheelBrakeDeceleration);
        onewheelMaxSteerRateDegPerSec = at(v, 30, onewheelMaxSteerRateDegPerSec);
        onewheelSteerSpeedFalloff = at(v, 31, onewheelSteerSpeedFalloff);
        onewheelRangeBlocks = at(v, 32, onewheelRangeBlocks);
        onewheelPushMaxSpeed = at(v, 33, onewheelPushMaxSpeed);
        onewheelPushAcceleration = at(v, 34, onewheelPushAcceleration);
        slopeGravity = at(v, 35, slopeGravity);
        maxGrade = at(v, 36, maxGrade);
        gradeSmoothing = at(v, 37, gradeSmoothing);
        defaultSurfaceGrip = at(v, 38, defaultSurfaceGrip);
        defaultSurfaceRoll = at(v, 39, defaultSurfaceRoll);
    }

    /** {@code v[i]} if the sending server had that value, else {@code fallback} (keep our own). */
    private static double at(double[] v, int i, double fallback) {
        return i < v.length ? v[i] : fallback;
    }

    /**
     * The pedal-bicycle handling, from the current config values.
     *
     * <p>Every factory below ends in the same {@link #reverseMaxSpeed} / {@link #reverseAcceleration}
     * pair, on purpose: backing any of these up is the rider shuffling it with their feet, and that is
     * not a thing a motor or a bigger wheel makes you better at. They also all end in the same
     * {@link #slopeGravity}, for a different reason — gravity on a slope is mass-independent, so no
     * variant gets to feel a hill differently from another until we decide one should.</p>
     */
    public static BikeTuning bicycleTuning() {
        return new BikeTuning(maxSpeed, pedalAcceleration, brakeDeceleration,
            rollingResistance, airDrag, maxSteerRateDegPerSec, steerSpeedFalloff,
            reverseMaxSpeed, reverseAcceleration, slopeGravity);
    }

    /**
     * The e-bike handling: the bicycle baseline with a higher assisted top speed and brisker
     * acceleration. Brake, drag and steering are shared with the bicycle — an e-bike stops and turns
     * like a bike, it just goes faster. This is the "variants are data" principle: a variant is a few
     * overridden numbers, not a new movement code path.
     */
    public static BikeTuning eBikeTuning() {
        return new BikeTuning(ebikeMaxSpeed, ebikePedalAcceleration, brakeDeceleration,
            rollingResistance, airDrag, maxSteerRateDegPerSec, steerSpeedFalloff,
            reverseMaxSpeed, reverseAcceleration, slopeGravity);
    }

    /**
     * The scooter handling: slower than a bicycle with brisker but weaker braking and twitchier
     * steering (small wheels, standing rider), sharing the bicycle's roll/air drag. Same "variants
     * are data" principle as the e-bike — a handful of overridden numbers, no new code path.
     */
    public static BikeTuning scooterTuning() {
        return new BikeTuning(scooterMaxSpeed, scooterAcceleration, scooterBrakeDeceleration,
            rollingResistance, airDrag, scooterMaxSteerRateDegPerSec, scooterSteerSpeedFalloff,
            reverseMaxSpeed, reverseAcceleration, slopeGravity);
    }

    /**
     * The performance scooter: the standard scooter's handling with a much higher top speed and
     * brisker acceleration. Brake and (twitchy) steer are shared — it goes faster, it doesn't
     * magically stop or turn better. Same "variants are data" principle as the e-bike.
     */
    public static BikeTuning scooterFastTuning() {
        return new BikeTuning(scooterFastMaxSpeed, scooterFastAcceleration, scooterBrakeDeceleration,
            rollingResistance, airDrag, scooterMaxSteerRateDegPerSec, scooterSteerSpeedFalloff,
            reverseMaxSpeed, reverseAcceleration, slopeGravity);
    }

    /**
     * A scooter with a flat battery: kick-along speed and acceleration, with the powered scooter's
     * brakes and (twitchy) steering unchanged — a dead battery costs you the motor, not the wheels.
     * This is the {@code unpowered} end of {@link BikeTuning#withAssist} for both scooter variants.
     */
    public static BikeTuning scooterKickTuning() {
        return new BikeTuning(scooterKickMaxSpeed, scooterKickAcceleration, scooterBrakeDeceleration,
            rollingResistance, airDrag, scooterMaxSteerRateDegPerSec, scooterSteerSpeedFalloff,
            reverseMaxSpeed, reverseAcceleration, slopeGravity);
    }

    /**
     * The one-wheel handling: scooter-class top speed with brisker acceleration, softer braking and
     * markedly better steering — a self-balancing board carves, and it keeps carving at speed where a
     * scooter's small wheels have gone twitchy ({@link #onewheelSteerSpeedFalloff} is the highest of
     * any variant). Roll and air drag stay shared with everything else, same "variants are data"
     * principle as the scooters: a handful of overridden numbers, no new movement code path.
     */
    public static BikeTuning onewheelTuning() {
        return new BikeTuning(onewheelMaxSpeed, onewheelAcceleration, onewheelBrakeDeceleration,
            rollingResistance, airDrag, onewheelMaxSteerRateDegPerSec, onewheelSteerSpeedFalloff,
            reverseMaxSpeed, reverseAcceleration, slopeGravity);
    }

    /**
     * A one-wheel with a flat battery: a walking-pace crawl, with the powered board's braking and
     * steering unchanged. This is the {@code unpowered} end of {@link BikeTuning#withAssist} for
     * {@code ONEWHEEL}.
     *
     * <p>Slower even than the scooter's kick tuning, because there is nothing to kick — a dead board is
     * being walked home, not scooted. It is deliberately <i>not</i> zero: a real self-balancing board
     * with a flat battery genuinely will not carry you, but the rule this codebase already committed to
     * with the scooter is that being stranded is not a fun mechanic.</p>
     */
    public static BikeTuning onewheelPushTuning() {
        return new BikeTuning(onewheelPushMaxSpeed, onewheelPushAcceleration, onewheelBrakeDeceleration,
            rollingResistance, airDrag, onewheelMaxSteerRateDegPerSec, onewheelSteerSpeedFalloff,
            reverseMaxSpeed, reverseAcceleration, slopeGravity);
    }

    private static void load() {
        config.load();

        config.addCustomCategoryComment(CATEGORY_PHYSICS,
            "Bike handling. These values change movement RESULTS, so on a multiplayer server the "
                + "server's copy is authoritative — a client with different values will visibly "
                + "desync from the bike it is riding.");
        config.addCustomCategoryComment(CATEGORY_CLIENT,
            "Client-side presentation only. Never synced; safe to differ per player.");

        maxSpeed = config.get(CATEGORY_PHYSICS, "maxSpeed", maxSpeed,
            "Top forward speed under pedal power, blocks/second.", 1.0D, 60.0D).getDouble();
        pedalAcceleration = config.get(CATEGORY_PHYSICS, "pedalAcceleration", pedalAcceleration,
            "Forward acceleration while pedalling, blocks/second^2.", 0.1D, 50.0D).getDouble();
        brakeDeceleration = config.get(CATEGORY_PHYSICS, "brakeDeceleration", brakeDeceleration,
            "Deceleration while braking, blocks/second^2.", 0.1D, 100.0D).getDouble();
        rollingResistance = config.get(CATEGORY_PHYSICS, "rollingResistance", rollingResistance,
            "Fraction of speed lost per second while coasting (exponential decay).", 0.0D, 5.0D).getDouble();
        airDrag = config.get(CATEGORY_PHYSICS, "airDrag", airDrag,
            "Quadratic air-drag coefficient.", 0.0D, 1.0D).getDouble();
        maxSteerRateDegPerSec = config.get(CATEGORY_PHYSICS, "maxSteerRateDegPerSec", maxSteerRateDegPerSec,
            "Maximum steering rate at low speed, degrees/second.", 1.0D, 720.0D).getDouble();
        steerSpeedFalloff = config.get(CATEGORY_PHYSICS, "steerSpeedFalloff", steerSpeedFalloff,
            "Speed (blocks/s) at which steering authority has halved.", 0.1D, 60.0D).getDouble();
        reverseMaxSpeed = config.get(CATEGORY_PHYSICS, "reverseMaxSpeed", reverseMaxSpeed,
            "Top backwards speed, blocks/second, shared by every variant. Backing up is a manual "
                + "shuffle, so keep it well under walking pace (4.3).", 0.0D, 10.0D).getDouble();
        reverseAcceleration = config.get(CATEGORY_PHYSICS, "reverseAcceleration", reverseAcceleration,
            "Acceleration while backing up, blocks/second^2, shared by every variant.",
            0.1D, 20.0D).getDouble();
        stepHeight = config.get(CATEGORY_PHYSICS, "stepHeight", stepHeight,
            "How high a lip (blocks) a rideable rolls straight over: kerbs, slabs and the shallow "
                + "graded blocks road mods build hills from. 0.6 matches a walking player, so a bike "
                + "goes wherever its rider could walk. 0.5 = slabs only; 0 = every lip is a wall.",
            0.0D, 1.0D).getDouble();
        physicsSubSteps = config.get(CATEGORY_PHYSICS, "physicsSubSteps", physicsSubSteps,
            "Physics sub-steps per game tick. Higher is smoother steering and costs CPU only.",
            1, 16).getInt();

        slopeGravity = config.get(CATEGORY_PHYSICS, "slopeGravity", slopeGravity,
            "Gravity along a slope, blocks/second^2 — how much hills cost to climb and give back on "
                + "the way down. 0 disables hill effort entirely. Deliberately not 9.81: Minecraft's "
                + "hills are steep and a real g makes a 1-in-3 road unrideable.", 0.0D, 30.0D).getDouble();
        maxGrade = config.get(CATEGORY_PHYSICS, "maxGrade", maxGrade,
            "Steepest slope (rise/run) the handling model is ever told about. This is a correctness "
                + "guard, not a taste one: a kerb step-up rises stepHeight in a few centimetres of "
                + "run, which is arithmetically a cliff, and without this clamp every kerb would "
                + "read as a mountain.", 0.05D, 2.0D).getDouble();
        gradeSmoothing = config.get(CATEGORY_PHYSICS, "gradeSmoothing", gradeSmoothing,
            "How much of the way the measured slope moves toward its new reading each tick (0-1). "
                + "Lower is smoother and laggier.", 0.01D, 1.0D).getDouble();
        defaultSurfaceGrip = config.get(CATEGORY_PHYSICS, "defaultSurfaceGrip", defaultSurfaceGrip,
            "Traction multiplier for any block not named in surfaceGrip.", 0.05D, 2.0D).getDouble();
        defaultSurfaceRoll = config.get(CATEGORY_PHYSICS, "defaultSurfaceRoll", defaultSurfaceRoll,
            "Rolling-resistance multiplier for any block not named in surfaceGrip.", 0.1D, 10.0D).getDouble();
        surfaceGrip = config.get(CATEGORY_PHYSICS, "surfaceGrip", surfaceGrip,
            "What each block feels like under a tyre: registryName=grip,rollMultiplier. Grip scales "
                + "braking, steering and traction (never top speed); the roll multiplier scales how "
                + "quickly you lose speed coasting, which is what makes a made road worth riding on. "
                + "One * wildcard is allowed per entry — road mods generate paint blocks per colour "
                + "and the colour set can grow at runtime, so patterns are the only form that stays "
                + "correct. Naming a mod you don't have installed costs nothing. Lines starting with "
                + "# are ignored.").getStringList();
        com.micatechnologies.minecraft.ldib.integration.RoadSurfaces.reload(surfaceGrip);
        ebikeMaxSpeed = config.get(CATEGORY_PHYSICS, "ebikeMaxSpeed", ebikeMaxSpeed,
            "E-bike assisted top speed, blocks/second.", 1.0D, 60.0D).getDouble();
        ebikePedalAcceleration = config.get(CATEGORY_PHYSICS, "ebikePedalAcceleration",
            ebikePedalAcceleration, "E-bike acceleration (motor assist), blocks/second^2.",
            0.1D, 50.0D).getDouble();
        scooterMaxSpeed = config.get(CATEGORY_PHYSICS, "scooterMaxSpeed", scooterMaxSpeed,
            "Scooter top speed, blocks/second (~12 mph).", 1.0D, 60.0D).getDouble();
        scooterAcceleration = config.get(CATEGORY_PHYSICS, "scooterAcceleration", scooterAcceleration,
            "Scooter acceleration, blocks/second^2.", 0.1D, 50.0D).getDouble();
        scooterFastMaxSpeed = config.get(CATEGORY_PHYSICS, "scooterFastMaxSpeed", scooterFastMaxSpeed,
            "Performance-scooter top speed, blocks/second (~22 mph).", 1.0D, 60.0D).getDouble();
        scooterFastAcceleration = config.get(CATEGORY_PHYSICS, "scooterFastAcceleration",
            scooterFastAcceleration, "Performance-scooter acceleration, blocks/second^2.",
            0.1D, 50.0D).getDouble();
        scooterBrakeDeceleration = config.get(CATEGORY_PHYSICS, "scooterBrakeDeceleration",
            scooterBrakeDeceleration, "Scooter braking, blocks/second^2.", 0.1D, 100.0D).getDouble();
        scooterMaxSteerRateDegPerSec = config.get(CATEGORY_PHYSICS, "scooterMaxSteerRateDegPerSec",
            scooterMaxSteerRateDegPerSec, "Scooter max steering rate at low speed, degrees/second.",
            1.0D, 720.0D).getDouble();
        scooterSteerSpeedFalloff = config.get(CATEGORY_PHYSICS, "scooterSteerSpeedFalloff",
            scooterSteerSpeedFalloff, "Speed (blocks/s) at which scooter steering authority has halved.",
            0.1D, 60.0D).getDouble();

        ebikeRangeBlocks = config.get(CATEGORY_PHYSICS, "ebikeRangeBlocks", ebikeRangeBlocks,
            "Blocks an e-bike travels under power on a full charge. 0 disables the battery entirely.",
            0.0D, 1000000.0D).getDouble();
        scooterRangeBlocks = config.get(CATEGORY_PHYSICS, "scooterRangeBlocks", scooterRangeBlocks,
            "Blocks a scooter travels under power on a full charge. 0 disables the battery.",
            0.0D, 1000000.0D).getDouble();
        scooterFastRangeBlocks = config.get(CATEGORY_PHYSICS, "scooterFastRangeBlocks",
            scooterFastRangeBlocks,
            "Blocks a performance scooter travels on a full charge. 0 disables the battery.",
            0.0D, 1000000.0D).getDouble();
        batteryReserveFraction = config.get(CATEGORY_PHYSICS, "batteryReserveFraction",
            batteryReserveFraction,
            "Charge fraction below which motor assist fades toward nothing. 0 = cut out abruptly.",
            0.0D, 1.0D).getDouble();
        scooterKickMaxSpeed = config.get(CATEGORY_PHYSICS, "scooterKickMaxSpeed", scooterKickMaxSpeed,
            "Top speed of a scooter with a flat battery (kicking it along), blocks/second.",
            0.1D, 60.0D).getDouble();
        scooterKickAcceleration = config.get(CATEGORY_PHYSICS, "scooterKickAcceleration",
            scooterKickAcceleration,
            "Acceleration of a scooter with a flat battery, blocks/second^2.", 0.1D, 50.0D).getDouble();

        onewheelMaxSpeed = config.get(CATEGORY_PHYSICS, "onewheelMaxSpeed", onewheelMaxSpeed,
            "One-wheel top speed, blocks/second (~12 mph, matched to the standard scooter).",
            1.0D, 60.0D).getDouble();
        onewheelAcceleration = config.get(CATEGORY_PHYSICS, "onewheelAcceleration", onewheelAcceleration,
            "One-wheel acceleration, blocks/second^2.", 0.1D, 50.0D).getDouble();
        onewheelBrakeDeceleration = config.get(CATEGORY_PHYSICS, "onewheelBrakeDeceleration",
            onewheelBrakeDeceleration,
            "One-wheel braking, blocks/second^2. No brake lever — this is the motor slowing the tyre.",
            0.1D, 100.0D).getDouble();
        onewheelMaxSteerRateDegPerSec = config.get(CATEGORY_PHYSICS, "onewheelMaxSteerRateDegPerSec",
            onewheelMaxSteerRateDegPerSec, "One-wheel max steering rate at low speed, degrees/second.",
            1.0D, 720.0D).getDouble();
        onewheelSteerSpeedFalloff = config.get(CATEGORY_PHYSICS, "onewheelSteerSpeedFalloff",
            onewheelSteerSpeedFalloff,
            "Speed (blocks/s) at which one-wheel steering authority has halved. Higher than the "
                + "scooter's, because a board still carves at speed.", 0.1D, 60.0D).getDouble();
        onewheelRangeBlocks = config.get(CATEGORY_PHYSICS, "onewheelRangeBlocks", onewheelRangeBlocks,
            "Blocks a one-wheel travels under power on a full charge. 0 disables the battery.",
            0.0D, 1000000.0D).getDouble();
        onewheelPushMaxSpeed = config.get(CATEGORY_PHYSICS, "onewheelPushMaxSpeed", onewheelPushMaxSpeed,
            "Top speed of a one-wheel with a flat battery (walking it home), blocks/second.",
            0.1D, 60.0D).getDouble();
        onewheelPushAcceleration = config.get(CATEGORY_PHYSICS, "onewheelPushAcceleration",
            onewheelPushAcceleration,
            "Acceleration of a one-wheel with a flat battery, blocks/second^2.", 0.1D, 50.0D).getDouble();

        enableRideHud = config.get(CATEGORY_CLIENT, "enableRideHud", enableRideHud,
            "Show the live speed readout while riding.").getBoolean();
        cameraLeanStrength = config.get(CATEGORY_CLIENT, "cameraLeanStrength", cameraLeanStrength,
            "How much of the bike's lean the rider's camera copies (0 = level horizon, 1 = full "
                + "lean). Camera roll causes motion sickness for some players; 0 disables it.",
            0.0D, 1.0D).getDouble();
        viewRecenterStrength = config.get(CATEGORY_CLIENT, "viewRecenterStrength", viewRecenterStrength,
            "How hard a moving rider's view drifts back toward straight ahead (0 = never, look "
                + "around freely at any speed; 1 = snaps forward as soon as you stop turning). "
                + "0.05 is about one second to settle. You can always look around freely while "
                + "stopped, whatever this is set to.", 0.0D, 1.0D).getDouble();

        config.addCustomCategoryComment(CATEGORY_BIKESHARE,
            "Bike-share stations. A kiosk plus the docks within its radius form a station; rentals can "
                + "be billed per minute through an installed economy mod (SUM) — which stays optional.");
        shareStationRadius = config.get(CATEGORY_BIKESHARE, "stationRadius", shareStationRadius,
            "Radius (blocks) around a kiosk within which docks belong to its station.", 1, 64).getInt();
        shareUnlockFee = config.get(CATEGORY_BIKESHARE, "unlockFee", shareUnlockFee,
            "Flat fee to unlock a rental, in the economy's currency. 0 = no unlock fee.", 0.0D, 100000.0D).getDouble();
        shareRateBikePerMinute = config.get(CATEGORY_BIKESHARE, "rateBikePerMinute", shareRateBikePerMinute,
            "Per-minute rate for a pedal bike. Default $1 / 5 min.", 0.0D, 100000.0D).getDouble();
        shareRateEbikePerMinute = config.get(CATEGORY_BIKESHARE, "rateEbikePerMinute", shareRateEbikePerMinute,
            "Per-minute rate for an e-bike. Default $1 / 3 min.", 0.0D, 100000.0D).getDouble();
        shareRateScooterPerMinute = config.get(CATEGORY_BIKESHARE, "rateScooterPerMinute", shareRateScooterPerMinute,
            "Per-minute rate for a scooter (either speed). Default $1 / 4 min.", 0.0D, 100000.0D).getDouble();
        shareUseEconomy = config.get(CATEGORY_BIKESHARE, "useEconomy", shareUseEconomy,
            "Bill rentals through an installed economy mod (SUM) when any fee/rate > 0.").getBoolean();

        if (config.hasChanged()) {
            config.save();
        }
    }
}
