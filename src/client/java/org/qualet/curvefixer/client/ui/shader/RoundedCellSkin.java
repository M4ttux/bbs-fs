package org.qualet.curvefixer.client.ui.shader;

import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;
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

    /** Border ring thickness (px) for an animated/added cell. */
    private static final float ADDED_BORDER_INSET = 2F;

    /**
     * Cell background: a rounded primary/neutral fill. An already-animated cell gets a {@code borderColor}
     * ring drawn as TWO rounded boxes — a full border box, then the fill inset on top. This is deliberate:
     * a thin {@code roundedFrame} ring is eaten by the corner anti-aliasing (the ring "doesn't render"),
     * whereas two solid boxes always paint a visible ring. Same technique as refreshedui's
     * {@code RoundedAreas.renderField}.
     */
    public static void background(Batcher2D batcher, Area a, int fill, boolean added, int borderColor)
    {
        IRoundedBatcher rounded = (IRoundedBatcher) batcher;
        float radius = Math.max(0.5F, UICornerRadii.buttonsAndTrackpads());

        if (added)
        {
            float inset = ADDED_BORDER_INSET;

            rounded.roundedBox(a.x, a.y, a.w, a.h, radius, borderColor);
            rounded.roundedBox(a.x + inset, a.y + inset, a.w - inset * 2F, a.h - inset * 2F, Math.max(0.5F, radius - inset), fill);
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
