package org.qualet.curvefixer.mixin.client;

import mchorse.bbs_mod.ui.film.clips.UICurveClip;
import mchorse.bbs_mod.ui.framework.UIContext;
import org.qualet.curvefixer.client.ShaderCurvePicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

/**
 * Reroutes the curve clip's "add curve" action to the refreshed shader-options picker.
 *
 * <p>BBS's {@code offerCurveKeys} is the single public entry the add-action uses to show the flat
 * pick-list. Intercepting it at {@code HEAD} (rather than rewriting the inline lambda in
 * {@code registerUI}) is the robust seam: when a shader pack/menu is available we open the picker and
 * cancel; otherwise we fall through to the stock list unchanged. The picker's "legacy" button calls
 * {@code offerCurveKeys} again to reuse that same stock list (see {@link ShaderCurvePicker}).</p>
 */
@Mixin(value = UICurveClip.class, remap = false)
public abstract class UICurveClipMixin
{
    @Inject(method = "offerCurveKeys", at = @At("HEAD"), cancellable = true, remap = false)
    private static void curvefixer$rerouteToPicker(UIContext context, List<String> existing, Consumer<String> callback, CallbackInfo ci)
    {
        if (ShaderCurvePicker.open(context, existing, callback))
        {
            ci.cancel();
        }
    }
}
