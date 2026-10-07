package mchorse.bbs_mod.ui.film.replays.kits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class KitRoller
{
    public static final List<String> DEFAULT_MATERIALS = List.of(
        "leather",
        "chainmail",
        "iron",
        "gold",
        "diamond",
        "netherite"
    );

    public static long mix(long seed, int index)
    {
        long z = seed + 0x9E3779B97F4A7C15L * (index + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static KitSpec roll(
        KitConfig config,
        Random random,
        List<String> availablePatterns,
        List<String> availableTrimMaterials
    )
    {
        return roll(config, random, availablePatterns, availableTrimMaterials, DEFAULT_MATERIALS);
    }

    public static KitSpec roll(
        KitConfig config,
        Random random,
        List<String> availablePatterns,
        List<String> availableTrimMaterials,
        List<String> allValidMaterials
    )
    {
        if (config == null)
        {
            config = new KitConfig();
        }

        if (random == null)
        {
            random = new Random();
        }

        if (allValidMaterials == null || allValidMaterials.isEmpty())
        {
            allValidMaterials = DEFAULT_MATERIALS;
        }

        /* 1. Base material */
        String baseMaterial;
        List<String> armorPool = config.armorPool;

        if (armorPool == null || armorPool.isEmpty())
        {
            System.err.println("[BBS RandomKits] Armor pool was empty, defaulting to iron.");
            baseMaterial = "iron";
        }
        else
        {
            baseMaterial = armorPool.get(random.nextInt(armorPool.size()));
        }

        /* 2. Set trim */
        String setTrimPattern = null;
        String setTrimMaterial = null;

        if (config.trimEnabled)
        {
            if (config.trimPattern != null && !config.trimPattern.isEmpty())
            {
                setTrimPattern = config.trimPattern;
            }
            else if (availablePatterns != null && !availablePatterns.isEmpty())
            {
                setTrimPattern = availablePatterns.get(random.nextInt(availablePatterns.size()));
            }

            if (config.trimMaterial != null && !config.trimMaterial.isEmpty())
            {
                setTrimMaterial = config.trimMaterial;
            }
            else if (availableTrimMaterials != null && !availableTrimMaterials.isEmpty())
            {
                setTrimMaterial = availableTrimMaterials.get(random.nextInt(availableTrimMaterials.size()));
            }
        }

        /* 3. Base pieces */
        double trimChance = Math.max(0.0, Math.min(1.0, config.trimChance));
        ArmorPieceSpec[] pieces = new ArmorPieceSpec[4];

        for (int i = 0; i < 4; i++)
        {
            boolean hasTrim = config.trimEnabled && (random.nextDouble() < trimChance);
            String pat = hasTrim ? setTrimPattern : null;
            String mat = hasTrim ? setTrimMaterial : null;

            pieces[i] = new ArmorPieceSpec(baseMaterial, pat, mat, false);
        }

        /* 4. Weapon */
        String weaponId = null;

        if (config.weaponPool != null && !config.weaponPool.isEmpty())
        {
            weaponId = config.weaponPool.get(random.nextInt(config.weaponPool.size()));
        }

        /* 5. Off-hand */
        String offHandId = null;

        if (config.offHandPool != null && !config.offHandPool.isEmpty())
        {
            if (config.offHandPool.size() == 1)
            {
                offHandId = config.offHandPool.get(0);
            }
            else
            {
                List<String> compatibleOffHands = new ArrayList<>();

                for (String offHandCandidate : config.offHandPool)
                {
                    if (isCompatibleOffHand(weaponId, offHandCandidate))
                    {
                        compatibleOffHands.add(offHandCandidate);
                    }
                }

                if (compatibleOffHands.isEmpty())
                {
                    compatibleOffHands = config.offHandPool;
                }

                offHandId = compatibleOffHands.get(random.nextInt(compatibleOffHands.size()));
            }
        }

        /* 6. Variation */
        if (config.variationEnabled)
        {
            int maxVaried = Math.max(0, Math.min(4, config.variationMaxPieces));
            double varChance = Math.max(0.0, Math.min(1.0, config.variationChance));

            List<Integer> indices = new ArrayList<>(List.of(0, 1, 2, 3));
            Collections.shuffle(indices, random);

            int variedCount = 0;

            for (int idx : indices)
            {
                if (variedCount >= maxVaried)
                {
                    break;
                }

                if (random.nextDouble() < varChance)
                {
                    ArmorPieceSpec current = pieces[idx];
                    String pieceMat = current.materialKey();
                    String pieceTrimPat = current.trimPattern();
                    String pieceTrimMat = current.trimMaterial();
                    boolean changed = false;

                    if (config.variationChangesMaterial)
                    {
                        List<String> otherMaterials = new ArrayList<>();

                        for (String m : allValidMaterials)
                        {
                            if (!m.equalsIgnoreCase(baseMaterial))
                            {
                                otherMaterials.add(m);
                            }
                        }

                        if (!otherMaterials.isEmpty())
                        {
                            pieceMat = otherMaterials.get(random.nextInt(otherMaterials.size()));
                            changed = true;
                        }
                    }

                    if (config.variationChangesTrim && config.trimEnabled)
                    {
                        if (availablePatterns != null && !availablePatterns.isEmpty())
                        {
                            List<String> otherPatterns = new ArrayList<>();

                            for (String p : availablePatterns)
                            {
                                if (!p.equals(setTrimPattern))
                                {
                                    otherPatterns.add(p);
                                }
                            }

                            if (!otherPatterns.isEmpty())
                            {
                                pieceTrimPat = otherPatterns.get(random.nextInt(otherPatterns.size()));
                            }
                            else
                            {
                                pieceTrimPat = availablePatterns.get(random.nextInt(availablePatterns.size()));
                            }
                        }

                        if (availableTrimMaterials != null && !availableTrimMaterials.isEmpty())
                        {
                            List<String> otherTrimMats = new ArrayList<>();

                            for (String tm : availableTrimMaterials)
                            {
                                if (!tm.equals(setTrimMaterial))
                                {
                                    otherTrimMats.add(tm);
                                }
                            }

                            if (!otherTrimMats.isEmpty())
                            {
                                pieceTrimMat = otherTrimMats.get(random.nextInt(otherTrimMats.size()));
                            }
                            else
                            {
                                pieceTrimMat = availableTrimMaterials.get(random.nextInt(availableTrimMaterials.size()));
                            }
                        }

                        changed = true;
                    }

                    if (changed)
                    {
                        pieces[idx] = new ArmorPieceSpec(pieceMat, pieceTrimPat, pieceTrimMat, true);
                        variedCount++;
                    }
                }
            }
        }

        return new KitSpec(pieces[0], pieces[1], pieces[2], pieces[3], weaponId, offHandId);
    }

    public static boolean isCompatibleOffHand(String weaponId, String offHandId)
    {
        if (weaponId == null || offHandId == null)
        {
            return true;
        }

        boolean isRocket = offHandId.contains("firework_rocket");
        boolean isCrossbow = weaponId.contains("crossbow");

        if (isRocket)
        {
            return isCrossbow;
        }

        return true;
    }
}
