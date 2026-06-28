package org.qualet.curvefixer;

import mchorse.bbs_mod.events.BBSAddonMod;
import mchorse.bbs_mod.events.Subscribe;
import mchorse.bbs_mod.events.register.RegisterSourcePacksEvent;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import org.qualet.curvefixer.resources.CurveFixerAssetsSourcePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BBS addon entry point (server/common side). Wired through the {@code bbs-addon} entrypoint in
 * {@code fabric.mod.json}; BBS discovers every {@link BBSAddonMod} and forwards events to its
 * {@link Subscribe @Subscribe} methods.
 *
 * <p>This is the addon skeleton — it does three things and nothing else:</p>
 * <ol>
 *   <li>registers an {@link mchorse.bbs_mod.resources.ISourcePack} so the addon can serve its own
 *       assets / override BBS's (see {@link CurveFixerAssetsSourcePack});</li>
 *   <li>holds the addon's settings as static fields, populated by
 *       {@code org.qualet.curvefixer.mixin.BBSSettingsMixin};</li>
 *   <li>logs a line at construction so you can confirm the addon loaded.</li>
 * </ol>
 *
 * <p>Replace the example {@link #enabled} setting and the empty source pack with the real
 * feature once the skeleton is verified in-game.</p>
 */
public class CurveFixerAddon implements BBSAddonMod
{
    public static final String MOD_ID = "curvefixer";

    private static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    /** Example master toggle for the addon. Default true. Registered by {@code BBSSettingsMixin}. */
    public static ValueBoolean enabled;

    /** Nested settings group under BBS's personalization category. */
    public static ValueGroup settingsGroup;

    public CurveFixerAddon()
    {
        LOG.info("CurveFixerAddon loaded (bbs-addon entrypoint)");
    }

    /**
     * Register the addon's source pack with BBS's {@link mchorse.bbs_mod.resources.AssetProvider}.
     *
     * <p>{@code AssetProvider} serves the FIRST pack whose {@code hasAsset} matches, so the choice of
     * call matters:</p>
     * <ul>
     *   <li>{@code register(...)} — appends; use it to add NEW assets in your own namespace.</li>
     *   <li>{@code registerFirst(...)} — prepends; required to OVERRIDE assets BBS already serves
     *       (BBS adds its internal pack before posting this event).</li>
     * </ul>
     *
     * <p>{@link Subscribe @Subscribe} methods must be public — BBS's event bus invokes them via
     * reflection.</p>
     */
    @Subscribe
    public void registerSourcePacks(RegisterSourcePacksEvent event)
    {
        event.provider.registerFirst(new CurveFixerAssetsSourcePack());
    }
}
