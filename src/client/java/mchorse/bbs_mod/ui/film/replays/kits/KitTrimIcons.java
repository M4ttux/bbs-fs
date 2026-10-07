package mchorse.bbs_mod.ui.film.replays.kits;

import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class KitTrimIcons
{
    private static final Map<String, ItemStack> MATERIALS = new HashMap<>();
    private static final Map<String, ItemStack> PATTERNS = new HashMap<>();

    public static ItemStack material(String id)
    {
        if (id == null || id.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        if (MATERIALS.containsKey(id))
        {
            return MATERIALS.get(id);
        }

        ItemStack stack = resolveMaterial(id);

        MATERIALS.put(id, stack);

        return stack;
    }

    private static ItemStack resolveMaterial(String id)
    {
        String path = id;
        String namespace = "minecraft";

        if (id.contains(":"))
        {
            String[] parts = id.split(":", 2);

            namespace = parts[0];
            path = parts[1];
        }

        if (!namespace.equals("minecraft"))
        {
            return ItemStack.EMPTY;
        }

        String itemPath = switch (path)
        {
            case "amethyst" -> "amethyst_shard";
            case "copper" -> "copper_ingot";
            case "diamond" -> "diamond";
            case "emerald" -> "emerald";
            case "gold" -> "gold_ingot";
            case "iron" -> "iron_ingot";
            case "lapis" -> "lapis_lazuli";
            case "netherite" -> "netherite_ingot";
            case "quartz" -> "quartz";
            case "redstone" -> "redstone";
            case "resin" -> "resin_brick";
            default -> null;
        };

        if (itemPath == null)
        {
            return ItemStack.EMPTY;
        }

        Identifier itemId = Identifier.of(namespace, itemPath);

        if (!Registries.ITEM.containsId(itemId))
        {
            return ItemStack.EMPTY;
        }

        return new ItemStack(Registries.ITEM.get(itemId));
    }

    public static ItemStack pattern(String id)
    {
        if (id == null || id.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        if (PATTERNS.containsKey(id))
        {
            return PATTERNS.get(id);
        }

        ItemStack stack = resolvePattern(id);

        PATTERNS.put(id, stack);

        return stack;
    }

    private static ItemStack resolvePattern(String id)
    {
        String path = id;
        String namespace = "minecraft";

        if (id.contains(":"))
        {
            String[] parts = id.split(":", 2);

            namespace = parts[0];
            path = parts[1];
        }

        Identifier itemId = Identifier.of(namespace, path + "_armor_trim_smithing_template");

        if (!Registries.ITEM.containsId(itemId))
        {
            return ItemStack.EMPTY;
        }

        return new ItemStack(Registries.ITEM.get(itemId));
    }

    public static void draw(UIContext context, ItemStack stack, int x, int y)
    {
        if (stack == null || stack.isEmpty())
        {
            return;
        }

        org.joml.Matrix3x2fStack matrices = context.batcher.getContext().getMatrices();
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

        matrices.pushMatrix();
        consumers.setUI(true);
        context.batcher.getContext().drawItem(stack, x, y);
        consumers.setUI(false);
        matrices.popMatrix();
    }
}
