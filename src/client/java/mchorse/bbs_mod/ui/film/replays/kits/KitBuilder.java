package mchorse.bbs_mod.ui.film.replays.kits;

import mchorse.bbs_mod.data.GameRegistries;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.item.equipment.trim.ArmorTrimMaterial;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class KitBuilder
{
    public static String getArmorPrefix(String materialKey)
    {
        if (materialKey == null)
        {
            return "iron_";
        }

        return switch (materialKey.toLowerCase())
        {
            case "leather" -> "leather_";
            case "chainmail" -> "chainmail_";
            case "iron" -> "iron_";
            case "gold" -> "golden_";
            case "diamond" -> "diamond_";
            case "netherite" -> "netherite_";
            case "copper" -> "copper_";
            default -> materialKey.toLowerCase() + "_";
        };
    }

    public static List<String> getAvailablePatterns(RegistryWrapper.WrapperLookup lookup)
    {
        if (lookup == null)
        {
            return Collections.emptyList();
        }

        try
        {
            return lookup.getOrThrow(RegistryKeys.TRIM_PATTERN)
                .streamKeys()
                .map((k) -> k.getValue().getPath())
                .sorted()
                .toList();
        }
        catch (Exception e)
        {
            System.err.println("[BBS RandomKits] Failed to get trim patterns: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public static List<String> getAvailableTrimMaterials(RegistryWrapper.WrapperLookup lookup)
    {
        if (lookup == null)
        {
            return Collections.emptyList();
        }

        try
        {
            return lookup.getOrThrow(RegistryKeys.TRIM_MATERIAL)
                .streamKeys()
                .map((k) -> k.getValue().getPath())
                .sorted()
                .toList();
        }
        catch (Exception e)
        {
            System.err.println("[BBS RandomKits] Failed to get trim materials: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public static ResolvedKit build(
        KitSpec spec,
        KitConfig config,
        Random random,
        RegistryWrapper.WrapperLookup lookup
    )
    {
        if (spec == null)
        {
            return new ResolvedKit(
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                ItemStack.EMPTY
            );
        }

        ItemStack head = buildArmorPiece(spec.head(), "helmet", config, random, lookup);
        ItemStack chest = buildArmorPiece(spec.chest(), "chestplate", config, random, lookup);
        ItemStack legs = buildArmorPiece(spec.legs(), "leggings", config, random, lookup);
        ItemStack feet = buildArmorPiece(spec.feet(), "boots", config, random, lookup);

        ItemStack mainHand = buildHandItem(spec.mainHandId(), config, random, lookup, false);
        ItemStack offHand = buildHandItem(spec.offHandId(), config, random, lookup, true);

        return new ResolvedKit(head, chest, legs, feet, mainHand, offHand);
    }

    private static ItemStack buildArmorPiece(
        ArmorPieceSpec spec,
        String slotSuffix,
        KitConfig config,
        Random random,
        RegistryWrapper.WrapperLookup lookup
    )
    {
        if (spec == null)
        {
            return ItemStack.EMPTY;
        }

        String prefix = getArmorPrefix(spec.materialKey());
        String itemId = "minecraft:" + prefix + slotSuffix;
        Identifier id = Identifier.of(itemId);

        if (!Registries.ITEM.containsId(id))
        {
            System.err.println("[BBS RandomKits] Item id not found in registry: " + itemId);
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(Registries.ITEM.get(id));

        if (spec.trimPattern() != null && spec.trimMaterial() != null && lookup != null)
        {
            try
            {
                var materials = lookup.getOrThrow(RegistryKeys.TRIM_MATERIAL);
                var patterns = lookup.getOrThrow(RegistryKeys.TRIM_PATTERN);

                Identifier matId = spec.trimMaterial().contains(":")
                    ? Identifier.of(spec.trimMaterial())
                    : Identifier.of("minecraft", spec.trimMaterial());

                Identifier patId = spec.trimPattern().contains(":")
                    ? Identifier.of(spec.trimPattern())
                    : Identifier.of("minecraft", spec.trimPattern());

                RegistryEntry.Reference<ArmorTrimMaterial> matEntry =
                    materials.getOrThrow(RegistryKey.of(RegistryKeys.TRIM_MATERIAL, matId));
                RegistryEntry.Reference<ArmorTrimPattern> patEntry =
                    patterns.getOrThrow(RegistryKey.of(RegistryKeys.TRIM_PATTERN, patId));

                stack.set(DataComponentTypes.TRIM, new ArmorTrim(matEntry, patEntry));
            }
            catch (Exception e)
            {
                System.err.println("[BBS RandomKits] Failed to apply trim to " + itemId + ": " + e.getMessage());
            }
        }

        if (config.enchantEnabled && config.enchantArmor && lookup != null)
        {
            applyEnchantments(stack, config, random, lookup);
        }

        return stack;
    }

    private static ItemStack buildHandItem(
        String itemId,
        KitConfig config,
        Random random,
        RegistryWrapper.WrapperLookup lookup,
        boolean isOffHand
    )
    {
        if (itemId == null || itemId.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        Identifier id = itemId.contains(":") ? Identifier.of(itemId) : Identifier.of("minecraft", itemId);

        if (!Registries.ITEM.containsId(id))
        {
            System.err.println("[BBS RandomKits] Item id not found in registry: " + itemId);
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(Registries.ITEM.get(id));

        if (isOffHand && id.getPath().equals("firework_rocket"))
        {
            stack.setCount(64);
        }

        if (config.enchantEnabled && config.enchantWeapon && lookup != null)
        {
            applyEnchantments(stack, config, random, lookup);
        }

        return stack;
    }

    public static void applyEnchantments(
        ItemStack stack,
        KitConfig config,
        Random random,
        RegistryWrapper.WrapperLookup lookup
    )
    {
        if (stack.isEmpty() || lookup == null)
        {
            return;
        }

        try
        {
            var enchs = lookup.getOrThrow(RegistryKeys.ENCHANTMENT);
            List<RegistryEntry.Reference<Enchantment>> candidates = new ArrayList<>();

            for (var entry : enchs.streamEntries().toList())
            {
                Identifier id = entry.registryKey().getValue();

                if (id.getPath().equals("binding_curse") || id.getPath().equals("vanishing_curse"))
                {
                    continue;
                }

                if (entry.value().isSupportedItem(stack))
                {
                    candidates.add(entry);
                }
            }

            if (candidates.isEmpty())
            {
                return;
            }

            Collections.shuffle(candidates, random);

            List<RegistryEntry.Reference<Enchantment>> chosen = new ArrayList<>();
            double subsetChance = Math.max(0.0, Math.min(1.0, config.enchantSubsetChance));

            for (var candidate : candidates)
            {
                boolean compatible = true;

                for (var existing : chosen)
                {
                    if (!Enchantment.canBeCombined(existing, candidate))
                    {
                        compatible = false;
                        break;
                    }
                }

                if (!compatible)
                {
                    continue;
                }

                if (config.enchantSubset && random.nextDouble() >= subsetChance)
                {
                    continue;
                }

                chosen.add(candidate);
            }

            for (var entry : chosen)
            {
                int maxLevel = entry.value().getMaxLevel();
                int level;

                switch (config.levelMode)
                {
                    case LOW -> level = 1;
                    case MID -> level = Math.max(1, (int) Math.ceil(maxLevel / 2.0));
                    case RANDOM -> level = maxLevel > 1 ? 1 + random.nextInt(maxLevel) : 1;
                    case MAX -> level = maxLevel;
                    default -> level = maxLevel;
                }

                stack.addEnchantment(entry, Math.max(1, Math.min(maxLevel, level)));
            }
        }
        catch (Exception e)
        {
            System.err.println("[BBS RandomKits] Error applying enchantments to stack: " + e.getMessage());
        }
    }
}
