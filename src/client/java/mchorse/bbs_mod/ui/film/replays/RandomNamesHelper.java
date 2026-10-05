package mchorse.bbs_mod.ui.film.replays;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RandomNamesHelper
{
    private static void logError(String msg, Throwable t)
    {
        System.err.println("[BBS RandomNames] " + msg);
        if (t != null)
        {
            t.printStackTrace();
        }
    }

    private static final Pattern COLOR_PATTERN = Pattern.compile("(\\[[0-9a-fA-F]|\\[#[0-9a-fA-F]{6}\\]|\u00A7[0-9a-fA-F])");

    public static class RankEntry
    {
        public String id = "";
        public String prefix = "";
        public String color = "";
        public double weight = 1.0;
    }

    public static class NamesConfig
    {
        public double rankChance = 0.5;
        public List<RankEntry> ranks = new ArrayList<>();
        public List<String> names = new ArrayList<>();
    }

    public static File getFile()
    {
        File bbsFolder = new File(BBSMod.getGameFolder(), "config/bbs");
        bbsFolder.mkdirs();
        return new File(bbsFolder, "random_names.json");
    }

    public static void applyRandomNames(List<Replay> replays, UIContext context, Runnable onApplied)
    {
        if (replays == null || replays.size() < 2)
        {
            return;
        }

        NamesConfig config;
        try
        {
            config = loadConfig();
        }
        catch (Exception e)
        {
            logError("Failed to load random names configuration!", e);
            context.notifyError(UIKeys.SCENE_REPLAYS_RANDOM_NAMES_FILE_ERROR);
            return;
        }

        if (config.names.isEmpty())
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_RANDOM_NAMES_EMPTY_POOL);
            return;
        }

        List<Replay> targetReplays = new ArrayList<>();
        for (Replay replay : replays)
        {
            Form form = replay.form.get();
            if (form != null && !findLabelForms(form).isEmpty())
            {
                targetReplays.add(replay);
            }
        }

        if (targetReplays.isEmpty())
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_RANDOM_NAMES_NO_LABELS);
            return;
        }

        Random random = new Random();
        List<String> pool = new ArrayList<>(config.names);
        Collections.shuffle(pool, random);

        int nameIndex = 0;
        int appliedCount = 0;

        for (Replay replay : targetReplays)
        {
            if (nameIndex >= pool.size())
            {
                Collections.shuffle(pool, random);
                nameIndex = 0;
            }

            String username = pool.get(nameIndex++);
            RankEntry rank = pickRandomRank(config, random);
            String formattedName = formatName(username, rank);

            Form copy = FormUtils.copy(replay.form.get());
            List<LabelForm> labelForms = findLabelForms(copy);

            for (LabelForm label : labelForms)
            {
                label.text.set(formattedName);
            }

            replay.form.set(copy);
            appliedCount++;
        }

        if (appliedCount > 0)
        {
            if (onApplied != null)
            {
                onApplied.run();
            }
            context.notifySuccess(UIKeys.SCENE_REPLAYS_RANDOM_NAMES_SUCCESS.format(appliedCount));
        }
    }

    public static List<LabelForm> findLabelForms(Form form)
    {
        List<LabelForm> list = new ArrayList<>();
        collectLabelForms(form, list);
        return list;
    }

    private static void collectLabelForms(Form form, List<LabelForm> list)
    {
        if (form == null)
        {
            return;
        }

        if (form.parts != null)
        {
            for (BodyPart part : form.parts.getList())
            {
                Form partForm = part.getForm();
                if (partForm instanceof LabelForm label)
                {
                    list.add(label);
                }
                else if (partForm != null)
                {
                    collectLabelForms(partForm, list);
                }
            }
        }
    }

    public static RankEntry pickRandomRank(NamesConfig config, Random random)
    {
        if (config.ranks.isEmpty() || random.nextDouble() >= config.rankChance)
        {
            return null;
        }

        double totalWeight = 0;
        for (RankEntry rank : config.ranks)
        {
            totalWeight += Math.max(0, rank.weight);
        }

        if (totalWeight <= 0)
        {
            return config.ranks.get(random.nextInt(config.ranks.size()));
        }

        double target = random.nextDouble() * totalWeight;
        double current = 0;
        for (RankEntry rank : config.ranks)
        {
            current += Math.max(0, rank.weight);
            if (target <= current)
            {
                return rank;
            }
        }

        return config.ranks.get(config.ranks.size() - 1);
    }

    public static String formatName(String username, RankEntry rank)
    {
        if (rank == null || rank.prefix == null || rank.prefix.isEmpty())
        {
            return username;
        }

        String color = rank.color != null ? rank.color : "";
        if (!color.isEmpty())
        {
            return rank.prefix + color + username;
        }

        return rank.prefix + username;
    }

    public static String extractColorFromPrefix(String prefix)
    {
        if (prefix == null || prefix.isEmpty())
        {
            return "";
        }

        Matcher matcher = COLOR_PATTERN.matcher(prefix);
        if (matcher.find())
        {
            return matcher.group(1);
        }

        return "";
    }

    public static NamesConfig loadConfig() throws Exception
    {
        File file = getFile();
        if (!file.exists())
        {
            createDefaultFile(file);
        }

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))
        {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (!rootElement.isJsonObject())
            {
                throw new IllegalStateException("Root element in " + file.getName() + " is not a JSON object");
            }

            JsonObject root = rootElement.getAsJsonObject();
            NamesConfig config = new NamesConfig();

            if (root.has("settings") && root.get("settings").isJsonObject())
            {
                JsonObject settings = root.getAsJsonObject("settings");
                if (settings.has("rank_chance"))
                {
                    config.rankChance = settings.get("rank_chance").getAsDouble();
                }
            }

            if (root.has("ranks") && root.get("ranks").isJsonArray())
            {
                for (JsonElement el : root.getAsJsonArray("ranks"))
                {
                    if (el.isJsonObject())
                    {
                        JsonObject obj = el.getAsJsonObject();
                        RankEntry rank = new RankEntry();
                        rank.id = obj.has("id") ? obj.get("id").getAsString() : "";
                        rank.prefix = obj.has("prefix") ? obj.get("prefix").getAsString() : "";
                        if (obj.has("color"))
                        {
                            rank.color = obj.get("color").getAsString();
                        }
                        else
                        {
                            rank.color = extractColorFromPrefix(rank.prefix);
                        }
                        rank.weight = obj.has("weight") ? obj.get("weight").getAsDouble() : 1.0;
                        config.ranks.add(rank);
                    }
                }
            }

            if (root.has("names") && root.get("names").isJsonArray())
            {
                for (JsonElement el : root.getAsJsonArray("names"))
                {
                    if (el.isJsonPrimitive())
                    {
                        String name = el.getAsString().trim();
                        if (!name.isEmpty())
                        {
                            config.names.add(name);
                        }
                    }
                }
            }

            return config;
        }
    }

    private static void createDefaultFile(File file)
    {
        try
        {
            if (file.getParentFile() != null)
            {
                file.getParentFile().mkdirs();
            }

            JsonObject root = new JsonObject();
            JsonObject settings = new JsonObject();
            settings.addProperty("rank_chance", 0.5);
            root.add("settings", settings);

            JsonArray ranks = new JsonArray();

            JsonObject rank1 = new JsonObject();
            rank1.addProperty("id", "queso");
            rank1.addProperty("prefix", "[e[lQueso [r");
            rank1.addProperty("color", "[e");
            rank1.addProperty("weight", 1.0);
            ranks.add(rank1);

            JsonObject rank2 = new JsonObject();
            rank2.addProperty("id", "vip");
            rank2.addProperty("prefix", "[a[lVIP [r");
            rank2.addProperty("color", "[a");
            rank2.addProperty("weight", 2.0);
            ranks.add(rank2);

            JsonObject rank3 = new JsonObject();
            rank3.addProperty("id", "miembro");
            rank3.addProperty("prefix", "Miembro ");
            rank3.addProperty("color", "");
            rank3.addProperty("weight", 3.0);
            ranks.add(rank3);

            root.add("ranks", ranks);

            JsonArray names = new JsonArray();
            String[] defaultNames = new String[] {
                "Juanito123", "MatiasDev", "Carlos_99", "PlayerOne",
                "GamerPro", "Steve", "Alex", "CheesyBoy", "NightRider",
                "ShadowNinja", "BlockMaster", "RedstoneKing"
            };
            for (String name : defaultNames)
            {
                names.add(name);
            }
            root.add("names", names);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))
            {
                gson.toJson(root, writer);
            }
        }
        catch (Exception e)
        {
            logError("Failed to create default random_names.json!", e);
        }
    }
}
