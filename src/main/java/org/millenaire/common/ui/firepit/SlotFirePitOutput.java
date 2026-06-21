package org.millenaire.common.ui.firepit;

import org.jspecify.annotations.NonNull;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.phys.Vec3;
import org.millenaire.common.entity.TileEntityFirePit;

public class SlotFirePitOutput extends Slot {
   private final Player player;
   private int removeCount;

   public SlotFirePitOutput(Player player, Container container, int slotIndex, int xPosition, int yPosition) {
      super(container, slotIndex, xPosition, yPosition);
      this.player = player;
   }

   @Override
   @NonNull
   public ItemStack remove(int amount) {
      if (this.hasItem()) {
         this.removeCount = this.removeCount + Math.min(amount, this.getItem().getCount());
      }

      return super.remove(amount);
   }

   @Override
   public boolean mayPlace(@NonNull ItemStack stack) {
      return false;
   }

   @Override
   protected void onQuickCraft(@NonNull ItemStack picked, int count) {
      this.removeCount += count;
      this.checkTakeAchievements(picked);
   }

   @Override
   protected void checkTakeAchievements(@NonNull ItemStack stack) {
      stack.onCraftedBy(this.player, this.removeCount);
      // 1.12 awarded smelting XP per item pulled from the output, computed from the result's smelting
      // recipe (FurnaceRecipes.getSmeltingExperience) and spawned as XP orbs. 26.2 keeps recipe XP on
      // the recipe (AbstractCookingRecipe.experience); the Mill fire-pit BE doesn't track recipes-used,
      // so we faithfully reproduce the original by-result lookup: find the smelting recipe whose result
      // matches the pulled stack, take its experience, and award orbs scaled by the removed count using
      // the same floor + fractional-rounding logic as AbstractFurnaceBlockEntity.createExperience.
      if (this.container instanceof TileEntityFirePit firePit
         && firePit.getLevel() instanceof ServerLevel serverLevel
         && this.removeCount > 0) {
         float exp = getSmeltingExperience(serverLevel, stack);
         if (exp > 0.0F) {
            float total = this.removeCount * exp;
            int xpReward = Mth.floor(total);
            float xpFraction = Mth.frac(total);
            if (xpFraction != 0.0F && serverLevel.getRandom().nextFloat() < xpFraction) {
               xpReward++;
            }
            if (xpReward > 0) {
               Vec3 pos = Vec3.atCenterOf(firePit.getBlockPos());
               ExperienceOrb.award(serverLevel, pos, xpReward);
            }
         }
      }

      this.removeCount = 0;
   }

   /** Smelting XP for the given result stack (mirrors 1.12 FurnaceRecipes.getSmeltingExperience). */
   private static float getSmeltingExperience(ServerLevel level, ItemStack result) {
      // AbstractCookingRecipe.assemble ignores the input and returns the fixed result, so an empty
      // input is sufficient to read each smelting recipe's output for the by-result XP lookup.
      SingleRecipeInput probe = new SingleRecipeInput(ItemStack.EMPTY);
      for (RecipeHolder<?> holder : level.getServer().getRecipeManager().getRecipes()) {
         if (holder.value() instanceof SmeltingRecipe smelting) {
            ItemStack recipeResult = smelting.assemble(probe);
            if (!recipeResult.isEmpty() && ItemStack.isSameItem(recipeResult, result)) {
               return smelting.experience();
            }
         }
      }
      return 0.0F;
   }

   @Override
   public void onTake(@NonNull Player thePlayer, @NonNull ItemStack stack) {
      this.checkTakeAchievements(stack);
      super.onTake(thePlayer, stack);
   }
}
