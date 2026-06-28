package org.qualet.curvefixer.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.ui.film.clips.UICurveClip;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import net.fabricmc.loader.api.FabricLoader;
import org.qualet.curvefixer.client.ui.shader.UIShaderOptionPicker;
import org.qualet.curvefixer.iris.CurveFixerIris;
import org.qualet.curvefixer.iris.ShaderMenu;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Entry point of the refreshed shader-curve editor. Decides whether the "add curve" action opens the
 * BBS-styled {@link UIShaderOptionPicker} (a mirror of the Iris shader-options menu) or falls back to
 * BBS's stock flat list.
 *
 * <p><b>Iris isolation:</b> this class references no {@code net.irisshaders.*} type directly; it only
 * reaches Iris through {@link CurveFixerIris} <i>after</i> the {@code isModLoaded("iris")} gate. That
 * keeps {@link CurveFixerIris} (and Iris itself) unloaded when Iris is absent, so the addon degrades
 * to the stock list instead of crashing.</p>
 */
public final class ShaderCurvePicker
{
    /**
     * Re-entrancy guard for the picker's "legacy list" button. The button re-invokes BBS's
     * {@code offerCurveKeys} (which this addon intercepts) to reuse the stock flat-list logic
     * verbatim; this flag makes that one call skip the picker and fall through to the stock body.
     */
    private static boolean suppressOnce;

    private ShaderCurvePicker()
    {}

    /**
     * @return {@code true} if the refreshed picker was shown (caller must cancel the stock list);
     *         {@code false} to let BBS's stock {@code offerCurveKeys} flat list run.
     */
    public static boolean open(UIContext context, List<String> existing, Consumer<String> callback)
    {
        if (suppressOnce)
        {
            suppressOnce = false;

            return false;
        }

        if (!FabricLoader.getInstance().isModLoaded("iris"))
        {
            return false;
        }

        ShaderMenu menu = CurveFixerIris.buildShaderMenu();

        if (menu == null)
        {
            return false;
        }

        Map<String, String> languageMap = CurveFixerIris.getShadersRawLanguageMap(BBSModClient.getLanguageKey());
        Consumer<String> onAddOptionId = (id) -> callback.accept(CurveClip.SHADER_CURVES_PREFIX + id);
        Runnable openLegacy = () ->
        {
            suppressOnce = true;
            UICurveClip.offerCurveKeys(context, existing, callback);
        };

        UIShaderOptionPicker picker = new UIShaderOptionPicker(menu, languageMap, collectAddedOptionIds(existing), onAddOptionId, openLegacy);

        UIOverlay.addOverlay(context, picker, picker.preferredWidth(), picker.preferredHeight());

        return true;
    }

    /**
     * Bare option ids (prefix stripped) of the curve channels already present, so the picker can mark
     * those cells as animated (green outline).
     */
    private static Set<String> collectAddedOptionIds(List<String> existing)
    {
        Set<String> added = new HashSet<>();

        for (String id : existing)
        {
            if (id.startsWith(CurveClip.SHADER_CURVES_PREFIX))
            {
                added.add(id.substring(CurveClip.SHADER_CURVES_PREFIX.length()));
            }
        }

        return added;
    }
}
