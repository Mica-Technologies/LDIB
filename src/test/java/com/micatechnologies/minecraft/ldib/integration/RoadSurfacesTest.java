package com.micatechnologies.minecraft.ldib.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The surface table's parsing and matching rules.
 *
 * <p>Worth pinning because this is config a server owner types by hand, in a file, with no
 * autocomplete and no validation beyond what is here — and because the two things most likely to be
 * wrong are silent. A pattern that fails to match makes a road quietly ride like grass; a malformed
 * line that throws would take out a player's login, since the table is rebuilt on config load
 * <i>and</i> on the server's config sync.</p>
 *
 * <p>Only the name-matching half is exercised: {@code terrainAt} needs a {@code World} and belongs to
 * the entity layer's play-testing, but every rule that decides <i>which</i> numbers a block gets lives
 * in {@code surfaceFor}, which is pure string work.</p>
 */
class RoadSurfacesTest {

    @AfterEach
    void clearTable() {
        RoadSurfaces.reload(new String[0]);
    }

    // --- Matching ------------------------------------------------------------------------------

    @Test
    void exactNamesMatch() {
        RoadSurfaces.reload(new String[] {"minecraft:ice=0.2,0.55"});
        assertArrayEquals(new double[] {0.2D, 0.55D}, RoadSurfaces.surfaceFor("minecraft:ice"));
        assertNull(RoadSurfaces.surfaceFor("minecraft:packed_ice"), "must not match a longer name");
        assertNull(RoadSurfaces.surfaceFor("minecraft:stone"));
    }

    @Test
    void aTrailingWildcardMatchesAPrefix() {
        RoadSurfaces.reload(new String[] {"furenikusroads:road_block_*=1.0,0.9"});
        assertNotNull(RoadSurfaces.surfaceFor("furenikusroads:road_block_standard"));
        assertNotNull(RoadSurfaces.surfaceFor("furenikusroads:road_block_green"));
        assertNull(RoadSurfaces.surfaceFor("furenikusroads:sidewalk"));
        assertNull(RoadSurfaces.surfaceFor("someothermod:road_block_standard"),
            "the namespace is part of the pattern");
    }

    @Test
    void anInnerWildcardMatchesEveryPaintColour() {
        // The case the wildcard exists for: Fureniku's generates <colour>_bike per installed colour,
        // and the colour set grows at runtime when someone adds a paint addon. A table naming literal
        // colours would silently miss a server that paints its lanes in one we did not predict.
        RoadSurfaces.reload(new String[] {"furenikusroads:*_bike=1.0,0.88"});
        for (String colour : new String[] {"white", "yellow", "red", "green", "blue", "chartreuse"}) {
            assertNotNull(RoadSurfaces.surfaceFor("furenikusroads:" + colour + "_bike"),
                colour + " should have matched the wildcard");
        }
        assertNull(RoadSurfaces.surfaceFor("furenikusroads:green_bike_icon"),
            "_bike must not swallow _bike_icon — they are separate entries with separate numbers");
    }

    @Test
    void matchingIsCaseInsensitiveBothWays() {
        RoadSurfaces.reload(new String[] {"MineCraft:Ice=0.2,0.55"});
        assertNotNull(RoadSurfaces.surfaceFor("minecraft:ice"));
        assertNotNull(RoadSurfaces.surfaceFor("MINECRAFT:ICE"));
    }

    @Test
    void exactEntriesBeatPatterns() {
        RoadSurfaces.reload(new String[] {
            "furenikusroads:road_block_*=1.0,0.90",
            "furenikusroads:road_block_gravel=0.8,1.4",
        });
        assertArrayEquals(new double[] {0.8D, 1.4D},
            RoadSurfaces.surfaceFor("furenikusroads:road_block_gravel"),
            "a specific entry must win over a catch-all, whatever order they appear in");
        assertArrayEquals(new double[] {1.0D, 0.90D},
            RoadSurfaces.surfaceFor("furenikusroads:road_block_standard"));
    }

    @Test
    void earlierPatternsWinOverLaterOnes() {
        RoadSurfaces.reload(new String[] {
            "mod:special_*=0.5,2.0",
            "mod:*=1.0,1.0",
        });
        assertArrayEquals(new double[] {0.5D, 2.0D}, RoadSurfaces.surfaceFor("mod:special_thing"));
        assertArrayEquals(new double[] {1.0D, 1.0D}, RoadSurfaces.surfaceFor("mod:ordinary_thing"));
    }

    // --- Robustness ----------------------------------------------------------------------------

    @Test
    void junkLinesAreSkippedRatherThanThrown() {
        // This runs inside a login packet handler. A stray comma in someone's server config must cost
        // them a surface, not a connection.
        RoadSurfaces.reload(new String[] {
            "# a comment",
            "",
            "   ",
            "no_equals_sign",
            "minecraft:trailing=",
            "=0.5,0.5",
            "minecraft:onevalue=0.5",
            "minecraft:threevalues=0.5,0.5,0.5",
            "minecraft:notanumber=abc,def",
            "minecraft:negativegrip=-1.0,1.0",
            "minecraft:zerogrip=0,1.0",
            "minecraft:negativeroll=1.0,-1.0",
            "minecraft:good=0.9,1.1",
        });
        assertArrayEquals(new double[] {0.9D, 1.1D}, RoadSurfaces.surfaceFor("minecraft:good"),
            "the one valid line should have survived a page of junk");
        assertNull(RoadSurfaces.surfaceFor("minecraft:notanumber"));
        assertNull(RoadSurfaces.surfaceFor("minecraft:negativegrip"));
        assertNull(RoadSurfaces.surfaceFor("minecraft:zerogrip"), "zero grip would divide the handling by nothing");
        assertNull(RoadSurfaces.surfaceFor("minecraft:negativeroll"));
        assertNull(RoadSurfaces.surfaceFor("minecraft:onevalue"));
    }

    @Test
    void whitespaceAroundEntriesIsForgiven() {
        RoadSurfaces.reload(new String[] {"  minecraft:ice  =  0.2 , 0.55  "});
        assertArrayEquals(new double[] {0.2D, 0.55D}, RoadSurfaces.surfaceFor("minecraft:ice"));
    }

    @Test
    void nullsAreSurvivable() {
        RoadSurfaces.reload(null);
        assertTrue(RoadSurfaces.isEmpty());
        RoadSurfaces.reload(new String[] {null, "minecraft:ice=0.2,0.55", null});
        assertNotNull(RoadSurfaces.surfaceFor("minecraft:ice"));
        assertNull(RoadSurfaces.surfaceFor(null));
    }

    @Test
    void reloadingReplacesRatherThanAccumulates() {
        // A client that joins server A and then server B must not still be riding on A's surfaces.
        RoadSurfaces.reload(new String[] {"serverA:road=1.0,0.5"});
        assertNotNull(RoadSurfaces.surfaceFor("serverA:road"));
        RoadSurfaces.reload(new String[] {"serverB:road=1.0,0.5"});
        assertNull(RoadSurfaces.surfaceFor("serverA:road"), "the previous table must be gone entirely");
        assertNotNull(RoadSurfaces.surfaceFor("serverB:road"));
    }

    @Test
    void anEmptyTableIsEmptyAndMatchesNothing() {
        RoadSurfaces.reload(new String[] {"# nothing but comments"});
        assertTrue(RoadSurfaces.isEmpty(), "a comment-only table should let the entity skip sampling");
        assertNull(RoadSurfaces.surfaceFor("minecraft:stone"));

        RoadSurfaces.reload(new String[] {"minecraft:stone=1.0,1.0"});
        assertFalse(RoadSurfaces.isEmpty());
    }

    @Test
    void theShippedDefaultsAllParse() {
        // Guards against a typo in the defaults shipping a table that silently does less than it says.
        String[] defaults = com.micatechnologies.minecraft.ldib.LdibConfig.surfaceGrip;
        RoadSurfaces.reload(defaults);
        assertFalse(RoadSurfaces.isEmpty(), "the shipped defaults should produce a usable table");
        assertNotNull(RoadSurfaces.surfaceFor("minecraft:ice"), "ice should be slippery out of the box");
        assertNotNull(RoadSurfaces.surfaceFor("minecraft:sand"));
        assertNotNull(RoadSurfaces.surfaceFor("furenikusroads:road_block_standard"),
            "the deployment target's roads should be covered by the defaults");
        assertNotNull(RoadSurfaces.surfaceFor("furenikusroads:green_bike"),
            "and its bike-lane paint, in a colour that is not built into the road mod");

        double[] ice = RoadSurfaces.surfaceFor("minecraft:ice");
        double[] road = RoadSurfaces.surfaceFor("furenikusroads:road_block_standard");
        assertTrue(ice[0] < road[0], "ice must have less grip than tarmac");
        assertTrue(RoadSurfaces.surfaceFor("minecraft:sand")[1] > road[1],
            "sand must drag more than tarmac");
    }
}
