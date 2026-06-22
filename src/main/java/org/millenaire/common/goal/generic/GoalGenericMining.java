package org.millenaire.common.goal.generic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import org.millenaire.common.annotedparameters.AnnotedParameter;
import org.millenaire.common.annotedparameters.ConfigAnnotations;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.goal.Goal;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.pathing.atomicstryker.AStarConfig;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillRandom;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;

public class GoalGenericMining extends GoalGeneric {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] IS_ULU;
   public static final String GOAL_TYPE = "mining";

   private static ItemStack[] isUlu() {
      if (IS_ULU == null) {
         IS_ULU = new ItemStack[]{new ItemStack(MillItems.ULU, 1)};
      }
      return IS_ULU;
   }
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.BLOCKSTATE
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Blockstate of the source, like stone (not necessarily the block being harvest - stone gives cobblestone for example)."
   )
   public BlockState sourceBlockState = null;
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.INVITEM_NUMBER_ADD,
      paramName = "loot"
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Blocks or items  gained when mining."
   )
   public Map<InvItem, Integer> loots = new HashMap<>();

   @Override
   public int actionDuration(MillVillager villager) {
      Block block = this.sourceBlockState.getBlock();
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

   @Override
   public void applyDefaultSettings() {
      this.lookAtGoal = true;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) throws Exception {
      List<Building> buildings = this.getBuildings(villager);
      List<Point> validSources = new ArrayList<>();
      List<Building> validDests = new ArrayList<>();

      for (Building possibleDest : buildings) {
         List<CopyOnWriteArrayList<Point>> sources = possibleDest.getResManager().sources;

         for (int i = 0; i < sources.size(); i++) {
            if (this.sourceBlockState == possibleDest.getResManager().sourceTypes.get(i)) {
               for (int j = 0; j < sources.get(i).size(); j++) {
                  BlockState actualBlockState = WorldUtilities.getBlockState(villager.level(), sources.get(i).get(j));
                  if (actualBlockState == this.sourceBlockState) {
                     validSources.add(sources.get(i).get(j));
                     validDests.add(possibleDest);
                  }
               }
            }
         }
      }

      if (validSources.isEmpty()) {
         return null;
      } else {
         int randomTarget = MillRandom.randomInt(validSources.size());
         return this.packDest(validSources.get(randomTarget), validDests.get(randomTarget));
      }
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) throws Exception {
      Block targetBlock = this.sourceBlockState.getBlock();
      if (targetBlock == Blocks.SAND || targetBlock == Blocks.CLAY || targetBlock == Blocks.GRAVEL) {
         return villager.getBestShovelStack();
      } else {
         return targetBlock != Blocks.SNOW_BLOCK && targetBlock != Blocks.ICE ? villager.getBestPickaxeStack() : isUlu();
      }
   }

   @Override
   public ItemStack getIcon() {
      if (this.icon != null) {
         return this.icon.getItemStack();
      } else {
         return this.sourceBlockState != null
            ? new ItemStack(this.sourceBlockState.getBlock(), 1)
            : null;
      }
   }

   @Override
   public AStarConfig getPathingConfig(MillVillager villager) {
      return !villager.canVillagerClearLeaves() ? JPS_CONFIG_WIDE_NO_LEAVES : JPS_CONFIG_WIDE;
   }

   @Override
   public String getTypeLabel() {
      return "mining";
   }

   @Override
   public boolean isDestPossibleSpecific(MillVillager villager, Building b) {
      return true;
   }

   @Override
   public boolean isPossibleGenericGoal(MillVillager villager) throws Exception {
      return true;
   }

   @Override
   public boolean performAction(MillVillager villager) throws Exception {
      for (InvItem key : this.loots.keySet()) {
         villager.addToInv(key, this.loots.get(key));
         if (MillConfigValues.LogMiner >= 3 && villager.extraLog) {
            MillLog.debug(this, "Gathered " + key + " at: " + villager.getGoalDestPoint());
         }
      }

      WorldUtilities.playSoundBlockBreaking(villager.level(), villager.getGoalDestPoint(), this.sourceBlockState.getBlock(), 1.0F);
      return true;
   }

   @Override
   public boolean stuckAction(MillVillager villager) throws Exception {
      return this.performAction(villager);
   }

   @Override
   public boolean swingArms() {
      return true;
   }

   @Override
   public boolean validateGoal() {
      return true;
   }
}
