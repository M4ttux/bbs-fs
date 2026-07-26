package org.qualet.curvefixer.mixin.client;

import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.film.WorldFilmController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the camera clip context a world film controller applies the film's camera clips (curve
 * clips included) with, every render frame. Lets curve consumers read curve data during world
 * playback, when no camera work controller exists.
 */
@Mixin(value = WorldFilmController.class, remap = false)
public interface WorldFilmControllerAccessor
{
    @Accessor("context")
    CameraClipContext curvefixer$getContext();
}
