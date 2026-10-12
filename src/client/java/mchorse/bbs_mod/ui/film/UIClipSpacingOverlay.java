package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.function.IntConsumer;

public class UIClipSpacingOverlay extends UIOverlayPanel
{
    public UIClipSpacingOverlay(IntConsumer apply)
    {
        super(UIKeys.ACTION_TIMELINE_SPACING);

        UITrackpad gap = new UITrackpad(null).limit(0, Integer.MAX_VALUE).integer();
        gap.setValue(0);
        gap.tooltip(UIKeys.ACTION_TIMELINE_SPACING_HINT);
        UIButton confirm = new UIButton(UIKeys.GENERAL_OK, button ->
        {
            apply.accept((int) gap.getValue());
            this.close();
        });
        UIElement column = UI.column(5, 0,
            UI.label(UIKeys.ACTION_TIMELINE_SPACING_TICKS), gap, confirm);
        column.relative(this.content).xy(8, 8).w(1F, -16);
        this.content.add(column);
    }
}
