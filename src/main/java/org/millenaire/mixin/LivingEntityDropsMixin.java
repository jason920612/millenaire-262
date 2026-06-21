package org.millenaire.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

import org.millenaire.common.forge.MillEventController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the 1.12 Forge {@code LivingDropsEvent} handler
 * ({@link MillEventController#addInuitDrops}). Adds the Inuit hunting drops (seafood / wolf meat /
 * bear meat) when the matching mob is killed by a player who has the corresponding hunting tag.
 *
 * <p>Verified target: {@code net.minecraft.world.entity.LivingEntity#dropAllDeathLoot(ServerLevel,
 * DamageSource)} — invoked from {@code die(DamageSource)} once per death, the closest 26.2 analogue
 * to the Forge drops event. {@code addInuitDrops} fills a {@link List} of fresh {@link ItemEntity}s,
 * which are then spawned directly (26.2 has no mutable drop-collection event).</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDropsMixin {

   @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("TAIL"))
   private void millenaire$addInuitDrops(ServerLevel level, DamageSource source, CallbackInfo ci) {
      LivingEntity self = (LivingEntity)(Object)this;
      List<ItemEntity> drops = new ArrayList<>();
      new MillEventController().addInuitDrops(self, source, drops);
      for (ItemEntity drop : drops) {
         level.addFreshEntity(drop);
      }
   }
}
