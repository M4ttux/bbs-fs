package mchorse.bbs_mod.ui;

import mchorse.bbs_mod.ui.film.replays.RandomNamesHelper;

public class RandomNamesHelperTest
{
    public static void main(String[] args)
    {
        // 1. Color extraction test
        assertEq("[e", RandomNamesHelper.extractColorFromPrefix("[e[lQueso [r"), "Extract [e from [e[lQueso [r");
        assertEq("[a", RandomNamesHelper.extractColorFromPrefix("[a[lVIP [r"), "Extract [a from [a[lVIP [r");
        assertEq("", RandomNamesHelper.extractColorFromPrefix("Miembro "), "Extract empty from Miembro");

        // 2. Format name with rank and color
        RandomNamesHelper.RankEntry queso = new RandomNamesHelper.RankEntry();
        queso.id = "queso";
        queso.prefix = "[e[lQueso [r";
        queso.color = "[e";
        assertEq("[e[lQueso [r[eJuanito123", RandomNamesHelper.formatName("Juanito123", queso), "Format with colored rank");

        // 3. Format name with rank without color
        RandomNamesHelper.RankEntry miembro = new RandomNamesHelper.RankEntry();
        miembro.id = "miembro";
        miembro.prefix = "Miembro ";
        miembro.color = "";
        assertEq("Miembro Juanito123", RandomNamesHelper.formatName("Juanito123", miembro), "Format with uncolored rank");

        // 4. Format name without rank
        assertEq("Juanito123", RandomNamesHelper.formatName("Juanito123", null), "Format without rank");

        // 5. Test non-repetition
        java.util.List<String> pool = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++)
        {
            pool.add("User_" + i);
        }
        java.util.Random random = new java.util.Random(12345);
        java.util.List<String> shuffled = new java.util.ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, random);

        java.util.Set<String> assigned = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++)
        {
            String name = shuffled.get(i);
            if (!assigned.add(name))
            {
                throw new AssertionError("Duplicate name assigned in pool: " + name);
            }
        }
        if (assigned.size() != 20)
        {
            throw new AssertionError("Expected 20 unique names, got: " + assigned.size());
        }

        // 6. Test rank picking
        RandomNamesHelper.NamesConfig config = new RandomNamesHelper.NamesConfig();
        config.rankChance = 1.0;
        config.ranks.add(queso);
        config.ranks.add(miembro);
        RandomNamesHelper.RankEntry picked = RandomNamesHelper.pickRandomRank(config, random);
        if (picked == null)
        {
            throw new AssertionError("Expected non-null rank when rankChance is 1.0");
        }

        config.rankChance = 0.0;
        RandomNamesHelper.RankEntry unpicked = RandomNamesHelper.pickRandomRank(config, random);
        if (unpicked != null)
        {
            throw new AssertionError("Expected null rank when rankChance is 0.0");
        }

        System.out.println("ALL RANDOM NAMES TESTS PASSED!");
    }

    private static void assertEq(String expected, String actual, String message)
    {
        if (!expected.equals(actual))
        {
            throw new AssertionError(message + " - Expected: '" + expected + "', but got: '" + actual + "'");
        }
    }
}
