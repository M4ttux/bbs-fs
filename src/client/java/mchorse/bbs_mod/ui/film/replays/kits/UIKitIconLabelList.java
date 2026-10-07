package mchorse.bbs_mod.ui.film.replays.kits;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UILabelList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.utils.Label;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class UIKitIconLabelList extends UILabelList<String>
{
    private final Function<String, ItemStack> icon;

    public UIKitIconLabelList(Consumer<List<Label<String>>> callback, Function<String, ItemStack> icon)
    {
        super(callback);

        this.icon = icon;
        this.scroll.scrollItemSize = 20;
    }

    @Override
    protected void renderElementPart(UIContext context, Label<String> element, int i, int x, int y, boolean hover, boolean selected)
    {
        int h = this.scroll.scrollItemSize;
        boolean lit = hover || selected;
        int iconX = x + UIList.ROW_PADDING;
        int iconY = y + (h - UIList.ICON_SLOT) / 2;
        int textX = iconX + UIList.ICON_SLOT + UIList.ICON_GAP;

        if (element.value.isEmpty())
        {
            /* "Random" row */
            context.batcher.icon(Icons.REFRESH, RowStyle.iconColor(lit), iconX, iconY);
        }
        else if (this.icon != null)
        {
            ItemStack stack = this.icon.apply(element.value);

            if (stack != null && !stack.isEmpty())
            {
                KitTrimIcons.draw(context, stack, iconX, iconY);
            }
        }

        FontRenderer font = context.batcher.getFont();
        String text = element.title != null ? element.title.get() : "";
        String title = font.limitToWidth(text, this.area.w - (textX - x) - UIList.ROW_PADDING);

        context.batcher.textShadow(title, textX, y + (h - font.getHeight()) / 2, RowStyle.textColor(lit));
    }
}
