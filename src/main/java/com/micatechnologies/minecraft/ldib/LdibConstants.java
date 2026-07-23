package com.micatechnologies.minecraft.ldib;

/**
 * Compile-time constants for the mod's identity.
 *
 * <p>The values come from {@code Tags}, a class generated at build time by the GTCEu
 * buildscript from {@code buildscript.properties} ({@code generateGradleTokenClass}). Do not
 * hard-code the mod id or version anywhere else — the version in particular is derived from the
 * latest git tag, so a literal would drift the moment a release is cut.</p>
 */
public final class LdibConstants {

    /** Registry namespace and Forge mod id. Every {@code ResourceLocation} we create uses this. */
    public static final String MOD_NAMESPACE = Tags.MODID;

    /** Human-readable mod name, as shown in the Forge mod list. */
    public static final String MOD_NAME = Tags.MODNAME;

    /** Version string, derived from the latest git tag ({@code YYYY.MM.DD} for releases). */
    public static final String MOD_VERSION = Tags.VERSION;

    /** Minecraft ticks per second. */
    public static final double TICKS_PER_SECOND = 20.0D;

    /**
     * Seconds of simulated time per Minecraft tick. Every real-world unit the bike model works in
     * (blocks/second, blocks/second²) is converted through this exactly once, at the boundary
     * between {@code ldib.physics} and the entity update. Blocks are treated as metres.
     */
    public static final double SECONDS_PER_TICK = 1.0D / TICKS_PER_SECOND;

    private LdibConstants() {
        throw new AssertionError("No instances.");
    }
}
