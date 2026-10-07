package mchorse.bbs_mod.ui;

import mchorse.bbs_mod.ui.film.replays.kits.ArmorPieceSpec;
import mchorse.bbs_mod.ui.film.replays.kits.KitConfig;
import mchorse.bbs_mod.ui.film.replays.kits.KitRoller;
import mchorse.bbs_mod.ui.film.replays.kits.KitSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class KitRollerTest
{
    private static final List<String> PATTERNS = List.of("silence", "rib", "vex", "ward", "eye");
    private static final List<String> MATERIALS = List.of("gold", "redstone", "diamond", "netherite", "amethyst");

    private static void assertEq(Object expected, Object actual, String testName)
    {
        if (Objects.equals(expected, actual))
        {
            return;
        }

        throw new AssertionError("FAILED: " + testName + " - Expected: [" + expected + "], Got: [" + actual + "]");
    }

    private static void assertNotEq(Object unexpected, Object actual, String testName)
    {
        if (!Objects.equals(unexpected, actual))
        {
            return;
        }

        throw new AssertionError("FAILED: " + testName + " - Did not expect: [" + unexpected + "]");
    }

    private static void assertTrue(boolean condition, String testName)
    {
        if (!condition)
        {
            throw new AssertionError("FAILED: " + testName + " - Expected true, got false");
        }
    }

    private static void assertNotNull(Object obj, String testName)
    {
        if (obj == null)
        {
            throw new AssertionError("FAILED: " + testName + " - Expected non-null value");
        }
    }

    private static void assertNull(Object obj, String testName)
    {
        if (obj != null)
        {
            throw new AssertionError("FAILED: " + testName + " - Expected null, got: " + obj);
        }
    }

    public static void testDeterminism()
    {
        KitConfig config = new KitConfig();
        config.armorPool = List.of("iron", "diamond", "netherite");
        long seed = 123456789L;

        KitSpec roll1 = KitRoller.roll(config, new Random(KitRoller.mix(seed, 0)), PATTERNS, MATERIALS);
        KitSpec roll2 = KitRoller.roll(config, new Random(KitRoller.mix(seed, 0)), PATTERNS, MATERIALS);

        assertEq(roll1, roll2, "Same seed and index must produce identical KitSpec");
    }

    public static void testReplayIndependence()
    {
        KitConfig config = new KitConfig();
        config.armorPool = List.of("leather", "chainmail", "iron", "gold", "diamond", "netherite");
        config.weaponPool = List.of("minecraft:diamond_sword", "minecraft:bow", "minecraft:crossbow");
        long seed = 987654321L;

        KitSpec roll0 = KitRoller.roll(config, new Random(KitRoller.mix(seed, 0)), PATTERNS, MATERIALS);
        KitSpec roll1 = KitRoller.roll(config, new Random(KitRoller.mix(seed, 1)), PATTERNS, MATERIALS);

        assertNotEq(roll0, roll1, "Different replay indices should give different rolls");
    }

    public static void testFixedMaterial()
    {
        KitConfig config = new KitConfig();
        config.armorPool = List.of("diamond");
        config.variationEnabled = false;

        Random random = new Random(42);

        for (int i = 0; i < 20; i++)
        {
            KitSpec spec = KitRoller.roll(config, random, PATTERNS, MATERIALS);

            assertEq("diamond", spec.head().materialKey(), "Head diamond");
            assertEq("diamond", spec.chest().materialKey(), "Chest diamond");
            assertEq("diamond", spec.legs().materialKey(), "Legs diamond");
            assertEq("diamond", spec.feet().materialKey(), "Feet diamond");
        }
    }

    public static void testVariation()
    {
        KitConfig config = new KitConfig();
        config.armorPool = List.of("netherite");
        config.variationEnabled = true;
        config.variationChance = 1.0;
        config.variationMaxPieces = 1;
        config.variationChangesMaterial = true;
        config.variationChangesTrim = true;

        Random random = new Random(99);

        for (int i = 0; i < 20; i++)
        {
            KitSpec spec = KitRoller.roll(config, random, PATTERNS, MATERIALS);
            int variedCount = 0;

            if (spec.head().varied()) variedCount++;
            if (spec.chest().varied()) variedCount++;
            if (spec.legs().varied()) variedCount++;
            if (spec.feet().varied()) variedCount++;

            assertEq(1, variedCount, "Expected exactly 1 varied piece");
        }

        config.variationMaxPieces = 4;
        KitSpec spec4 = KitRoller.roll(config, random, PATTERNS, MATERIALS);
        int count4 = 0;

        if (spec4.head().varied()) count4++;
        if (spec4.chest().varied()) count4++;
        if (spec4.legs().varied()) count4++;
        if (spec4.feet().varied()) count4++;

        assertEq(4, count4, "Expected all 4 varied pieces");

        ArmorPieceSpec[] pieces = { spec4.head(), spec4.chest(), spec4.legs(), spec4.feet() };

        for (ArmorPieceSpec p : pieces)
        {
            assertNotEq("netherite", p.materialKey(), "Varied piece material must differ from base");
        }
    }

    public static void testConsistentTrim()
    {
        KitConfig config = new KitConfig();
        config.trimEnabled = true;
        config.trimChance = 1.0;
        config.variationEnabled = false;

        Random random = new Random(101);
        KitSpec spec = KitRoller.roll(config, random, PATTERNS, MATERIALS);

        assertNotNull(spec.head().trimPattern(), "Head trim pattern");
        assertNotNull(spec.head().trimMaterial(), "Head trim material");
        assertEq(spec.head().trimPattern(), spec.chest().trimPattern(), "Chest trim pattern matches head");
        assertEq(spec.head().trimPattern(), spec.legs().trimPattern(), "Legs trim pattern matches head");
        assertEq(spec.head().trimPattern(), spec.feet().trimPattern(), "Feet trim pattern matches head");
        assertEq(spec.head().trimMaterial(), spec.chest().trimMaterial(), "Chest trim material matches head");
        assertEq(spec.head().trimMaterial(), spec.legs().trimMaterial(), "Legs trim material matches head");
        assertEq(spec.head().trimMaterial(), spec.feet().trimMaterial(), "Feet trim material matches head");

        config.trimEnabled = false;
        KitSpec noTrim = KitRoller.roll(config, random, PATTERNS, MATERIALS);

        assertNull(noTrim.head().trimPattern(), "No trim head pattern");
        assertNull(noTrim.chest().trimPattern(), "No trim chest pattern");
        assertNull(noTrim.legs().trimPattern(), "No trim legs pattern");
        assertNull(noTrim.feet().trimPattern(), "No trim feet pattern");
    }

    public static void testWeaponOffHandCompatibility()
    {
        KitConfig swordConfig = new KitConfig();
        swordConfig.weaponPool = List.of("minecraft:diamond_sword");
        swordConfig.offHandPool = List.of("minecraft:firework_rocket", "minecraft:shield");

        Random random = new Random(777);

        for (int i = 0; i < 30; i++)
        {
            KitSpec spec = KitRoller.roll(swordConfig, random, PATTERNS, MATERIALS);

            assertEq("minecraft:shield", spec.offHandId(), "Sword must never receive firework rocket when shield is available");
        }

        KitConfig crossbowConfig = new KitConfig();
        crossbowConfig.weaponPool = List.of("minecraft:crossbow");
        crossbowConfig.offHandPool = List.of("minecraft:firework_rocket");
        KitSpec specCb = KitRoller.roll(crossbowConfig, random, PATTERNS, MATERIALS);

        assertEq("minecraft:firework_rocket", specCb.offHandId(), "Crossbow can receive firework rocket");
    }

    public static void testEmptyPoolsDoNotThrow()
    {
        KitConfig config = new KitConfig();
        config.armorPool = new ArrayList<>();
        config.weaponPool = new ArrayList<>();
        config.offHandPool = new ArrayList<>();

        KitSpec spec = KitRoller.roll(config, new Random(1), new ArrayList<>(), new ArrayList<>());

        assertNotNull(spec, "Spec not null on empty pools");
        assertEq("iron", spec.head().materialKey(), "Fallback to iron");
        assertNull(spec.mainHandId(), "No weapon on empty pool");
        assertNull(spec.offHandId(), "No offhand on empty pool");
    }

    public static void main(String[] args)
    {
        testDeterminism();
        testReplayIndependence();
        testFixedMaterial();
        testVariation();
        testConsistentTrim();
        testWeaponOffHandCompatibility();
        testEmptyPoolsDoNotThrow();

        System.out.println("ALL KIT ROLLER TESTS PASSED!");
    }
}
