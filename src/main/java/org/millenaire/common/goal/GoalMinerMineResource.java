package org.millenaire.common.goal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.pathing.atomicstryker.AStarConfig;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;

@DocumentedElement.Documentation("Go and mine rocks etc at the villager's house.")
public class GoalMinerMineResource extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] IS_ULU;
   public String buildingTag = null;

   private static ItemStack[] isUlu() {
      if (IS_ULU == null) {
         IS_ULU = new ItemStack[]{new ItemStack(MillItems.ULU, 1)};
      }
      return IS_ULU;
   }

   public GoalMinerMineResource() {
      this.icon = InvItem.createInvItem(Items.IRON_PICKAXE);
   }

   @Override
   public int actionDuration(MillVillager villager) {
      Block block = villager.getBlock(villager.getGoalDestPoint());
      if (block == Blocks.STONE || block == Blocks.SANDSTONE) {
         int toolEfficiency = (int)villager.getBestPickaxe().getDestroySpeed(new ItemStack(villager.getBestPickaxe(), 1), Blocks.SANDSTONE.defaultBlockState());
         return 140 - 4 * toolEfficiency;
      } else if (block != Blocks.SAND && block != Blocks.CLAY && block != Blocks.GRAVEL) {
         return 70;
      } else {
         int toolEfficiency = (int)villager.getBestShovel().getDestroySpeed(new ItemStack(villager.getBestShovel(), 1), Blocks.SAND.defaultBlockState());
         return 140 - 4 * toolEfficiency;
      }
   }

   public List<Building> getBuildings(MillVillager villager) {
      List<Building> buildings = new ArrayList<>();
      if (this.buildingTag == null) {
         buildings.add(villager.getHouse());
      } else {
         for (Building b : villager.getTownHall().getBuildingsWithTag(this.buildingTag)) {
            buildings.add(b);
         }
      }

      return buildings;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) throws Exception {
      List<Building> buildings = this.getBuildings(villager);
      List<Point> validSources = new ArrayList<>();
      List<Building> validDests = new ArrayList<>();

      for (Building possibleDest : buildings) {
         List<CopyOnWriteArrayList<Point>> sources = possibleDest.getResManager().sources;

         for (int i = 0; i < sources.size(); i++) {
            BlockState sourceBlockState = possibleDest.getResManager().sourceTypes.get(i);

            for (int j = 0; j < sources.get(i).size(); j++) {
               BlockState actualBlockState = WorldUtilities.getBlockState(villager.level(), sources.get(i).get(j));
               if (actualBlockState == sourceBlockState) {
                  validSources.add(sources.get(i).get(j));
                  validDests.add(possibleDest);
               }
            }
         }
      }

      if (validSources.isEmpty()) {
         return null;
      } else {
         int randomTarget = MillCommonUtilities.randomInt(validSources.size());
         return this.packDest(validSources.get(randomTarget), validDests.get(randomTarget));
      }
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) throws Exception {
      Block targetBlock = villager.getBlock(villager.getGoalDestPoint());
      if (targetBlock == Blocks.SAND || targetBlock == Blocks.CLAY || targetBlock == Blocks.GRAVEL) {
         return villager.getBestShovelStack();
      } else {
         return targetBlock != Blocks.SNOW && targetBlock != Blocks.ICE ? villager.getBestPickaxeStack() : isUlu();
      }
   }

   @Override
   public AStarConfig getPathingConfig(MillVillager villager) {
      return !villager.canVillagerClearLeaves() ? JPS_CONFIG_WIDE_NO_LEAVES : JPS_CONFIG_WIDE;
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
      BlockState blockState = WorldUtilities.getBlockState(villager.level(), villager.getGoalDestPoint());
      Block block = blockState.getBlock();
      if (block == Blocks.SAND) {
         villager.addToInv(Blocks.SAND, 1);
         WorldUtilities.playSoundBlockBreaking(villager.level(), villager.getGoalDestPoint(), Blocks.SAND, 1.0F);
         if (MillConfigValues.LogMiner >= 3 && villager.extraLog) {
            MillLog.debug(this, "Gathered sand at: " + villager.getGoalDestPoint());
         }
      } else if (block == Blocks.STONE) {
         // 26.2: BlockStone.VARIANT is gone; granite/diorite/etc. are distinct blocks now, so plain STONE always yields cobblestone
         villager.addToInv(Blocks.COBBLESTONE, 1);
         WorldUtilities.playSoundBlockBreaking(villager.level(), villager.getGoalDestPoint(), Blocks.STONE, 1.0F);
         if (MillConfigValues.LogMiner >= 3 && villager.extraLog) {
            MillLog.debug(this, "Gather cobblestone at: " + villager.getGoalDestPoint());
         }
      } else if (block == Blocks.SANDSTONE) {
         villager.addToInv(Blocks.SANDSTONE, 1);
         WorldUtilities.playSoundBlockBreaking(villager.level(), villager.getGoalDestPoint(), Blocks.SANDSTONE, 1.0F);
         if (MillConfigValues.LogMiner >= 3 && villager.extraLog) {
            MillLog.debug(this, "Gather sand stone at: " + villager.getGoalDestPoint());
         }
      } else if (block == Blocks.CLAY) {
         villager.addToInv(Items.CLAY_BALL, 1);
         WorldUtilities.playSoundBlockBreaking(villager.level(), villager.getGoalDestPoint(), Blocks.CLAY, 1.0F);
         if (MillConfigValues.LogMiner >= 3 && villager.extraLog) {
            MillLog.debug(this, "Gather clay at: " + villager.getGoalDestPoint());
         }
      } else if (block == Blocks.GRAVEL) {
         villager.addToInv(Blocks.GRAVEL, 1);
         WorldUtilities.playSoundBlockBreaking(villager.level(), villager.getGoalDestPoint(), Blocks.GRAVEL, 1.0F);
         if (MillConfigValues.LogMiner >= 3 && villager.extraLog) {
            MillLog.debug(this, "Gather gravel at: " + villager.getGoalDestPoint());
         }
      } else if (block == Blocks.SNOW) {
         villager.addToInv(MillBlocks.SNOW_BRICK, 1);
         WorldUtilities.playSoundBlockBreaking(villager.level(), villager.getGoalDestPoint(), Blocks.SNOW_BLOCK, 1.0F);
         if (MillConfigValues.LogMiner >= 3) {
            MillLog.debug(this, "Gather snow at: " + villager.getGoalDestPoint());
         }
      } else if (block == Blocks.ICE) {
         villager.addToInv(MillBlocks.ICE_BRICK, 1);
         WorldUtilities.playSoundBlockBreaking(villager.level(), villager.getGoalDestPoint(), Blocks.ICE, 1.0F);
         if (MillConfigValues.LogMiner >= 3) {
            MillLog.debug(this, "Gather ice at: " + villager.getGoalDestPoint());
         }
      }

      return true;
   }

   @Override
   public int priority(MillVillager villager) throws Exception {
      return 30;
   }

   @Override
   public int range(MillVillager villager) {
      return 5;
   }

   @Override
   public boolean stuckAction(MillVillager villager) throws Exception {
      return this.performAction(villager);
   }

   @Override
   public boolean swingArms() {
      return true;
   }
}
