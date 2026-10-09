package mchorse.bbs_mod.tntrule;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.rule.GameRule;
import net.minecraft.world.rule.GameRuleCategory;

public final class TntRule
{
    public static final String NAMESPACE = "bbs";

    public static final GameRule<Boolean> TNT_GRIEFING = GameRuleBuilder.forBoolean(true)
        .category(GameRuleCategory.MISC)
        .buildAndRegister(Identifier.of(NAMESPACE, "tnt_griefing"));

    private TntRule()
    {}

    /** Forces registration during mod initialization, before worlds are loaded. */
    public static void init()
    {}

    public static boolean isTntGriefingEnabled(ServerWorld world)
    {
        return world.getGameRules().getValue(TNT_GRIEFING);
    }
}
