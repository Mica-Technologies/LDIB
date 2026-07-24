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
     * Physics sub-steps per game tick. One 50 ms step is coarse for steering; sub-stepping is the
     * cheap fix and costs integrator time only, never bandwidth.
     */
    public static int physicsSubSteps = 2;

    /** Whether the live speed readout is drawn while riding. Pure convenience; never synced. */
    public static boolean enableRideHud = true;

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
        };
    }

    /** Apply values captured by {@link #captureSyncable()} (same order) into the live fields. */
    public static void applySyncable(double[] v) {
        maxSpeed = v[0];
        pedalAcceleration = v[1];
        brakeDeceleration = v[2];
        rollingResistance = v[3];
        airDrag = v[4];
        maxSteerRateDegPerSec = v[5];
        steerSpeedFalloff = v[6];
        physicsSubSteps = (int) v[7];
        ebikeMaxSpeed = v[8];
        ebikePedalAcceleration = v[9];
        scooterMaxSpeed = v[10];
        scooterAcceleration = v[11];
        scooterBrakeDeceleration = v[12];
        scooterMaxSteerRateDegPerSec = v[13];
        scooterSteerSpeedFalloff = v[14];
        scooterFastMaxSpeed = v[15];
        scooterFastAcceleration = v[16];
        shareStationRadius = (int) v[17];
    }

    /** The pedal-bicycle handling, from the current config values. */
    public static BikeTuning bicycleTuning() {
        return new BikeTuning(maxSpeed, pedalAcceleration, brakeDeceleration,
            rollingResistance, airDrag, maxSteerRateDegPerSec, steerSpeedFalloff);
    }

    /**
     * The e-bike handling: the bicycle baseline with a higher assisted top speed and brisker
     * acceleration. Brake, drag and steering are shared with the bicycle — an e-bike stops and turns
     * like a bike, it just goes faster. This is the "variants are data" principle: a variant is a few
     * overridden numbers, not a new movement code path.
     */
    public static BikeTuning eBikeTuning() {
        return new BikeTuning(ebikeMaxSpeed, ebikePedalAcceleration, brakeDeceleration,
            rollingResistance, airDrag, maxSteerRateDegPerSec, steerSpeedFalloff);
    }

    /**
     * The scooter handling: slower than a bicycle with brisker but weaker braking and twitchier
     * steering (small wheels, standing rider), sharing the bicycle's roll/air drag. Same "variants
     * are data" principle as the e-bike — a handful of overridden numbers, no new code path.
     */
    public static BikeTuning scooterTuning() {
        return new BikeTuning(scooterMaxSpeed, scooterAcceleration, scooterBrakeDeceleration,
            rollingResistance, airDrag, scooterMaxSteerRateDegPerSec, scooterSteerSpeedFalloff);
    }

    /**
     * The performance scooter: the standard scooter's handling with a much higher top speed and
     * brisker acceleration. Brake and (twitchy) steer are shared — it goes faster, it doesn't
     * magically stop or turn better. Same "variants are data" principle as the e-bike.
     */
    public static BikeTuning scooterFastTuning() {
        return new BikeTuning(scooterFastMaxSpeed, scooterFastAcceleration, scooterBrakeDeceleration,
            rollingResistance, airDrag, scooterMaxSteerRateDegPerSec, scooterSteerSpeedFalloff);
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
        physicsSubSteps = config.get(CATEGORY_PHYSICS, "physicsSubSteps", physicsSubSteps,
            "Physics sub-steps per game tick. Higher is smoother steering and costs CPU only.",
            1, 16).getInt();
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

        enableRideHud = config.get(CATEGORY_CLIENT, "enableRideHud", enableRideHud,
            "Show the live speed readout while riding.").getBoolean();

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
