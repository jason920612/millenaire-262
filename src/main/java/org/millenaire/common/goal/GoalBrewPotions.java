package org.millenaire.common.goal;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.Point;

@DocumentedElement.Documentation("Brew alchemical potions from nether warts. Currently broken.")
public class GoalBrewPotions extends Goal {
   public GoalBrewPotions() {
      this.icon = InvItem.createInvItem(Items.POTION);
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) throws Exception {
      int nbWarts = villager.getHouse().countGoods(Items.NETHER_WART);
      int nbBottles = villager.getHouse().countGoods(Items.GLASS_BOTTLE);
      int nbPotions = villager.getHouse().countGoods(Items.POTION, -1);

      for (Point p : villager.getHouse().getResManager().brewingStands) {
         BrewingStandBlockEntity brewingStand = p.getBrewingStand(villager.level());
         if (brewingStand != null && isIdle(brewingStand)) {
            if (brewingStand.getItem(3) == ItemStack.EMPTY && nbWarts > 0 && nbPotions < 64) {
               return this.packDest(p, villager.getHouse());
            }

            if (nbBottles > 2
               && (
                  brewingStand.getItem(0) == ItemStack.EMPTY
                     || brewingStand.getItem(1) == ItemStack.EMPTY
                     || brewingStand.getItem(2) == ItemStack.EMPTY
               )
               && nbPotions < 64) {
               return this.packDest(p, villager.getHouse());
            }

            for (int i = 0; i < 3; i++) {
               // 1.12 checked the potion meta == 16 (an Awkward potion: water + nether wart). On 26.2
               // that variant lives in the POTION_CONTENTS component as Potions.AWKWARD.
               if (isAwkwardPotion(brewingStand.getItem(i))) {
                  return this.packDest(p, villager.getHouse());
               }
            }
         }
      }

      return null;
   }

   /**
    * 1.12 gated all actions on {@code brewingStand.getField(0) == 0} (brewTime idle). brewTime is no
    * longer publicly accessible on {@link BrewingStandBlockEntity}, so we approximate "idle" as "not
    * currently brewing": a brew is in progress only when the ingredient slot holds nether wart and at
    * least one water potion is loaded (the state that produces awkward potions). This keeps the
    * villager from disturbing an active brew.
    */
   private static boolean isIdle(BrewingStandBlockEntity brewingStand) {
      ItemStack ingredient = brewingStand.getItem(3);
      if (ingredient == ItemStack.EMPTY || ingredient.getItem() != Items.NETHER_WART) {
         return true;
      }

      for (int i = 0; i < 3; i++) {
         if (isWaterPotion(brewingStand.getItem(i))) {
            return false; // a water potion + nether wart = actively brewing into awkward
         }
      }

      return true;
   }

   private static boolean isWaterPotion(ItemStack stack) {
      return stack != ItemStack.EMPTY
         && stack.getItem() == Items.POTION
         && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
   }

   private static boolean isAwkwardPotion(ItemStack stack) {
      return stack != ItemStack.EMPTY
         && stack.getItem() == Items.POTION
         && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.AWKWARD);
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) throws Exception {
      return this.getDestination(villager) != null;
   }

   @Override
   public boolean performAction(MillVillager villager) throws Exception {
      int nbWarts = villager.getHouse().countGoods(Items.NETHER_WART);
      int nbBottles = villager.getHouse().countGoods(Items.GLASS_BOTTLE);
      int nbPotions = villager.getHouse().countGoods(Items.POTION);
      BrewingStandBlockEntity brewingStand = villager.getGoalDestPoint().getBrewingStand(villager.level());
      if (brewingStand == null) {
         return true;
      } else {
         if (isIdle(brewingStand)) {
            if (brewingStand.getItem(3) == ItemStack.EMPTY && nbWarts > 0 && nbPotions < 64) {
               brewingStand.setItem(3, new ItemStack(Items.NETHER_WART, 1));
               villager.getHouse().takeGoods(Items.NETHER_WART, 1);
            }

            if (nbBottles > 2 && nbPotions < 64) {
               for (int i = 0; i < 3; i++) {
                  if (brewingStand.getItem(i) == ItemStack.EMPTY) {
                     // 26.2: a water bottle is a POTION whose POTION_CONTENTS component is Potions.WATER
                     // (the 1.12 setTagInfo("Potion","minecraft:water") + meta 0).
                     ItemStack waterPotion = new ItemStack(Items.POTION, 1);
                     waterPotion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
                     brewingStand.setItem(i, waterPotion);
                     villager.getHouse().takeGoods(Items.GLASS_BOTTLE, 1);
                  }
               }
            }

            for (int ix = 0; ix < 3; ix++) {
               // Collect finished awkward potions (the 1.12 meta 16 result) back into the house store.
               if (isAwkwardPotion(brewingStand.getItem(ix))) {
                  brewingStand.setItem(ix, ItemStack.EMPTY);
                  villager.getHouse().storeGoods(Items.POTION, 16, 1);
               }
            }
         }

         return true;
      }
   }

   @Override
   public int priority(MillVillager villager) throws Exception {
      return 100;
   }

   @Override
   public boolean swingArms() {
      return true;
   }
}
