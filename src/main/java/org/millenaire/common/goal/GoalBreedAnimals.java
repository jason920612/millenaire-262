package org.millenaire.common.goal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.pathing.atomicstryker.AStarConfig;
import org.millenaire.common.utilities.WorldUtilities;

@DocumentedElement.Documentation("Breed animals at the villager's house, of types decided by tags like 'cattle', 'pig' etc tags.")
public class GoalBreedAnimals extends Goal {
   private static final Item[] CEREALS = new Item[]{Items.WHEAT, MillItems.RICE, MillItems.MAIZE};
   private static final Item[] SEEDS = new Item[]{Items.WHEAT_SEEDS, MillItems.RICE, MillItems.MAIZE};
   private static final Item[] CARROTS = new Item[]{Items.CARROT};

   public GoalBreedAnimals() {
      this.icon = InvItem.createInvItem(Items.WHEAT);
   }

   private Item[] getBreedingItems(Class animalClass) {
      if (animalClass == Cow.class || animalClass == Sheep.class) {
         return CEREALS;
      } else if (animalClass == Pig.class) {
         return CARROTS;
      } else {
         return animalClass == Chicken.class ? SEEDS : null;
      }
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) throws Exception {
      for (Class animalClass : this.getValidAnimalClasses(villager)) {
         Item[] breedingItems = this.getBreedingItems(animalClass);
         boolean available = false;
         if (breedingItems == null) {
            available = true;
         } else {
            for (Item breedingItem : breedingItems) {
               if (!available && villager.getHouse().countGoods(breedingItem) > 0) {
                  available = true;
               }
            }
         }

         if (available) {
            int targetAnimals = 0;

            for (int i = 0; i < villager.getHouse().getResManager().spawns.size(); i++) {
               EntityType<?> spawnType = BuiltInRegistries.ENTITY_TYPE.getValue(villager.getHouse().getResManager().spawnTypes.get(i));
               if (spawnType != null && animalClass.isAssignableFrom(spawnType.getBaseClass())) {
                  targetAnimals = villager.getHouse().getResManager().spawns.get(i).size();
               }
            }

            List<Entity> animals = WorldUtilities.getEntitiesWithinAABB(villager.level(), animalClass, villager.getHouse().getPos(), 15, 10);
            int nbAdultAnimal = 0;
            int nbAnimal = 0;

            for (Entity ent : animals) {
               Animal animal = (Animal)ent;
               if (animal.getAge() == 0) {
                  nbAdultAnimal++;
               }

               nbAnimal++;
            }

            if (nbAdultAnimal >= 2 && nbAnimal < targetAnimals * 2) {
               for (Entity ent : animals) {
                  Animal animal = (Animal)ent;
                  if (animal.getAge() == 0 && !animal.isInLove()) {
                     return this.packDest(null, villager.getHouse(), animal);
                  }
               }
            }
         }
      }

      return null;
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) throws Exception {
      if (villager.getGoalDestEntity() != null && villager.getGoalDestEntity() instanceof Animal) {
         Animal animal = (Animal)villager.getGoalDestEntity();
         Item[] breedingItems = this.getBreedingItems(animal.getClass());
         if (breedingItems != null) {
            for (Item breedingItem : breedingItems) {
               if (villager.getHouse().countGoods(breedingItem) > 0) {
                  return new ItemStack[]{new ItemStack(breedingItem, 1)};
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   @Override
   public AStarConfig getPathingConfig(MillVillager villager) {
      return !villager.canVillagerClearLeaves() ? JPS_CONFIG_WIDE_NO_LEAVES : JPS_CONFIG_WIDE;
   }

   private List<Class> getValidAnimalClasses(MillVillager villager) {
      List<Class> validAnimals = new ArrayList<>();
      if (villager.getHouse().containsTags("sheeps")) {
         validAnimals.add(Sheep.class);
         validAnimals.add(Chicken.class);
      }

      if (villager.getHouse().containsTags("cattle")) {
         validAnimals.add(Cow.class);
      }

      if (villager.getHouse().containsTags("pigs")) {
         validAnimals.add(Pig.class);
      }

      if (villager.getHouse().containsTags("chicken")) {
         validAnimals.add(Chicken.class);
      }

      return validAnimals;
   }

   @Override
   public boolean isFightingGoal() {
      return false;
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) throws Exception {
      return this.getDestination(villager) != null;
   }

   @Override
   public boolean lookAtGoal() {
      return true;
   }

   @Override
   public boolean performAction(MillVillager villager) throws Exception {
      for (Class animalClass : this.getValidAnimalClasses(villager)) {
         List<? extends Entity> nearbyAnimals = WorldUtilities.getEntitiesWithinAABB(villager.level(), animalClass, villager.getPos(), 4, 2);
         for (Entity ent : nearbyAnimals) {
            if (!ent.isRemoved()) {
               Animal animal = (Animal)ent;
               Item[] breedingItems = this.getBreedingItems(animal.getClass());
               boolean available = false;
               Item foundBreedingItem = null;
               if (breedingItems == null) {
                  available = true;
               } else {
                  for (Item breedingItem : breedingItems) {
                     if (!available && villager.getHouse().countGoods(breedingItem) > 0) {
                        available = true;
                        foundBreedingItem = breedingItem;
                     }
                  }
               }

               if (available && !animal.isBaby() && !animal.isInLove() && animal.getAge() == 0) {
                  animal.setInLove(null);
                  animal.setTarget(null);
                  if (foundBreedingItem != null) {
                     villager.getHouse().takeGoods(foundBreedingItem, 1);
                  }

                  villager.swing(InteractionHand.MAIN_HAND);
                  ServerSender.sendAnimalBreeding(animal);
               }
            }
         }
      }

      return true;
   }

   @Override
   public int priority(MillVillager villager) throws Exception {
      return 10000;
   }

   @Override
   public int range(MillVillager villager) {
      return 5;
   }
}
