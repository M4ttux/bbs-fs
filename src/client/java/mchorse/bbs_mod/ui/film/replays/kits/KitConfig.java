package mchorse.bbs_mod.ui.film.replays.kits;

import java.util.ArrayList;
import java.util.List;

public class KitConfig
{
    public enum Mode
    {
        RANDOM, PRESET
    }

    public enum LevelMode
    {
        LOW, MID, MAX, RANDOM
    }

    public Mode mode = Mode.RANDOM;
    public String presetId = "";

    public boolean fixedSeed = false;
    public long seed = 0L;

    /** Claves de material activas. Una sola activa = material fijo. Ej: "netherite", "gold", "leather". */
    public List<String> armorPool = new ArrayList<>(List.of("iron", "diamond", "netherite"));

    public boolean trimEnabled = true;
    public double trimChance = 1.0;           // 0..1, probabilidad de que una pieza lleve trim
    public String trimPattern = "";           // "" = aleatorio; si no, id de patron ("silence")
    public String trimMaterial = "";          // "" = aleatorio; si no, id de material ("gold")

    /** Ids de items. Pool vacio = sin arma. Un solo elemento = arma fija. */
    public List<String> weaponPool = new ArrayList<>(List.of(
        "minecraft:netherite_sword",
        "minecraft:diamond_sword",
        "minecraft:iron_sword",
        "minecraft:bow",
        "minecraft:crossbow",
        "minecraft:trident"
    ));

    public List<String> offHandPool = new ArrayList<>(List.of(
        "minecraft:shield",
        "minecraft:totem_of_undying"
    ));

    public boolean enchantEnabled = false;
    public boolean enchantArmor = true;
    public boolean enchantWeapon = true;
    public LevelMode levelMode = LevelMode.MAX;
    public boolean enchantSubset = false;     // false = todos los compatibles; true = subconjunto aleatorio
    public double enchantSubsetChance = 0.5;

    public boolean variationEnabled = false;
    public double variationChance = 0.25;     // por pieza
    public int variationMaxPieces = 1;        // 0..4
    public boolean variationChangesMaterial = true;
    public boolean variationChangesTrim = true;

    public KitConfig copy()
    {
        KitConfig copy = new KitConfig();

        copy.mode = this.mode;
        copy.presetId = this.presetId;
        copy.fixedSeed = this.fixedSeed;
        copy.seed = this.seed;
        copy.armorPool = new ArrayList<>(this.armorPool);
        copy.trimEnabled = this.trimEnabled;
        copy.trimChance = this.trimChance;
        copy.trimPattern = this.trimPattern;
        copy.trimMaterial = this.trimMaterial;
        copy.weaponPool = new ArrayList<>(this.weaponPool);
        copy.offHandPool = new ArrayList<>(this.offHandPool);
        copy.enchantEnabled = this.enchantEnabled;
        copy.enchantArmor = this.enchantArmor;
        copy.enchantWeapon = this.enchantWeapon;
        copy.levelMode = this.levelMode;
        copy.enchantSubset = this.enchantSubset;
        copy.enchantSubsetChance = this.enchantSubsetChance;
        copy.variationEnabled = this.variationEnabled;
        copy.variationChance = this.variationChance;
        copy.variationMaxPieces = this.variationMaxPieces;
        copy.variationChangesMaterial = this.variationChangesMaterial;
        copy.variationChangesTrim = this.variationChangesTrim;

        return copy;
    }
}
