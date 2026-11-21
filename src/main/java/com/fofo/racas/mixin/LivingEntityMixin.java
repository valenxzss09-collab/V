package com.fofo.racas.mixin;

import com.fofo.racas.Race;
import com.fofo.racas.RaceData;
import com.fofo.racas.RaceEffects;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @WrapMethod(method = "damage")
    private boolean racas$damage(ServerWorld world, DamageSource source, float amount, Operation<Boolean> original) {
        float result = RaceEffects.modifyDamage((LivingEntity) (Object) this, source, amount);
        if (result < 0) return false;
        return original.call(world, source, result);
    }

    // Banshee: poções recebidas duram o dobro (efeitos do próprio mod são "ambient" e ficam de fora).
    @WrapMethod(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z")
    private boolean racas$effect(StatusEffectInstance effect, Entity source, Operation<Boolean> original) {
        if ((Object) this instanceof ServerPlayerEntity p
            && !effect.isAmbient()
            && effect.getDuration() > 0
            && RaceData.get().getRace(p.getUuid()) == Race.BANSHEE) {
            effect = new StatusEffectInstance(effect.getEffectType(), effect.getDuration() * 2,
                effect.getAmplifier(), effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon());
        }
        return original.call(effect, source);
    }
}
