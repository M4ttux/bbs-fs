package org.qualet.curvefixer.mixin.client;

import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Films;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Exposes the film controllers currently playing in the world. BBS only offers lookup by film id
 * ({@link Films#getController(String)}), but the curve fallback needs to scan whatever is playing
 * without knowing its id.
 */
@Mixin(value = Films.class, remap = false)
public interface FilmsAccessor
{
    @Accessor("controllers")
    List<BaseFilmController> curvefixer$getControllers();
}
