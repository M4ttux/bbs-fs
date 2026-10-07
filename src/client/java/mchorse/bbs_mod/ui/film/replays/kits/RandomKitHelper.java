package mchorse.bbs_mod.ui.film.replays.kits;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.GameRegistries;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.registry.RegistryWrapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

public class RandomKitHelper
{
    public static void logError(String msg, Throwable t)
    {
        System.err.println("[BBS RandomKits] " + msg);

        if (t != null)
        {
            t.printStackTrace();
        }
    }

    public static File getConfigFile()
    {
        File bbsFolder = new File(BBSMod.getGameFolder(), "config/bbs");

        bbsFolder.mkdirs();

        return new File(bbsFolder, "random_kit.json");
    }

    public static KitConfig loadConfig()
    {
        File file = getConfigFile();

        if (file.exists())
        {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))
            {
                KitConfig cfg = new Gson().fromJson(reader, KitConfig.class);

                if (cfg != null)
                {
                    return cfg;
                }
            }
            catch (Exception e)
            {
                logError("Failed to load random kit config!", e);
            }
        }

        return new KitConfig();
    }

    public static void saveConfig(KitConfig config)
    {
        if (config == null)
        {
            return;
        }

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(getConfigFile()), StandardCharsets.UTF_8))
        {
            new GsonBuilder().setPrettyPrinting().create().toJson(config, writer);
        }
        catch (Exception e)
        {
            logError("Failed to save random kit config!", e);
        }
    }

    public static void applyRandomKits(List<Replay> replays, KitConfig config, UIContext context, Runnable onApplied)
    {
        applyRandomKits(replays, null, config, context, onApplied);
    }

    public static void applyRandomKits(
        List<Replay> replays,
        Film film,
        KitConfig config,
        UIContext context,
        Runnable onApplied
    )
    {
        if (replays == null || replays.isEmpty())
        {
            return;
        }

        RegistryWrapper.WrapperLookup lookup = GameRegistries.lookup();

        if (lookup == null)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_KITS_ERROR_NO_WORLD);
            return;
        }

        if (config == null)
        {
            config = new KitConfig();
        }

        saveConfig(config);

        if (config.mode == KitConfig.Mode.PRESET)
        {
            if (config.presetId == null || config.presetId.isEmpty() || !KitPresets.exists(config.presetId))
            {
                context.notifyError(UIKeys.SCENE_REPLAYS_KITS_ERROR_PRESET_MISSING);
                return;
            }

            ResolvedKit presetKit;

            try
            {
                presetKit = KitPresets.load(config.presetId);
            }
            catch (Exception e)
            {
                logError("Failed to load preset: " + config.presetId, e);
                context.notifyError(UIKeys.SCENE_REPLAYS_KITS_ERROR_PRESET_FAILED);
                return;
            }

            for (Replay replay : replays)
            {
                KitApplier.apply(replay, presetKit.copy());
            }

            if (onApplied != null)
            {
                onApplied.run();
            }

            context.notifySuccess(UIKeys.SCENE_REPLAYS_KITS_SUCCESS.format(replays.size(), "preset:" + config.presetId));
            return;
        }

        long baseSeed = config.fixedSeed ? config.seed : System.nanoTime();
        List<String> patterns = KitBuilder.getAvailablePatterns(lookup);
        List<String> materials = KitBuilder.getAvailableTrimMaterials(lookup);

        int applied = 0;

        for (int i = 0; i < replays.size(); i++)
        {
            Replay replay = replays.get(i);
            int replayIndex = (film != null && film.replays.getList().contains(replay)) ? film.replays.getList().indexOf(replay) : i;

            long replaySeed = KitRoller.mix(baseSeed, replayIndex);
            Random random = new Random(replaySeed);

            KitSpec spec = KitRoller.roll(config, random, patterns, materials);
            ResolvedKit kit = KitBuilder.build(spec, config, random, lookup);

            KitApplier.apply(replay, kit);
            applied++;
        }

        if (applied > 0)
        {
            if (onApplied != null)
            {
                onApplied.run();
            }

            context.notifySuccess(UIKeys.SCENE_REPLAYS_KITS_SUCCESS.format(applied, String.valueOf(baseSeed)));
        }
    }
}
