package org.qualet.curvefixer.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.qualet.curvefixer.CurveFixerAddon.MOD_ID;

/**
 * Client entry point ({@code client} entrypoint in {@code fabric.mod.json}). Registers the runtime
 * localized labels for the addon's settings group (see {@link CurveFixerStrings}) and logs a line
 * so you can confirm the client side initialized.
 */
public class CurveFixerClient implements ClientModInitializer
{
    private static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient()
    {
        BBSMod.events.register(new CurveFixerStrings());
        CurveFixerStrings.apply(BBSModClient.getL10n());

        LOG.info("CurveFixerClient.onInitializeClient — addon client initialized");
    }
}
