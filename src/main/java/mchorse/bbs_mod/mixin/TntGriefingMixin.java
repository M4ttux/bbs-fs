package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.tntrule.TntRule;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerWorld.class)
public abstract class TntGriefingMixin
{
    /* Target the final server overload, shared by primed TNT and TNT minecarts. */
    @ModifyVariable(
        method = "createExplosion(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/damage/DamageSource;Lnet/minecraft/world/explosion/ExplosionBehavior;DDDFZLnet/minecraft/world/World$ExplosionSourceType;Lnet/minecraft/particle/ParticleEffect;Lnet/minecraft/particle/ParticleEffect;Lnet/minecraft/util/collection/WeightedPool;Lnet/minecraft/registry/entry/RegistryEntry;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private World.ExplosionSourceType bbs$tntGriefing(World.ExplosionSourceType type)
    {
        if (type == World.ExplosionSourceType.TNT
            && !TntRule.isTntGriefingEnabled((ServerWorld) (Object) this))
        {
            return World.ExplosionSourceType.NONE;
        }

        return type;
    }
}
