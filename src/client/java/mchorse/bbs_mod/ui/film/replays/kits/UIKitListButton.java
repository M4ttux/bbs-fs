package mchorse.bbs_mod.ui.film.replays.kits;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.utils.Label;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class UIKitListButton extends UIButton
{
    private final IKey overlayTitle;
    private final Supplier<List<Label<String>>> entriesSupplier;
    private final Supplier<String> currentSupplier;
    private final Function<String, ItemStack> iconSupplier;
    private final Consumer<String> onPick;

    public UIKitListButton(
        IKey overlayTitle,
        Supplier<List<Label<String>>> entriesSupplier,
        Supplier<String> currentSupplier,
        Consumer<String> onPick
    )
    {
        this(overlayTitle, entriesSupplier, currentSupplier, null, onPick);
    }

    public UIKitListButton(
        IKey overlayTitle,
        Supplier<List<Label<String>>> entriesSupplier,
        Supplier<String> currentSupplier,
        Function<String, ItemStack> iconSupplier,
        Consumer<String> onPick
    )
    {
        super(IKey.EMPTY, null);

        this.overlayTitle = overlayTitle;
        this.entriesSupplier = entriesSupplier;
        this.currentSupplier = currentSupplier;
        this.iconSupplier = iconSupplier;
        this.onPick = onPick;

        this.callback = (b) -> this.openOverlay();

        this.updateLabel();
    }

    public void updateLabel()
    {
        String current = this.currentSupplier.get();
        List<Label<String>> entries = this.entriesSupplier.get();

        if (entries != null)
        {
            for (Label<String> entry : entries)
            {
                if (Objects.equals(entry.value, current))
                {
                    this.label = entry.title;
                    return;
                }
            }
        }

        this.label = current == null || current.isEmpty() ? IKey.EMPTY : IKey.raw(current);
    }

    private void openOverlay()
    {
        List<Label<String>> entries = this.entriesSupplier.get();
        String current = this.currentSupplier.get();

        UIKitListOverlayPanel panel = new UIKitListOverlayPanel(
            this.overlayTitle,
            entries,
            current,
            this.iconSupplier,
            (val) ->
            {
                if (this.onPick != null)
                {
                    this.onPick.accept(val);
                }
                this.updateLabel();
            }
        );

        UIOverlay.addOverlay(this.getContext(), panel, 240, 0.7F);
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        super.renderSkin(context);

        if (this.iconSupplier != null)
        {
            String current = this.currentSupplier.get();
            int iconX = this.area.x + 4;
            int iconY = this.area.y + (this.area.h - 16) / 2;

            if (current == null || current.isEmpty())
            {
                context.batcher.icon(Icons.REFRESH, RowStyle.iconColor(this.hover), iconX, iconY);
            }
            else
            {
                ItemStack stack = this.iconSupplier.apply(current);

                if (stack != null && !stack.isEmpty())
                {
                    KitTrimIcons.draw(context, stack, iconX, iconY);
                }
            }
        }
    }
}
