package org.qualet.curvefixer.mixin.client;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core of the more reliable curve pipeline: make <b>every float option</b> curvable instead of only
 * the ones the pack lists under {@code sliders=}, and switch the {@code #if}/{@code #define}
 * exclusion scans to whole-identifier matching.
 *
 * <p>Floating-point options are always safe to drive from a per-frame uniform (GLSL only requires
 * <i>integers</i> to be compile-time constants — array sizes, loop bounds), so packs that expose most
 * numeric options as cycling value boxes (e.g. Photon: 2 sliders vs ~297 numeric defines) become
 * almost fully animatable. Integer options are kept only when the author exposed them as sliders.</p>
 *
 * <p>This is a single-method {@link Overwrite} of a small, self-contained private method — pinned to
 * BBS 2.3.1. If BBS rewrites {@code removeIrrelevantVariables}, this must be re-synced.</p>
 */
@Mixin(value = ShaderCurves.class, remap = false)
public abstract class ShaderCurvesMixin
{
    @Shadow(remap = false)
    private static Set<String> prohibitedVariables;

    @Shadow(remap = false)
    private static boolean isIdentifierPart(char c)
    {
        throw new AssertionError();
    }

    /**
     * @author curvefixer
     * @reason Make all float options curvable (not just {@code sliders=}); whole-identifier
     *         filtering. See class javadoc.
     */
    @Overwrite(remap = false)
    private static void removeIrrelevantVariables(String source, Map<String, ShaderCurves.ShaderVariable> variables)
    {
        /*
         * Floating-point options are always safe to drive from a per-frame uniform: GLSL never
         * requires a float to be a compile-time constant (array sizes and loop bounds must be int).
         * So every float option is curvable.
         *
         * Integer options, on the other hand, are frequently sample counts / array sizes / loop
         * bounds that must stay `const` (turning them into a uniform breaks compilation). We keep
         * only the integers the shader pack author explicitly exposed as sliders, trusting those to
         * be plain runtime values.
         */
        List<String> sliders = BBSRendering.getShadersSliderOptions();

        variables.values().removeIf((v) -> v.integer && !sliders.contains(v.name));

        for (String prohibitedVariable : prohibitedVariables)
        {
            variables.remove(prohibitedVariable);
        }

        int index = 0;

        while ((index = source.indexOf("#", index + 1)) != -1)
        {
            int newLine = source.indexOf('\n', index);

            if (newLine >= 0)
            {
                String substr = source.substring(index, newLine);

                if (substr.startsWith("#if") || substr.startsWith("#elif"))
                {
                    variables.values().removeIf((v) -> curvefixer$containsIdentifier(substr, v.name));
                }
                else if (substr.startsWith("#define"))
                {
                    final int WHITESPACE = 0, CHARACTERS = 1;
                    int iindex = 7;
                    int state = 0;
                    int switches = 0;

                    while (iindex < newLine - index)
                    {
                        char c = substr.charAt(iindex);

                        if (state == WHITESPACE && Character.isWhitespace(c))
                        {
                            state = CHARACTERS;
                            switches += 1;
                        }
                        else if (Character.isWhitespace(c))
                        {
                            state = WHITESPACE;
                        }

                        if (switches == 2)
                        {
                            break;
                        }

                        iindex += 1;
                    }

                    final String subsubstr = substr.substring(iindex);

                    variables.values().removeIf((v) -> curvefixer$containsIdentifier(subsubstr, v.name));
                }
            }
        }
    }

    /**
     * Whole-identifier containment: true only when {@code identifier} appears in {@code haystack}
     * bounded by non-identifier characters. Avoids the false positives of a plain substring check
     * (e.g. {@code DOF_FOCUS} matching inside {@code DOF_FOCUS_DISTANCE}), which would otherwise
     * wrongly drop a valid option referenced only by a longer-named neighbour.
     */
    @Unique
    private static boolean curvefixer$containsIdentifier(String haystack, String identifier)
    {
        int from = 0;

        while ((from = haystack.indexOf(identifier, from)) != -1)
        {
            int end = from + identifier.length();
            boolean leftOk = from == 0 || !isIdentifierPart(haystack.charAt(from - 1));
            boolean rightOk = end >= haystack.length() || !isIdentifierPart(haystack.charAt(end));

            if (leftOk && rightOk)
            {
                return true;
            }

            from = end;
        }

        return false;
    }
}
