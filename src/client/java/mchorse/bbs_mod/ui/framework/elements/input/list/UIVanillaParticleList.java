package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.particles.vanilla.VanillaParticlePreview;
import mchorse.bbs_mod.ui.forms.editors.utils.UIParticleSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.framework.tooltips.LabelTooltip;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Consumer;

/** Small, static samples: opening the picker never spawns or ticks world particles. */
public class UIVanillaParticleList extends UIStringList
{
    private static final int PREVIEW_SIZE = 16;

    public UIVanillaParticleList(Consumer<List<String>> callback)
    {
        super(callback);

        this.scroll.scrollItemSize = PREVIEW_SIZE + 4;
        this.tooltip(new LabelTooltip(IKey.EMPTY, 240, Direction.LEFT));
    }

    @Override
    protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
    {
        int h = this.scroll.scrollItemSize;
        int left = x + this.rowContentX(element);
        int top = y + (h - PREVIEW_SIZE) / 2;
        boolean hasParams = UIParticleSettings.acceptsParameters(element);

        VanillaParticlePreview.render(context, Identifier.of(element), left, top, PREVIEW_SIZE, RowStyle.iconColor(hover || selected));

        int iconX = this.area.ex() - 20;
        int rightLimit = hasParams ? iconX - 4 : this.area.ex() - 8;
        int textMaxW = rightLimit - (left + PREVIEW_SIZE + 4);
        String displayText = element;

        if (context.batcher.getFont().getWidth(displayText) > textMaxW)
        {
            displayText = context.batcher.getFont().limitToWidth(displayText, textMaxW);
        }

        context.batcher.textShadow(displayText, left + PREVIEW_SIZE + 4, y + (h - context.batcher.getFont().getHeight()) / 2,
            RowStyle.textColor(hover || selected));

        if (hasParams)
        {
            int iconY = y + (h - 16) / 2;
            boolean isHoverOnIcon = hover
                && context.mouseX >= iconX - 2
                && context.mouseX <= this.area.ex()
                && context.mouseY >= iconY - 2
                && context.mouseY < iconY + 18;

            int primary = BBSSettings.primaryColor.get();
            int iconColor = isHoverOnIcon ? Colors.WHITE : (hover || selected ? (Colors.A100 | primary) : Colors.setA(primary, 0.75F));

            if (isHoverOnIcon)
            {
                context.batcher.box(iconX - 2, iconY - 1, iconX + 16 + 2, iconY + 16 + 1, Colors.A25 | Colors.WHITE);
            }

            context.batcher.icon(Icons.PROPERTIES, iconColor, iconX, iconY);
        }
    }

    @Override
    public void renderTooltip(UIContext context, Area area)
    {
        int index = this.getHoveredIndex(context);

        if (index >= 0 && index < this.visible().size())
        {
            String element = this.visible().get(index);

            if (UIParticleSettings.acceptsParameters(element))
            {
                int iconX = this.area.ex() - 20;
                boolean onIndicator = context.mouseX >= iconX - 4 && context.mouseX <= this.area.ex();

                if (onIndicator)
                {
                    IKey tooltipKey = UIParticleSettings.getTooltipKey(element);
                    LabelTooltip tooltip = new LabelTooltip(tooltipKey, 240, Direction.LEFT);

                    int s = this.scroll.scrollItemSize;
                    int rowY = this.area.y + index * s - (int) this.scroll.getScroll();
                    Area iconArea = new Area(iconX, rowY + (s - 16) / 2, 16, 16);

                    context.tooltip.area.copy(iconArea);
                    context.tooltip.render(tooltip, context);
                }
            }
        }
    }
}
