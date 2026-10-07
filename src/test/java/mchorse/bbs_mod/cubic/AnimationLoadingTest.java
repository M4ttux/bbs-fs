package mchorse.bbs_mod.cubic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionPlayback;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.ProceduralAnimator;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.geo.GeoAnimationParser;
import mchorse.bbs_mod.math.molang.MolangParser;

import java.io.File;
import java.nio.file.Files;

public class AnimationLoadingTest
{
    private static int failures = 0;

    public static void main(String[] args)
    {
        testBedrockAnimationParsing();
        testAnimationsAliasLookup();
        testProceduralAnimatorPlayback();

        if (failures > 0)
        {
            throw new AssertionError(failures + " test(s) failed!");
        }
        else
        {
            System.out.println("All AnimationLoadingTest checks passed!");
        }
    }

    private static void testBedrockAnimationParsing()
    {
        System.out.println("--- testBedrockAnimationParsing ---");

        try
        {
            File animFile = new File("src/client/resources/assets/bbs/assets/models/player/alex/animations/howlin_alex.animation.json");

            if (!animFile.exists())
            {
                fail("Animation file does not exist: " + animFile.getAbsolutePath());
                return;
            }

            String content = Files.readString(animFile.toPath());
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            JsonObject animations = root.getAsJsonObject("animations");

            MolangParser parser = new MolangParser();
            JsonObject howlJson = animations.getAsJsonObject("animation.howling.howl");

            Animation howl = GeoAnimationParser.parse(parser, "animation.howling.howl", howlJson);

            check("Length in ticks", howl.getLengthInTicks() > 100, true);
            check("Has right_arm part", howl.parts.containsKey("right_arm"), true);
            check("Has left_arm part", howl.parts.containsKey("left_arm"), true);
            check("Has head part", howl.parts.containsKey("head"), true);
            check("right_arm has keyframes", howl.parts.get("right_arm").rx.getKeyframes().size() > 0, true);

            File scubaFile = new File("src/client/resources/assets/bbs/assets/models/player/alex/animations/animation.scuba_dance_loop.animation.json");
            if (scubaFile.exists())
            {
                String scubaContent = Files.readString(scubaFile.toPath());
                JsonObject scubaRoot = JsonParser.parseString(scubaContent).getAsJsonObject();
                JsonObject scubaAnims = scubaRoot.getAsJsonObject("animations");
                JsonObject scubaJson = scubaAnims.getAsJsonObject("animation.scuba_dance_loop");
                Animation scuba = GeoAnimationParser.parse(parser, "animation.scuba_dance_loop", scubaJson);

                check("Scuba length in ticks", scuba.getLengthInTicks(), 25);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception during parsing: " + e.getMessage());
        }
    }

    private static void testAnimationsAliasLookup()
    {
        System.out.println("--- testAnimationsAliasLookup ---");

        MolangParser parser = new MolangParser();
        Animations animations = new Animations(parser);

        Animation anim = new Animation("animation.howling.howl", parser);
        animations.add(anim);
        animations.addAlias("howlin_alex", anim);

        check("Exact match", animations.get("animation.howling.howl") == anim, true);
        check("Stripped prefix match", animations.get("howling.howl") == anim, true);
        check("Alias match", animations.get("howlin_alex") == anim, true);
        check("Non-existent match", animations.get("unknown") == null, true);
    }

    private static void testProceduralAnimatorPlayback()
    {
        System.out.println("--- testProceduralAnimatorPlayback ---");

        MolangParser parser = new MolangParser();
        Model model = new Model(parser);
        ModelGroup rightArm = new ModelGroup("right_arm");
        model.topGroups.add(rightArm);
        model.initialize();

        Animations animations = new Animations(parser);
        Animation anim = new Animation("animation.howling.howl", parser);
        anim.setLength(2.0);
        animations.add(anim);

        ModelInstance instance = new ModelInstance("player/alex", model, animations, null);
        ProceduralAnimator animator = new ProceduralAnimator();
        animator.setup(instance, new ActionsConfig(), false);

        check("Initial actions empty", animator.actions.isEmpty(), true);

        animator.playAnimation("animation.howling.howl");
        check("Play animation added action", animator.actions.size(), 1);

        /* applyActions with target == null should not crash and should apply */
        animator.applyActions(null, instance, 0F);

        /* update should tick action */
        animator.update(null);
        check("Action is ticking", animator.actions.get(0).getTick(0F) > 0, true);
    }

    private static void check(String name, Object actual, Object expected)
    {
        if (actual.equals(expected))
        {
            System.out.println("  [PASS] " + name + ": " + actual);
        }
        else
        {
            System.err.println("  [FAIL] " + name + " - expected: " + expected + ", got: " + actual);
            failures++;
        }
    }

    private static void fail(String message)
    {
        System.err.println("  [FAIL] " + message);
        failures++;
    }
}
