package org.millenaire.common.goal.generic;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.millenaire.common.annotedparameters.AnnotedParameter;
import org.millenaire.common.annotedparameters.ConfigAnnotations;
import org.millenaire.common.block.BlockGrapeVine;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.goal.Goal;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.BlockItemUtilities;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;

public class GoalGenericPlantCrop extends GoalGeneric {
   public static final String GOAL_TYPE = "planting";
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.BLOCK_ID
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Type of plant to plant."
   )
   public Identifier cropType = null;
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.BLOCKSTATE_ADD
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Blockstate to plant. If not set, defaults to cropType. If more than one set, picks one at random."
   )
   public List<BlockState> plantBlockState = new ArrayList<>();
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.INVITEM
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Seed item that gets consumed when planting."
   )
   public InvItem seed = null;
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.BLOCK_ID,
      defaultValue = "minecraft:farmland"
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Block to set below the crop."
   )
   public Identifier soilType = null;

   public static int getCropBlockMeta(Identifier cropType2) {
      return 0;
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
                  if (this.isValidPlantingLocation(villager.level(), p) && (dest == null || p.distanceTo(villager) < dest.distanceTo(villager))) {
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
   public ItemStack getIcon() {
      if (this.icon != null) {
         return this.icon.getItemStack();
      } else if (this.seed != null) {
         return this.seed.getItemStack();
      } else {
         return this.heldItems != null && this.heldItems.length > 0 ? this.heldItems[0] : null;
      }
   }

   @Override
   public String getTypeLabel() {
      return "planting";
   }

   @Override
   public boolean isDestPossibleSpecific(MillVillager villager, Building b) {
      return this.seed == null || b.countGoods(this.seed) + villager.countInv(this.seed) != 0;
   }

   @Override
   public boolean isPossibleGenericGoal(MillVillager villager) throws Exception {
      return this.getDestination(villager) != null;
   }

   private boolean isValidPlantingLocation(Level world, Point p) {
      Block blockTwoAbove = p.getAbove().getAbove().getBlock(world);
      Block blockAbove = p.getAbove().getBlock(world);
      Block farmBlock = p.getBlock(world);
      if (blockAbove != Blocks.AIR && blockAbove != Blocks.SNOW_BLOCK && blockAbove != Blocks.OAK_LEAVES
         || blockTwoAbove != Blocks.AIR && blockTwoAbove != Blocks.SNOW_BLOCK && blockTwoAbove != Blocks.OAK_LEAVES
         || farmBlock != Blocks.GRASS_BLOCK && farmBlock != Blocks.DIRT && farmBlock != Blocks.FARMLAND) {
         if (BlockItemUtilities.isBlockDecorativePlant(blockAbove)) {
            if (!this.cropType.equals(Mill.CROP_FLOWER)) {
               return true;
            }

            // 26.2: Blocks.DOUBLE_PLANT is gone; each tall plant is now its own DoublePlantBlock instance
            if (blockAbove != Blocks.POPPY && blockAbove != Blocks.DANDELION && !(blockAbove instanceof DoublePlantBlock)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   @Override
   public boolean performAction(MillVillager villager) {
      Building dest = villager.getGoalBuildingDest();
      if (dest == null) {
         return true;
      } else if (!this.isValidPlantingLocation(villager.level(), villager.getGoalDestPoint().getBelow())) {
         return true;
      } else {
         if (this.seed != null) {
            int taken = villager.takeFromInv(this.seed, 1);
            if (taken == 0) {
               dest.takeGoods(this.seed, 1);
            }
         }

         Block soil = BuiltInRegistries.BLOCK.getValue(this.soilType);
         if (villager.getGoalDestPoint().getBelow().getBlock(villager.level()) != soil) {
            villager.setBlockAndMetadata(villager.getGoalDestPoint().getBelow(), soil, 0);
         }

         if (!this.plantBlockState.isEmpty()) {
            BlockState cropState = this.plantBlockState.get(MillCommonUtilities.randomInt(this.plantBlockState.size()));
            villager.setBlockstate(villager.getGoalDestPoint(), cropState);
            if (cropState.getBlock() instanceof DoublePlantBlock) {
               villager.setBlockstate(villager.getGoalDestPoint().getAbove(), cropState.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            }
         } else {
            Block cropBlock = BuiltInRegistries.BLOCK.getValue(this.cropType);
            int cropMeta = getCropBlockMeta(this.cropType);
            villager.setBlockAndMetadata(villager.getGoalDestPoint(), cropBlock, cropMeta);
            if (cropBlock instanceof DoublePlantBlock) {
               // 26.2: the 1.12 upper-half meta (cropMeta | 8) is now the DoublePlantBlock.HALF=UPPER
               // blockstate property.
               villager.setBlockstate(
                  villager.getGoalDestPoint().getAbove(),
                  cropBlock.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER)
               );
            } else if (cropBlock instanceof BlockGrapeVine) {
               villager.setBlockAndMetadata(villager.getGoalDestPoint().getAbove(), cropBlock, cropMeta | 8);
            }
         }

         villager.swing(InteractionHand.MAIN_HAND);
         if (this.isDestPossibleSpecific(villager, villager.getGoalBuildingDest())) {
            try {
               villager.setGoalInformation(this.getDestination(villager));
            } catch (MillLog.MillenaireException destException) {
               // FAIL-FAST: failing to recompute the plant destination left the villager's goal state stale
               // (1.12 logged-and-continued). Surface the navigation corruption loudly.
               throw MillCrash.fail("Goal", "failed to recompute plant-crop destination for " + villager + ": " + destException);
            }

            return false;
         } else {
            return true;
         }
      }
   }

   @Override
   public int priority(MillVillager villager) throws MillLog.MillenaireException {
      Goal.GoalInformation info = this.getDestination(villager);
      return info != null && info.getDest() != null ? (int)(100.0 - villager.getPos().distanceTo(info.getDest())) : -1;
   }

   @Override
   public boolean validateGoal() {
      if (this.cropType == null) {
         MillLog.error(this, "The croptype is mandatory in custom planting goals.");
         return false;
      } else {
         return true;
      }
   }
}
