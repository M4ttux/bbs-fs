package mchorse.bbs_mod.particles.vanilla;

import com.google.gson.JsonParser;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleTextureData;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.AtlasManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.util.Atlases;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Static particle samples shared by the type picker and form thumbnails. */
public class VanillaParticlePreview
{
    private static final Map<Identifier, Optional<Preview>> PREVIEWS = new HashMap<>();

    public static void clearCache()
    {
        PREVIEWS.clear();
    }

    public static void render(UIContext context, Identifier particle, float x, float y, float size, int fallbackColor)
    {
        render(context, particle, null, x, y, size, fallbackColor);
    }

    public static void render(UIContext context, Identifier particle, String args, float x, float y, float size, int fallbackColor)
    {
        if (size <= 0)
        {
            return;
        }

        if (particle == null)
        {
            context.batcher.scaledIcon(Icons.PARTICLE, fallbackColor, x, y, size);
            return;
        }

        Preview preview = PREVIEWS.computeIfAbsent(particle, VanillaParticlePreview::loadPreview).orElse(null);

        if (preview != null)
        {
            SpriteAtlasTexture atlas = getAtlas(preview.atlas());

            if (atlas != null)
            {
                /* Resolve current UVs rather than keeping sprites across atlas reloads. */
                Sprite sprite = atlas.getSprite(preview.sprite());

                if (sprite != null && sprite != atlas.getMissingSprite())
                {
                    int color = preview.color();
                    Integer overrideColor = parseColor(args);

                    if (overrideColor != null)
                    {
                        color = overrideColor;
                    }

                    context.batcher.drawSprite(sprite, x, y, size, size, color);
                    return;
                }
            }
        }

        /* Custom model particles and modded types without a sprite still keep their slot. */
        context.batcher.scaledIcon(Icons.PARTICLE, fallbackColor, x, y, size);
    }

    public static Integer parseColor(String args)
    {
        if (args == null || args.trim().isEmpty() || args.startsWith("{"))
        {
            return null;
        }

        try
        {
            String[] parts = args.trim().split("\\s+");

            StatusEffect effect = Registries.STATUS_EFFECT.get(Identifier.of(parts[0].toLowerCase()));

            if (effect != null)
            {
                return 0xFF000000 | effect.getColor();
            }

            if (parts[0].startsWith("#") || parts[0].startsWith("0x") || parts[0].startsWith("0X"))
            {
                String hex = parts[0].startsWith("#") ? parts[0].substring(1) : parts[0].substring(2);

                return 0xFF000000 | Integer.parseInt(hex, 16);
            }

            if (parts.length >= 3)
            {
                float r = Float.parseFloat(parts[0]);
                float g = Float.parseFloat(parts[1]);
                float b = Float.parseFloat(parts[2]);
                int ir = (int) (Math.min(Math.max(r, 0F), 1F) * 255F);
                int ig = (int) (Math.min(Math.max(g, 0F), 1F) * 255F);
                int ib = (int) (Math.min(Math.max(b, 0F), 1F) * 255F);

                return 0xFF000000 | (ir << 16) | (ig << 8) | ib;
            }

            return 0xFF000000 | Integer.parseInt(parts[0]);
        }
        catch (Exception ignored)
        {
        }

        return null;
    }

    private static SpriteAtlasTexture getAtlas(Identifier atlasId)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null)
        {
            return null;
        }

        AbstractTexture texture = mc.getTextureManager().getTexture(atlasId);

        if (texture instanceof SpriteAtlasTexture atlas)
        {
            return atlas;
        }

        try
        {
            AtlasManager manager = mc.getAtlasManager();

            if (manager != null)
            {
                SpriteAtlasTexture atlas = manager.getAtlasTexture(getDefinitionId(atlasId));

                if (atlas != null)
                {
                    return atlas;
                }
            }
        }
        catch (Exception ignored)
        {
        }

        return null;
    }

    private static Identifier getDefinitionId(Identifier atlasId)
    {
        if (atlasId.equals(SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE))
        {
            return Atlases.PARTICLES;
        }
        else if (atlasId.equals(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE))
        {
            return Atlases.BLOCKS;
        }
        else if (atlasId.equals(SpriteAtlasTexture.ITEMS_ATLAS_TEXTURE))
        {
            return Atlases.ITEMS;
        }

        return atlasId;
    }

    private static Optional<Preview> loadPreview(Identifier particle)
    {
        int color = Colors.WHITE;

        if (particle.getNamespace().equals("minecraft"))
        {
            /* These types use block/item models or emit another particle instead of owning sprites.
             * Argument-dependent types use a representative sample, without changing form settings. */
            String blockSprite = switch (particle.getPath())
            {
                case "block", "block_crumble", "dust_pillar" -> "block/stone";
                case "block_marker" -> "item/barrier";
                case "item", "item_snowball" -> "item/snowball";
                case "item_slime" -> "item/slime_ball";
                case "item_cobweb" -> "block/cobweb";
                default -> null;
            };

            if (blockSprite != null)
            {
                Identifier atlasId = blockSprite.startsWith("item/")
                    ? SpriteAtlasTexture.ITEMS_ATLAS_TEXTURE
                    : SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;

                return Optional.of(new Preview(atlasId, Identifier.of(blockSprite), color));
            }

            color = sampleColor(particle.getPath());

            if (particle.getPath().equals("explosion_emitter"))
            {
                particle = Identifier.of("explosion");
            }
            else if (particle.getPath().startsWith("gust_emitter"))
            {
                particle = Identifier.of("gust");
            }
        }

        Identifier definition = Identifier.of(particle.getNamespace(), "particles/" + particle.getPath() + ".json");
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(definition);

        if (resource.isPresent())
        {
            try (Reader reader = resource.get().getReader())
            {
                List<Identifier> textures = ParticleTextureData.load(JsonParser.parseReader(reader).getAsJsonObject()).getTextureList();

                if (textures != null && !textures.isEmpty())
                {
                    /* A middle frame is readable even for effects which start with an empty frame. */
                    return Optional.of(new Preview(SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE, textures.get(textures.size() / 2), color));
                }
            }
            catch (IOException | RuntimeException e)
            {
                /* A missing or malformed resource-pack definition must not prevent picking a type. */
            }
        }

        return Optional.empty();
    }

    private static int sampleColor(String particle)
    {
        /* Vanilla applies these tints in particle factories, not in the texture definitions.
         * Parameterized/random colors are representative defaults for the type picker. */
        return switch (particle)
        {
            case "dust" -> 0xFFFF3030;
            case "dust_color_transition" -> 0xFF55CCBB;
            case "falling_dust" -> 0xFFDBCAA0;
            case "smoke", "large_smoke", "ash" -> 0xFF888888;
            case "dust_plume" -> 0xFFBCA795;
            case "dripping_water", "falling_water", "dripping_dripstone_water", "falling_dripstone_water",
                "rain", "splash", "fishing" -> 0xFF4060FF;
            case "dripping_lava", "falling_lava", "landing_lava", "dripping_dripstone_lava", "falling_dripstone_lava" -> 0xFFFF6600;
            case "dripping_honey", "falling_honey", "landing_honey", "falling_nectar" -> 0xFFFFB52E;
            case "dripping_obsidian_tear", "falling_obsidian_tear", "landing_obsidian_tear" -> 0xFF8245E6;
            case "portal", "reverse_portal", "dragon_breath", "witch" -> 0xFFB34DE6;
            case "happy_villager", "composter", "totem_of_undying" -> 0xFF66CC33;
            case "sneeze" -> 0xFFB3C733;
            case "falling_spore_blossom", "spore_blossom_air" -> 0xFF519B24;
            case "crimson_spore" -> 0xFFE66666;
            case "warped_spore" -> 0xFF19B3A6;
            case "mycelium" -> 0xFFAA88AA;
            case "underwater", "dolphin" -> 0xFF6699FF;
            case "squid_ink" -> 0xFF333344;
            case "glow_squid_ink", "glow", "scrape" -> 0xFF66DDCC;
            case "wax_on" -> 0xFFFF994D;
            case "note" -> 0xFF66CC33;
            default -> Colors.WHITE;
        };
    }

    private record Preview(Identifier atlas, Identifier sprite, int color)
    {}
}
