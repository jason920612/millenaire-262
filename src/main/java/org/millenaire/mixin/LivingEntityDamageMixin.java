package org.millenaire.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.millenaire.common.forge.MillEventController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the 1.12 Forge {@code LivingDamageEvent} handler
 * ({@link MillEventController#damageOnPlayer}). When a hired villager's owner is damaged by a
 * living attacker, the villager retaliates.
 *
 * <p>Verified target: {@code net.minecraft.world.entity.LivingEntity#hurtServer(ServerLevel,
 * DamageSource, float)} — the server-side damage entry point in MC 26.2.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

   @Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"))
   private void millenaire$damageOnPlayer(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
      LivingEntity self = (LivingEntity)(Object)this;
      new MillEventController().damageOnPlayer(self, source);
   }
}
