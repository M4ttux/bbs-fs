package org.qualet.curvefixer.mixin;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.qualet.curvefixer.CurveFixerAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Example main-side mixin: registers the addon's settings inside BBS's own <b>personalization</b>
 * category as a nested "curvefixer" group. Injected right after the second
 * {@code builder.category(String, Icon)} call in {@code BBSSettings.register} (ordinal 1 =
 * the personalization category).
 *
 * <p>This is the canonical pattern for an addon mixin — keep injections narrow ({@code @Inject} /
 * {@code @ModifyExpressionValue} / {@code @Redirect}) so they survive BBS upstream updates. The
 * group persists to config; note that the settings UI does not auto-render nested groups, so a
 * dedicated UI mixin is needed to show it (see {@code UISettingsOverlayPanelMixin} in the
 * resfreshed-addon reference project). For the skeleton we only prove the registration works.</p>
 */
@Mixin(BBSSettings.class)
public abstract class BBSSettingsMixin
{
    @Inject(
        method = "register",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/settings/SettingsBuilder;category(Ljava/lang/String;Lmchorse/bbs_mod/ui/utils/icons/Icon;)Lmchorse/bbs_mod/settings/SettingsBuilder;",
            ordinal = 1,
            shift = At.Shift.AFTER
        )
    )
    private static void curvefixer$registerSettings(SettingsBuilder builder, CallbackInfo ci)
    {
        ValueBoolean enabled = new ValueBoolean("enabled", true);

        ValueGroup group = new ValueGroup("curvefixer");
        group.icon = Icons.GEAR;
        group.add(enabled);

        builder.getCategory().add(group);

        CurveFixerAddon.enabled = enabled;
        CurveFixerAddon.settingsGroup = group;
    }
}
