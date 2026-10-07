package mchorse.bbs_mod.ui.film.replays.kits;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.list.UILabelList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.Label;

import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class UIKitListOverlayPanel extends UIOverlayPanel
{
    public UISearchList<Label<String>> strings;
    private Consumer<String> callback;

    public UIKitListOverlayPanel(IKey title, List<Label<String>> entries, String current, Consumer<String> callback)
    {
        this(title, entries, current, null, callback);
    }

    public UIKitListOverlayPanel(
        IKey title,
        List<Label<String>> entries,
        String current,
        Function<String, ItemStack> iconSupplier,
        Consumer<String> callback
    )
    {
        super(title);

        this.callback = callback;

        Consumer<List<Label<String>>> onSelect = (list) ->
        {
            if (list != null && !list.isEmpty())
            {
                this.accept(list.get(0).value);
            }
        };

        UILabelList<String> labelList = iconSupplier != null
            ? new UIKitIconLabelList(onSelect, iconSupplier)
            : new UILabelList<>(onSelect);

        this.strings = new UISearchList<>(labelList);
        this.strings.label(UIKeys.GENERAL_SEARCH).full(this.content).x(6).w(1F, -12);

        if (entries != null)
        {
            this.strings.list.add(entries);
        }

        this.strings.list.scroll.scrollSpeed *= 2;

        if (current != null)
        {
            for (Label<String> label : this.strings.list.getList())
            {
                if (Objects.equals(label.value, current))
                {
                    this.strings.list.setCurrentScroll(label);
                    break;
                }
            }
        }

        this.content.add(this.strings);
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);

        this.getContext().focus(this.strings.search);
    }

    protected void accept(String value)
    {
        if (this.callback != null)
        {
            this.callback.accept(value);
        }

        this.close();
    }
}
