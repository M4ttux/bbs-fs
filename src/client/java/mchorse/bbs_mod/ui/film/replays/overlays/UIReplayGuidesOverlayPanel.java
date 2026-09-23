package mchorse.bbs_mod.ui.film.replays.overlays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;

public class UIReplayGuidesOverlayPanel extends UIOverlayPanel
{
    public final UIToggle enabled;
    public final UISliderTrackpad thickness;
    public final UISliderTrackpad opacity;

    public UIReplayGuidesOverlayPanel()
    {
        super(UIKeys.FILM_REPLAY_GUIDES_TITLE);

        this.enabled = new UIToggle(UIKeys.FILM_REPLAY_GUIDES_ENABLED, (b) -> BBSSettings.replayGuideLines.set(b.getValue()));
        this.enabled.setValue(BBSSettings.replayGuideLines.get());
        this.enabled.tooltip(UIKeys.FILM_REPLAY_GUIDES_ENABLED_TOOLTIP);

        this.thickness = new UISliderTrackpad((v) -> BBSSettings.replayGuideThickness.set(v.intValue()));
        this.thickness.limit(1, 10, true).integer();
        this.thickness.setValue(BBSSettings.replayGuideThickness.get());
        this.thickness.tooltip(UIKeys.FILM_REPLAY_GUIDES_THICKNESS_TOOLTIP);

        this.opacity = new UISliderTrackpad((v) -> BBSSettings.replayGuideOpacity.set(v.floatValue()));
        this.opacity.limit(0, 1).increment(0.01D);
        this.opacity.setValue(BBSSettings.replayGuideOpacity.get());
        this.opacity.tooltip(UIKeys.FILM_REPLAY_GUIDES_OPACITY_TOOLTIP);

        UIElement column = UI.column(4, 6,
            this.enabled,
            UI.labelRow(UIKeys.FILM_REPLAY_GUIDES_THICKNESS, this.thickness),
            UI.labelRow(UIKeys.FILM_REPLAY_GUIDES_OPACITY, this.opacity)
        );

        column.relative(this.content).x(6).y(6).w(1F, -12).hTo(this.content.area, 1F);
        this.content.add(column);
    }
}
