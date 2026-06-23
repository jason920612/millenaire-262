package org.millenaire.mixin.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;

import org.millenaire.common.entity.MillVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * O4 client-side companion to {@code FishingHookMixin}: keep a MILL-VILLAGER-owned bobber ALIVE on the client so its
 * vanilla model bobs visibly in the water.
 *
 * <p>Vanilla {@link FishingHook#recreateFromPacket} discards the client copy when {@code getPlayerOwner()==null}
 * (FishingHook.java:553-559) — and a villager owner is never a Player, so the client would immediately remove the
 * bobber and nothing would render. We let the vanilla method run its normal {@code super.recreateFromPacket} setup
 * (position/owner data) and only REDIRECT the player-only {@code discard()} call: if the hook's resolved owner is a
 * {@link MillVillager}, skip the discard so the bobber model survives + animates. (The rod→bobber line origin is
 * computed by {@code FishingHookRendererMixin}.)
 *
 * <p>Guarded: the discard is suppressed ONLY for a MillVillager owner. A real player's hook (or a genuinely invalid
 * owner) still discards exactly as vanilla does.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookClientMixin {

   @Redirect(
      method = "recreateFromPacket",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/FishingHook;discard()V"))
   private void millenaire$keepVillagerHookOnClient(FishingHook hook) {
      Entity owner = hook.getOwner();
      if (owner instanceof MillVillager) {
         // Mill-villager-owned bobber: KEEP it (skip vanilla's player-only client discard) so the model renders.
         return;
      }
      hook.discard(); // real player / invalid owner: vanilla behaviour.
   }
}
