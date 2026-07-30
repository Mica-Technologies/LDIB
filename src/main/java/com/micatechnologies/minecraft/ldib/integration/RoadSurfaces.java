package com.micatechnologies.minecraft.ldib.integration;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.physics.Terrain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * What a block feels like under a tyre: the registry-name → (grip, rolling resistance) table behind
 * {@link Terrain}'s surface half.
 *
 * <p><b>Soft integration, exactly like {@link SumEconomy}.</b> Nothing here references another mod at
 * compile time, or at all: surfaces are matched by <i>registry name</i> against a config list, so a
 * server running a road mod gets road physics by naming its blocks, and a server running none still
 * gets sensible vanilla behaviour. There is deliberately no hard or soft dependency, no
 * {@code @Optional}, and no event hook.</p>
 *
 * <p>That is not laziness — it is the only option, and it happens to be the right one. Fureniku's
 * Roads, the road mod this was written for, exposes no API, fires no events, has no IMC handler and
 * does not override block slipperiness or any entity hook. There is nothing to hook into, and equally
 * nothing that can fight us. A config table of names is strictly more general than an integration
 * would have been: it works for road mods nobody has written yet.</p>
 *
 * <h3>Patterns</h3>
 * An entry may contain a single {@code *}, matched as "starts with the bit before, ends with the bit
 * after". This is not decoration. Fureniku's paint blocks are generated per colour
 * ({@code <colour>_bike}, {@code <colour>_bike_icon}, …) and <b>the colour set is extensible at
 * runtime</b> — only white, yellow and red are built in; green and blue arrive from separate addon
 * mods. A table listing literal colours would silently miss a server that paints its lanes in one we
 * did not predict, so {@code furenikusroads:*_bike} is the only form that stays correct.
 *
 * <h3>Two layers</h3>
 * Road markings are usually a separate non-colliding block sitting <i>on top</i> of the road surface
 * (Fureniku's paint returns {@code NULL_AABB}), so the block a bike is standing on may be paint with
 * the real surface beneath it. {@link #terrainAt} therefore tries the block underfoot and then the one
 * below that, taking the first with an explicit entry — which reads "tarmac, painted" correctly
 * without the caller having to know that markings are a second layer.
 */
public final class RoadSurfaces {

    /** Exact {@code namespace:path} entries — the common case, resolved by hash lookup. */
    private static final Map<String, double[]> EXACT = new HashMap<>();

    /** Wildcard entries as {@code {prefix, suffix, grip, roll}}, tried in config order after a miss. */
    private static final List<Object[]> PATTERNS = new ArrayList<>();

    /**
     * Resolved results per {@link Block}, so a ridden bike is not rebuilding a registry-name string
     * every tick. Keyed by identity because blocks are singletons, and grip is a property of the
     * block rather than of its state. Cleared whenever the table is reloaded or re-synced.
     */
    private static final Map<Block, double[]> CACHE = new IdentityHashMap<>();

    /** Grip and roll for anything not named in the table. */
    private static final double[] MISS = {Double.NaN, Double.NaN};

    private RoadSurfaces() {
        throw new AssertionError("No instances.");
    }

    /**
     * Rebuild the table from raw config entries of the form {@code namespace:path=grip,roll}.
     *
     * <p>Malformed entries are skipped rather than thrown, and deliberately so: this runs from config
     * load <i>and</i> from a server's config sync on join, and neither is a place to fail a player's
     * login over a stray comma in somebody's server config.</p>
     */
    public static synchronized void reload(String[] entries) {
        EXACT.clear();
        PATTERNS.clear();
        CACHE.clear();
        if (entries == null) {
            return;
        }
        for (String raw : entries) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0 || eq == line.length() - 1) {
                continue;
            }
            String name = line.substring(0, eq).trim().toLowerCase(java.util.Locale.ROOT);
            String[] values = line.substring(eq + 1).split(",");
            if (values.length != 2) {
                continue;
            }
            double grip;
            double roll;
            try {
                grip = Double.parseDouble(values[0].trim());
                roll = Double.parseDouble(values[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (!(grip > 0.0D) || !(roll >= 0.0D)) {
                continue; // also rejects NaN, which would poison the physics silently
            }
            int star = name.indexOf('*');
            if (star < 0) {
                EXACT.put(name, new double[] {grip, roll});
            } else {
                PATTERNS.add(new Object[] {
                    name.substring(0, star), name.substring(star + 1), grip, roll});
            }
        }
    }

    /**
     * The terrain a rideable standing on {@code standingOn} is riding over, carrying {@code grade}
     * through unchanged (the slope is measured by the entity, not looked up here).
     *
     * @param standingOn the block position underfoot — the surface, not the rideable's own position
     */
    public static Terrain terrainAt(World world, BlockPos standingOn, double grade) {
        double[] surface = lookup(world, standingOn);
        if (surface == null) {
            // Paint, a carpet, a pressure plate — something with no opinion sitting on the real
            // surface. Fall through one block and ask again before giving up.
            surface = lookup(world, standingOn.down());
        }
        if (surface == null) {
            return new Terrain(grade, LdibConfig.defaultSurfaceGrip, LdibConfig.defaultSurfaceRoll);
        }
        return new Terrain(grade, surface[0], surface[1]);
    }

    /** Grip/roll for the block at {@code pos}, or {@code null} if the table does not name it. */
    private static double[] lookup(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        double[] cached = CACHE.get(block);
        if (cached != null) {
            return cached == MISS ? null : cached;
        }
        double[] resolved = resolve(block);
        // Negative results are cached too — a miss is the common case on ordinary terrain, and it is
        // the one we least want to pay a string build and a pattern sweep for on every tick.
        CACHE.put(block, resolved == null ? MISS : resolved);
        return resolved;
    }

    private static double[] resolve(Block block) {
        ResourceLocation id = block.getRegistryName();
        if (id == null) {
            return null;
        }
        return surfaceFor(id.toString());
    }

    /**
     * The {@code {grip, roll}} the table gives {@code registryName}, or {@code null} if it names no
     * such surface. Exact entries win over patterns; patterns are tried in config order, so an
     * earlier specific rule beats a later catch-all.
     *
     * <p>Deliberately takes a name rather than a {@code Block}: this is the whole matching rule, and
     * keeping it reachable without a world is what lets it be unit-tested on a bare JVM like the
     * physics is. {@link #resolve} is just this with the registry lookup in front.</p>
     */
    public static double[] surfaceFor(String registryName) {
        if (registryName == null) {
            return null;
        }
        String name = registryName.toLowerCase(java.util.Locale.ROOT);
        double[] exact = EXACT.get(name);
        if (exact != null) {
            return exact;
        }
        for (Object[] p : PATTERNS) {
            String prefix = (String) p[0];
            String suffix = (String) p[1];
            if (name.length() >= prefix.length() + suffix.length()
                && name.startsWith(prefix) && name.endsWith(suffix)) {
                return new double[] {(Double) p[2], (Double) p[3]};
            }
        }
        return null;
    }

    /** Whether the table names anything at all — lets the entity skip sampling entirely when empty. */
    public static boolean isEmpty() {
        return EXACT.isEmpty() && PATTERNS.isEmpty();
    }
}
