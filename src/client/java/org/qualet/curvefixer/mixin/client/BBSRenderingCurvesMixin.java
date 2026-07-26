package org.qualet.curvefixer.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.WorldFilmController;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Apply curve clip built-ins during world film playback.
 *
 * <p>BBS reads the {@code sun_rotation} / {@code brightness} / {@code weather} / {@code chroma_sky}
 * curve channels only from the current {@code CameraWorkCameraController}, which usually does not
 * exist while a film plays in the world: the "play film" hotkey plays without a camera, and first
 * person films never create a {@code PlayCameraController}. So those curves only ever worked in the
 * dashboard editor.</p>
 *
 * <p>{@code WorldFilmController} already applies the film's camera clips (curve clips included) every
 * render frame into its own {@code CameraClipContext} — so when BBS' own lookup comes back empty, fall
 * back to the contexts of the film controllers playing in the world.</p>
 *
 * <p>Injected at {@code RETURN} rather than overwriting: the vanilla camera-work path keeps priority,
 * and this only fills in the {@code null} results.</p>
 */
@Mixin(value = BBSRendering.class, remap = false)
public abstract class BBSRenderingCurvesMixin
{
    @Inject(method = "getTimeOfDay", at = @At("RETURN"), cancellable = true, remap = false)
    private static void curvefixer$fallbackTimeOfDay(CallbackInfoReturnable<Long> cir)
    {
        if (cir.getReturnValue() != null)
        {
            return;
        }

        Double v = curvefixer$getWorldCurveValue(ShaderCurves.SUN_ROTATION);

        if (v != null)
        {
            cir.setReturnValue((long) (v * 1000L));
        }
    }

    @Inject(method = "getBrightness", at = @At("RETURN"), cancellable = true, remap = false)
    private static void curvefixer$fallbackBrightness(CallbackInfoReturnable<Double> cir)
    {
        curvefixer$fallback(cir, ShaderCurves.BRIGHTNESS);
    }

    @Inject(method = "getWeather", at = @At("RETURN"), cancellable = true, remap = false)
    private static void curvefixer$fallbackWeather(CallbackInfoReturnable<Double> cir)
    {
        curvefixer$fallback(cir, ShaderCurves.WEATHER);
    }

    @Inject(method = "getChromaSkyColorArgb", at = @At("RETURN"), cancellable = true, remap = false)
    private static void curvefixer$fallbackChromaSky(CallbackInfoReturnable<Integer> cir)
    {
        if (cir.getReturnValue() != null || !curvefixer$canRead())
        {
            return;
        }

        for (BaseFilmController controller : ((FilmsAccessor) BBSModClient.getFilms()).curvefixer$getControllers())
        {
            if (controller instanceof WorldFilmController worldFilm)
            {
                Map<String, Integer> values = CurveClip.getColorValues(((WorldFilmControllerAccessor) worldFilm).curvefixer$getContext());
                Integer v = values == null ? null : values.get(CurveClip.CHROMA_SKY_COLOR);

                if (v != null)
                {
                    cir.setReturnValue(v);

                    return;
                }
            }
        }
    }

    @Unique
    private static void curvefixer$fallback(CallbackInfoReturnable<Double> cir, String key)
    {
        if (cir.getReturnValue() != null)
        {
            return;
        }

        Double v = curvefixer$getWorldCurveValue(key);

        if (v != null)
        {
            cir.setReturnValue(v);
        }
    }

    @Unique
    private static Double curvefixer$getWorldCurveValue(String key)
    {
        if (!curvefixer$canRead())
        {
            return null;
        }

        for (BaseFilmController controller : ((FilmsAccessor) BBSModClient.getFilms()).curvefixer$getControllers())
        {
            if (controller instanceof WorldFilmController worldFilm)
            {
                Map<String, Double> values = CurveClip.getValues(((WorldFilmControllerAccessor) worldFilm).curvefixer$getContext());
                Double v = values == null ? null : values.get(key);

                if (v != null)
                {
                    return v;
                }
            }
        }

        return null;
    }

    /**
     * The clip contexts are written by the render thread, so mirror BBS' own guard — the vanilla
     * getters bail out off-thread, and the {@code RETURN} injection would otherwise run there anyway.
     */
    @Unique
    private static boolean curvefixer$canRead()
    {
        return MinecraftClient.getInstance().isOnThread() && BBSModClient.getFilms() != null;
    }
}
