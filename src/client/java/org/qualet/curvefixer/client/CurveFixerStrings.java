package org.qualet.curvefixer.client;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.api.client.events.L10nReloadEvent;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.l10n.L10n;

/**
 * Supplies localized labels for the addon's "curvefixer" settings group at runtime instead of
 * shipping a string source pack. On every {@link L10nReloadEvent} (and once at client init) it sets
 * the {@code content} of the lang keys directly on the loaded string map, picking en/ru by the
 * current language. Keeps base BBS string files untouched and follows language switches.
 *
 * <p>Key prefix mirrors the settings path: {@code bbs.config.personalization.curvefixer.*}.</p>
 */
public class CurveFixerStrings
{
    private static final String PREFIX = "bbs.config.personalization.curvefixer.";

    public static void apply(L10n l10n)
    {
        if (l10n == null)
        {
            return;
        }

        boolean ru = "ru_ru".equals(BBSSettings.language.get());

        set(l10n, "title", "Curve Fixer", "Исправление кривых", ru);
        set(l10n, "enabled", "Enabled", "Включено", ru);
        set(l10n, "enabled-comment",
            "Master switch for the addon.",
            "Главный переключатель аддона.", ru);
    }

    private static void set(L10n l10n, String suffix, String en, String ru, boolean useRu)
    {
        l10n.getKey(PREFIX + suffix).content = useRu ? ru : en;
    }

    // Public — the BBS event bus invokes @Subscribe methods via reflection.
    @Subscribe
    public void onL10nReload(L10nReloadEvent event)
    {
        apply(event.l10n);
    }
}
