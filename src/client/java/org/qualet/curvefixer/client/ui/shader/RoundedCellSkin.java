package org.qualet.curvefixer.client.ui.shader;

import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;
import org.qualet.refreshedui.client.batcher.IRoundedBatcher;
import org.qualet.refreshedui.client.ui.UICornerRadii;

/**
 * RefreshedUI-only skin for {@link UIShaderOptionCell}: draws the cell background and value box with the
 * {@code refreshedui} addon's rounded-rect primitives so the picker matches the refreshed theme.
 *
 * <p><b>This is the only curvefixer class that references {@code org.qualet.refreshedui.*}.</b> It must
 * therefore never be loaded when refreshedui is absent — every caller goes through
 * {@link UIShaderOptionCell}'s {@code ROUNDED} gate (a class that does not reference refreshedui), so the
 * JVM only links {@link IRoundedBatcher} / {@link UICornerRadii} when refreshedui is actually present. This
 * mirrors how {@code CurveFixerIris} isolates {@code net.irisshaders.*}.</p>
 *
 * <p>The live {@link Batcher2D} implements {@link IRoundedBatcher} because refreshedui mixes those
 * primitives into BBS's {@code Batcher2D} as {@code @Unique} members — the cast resolves at runtime.</p>
 */
public final class RoundedCellSkin
{
    private RoundedCellSkin()
    {}

    /**
     * Cell background: a rounded primary/neutral fill. Already-animated cells draw a green border ring
     * (rounded frame, 2px) over the same fill in one batch, replacing the stock {@code box + outline}.
     */
    public static void background(Batcher2D batcher, Area a, int fill, boolean added)
    {
        IRoundedBatcher rounded = (IRoundedBatcher) batcher;
        float radius = Math.max(0.5F, UICornerRadii.buttonsAndTrackpads());

        if (added)
        {
            rounded.roundedFrame(a.x, a.y, a.w, a.h, radius, 2F, Colors.GREEN, fill);
        }
        else
        {
            rounded.roundedBox(a.x, a.y, a.w, a.h, radius, fill);
        }
    }

    /** Inner value-readout box (numeric chrome surface / plain enum box), nested one px tighter in radius. */
    public static void valueBox(Batcher2D batcher, float x, float y, float w, float h, int color)
    {
        float radius = Math.max(0.5F, UICornerRadii.buttonsAndTrackpads() - 1F);

        ((IRoundedBatcher) batcher).roundedBox(x, y, w, h, radius, color);
    }
}
