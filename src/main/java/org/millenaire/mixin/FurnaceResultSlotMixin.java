package org.millenaire.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;

import org.millenaire.common.forge.MillEventController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the 1.12 Forge {@code PlayerEvent.ItemSmeltedEvent} handler
 * ({@link MillEventController#handleFurnaceWithdrawals}). When a player withdraws a smelted item
 * from a furnace that belongs to a village building they do not own, they lose reputation.
 *
 * <p>Verified target: {@code net.minecraft.world.inventory.FurnaceResultSlot#onTake(Player,
 * ItemStack)} — the result-slot take callback for vanilla furnaces (furnace / blast furnace /
 * smoker). The {@code carried} stack is the item just taken out, matching the 1.12
 * {@code event.smelting}.</p>
 */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {

   @Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
   private void millenaire$handleFurnaceWithdrawals(Player player, ItemStack carried, CallbackInfo ci) {
      if (!player.level().isClientSide()) {
         new MillEventController().handleFurnaceWithdrawals(player, carried);
      }
   }
}
