package mchorse.bbs_mod.ui.film.replays.kits;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.GameRegistries;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KitPresets
{
    public static File getFolder()
    {
        File folder = new File(BBSMod.getGameFolder(), "config/bbs/kits");

        folder.mkdirs();

        return folder;
    }

    public static String sanitizeId(String id)
    {
        if (id == null)
        {
            return "preset";
        }

        String sanitized = id.toLowerCase().replaceAll("[^a-z0-9_-]", "_");

        return sanitized.isEmpty() ? "preset" : sanitized;
    }

    public static boolean exists(String id)
    {
        File file = new File(getFolder(), sanitizeId(id) + ".json");

        return file.exists();
    }

    public static List<String> list()
    {
        File folder = getFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null || files.length == 0)
        {
            return Collections.emptyList();
        }

        List<String> ids = new ArrayList<>();

        for (File f : files)
        {
            String name = f.getName();
            ids.add(name.substring(0, name.length() - 5));
        }

        Collections.sort(ids);

        return ids;
    }

    public static ResolvedKit load(String id) throws Exception
    {
        File file = new File(getFolder(), sanitizeId(id) + ".json");

        if (!file.exists())
        {
            throw new IllegalArgumentException("Preset file does not exist: " + file.getAbsolutePath());
        }

        JsonObject json;

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))
        {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }

        RegistryWrapper.WrapperLookup lookup = GameRegistries.lookup();

        if (lookup == null)
        {
            throw new IllegalStateException("Game registries lookup is null (no world loaded).");
        }

        DynamicOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, lookup);

        ItemStack head = decodeSlot(json, "head", ops);
        ItemStack chest = decodeSlot(json, "chest", ops);
        ItemStack legs = decodeSlot(json, "legs", ops);
        ItemStack feet = decodeSlot(json, "feet", ops);
        ItemStack mainHand = decodeSlot(json, "main_hand", ops);
        ItemStack offHand = decodeSlot(json, "off_hand", ops);

        return new ResolvedKit(head, chest, legs, feet, mainHand, offHand);
    }

    private static ItemStack decodeSlot(JsonObject json, String key, DynamicOps<JsonElement> ops)
    {
        if (!json.has(key) || json.get(key).isJsonNull())
        {
            return ItemStack.EMPTY;
        }

        try
        {
            return ItemStack.CODEC.parse(ops, json.get(key)).result().orElse(ItemStack.EMPTY);
        }
        catch (Exception e)
        {
            System.err.println("[BBS RandomKits] Failed to decode preset slot " + key + ": " + e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    public static void save(String id, String displayName, ResolvedKit kit) throws Exception
    {
        File file = new File(getFolder(), sanitizeId(id) + ".json");
        RegistryWrapper.WrapperLookup lookup = GameRegistries.lookup();

        if (lookup == null)
        {
            throw new IllegalStateException("Game registries lookup is null (no world loaded).");
        }

        DynamicOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, lookup);
        JsonObject json = new JsonObject();

        json.addProperty("name", displayName == null || displayName.isEmpty() ? id : displayName);

        encodeSlot(json, "head", kit.head(), ops);
        encodeSlot(json, "chest", kit.chest(), ops);
        encodeSlot(json, "legs", kit.legs(), ops);
        encodeSlot(json, "feet", kit.feet(), ops);
        encodeSlot(json, "main_hand", kit.mainHand(), ops);
        encodeSlot(json, "off_hand", kit.offHand(), ops);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))
        {
            gson.toJson(json, writer);
        }
    }

    private static void encodeSlot(JsonObject json, String key, ItemStack stack, DynamicOps<JsonElement> ops)
    {
        if (stack == null || stack.isEmpty())
        {
            return;
        }

        try
        {
            ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent((elem) -> json.add(key, elem));
        }
        catch (Exception e)
        {
            System.err.println("[BBS RandomKits] Failed to encode preset slot " + key + ": " + e.getMessage());
        }
    }

    public static ResolvedKit captureFromReplay(Replay replay)
    {
        if (replay == null)
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

        ReplayKeyframes k = replay.keyframes;
        ItemStack head = k.armorHead.interpolate(0F, ItemStack.EMPTY);
        ItemStack chest = k.armorChest.interpolate(0F, ItemStack.EMPTY);
        ItemStack legs = k.armorLegs.interpolate(0F, ItemStack.EMPTY);
        ItemStack feet = k.armorFeet.interpolate(0F, ItemStack.EMPTY);
        ItemStack offHand = k.offHand.interpolate(0F, ItemStack.EMPTY);

        int selected = k.selectedSlot.interpolate(0F, 0);

        if (selected < 0 || selected >= k.hotbar.size())
        {
            selected = 0;
        }

        ItemStack mainHand = k.hotbar.isEmpty() ? ItemStack.EMPTY : k.hotbar.get(selected).interpolate(0F, ItemStack.EMPTY);

        return new ResolvedKit(
            head == null ? ItemStack.EMPTY : head.copy(),
            chest == null ? ItemStack.EMPTY : chest.copy(),
            legs == null ? ItemStack.EMPTY : legs.copy(),
            feet == null ? ItemStack.EMPTY : feet.copy(),
            mainHand == null ? ItemStack.EMPTY : mainHand.copy(),
            offHand == null ? ItemStack.EMPTY : offHand.copy()
        );
    }
}
