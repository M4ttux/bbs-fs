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
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

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

        return raw
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
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        int color = this.form.color.get().getARGBColor();
        String text = StringUtils.processColoredText(this.resolveDynamicText(this.form.text.get(), null));
        /* The interface draws a unit of the layout over as many pixels as it is scaled by. */
        FontRenderer font = this.getFont((float) MinecraftClient.getInstance().getWindow().getScaleFactor());
        FontRenderer previous = context.batcher.setFont(font);

        try
        {
            List<String> wrap = font.wrap(text, x2 - x1 - 4);

            int th = font.getHeight();
            int lineHeight = th + 4;
            int h = th + (wrap.size() - 1) * lineHeight;
            int y = (y2 + y1) / 2 - h / 2;

            for (String s : wrap)
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
        String content = StringUtils.processColoredText(this.resolveDynamicText(this.form.text.get(), context.entity));
        float transition = context.getTransition();
        int w = renderer.getWidth(content) - 1;
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
                content,
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
            content,
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
        String content = StringUtils.processColoredText(this.resolveDynamicText(this.form.text.get(), context.entity));
        List<String> lines = FontRenderer.wrap(renderer, content, this.form.max.get());

        if (lines.size() <= 1)
        {
            this.renderString(context, consumers, font, light);

            return;
        }

        for (int i = 0; i < lines.size(); i++)
        {
            lines.set(i, lines.get(i).trim());
        }

        for (String line : lines)
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

            for (String line : lines)
            {
                int x2 = x + (this.form.anchorLines.get() ? (int) ((w - renderer.getWidth(line)) * this.form.anchorX.get()) : 0);

                renderer.draw(
                    line,
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

        for (String line : lines)
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