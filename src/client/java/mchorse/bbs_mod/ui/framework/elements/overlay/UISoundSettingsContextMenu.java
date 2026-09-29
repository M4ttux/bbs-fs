package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.screenplay.UIAudioPlayer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.utils.UI;

public class UISoundSettingsContextMenu extends UIContextMenu
{
    public UIToggle autoplay;
    public UISliderTrackpad volume;

    private UIElement column;

    public UISoundSettingsContextMenu(UIAudioPlayer player)
    {
        this.autoplay = new UIToggle(UIKeys.OVERLAYS_SOUNDS_AUTOPLAY, BBSSettings.audioOverlayAutoplay.get(), (t) ->
        {
            BBSSettings.audioOverlayAutoplay.set(t.getValue());
        });

        this.volume = new UISliderTrackpad((v) ->
        {
            BBSSettings.audioOverlayVolume.set(v.floatValue());

            if (player != null && player.getPlayer() != null)
            {
                player.getPlayer().setVolume(v.floatValue());
            }
        });
        this.volume.limit(BBSSettings.audioOverlayVolume).setValue(BBSSettings.audioOverlayVolume.get());
        this.volume.values(0.05F, 0.01F, 0.1F);

        this.column = UI.column(
            4, 8,
            this.autoplay,
            UI.label(UIKeys.OVERLAYS_SOUNDS_VOLUME),
            this.volume
        );
        this.column.relative(this).w(160);

        this.add(this.column);
        this.column.resize();
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public void setMouse(UIContext context)
    {
        this.xy(context.mouseX(), context.mouseY())
            .wh(this.column.area.w, this.column.area.h)
            .bounds(context.menu.overlay, 5);
    }
}
