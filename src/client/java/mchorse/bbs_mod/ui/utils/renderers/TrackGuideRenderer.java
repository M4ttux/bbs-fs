package mchorse.bbs_mod.ui.utils.renderers;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

public class TrackGuideRenderer
{
    /**
     * Checks if track guide lines are enabled in settings.
     */
    public static boolean isEnabled()
    {
        return BBSSettings.editorTrackGuides != null && BBSSettings.editorTrackGuides.get();
    }

    /**
     * Returns the opacity configured for track guide lines.
     */
    public static float getOpacity()
    {
        if (BBSSettings.editorTrackGuidesOpacity == null)
        {
            return 0.5F;
        }

        return BBSSettings.editorTrackGuidesOpacity.get();
    }

    /**
     * Render a horizontal track guide line across the given area at the vertical center of the track.
     */
    public static void renderTrackGuide(UIContext context, Area area, int y, int trackHeight, int color)
    {
        if (!isEnabled())
        {
            return;
        }

        float opacity = getOpacity();

        if (opacity <= 0F)
        {
            return;
        }

        int centerY = y + trackHeight / 2;
        int guideColor = Colors.setA(color, opacity);

        context.batcher.box(area.x, centerY, area.ex(), centerY + 1, guideColor);
    }

    /**
     * Render a horizontal track guide line across the given area at an exact center Y coordinate.
     */
    public static void renderTrackGuideAtY(UIContext context, Area area, int centerY, int color)
    {
        if (!isEnabled())
        {
            return;
        }

        float opacity = getOpacity();

        if (opacity <= 0F)
        {
            return;
        }

        int guideColor = Colors.setA(color, opacity);

        context.batcher.box(area.x, centerY, area.ex(), centerY + 1, guideColor);
    }
}
