package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.fonts.FontManager;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.OverlayBlend;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LabelFormRenderer extends FormRenderer<LabelForm>
{
    /* ----------------------------------------------------------------------------------------
     * 1.21.11 render: the label background box used GameRenderer::getPositionColorProgram via
     * RenderSystem.setShader + BufferRenderer.drawWithGlobalProgram (both removed). It is now drawn
     * through a BBS-owned POSITION_COLOR pipeline wrapped in a RenderLayer.
     * ---------------------------------------------------------------------------------------- */
    private static final RenderPipeline SHADOW_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/label_shadow"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    private static RenderLayer shadowLayer;

    private static RenderLayer getShadowLayer()
    {
        if (shadowLayer == null)
        {
            shadowLayer = RenderLayer.of(BBSMod.MOD_ID + "_label_shadow",
                RenderSetup.builder(SHADOW_PIPELINE).translucent().build());
        }

        return shadowLayer;
    }

    public static void fillQuad(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float a)
    {
        Matrix4f matrix4f = stack.peek().getPositionMatrix();

        /* 1 - BR, 2 - BL, 3 - TL, 4 - TR */
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a).texture(0F, 0F);
        builder.vertex(matrix4f, x2, y2, z2).color(r, g, b, a).texture(0F, 0F);
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a).texture(0F, 0F);
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a).texture(0F, 0F);
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a).texture(0F, 0F);
        builder.vertex(matrix4f, x4, y4, z4).color(r, g, b, a).texture(0F, 0F);
    }

    private float nametagAlpha = 1F;

    public LabelFormRenderer(LabelForm form)
    {
        super(form);
    }

    /**
     * The label's own font, or the default one when it doesn't have (or can't load) one.
     * The scale says how many screen pixels a unit of the layout covers where it's about
     * to be drawn - see {@link FontManager#get(Link, int, float)}.
     */
    private FontRenderer getFont(float scale)
    {
        FontRenderer font = BBSModClient.getFonts().get(this.form.font.get(), this.form.fontSize.get(), scale);

        return font == null ? Batcher2D.getDefaultTextRenderer() : font;
    }

    private int getLineHeight(FontRenderer font)
    {
        int lineHeight = this.form.lineHeight.get();

        return lineHeight > 0 ? lineHeight : font.getLineHeight();
    }

    /**
     * Resolve dynamic data placeholders in the label text.
     *
     * <p>Health tokens ({@code {hp}}, {@code {hp_int}}, {@code {max_hp}}) are read from the
     * <em>replay actor</em> ({@code entity}) when one is available so that they update in real time
     * as the replay takes damage. Hunger and XP level have no equivalent in
     * {@link mchorse.bbs_mod.forms.entities.IEntity}, so those always come from the local player.
     *
     * <p>Supported tokens:<br>
     * {@code {hp}}              — current health (decimal, from actor),<br>
     * {@code {hp_int}}          — current health rounded up to whole hearts (from actor),<br>
     * {@code {hp_colored}}      — integer health with color code (&sect;2 green &gt;60%, &sect;6 orange &gt;30%, &sect;4 red &le;30%),<br>
     * {@code {hp_color}}        — color code alone based on health ratio,<br>
     * {@code {max_hp}}          — maximum health (decimal, from actor),<br>
     * {@code {hunger}}          — food level (0–20, from local player),<br>
     * {@code {xp_level}}        — experience level (from local player).
     * </p>
     *
     * <p>When neither entity nor local player is available the tokens are replaced
     * with {@code "?"} so the label still renders.</p>
     *
     * @param raw    the raw label string
     * @param entity the replay actor currently being rendered, or {@code null} in UI previews
     */
    private String resolveDynamicText(String raw, mchorse.bbs_mod.forms.entities.IEntity entity)
    {
        if (raw == null || raw.isEmpty() || !raw.contains("{"))
        {
            return raw;
        }

        net.minecraft.client.network.ClientPlayerEntity player = MinecraftClient.getInstance().player;

        /* Health — prefer the replay actor so it reflects damage taken by the actor, not the viewer. */
        float currentHp = entity != null ? entity.getHealth() : player != null ? player.getHealth() : 20F;
        float maxHpVal  = entity != null ? entity.getMaxHealth() : player != null ? player.getMaxHealth() : 20F;
        float ratio     = maxHpVal > 0F ? (currentHp / maxHpVal) : (currentHp / 20F);

        /* Dynamic color code: green ([2 / §2) for high health, orange ([6 / §6) for mid, red ([4 / §4) for low */
        String colorCode = ratio > 0.6F ? "\u00A72" : ratio > 0.3F ? "\u00A76" : "\u00A74";

        String hp        = entity != null ? String.valueOf(Math.ceil(entity.getHealth()))
                         : player != null ? String.valueOf(Math.ceil(player.getHealth())) : "?";
        String hpInt     = entity != null ? String.valueOf((int) Math.ceil(entity.getHealth()))
                         : player != null ? String.valueOf((int) Math.ceil(player.getHealth())) : "?";
        String maxHp     = entity != null ? String.valueOf(Math.ceil(entity.getMaxHealth()))
                         : player != null ? String.valueOf(Math.ceil(player.getMaxHealth())) : "?";
        String hpColored = colorCode + hpInt;

        /* Hunger and XP — IEntity has no equivalent; always read from local player. */
        String hunger  = player != null ? String.valueOf(player.getHungerManager().getFoodLevel()) : "?";
        String xpLevel = player != null ? String.valueOf(player.experienceLevel) : "?";

        String resolved = raw
            .replace("{hp_colored}",     hpColored)
            .replace("{hp_int_colored}", hpColored)
            .replace("{hp_int_color}",   hpColored)
            .replace("{hp_color_int}",   hpColored)
            .replace("{hp_color}",       colorCode)
            .replace("{hp}",             hp)
            .replace("{hp_int}",         hpInt)
            .replace("{max_hp}",         maxHp)
            .replace("{hunger}",         hunger)
            .replace("{xp_level}",       xpLevel);

        return applyGradients(resolved, this.form.smoothGradient.get());
    }

    private static final Pattern GRADIENT_PATTERN =
        Pattern.compile("(?i)\\{(?:g|gradient)\\s*:\\s*(.*?)\\}");

    private static final int[] MC_COLORS = new int[] {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
        0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
        0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private static final char[] MC_COLOR_CODES = new char[] {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    /**
     * Resolves gradient tags like {@code {g: [4Hola[2}} or {@code {gradient: [4Hola[2}} or
     * {@code {g: [4, [2, Hola}}.
     */
    private static String applyGradients(String raw, boolean smooth)
    {
        if (raw == null || raw.isEmpty() || !raw.contains("{"))
        {
            return raw;
        }

        StringBuilder sb = new StringBuilder();
        int cursor = 0;
        Matcher m = GRADIENT_PATTERN.matcher(raw);

        while (m.find())
        {
            sb.append(raw, cursor, m.start());

            String inner = m.group(1);
            int matchEnd = m.end();

            /* When closed with double braces (e.g. {g: [4Hola[2}}), consume the extra '}' */
            if (matchEnd < raw.length() && raw.charAt(matchEnd) == '}')
            {
                matchEnd++;
            }

            sb.append(processGradientTag(inner, smooth));
            cursor = matchEnd;
        }

        sb.append(raw.substring(cursor));
        return sb.toString();
    }

    private static String processGradientTag(String inner, boolean smooth)
    {
        if (inner == null) return "";
        inner = inner.trim();

        int c1 = -1;
        int c2 = -1;
        String text = null;

        /* Comma-separated: e.g. "[4, [2, Hola" */
        if (inner.contains(","))
        {
            String[] parts = inner.split(",", 3);
            if (parts.length == 3)
            {
                c1 = parseColor(parts[0]);
                c2 = parseColor(parts[1]);
                text = parts[2].trim();
            }
        }

        /* Enclosed format: <start_color><text><end_color>, e.g. "[4Hola[2" */
        if (c1 == -1 || c2 == -1 || text == null)
        {
            int startColorEnd = findColorEnd(inner, 0);
            if (startColorEnd > 0)
            {
                c1 = parseColor(inner.substring(0, startColorEnd));

                int endColorStart = findColorStart(inner);
                if (endColorStart > startColorEnd)
                {
                    c2 = parseColor(inner.substring(endColorStart));
                    text = inner.substring(startColorEnd, endColorStart);
                }
            }
        }

        if (c1 != -1 && c2 != -1 && text != null)
        {
            return generateGradient(c1, c2, text, smooth);
        }

        return inner;
    }

    private static int findColorEnd(String str, int from)
    {
        if (from >= str.length()) return -1;

        char first = str.charAt(from);
        if (first == '[' || first == '\u00A7')
        {
            int next = from + 1;
            if (next < str.length() && str.charAt(next) == '#')
            {
                int closeBracket = str.indexOf(']', next);
                if (closeBracket > next && closeBracket <= next + 8)
                {
                    return closeBracket + 1;
                }
                return Math.min(str.length(), next + 7);
            }
            if (next < str.length())
            {
                if (next + 1 < str.length() && str.charAt(next + 1) == ']')
                {
                    return next + 2;
                }
                return next + 1;
            }
        }
        else if (first == '#')
        {
            return Math.min(str.length(), from + 7);
        }

        return -1;
    }

    private static int findColorStart(String str)
    {
        int len = str.length();
        if (len < 2) return -1;

        int end = len;
        while (end > 0 && (str.charAt(end - 1) == ']' || str.charAt(end - 1) == '}'))
        {
            end--;
        }

        for (int i = end - 1; i >= Math.max(0, end - 10); i--)
        {
            char ch = str.charAt(i);
            if (ch == '[' || ch == '\u00A7' || ch == '#')
            {
                return i;
            }
        }

        return -1;
    }

    private static int parseColor(String str)
    {
        if (str == null) return -1;
        str = str.trim();

        while (str.startsWith("[") || str.startsWith("\u00A7"))
        {
            str = str.substring(1);
        }
        while (str.endsWith("]") || str.endsWith("}"))
        {
            str = str.substring(0, str.length() - 1);
        }
        str = str.trim();
        if (str.startsWith("#"))
        {
            str = str.substring(1);
        }

        if (str.length() == 1)
        {
            char c = Character.toLowerCase(str.charAt(0));
            int idx = "0123456789abcdef".indexOf(c);
            if (idx >= 0)
            {
                return MC_COLORS[idx];
            }
        }
        if (str.length() == 6)
        {
            try
            {
                return Integer.parseInt(str, 16);
            }
            catch (Exception ignored) {}
        }
        if (str.length() == 3)
        {
            try
            {
                int r = Integer.parseInt(str.substring(0, 1), 16) * 17;
                int g = Integer.parseInt(str.substring(1, 2), 16) * 17;
                int b = Integer.parseInt(str.substring(2, 3), 16) * 17;
                return (r << 16) | (g << 8) | b;
            }
            catch (Exception ignored) {}
        }

        return -1;
    }

    private static String generateGradient(int c1, int c2, String text, boolean smooth)
    {
        if (text == null || text.isEmpty())
        {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int len = text.length();

        int visibleChars = 0;
        for (int i = 0; i < len; i++)
        {
            if (!Character.isWhitespace(text.charAt(i)))
            {
                visibleChars++;
            }
        }

        int visibleIndex = 0;
        char lastCode = ' ';

        for (int i = 0; i < len; i++)
        {
            char ch = text.charAt(i);

            if (Character.isWhitespace(ch))
            {
                sb.append(ch);
                continue;
            }

            float t = visibleChars > 1 ? (float) visibleIndex / (visibleChars - 1) : 0F;

            if (smooth)
            {
                int rgb = interpolateRgb(c1, c2, t);
                sb.append("\u00A7#").append(String.format("%06X", rgb & 0xFFFFFF));
            }
            else
            {
                char code = getGradientCode(c1, c2, t);
                if (code != lastCode)
                {
                    sb.append('\u00A7').append(code);
                    lastCode = code;
                }
            }

            sb.append(ch);
            visibleIndex++;
        }

        /* Reset formatting so subsequent text returns to the label's default color */
        sb.append("\u00A7r");
        return sb.toString();
    }

    private static int interpolateRgb(int c1, int c2, float t)
    {
        if (t <= 0F) return c1 & 0xFFFFFF;
        if (t >= 1F) return c2 & 0xFFFFFF;

        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        int r = Math.min(255, Math.max(0, Math.round(r1 + t * (r2 - r1))));
        int g = Math.min(255, Math.max(0, Math.round(g1 + t * (g2 - g1))));
        int b = Math.min(255, Math.max(0, Math.round(b1 + t * (b2 - b1))));

        return (r << 16) | (g << 8) | b;
    }

    public static Text parseToText(String content)
    {
        if (content == null || content.isEmpty())
        {
            return Text.empty();
        }

        MutableText root = Text.empty();
        Style style = Style.EMPTY;
        StringBuilder currentChunk = new StringBuilder();
        int len = content.length();

        for (int i = 0; i < len; i++)
        {
            char ch = content.charAt(i);

            if (ch == '\\' && i + 1 < len && content.charAt(i + 1) == '[')
            {
                currentChunk.append('[');
                i++;
                continue;
            }

            if ((ch == '\u00A7' || ch == '[') && i + 1 < len)
            {
                char next = content.charAt(i + 1);

                /* 24-bit hex color code: §#RRGGBB or [#RRGGBB] */
                if (next == '#' && i + 7 < len)
                {
                    String hex = content.substring(i + 2, i + 8);
                    try
                    {
                        int rgb = Integer.parseInt(hex, 16);

                        if (currentChunk.length() > 0)
                        {
                            root.append(Text.literal(currentChunk.toString()).setStyle(style));
                            currentChunk.setLength(0);
                        }

                        style = style.withColor(TextColor.fromRgb(rgb));
                        i += 7;
                        if (i + 1 < len && content.charAt(i + 1) == ']')
                        {
                            i++;
                        }
                        continue;
                    }
                    catch (NumberFormatException ignored) {}
                }

                /* Legacy formatting code: §0-§f, §k-§o, §r or [0-[f, [k-[o, [r */
                Formatting formatting = Formatting.byCode(next);
                if (formatting != null)
                {
                    if (currentChunk.length() > 0)
                    {
                        root.append(Text.literal(currentChunk.toString()).setStyle(style));
                        currentChunk.setLength(0);
                    }

                    if (formatting == Formatting.RESET)
                    {
                        style = Style.EMPTY;
                    }
                    else if (formatting.isColor())
                    {
                        style = Style.EMPTY.withColor(formatting);
                    }
                    else if (formatting.isModifier())
                    {
                        style = style.withFormatting(formatting);
                    }

                    i++;
                    if (i + 1 < len && content.charAt(i + 1) == ']')
                    {
                        i++;
                    }
                    continue;
                }
            }

            currentChunk.append(ch);
        }

        if (currentChunk.length() > 0)
        {
            root.append(Text.literal(currentChunk.toString()).setStyle(style));
        }

        return root;
    }

    private static OrderedText toShadow(OrderedText text, int shadowRgb)
    {
        TextColor color = TextColor.fromRgb(shadowRgb & 0xFFFFFF);

        return (visitor) -> text.accept((charIndex, style, codePoint) ->
        {
            return visitor.accept(charIndex, style.withColor(color), codePoint);
        });
    }

    private static char getGradientCode(int c1, int c2, float t)
    {
        if (t <= 0F) return getClosestMCCode(c1);
        if (t >= 1F) return getClosestMCCode(c2);

        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        float[] hsv1 = rgbToHsv(r1, g1, b1);
        float[] hsv2 = rgbToHsv(r2, g2, b2);

        int rgb;
        if (hsv1[1] > 0.15F && hsv2[1] > 0.15F)
        {
            float h1 = hsv1[0];
            float h2 = hsv2[0];
            float diff = h2 - h1;

            if (diff > 0.5F) diff -= 1.0F;
            else if (diff < -0.5F) diff += 1.0F;

            float h = (h1 + t * diff) % 1.0F;
            if (h < 0F) h += 1.0F;

            float s = hsv1[1] + t * (hsv2[1] - hsv1[1]);
            float v = hsv1[2] + t * (hsv2[2] - hsv1[2]);

            rgb = hsvToRgb(h, s, v);
        }
        else
        {
            int r = (int) (r1 + t * (r2 - r1));
            int g = (int) (g1 + t * (g2 - g1));
            int b = (int) (b1 + t * (b2 - b1));
            rgb = (r << 16) | (g << 8) | b;
        }

        return getClosestMCCode(rgb);
    }

    private static char getClosestMCCode(int rgb)
    {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        int bestIndex = 0;
        long bestDist = Long.MAX_VALUE;

        for (int i = 0; i < MC_COLORS.length; i++)
        {
            int cr = (MC_COLORS[i] >> 16) & 0xFF;
            int cg = (MC_COLORS[i] >> 8) & 0xFF;
            int cb = MC_COLORS[i] & 0xFF;

            int rmean = (r + cr) / 2;
            int dr = r - cr;
            int dg = g - cg;
            int db = b - cb;
            long dist = (((512 + rmean) * dr * dr) >> 8) + 4L * dg * dg + (((767 - rmean) * db * db) >> 8);

            if (dist < bestDist)
            {
                bestDist = dist;
                bestIndex = i;
            }
        }

        return MC_COLOR_CODES[bestIndex];
    }

    private static float[] rgbToHsv(int r, int g, int b)
    {
        float rf = r / 255F;
        float gf = g / 255F;
        float bf = b / 255F;

        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;

        float h = 0F;
        float s = max == 0F ? 0F : delta / max;
        float v = max;

        if (delta != 0F)
        {
            if (max == rf)
            {
                h = ((gf - bf) / delta) % 6F;
            }
            else if (max == gf)
            {
                h = ((bf - rf) / delta) + 2F;
            }
            else
            {
                h = ((rf - gf) / delta) + 4F;
            }
            h /= 6F;
            if (h < 0F) h += 1F;
        }

        return new float[] {h, s, v};
    }

    private static int hsvToRgb(float h, float s, float v)
    {
        float r = v, g = v, b = v;

        if (s > 0F)
        {
            h = (h % 1F) * 6F;
            int i = (int) h;
            float f = h - i;
            float p = v * (1F - s);
            float q = v * (1F - s * f);
            float t = v * (1F - s * (1F - f));

            switch (i)
            {
                case 0: r = v; g = t; b = p; break;
                case 1: r = q; g = v; b = p; break;
                case 2: r = p; g = v; b = t; break;
                case 3: r = p; g = q; b = v; break;
                case 4: r = t; g = p; b = v; break;
                default: r = v; g = p; b = q; break;
            }
        }

        return (((int) (r * 255F + 0.5F)) << 16) | (((int) (g * 255F + 0.5F)) << 8) | ((int) (b * 255F + 0.5F));
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        int color = this.form.color.get().getARGBColor();
        Text text = parseToText(this.resolveDynamicText(this.form.text.get(), null));
        /* The interface draws a unit of the layout over as many pixels as it is scaled by. */
        FontRenderer font = this.getFont((float) MinecraftClient.getInstance().getWindow().getScaleFactor());
        FontRenderer previous = context.batcher.setFont(font);

        try
        {
            List<OrderedText> wrap = font.wrap(text, x2 - x1 - 4);

            int th = font.getHeight();
            int lineHeight = th + 4;
            int h = th + Math.max(0, wrap.size() - 1) * lineHeight;
            int y = (y2 + y1) / 2 - h / 2;

            for (OrderedText s : wrap)
            {
                context.batcher.textShadow(s, x1 + 2, y, color);

                y += lineHeight;
            }
        }
        finally
        {
            context.batcher.setFont(previous);
        }
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        /* The film editor's stencil pass re-renders the film with a stencilMap on every frame the
         * mouse spends over the viewport. On 1.21.1 the picking branch here hijacked these draws
         * onto the picker program aimed at the raw-bound stencil FBO; that hijack has no 1.21.5+
         * equivalent yet, so a picking "draw" went through the ordinary vanilla text layer — whose
         * RenderLayer.draw targets MAIN_TARGET, the visible frame (StencilFormFramebuffer redirects
         * only BBSPickerRenderer draws). Combined with the old light = 0 it painted a lightmap-dark
         * copy of the text OVER the real one whenever the viewport was hovered: "the label is dark,
         * the colour applies badly". Until label picking moves onto BBSPickerRenderer (the same debt
         * as block/item/mob forms), the picking pass must draw nothing at all: the stencil keeps its
         * indices (updateStencilMap does not depend on what was drawn), and the label simply is not
         * pixel-pickable — which it already was not. */
        if (context.isPicking())
        {
            return;
        }

        context.stack.push();

        this.nametagAlpha = 1F;

        if (this.form.nametag.get() && context.entity != null && context.entity.isSneaking())
        {
            context.stack.translate(0F, -0.5F, 0F);
            this.nametagAlpha = 0.125F;
        }

        if (this.form.billboard.get())
        {
            MatrixStackUtils.billboard(context.stack);
        }

        FontRenderer font = this.getFont(FontManager.MAX_DETAIL);
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        float scale = 1F / 16F;
        int light = context.light;

        MatrixStackUtils.scaleStack(context.stack, scale, -scale, scale);

        /* TODO(1.21.11 render): RenderSystem.disableCull/enableCull removed; cull is now per-pipeline
         * state. Text renders through the vanilla text RenderLayer which sets its own cull. */

        /* The whole label (text + background) records as ONE deferred group: its parts rely on
         * each other's depth (glyphs over the background quad), so they replay together in
         * original order, while the group as a whole sorts against other translucent forms. */
        boolean grouped = FormTranslucentQueue.isActive();

        if (grouped)
        {
            Vector3f origin = context.stack.peek().getPositionMatrix().getTranslation(new Vector3f());

            FormTranslucentQueue.beginGroup(new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(origin), false);
        }

        if (this.form.max.get() <= 10)
        {
            this.renderString(context, consumers, font, light);
        }
        else
        {
            this.renderLimitedString(context, consumers, font, light);
        }

        if (grouped)
        {
            FormTranslucentQueue.endGroup();
        }

        CustomVertexConsumerProvider.clearRunnables();

        /* TODO(1.21.11 render): RenderSystem.enableDepthTest/enableCull removed (per-pipeline now). */

        context.stack.pop();
    }

    private void renderString(FormRenderingContext context, CustomVertexConsumerProvider consumers, FontRenderer font, int light)
    {
        TextRenderer renderer = font.getRenderer();
        Text text = parseToText(this.resolveDynamicText(this.form.text.get(), context.entity));
        OrderedText ordered = text.asOrderedText();
        float transition = context.getTransition();
        int w = renderer.getWidth(ordered) - 1;
        int h = font.getHeight();
        int x = (int) (-w * this.form.anchorX.get());
        int y = (int) (-h * this.form.anchorY.get());

        Color shadowColor = this.form.shadowColor.get().copy();
        Color color = new Color().set(context.color, true);

        FormColorBlend.blend(color, this.form.color.get());
        /* Text is a flat fill, so the CPU mix is exactly what the overlay texture would do. */
        OverlayBlend.apply(color, this.form.overlayColor.get());
        shadowColor.mul(context.color);

        shadowColor.a *= this.nametagAlpha;
        color.a *= this.nametagAlpha;

        if (shadowColor.a > 0)
        {
            context.stack.push();
            context.stack.translate(0F, 0F, -0.1F);
            renderer.draw(
                toShadow(ordered, shadowColor.getRGBColor()),
                x + this.form.shadowX.get(),
                y + this.form.shadowY.get(),
                shadowColor.getARGBColor(), false,
                context.stack.peek().getPositionMatrix(),
                consumers,
                TextRenderer.TextLayerType.NORMAL,
                0,
                light
            );
            context.stack.pop();
        }

        renderer.draw(
            ordered,
            x,
            y,
            color.getARGBColor(), false,
            context.stack.peek().getPositionMatrix(),
            consumers,
            TextRenderer.TextLayerType.NORMAL,
            0,
            light
        );

        /* TODO(1.21.11 render): RenderSystem.enableDepthTest removed (per-pipeline now). */

        consumers.draw();

        this.renderShadow(context, x, y, w, h);
    }

    private void renderLimitedString(FormRenderingContext context, CustomVertexConsumerProvider consumers, FontRenderer font, int light)
    {
        TextRenderer renderer = font.getRenderer();
        int lineHeight = this.getLineHeight(font);
        float transition = context.getTransition();
        int w = 0;
        int h = font.getHeight();
        Text text = parseToText(this.resolveDynamicText(this.form.text.get(), context.entity));
        List<OrderedText> lines = renderer.wrapLines(text, this.form.max.get());

        if (lines.size() <= 1)
        {
            this.renderString(context, consumers, font, light);

            return;
        }

        for (OrderedText line : lines)
        {
            w = Math.max(renderer.getWidth(line) - 1, w);
            h += lineHeight;
        }

        h -= lineHeight;

        int x = (int) (-w * this.form.anchorX.get());
        int y = (int) (-h * this.form.anchorY.get());
        int y2 = y;

        Color shadowColor = this.form.shadowColor.get().copy();

        shadowColor.mul(context.color);
        shadowColor.a *= this.nametagAlpha;

        if (shadowColor.a > 0)
        {
            context.stack.push();
            context.stack.translate(0F, 0F, -0.1F);

            for (OrderedText line : lines)
            {
                int x2 = x + (this.form.anchorLines.get() ? (int) ((w - renderer.getWidth(line)) * this.form.anchorX.get()) : 0);

                renderer.draw(
                    toShadow(line, shadowColor.getRGBColor()),
                    x2 + this.form.shadowX.get(),
                    y2 + this.form.shadowY.get(),
                    shadowColor.getARGBColor(), false,
                    context.stack.peek().getPositionMatrix(),
                    consumers,
                    TextRenderer.TextLayerType.NORMAL,
                    0,
                    light
                );

                y2 += lineHeight;
            }

            context.stack.pop();

            y2 = y;
        }

        Color cColor = new Color().set(context.color, true);

        FormColorBlend.blend(cColor, this.form.color.get());
        OverlayBlend.apply(cColor, this.form.overlayColor.get());
        cColor.a *= this.nametagAlpha;

        int color = cColor.getARGBColor();

        for (OrderedText line : lines)
        {
            int x2 = x + (this.form.anchorLines.get() ? (int) ((w - renderer.getWidth(line)) * this.form.anchorX.get()) : 0);

            renderer.draw(
                line,
                x2,
                y2,
                color, false,
                context.stack.peek().getPositionMatrix(),
                consumers,
                TextRenderer.TextLayerType.NORMAL,
                0,
                light
            );

            y2 += lineHeight;
        }

        consumers.draw();

        /* TODO(1.21.11 render): RenderSystem.enableDepthTest removed (per-pipeline now). */

        this.renderShadow(context, x, y, w, h);
    }

    private void renderShadow(FormRenderingContext context, int x, int y, int w, int h)
    {
        float offset = this.form.offset.get();
        Color color = this.form.background.get().copy();

        color.mul(context.color);
        color.a *= this.nametagAlpha;

        if (color.a <= 0)
        {
            return;
        }

        context.stack.push();
        context.stack.translate(0, 0, -0.2F);


        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        fillQuad(
            builder, context.stack,
            x + w + offset, y - offset, 0,
            x - offset, y - offset, 0,
            x - offset, y + h + offset, 0,
            x + w + offset, y + h + offset, 0,
            color.r, color.g, color.b, color.a
        );

        /* Was: enableBlend + enableDepthTest + setShader(getPositionColorProgram) +
         * drawWithGlobalProgram. The POSITION_COLOR pipeline now encodes blend + depth test. */
        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            getShadowLayer().draw(built);
        }

        context.stack.pop();
    }
}