package org.millenaire.common.goal.generic;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.millenaire.common.annotedparameters.AnnotedParameter;
import org.millenaire.common.annotedparameters.ConfigAnnotations;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.goal.Goal;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillRandom;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;

public class GoalGenericHarvestCrop extends GoalGeneric {
   public static final String GOAL_TYPE = "harvesting";
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.BLOCK_ID
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Type of plant to harvest."
   )
   public Identifier cropType = null;
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.BONUS_ITEM_ADD
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Item to be harvested, with chance."
   )
   public List<AnnotedParameter.BonusItem> harvestItem = new ArrayList<>();
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.INVITEM
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Boons for irrigated villages."
   )
   public InvItem irrigationBonusCrop = null;
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.BLOCKSTATE
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Blockstate the crop must have to be harvested. If not set, must have a meta of 7."
   )
   public BlockState harvestBlockState = null;

   public static int getCropBlockRipeMeta(Identifier cropType) {
      return 7;
   }

   @Override
   public void applyDefaultSettings() {
      this.duration = 2;
      this.lookAtGoal = true;
      this.tags.add("tag_agriculture");
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) throws MillLog.MillenaireException {
      Point dest = null;
      Building destBuilding = null;

      for (Building buildingDest : this.getBuildings(villager)) {
         if (this.isDestPossible(villager, buildingDest)) {
            List<Point> soils = buildingDest.getResManager().getSoilPoints(this.cropType);
            if (soils != null) {
               for (Point p : soils) {
                  if (this.isValidHarvestSoil(villager.level(), p) && (dest == null || p.distanceTo(villager) < dest.distanceTo(villager))) {
                     dest = p.getAbove();
                     destBuilding = buildingDest;
                  }
               }
            }
         }
      }

      return dest == null ? null : this.packDest(dest, destBuilding);
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) throws Exception {
      return villager.getBestHoeStack();
   }

   @Override
   public ItemStack getIcon() {
      if (this.icon != null) {
         return this.icon.getItemStack();
      } else {
         return !this.harvestItem.isEmpty() ? this.harvestItem.get(0).item.getItemStack() : null;
      }
   }

   @Override
   public String getTypeLabel() {
      return "harvesting";
   }

   @Override
   public boolean isDestPossibleSpecific(MillVillager villager, Building b) {
      return true;
   }

   @Override
   public boolean isPossibleGenericGoal(MillVillager villager) throws Exception {
      return this.getDestination(villager) != null;
   }

   private boolean isValidHarvestSoil(Level world, Point p) {
      if (this.harvestBlockState != null) {
         return p.getAbove().getBlockActualState(world) == this.harvestBlockState;
      }
      Block cropBlock = BuiltInRegistries.BLOCK.getValue(this.cropType);
      if (p.getAbove().getBlock(world) != cropBlock) {
         return false;
      }
      // 1.12 compared the legacy meta (== crop AGE) to 7. In 26.2 metadata is gone, so
      // read the AGE blockstate property directly; ripe == max age (7 for crops).
      BlockState cropState = p.getAbove().getBlockActualState(world);
      if (cropBlock instanceof net.minecraft.world.level.block.CropBlock crop) {
         return crop.getAge(cropState) >= getCropBlockRipeMeta(this.cropType);
      }
      return false;
   }

   @Override
   public boolean performAction(MillVillager villager) {
      if (this.isValidHarvestSoil(villager.level(), villager.getGoalDestPoint().getBelow())) {
         if (this.irrigationBonusCrop != null) {
            float irrigation = villager.getTownHall().getVillageIrrigation();
            double rand = Math.random();
            if (rand < irrigation / 100.0F) {
               villager.addToInv(this.irrigationBonusCrop, 1);
            }
         }

         Building dest = villager.getGoalBuildingDest();

         for (AnnotedParameter.BonusItem bonusItem : this.harvestItem) {
            if ((bonusItem.tag == null || dest != null && dest.containsTags(bonusItem.tag)) && MillRandom.randomInt(100) <= bonusItem.chance) {
               villager.addToInv(bonusItem.item, 1);
            }
         }

         villager.setBlockAndMetadata(villager.getGoalDestPoint(), Blocks.AIR, 0);
         if (villager.getBlock(villager.getGoalDestPoint().getAbove()) instanceof DoublePlantBlock) {
            villager.setBlockAndMetadata(villager.getGoalDestPoint().getAbove(), Blocks.AIR, 0);
         }

         villager.swing(InteractionHand.MAIN_HAND);
      }

      if (this.isDestPossibleSpecific(villager, villager.getGoalBuildingDest())) {
         try {
            villager.setGoalInformation(this.getDestination(villager));
         } catch (MillLog.MillenaireException destException) {
            // FAIL-FAST: failing to recompute the harvest destination left the villager's goal state
            // stale (1.12 logged-and-continued). Surface the navigation corruption loudly.
            throw MillCrash.fail("Goal", "failed to recompute harvest-crop destination for " + villager + ": " + destException);
         }

         return false;
      } else {
         return true;
      }
   }

   @Override
   public int priority(MillVillager villager) throws MillLog.MillenaireException {
      Goal.GoalInformation info = this.getDestination(villager);
      return info != null && info.getDest() != null ? (int)(1000.0 - villager.getPos().distanceTo(info.getDest())) : -1;
   }

   @Override
   public boolean validateGoal() {
      if (this.cropType == null) {
         MillLog.error(this, "The croptype is mandatory in custom harvest goals.");
         return false;
      } else {
         return true;
      }
   }
}
