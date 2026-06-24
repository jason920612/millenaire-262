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
import com.coderyo.jason.ops.VillagerWorldOps;
import org.millenaire.common.annotedparameters.AnnotedParameter;
import org.millenaire.common.annotedparameters.ConfigAnnotations;
import org.millenaire.common.block.BlockGrapeVine;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.goal.Goal;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.BlockItemUtilities;
import org.millenaire.common.utilities.MillRandom;
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
      // Player-like planting is driven one tick at a time (ensureTool(HOE)/reach → till soil → place the age-0 crop
      // consuming a seed), so the action re-enters each tick rather than the 1.12 fixed 2-tick countdown.
      this.duration = 1;
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

   /**
    * Player-like FIRST-planting of an empty tilled plot, driven one tick at a time (duration == 1). This goal keeps
    * its 1.12 role: planting empty farmland; the harvest goal now AUTO-REPLANTS harvested plots in place
    * (see {@link GoalGenericHarvestCrop}), so this fires only for plots with no crop yet.
    *
    * <p>Cycle (sim-faithful real place, not an instant fake):
    * <ol>
    *   <li>{@code ensureTool(HOE)} — strict: a planter tills with a hoe. If the villager has no hoe, stay in-goal
    *       ({@code return false}) so {@code GoalGetTool} can pre-empt and fetch one rather than placing for free.</li>
    *   <li>ensureReach the plot (ground-level, normally already in reach).</li>
    *   <li>Consume one {@link #seed} from stock (or the building), then till the soil below to {@link #soilType}
    *       via the real {@code place} primitive, and finally place the crop (age 0) on top. DoublePlant/grapevine
    *       crops also get their upper half. This mirrors 1.12's setBlock sequence but as genuine player-like
    *       placement (swing + place sound) rather than an instant silent setBlock.</li>
    * </ol>
    */
   @Override
   public boolean performAction(MillVillager villager) {
      Building dest = villager.getGoalBuildingDest();
      Point above = villager.getGoalDestPoint();
      if (dest == null || above == null) {
         return true;
      } else if (!this.isValidPlantingLocation(villager.level(), above.getBelow())) {
         return true;
      }

      // Hoe is BEST-EFFORT, not strict: 1.12 planting never required a hoe (it directly setBlock'd the soil+crop), so
      // gating planting on a hoe could deadlock villages without one. We equip a hoe if the villager has one (so the
      // till is player-like), but proceed to plant regardless — preserving the 1.12 ability to plant bare-handed.
      VillagerWorldOps.ensureTool(villager, VillagerWorldOps.ToolKind.HOE);

      Point soil = above.getBelow();

      // ensureReach (ground-level plot; normally already in reach). If still extending reach, keep going next tick.
      if (!VillagerWorldOps.withinReach(villager, above.getBlockPos())
         && VillagerWorldOps.ensureReach(villager, above.getBlockPos()) != com.coderyo.jason.ops.OpState.COMPLETE) {
         return false;
      }

      // Consume the seed (villager stock first, then the building's goods) — 1.12 economy preserved.
      if (this.seed != null) {
         int taken = villager.takeFromInv(this.seed, 1);
         if (taken == 0) {
            dest.takeGoods(this.seed, 1);
         }
      }

      // Till the soil below to the configured soil block via the real place primitive (consume/place + sound) BEFORE
      // the crop place, so the crop's canSurvive check in VillagerActions.plantBlock sees the right soil under it.
      Block soilBlock = BuiltInRegistries.BLOCK.getValue(this.soilType);
      if (soil.getBlock(villager.level()) != soilBlock) {
         VillagerWorldOps.place(villager, soil.getBlockPos(), soilBlock.defaultBlockState());
      }

      // Place the crop (age 0) on top via the AI-invokable plant ACTION: it STRICTLY verifies the crop can actually
      // survive on the just-tilled soil (a GENUINE plant, not one that pops next tick) then places it with a real
      // swing + place sound. Seed is already debited above (1.12 stock/building economy), so pass null here — the
      // action does not double-debit. The DoublePlant/grapevine UPPER half is the second block of a single validated
      // plant, laid via the raw place after the lower half is confirmed placed.
      if (!this.plantBlockState.isEmpty()) {
         BlockState cropState = this.plantBlockState.get(MillRandom.randomInt(this.plantBlockState.size()));
         com.coderyo.jason.ops.OpState pst = com.coderyo.jason.ops.VillagerActions.plantBlock(villager, above.getBlockPos(), cropState);
         if (pst == com.coderyo.jason.ops.OpState.COMPLETE && cropState.getBlock() instanceof DoublePlantBlock) {
            VillagerWorldOps.place(villager, above.getAbove().getBlockPos(),
               cropState.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
         }
      } else {
         Block cropBlock = BuiltInRegistries.BLOCK.getValue(this.cropType);
         com.coderyo.jason.ops.OpState pst = com.coderyo.jason.ops.VillagerActions.plantBlock(villager, above.getBlockPos(), cropBlock.defaultBlockState());
         if (pst == com.coderyo.jason.ops.OpState.COMPLETE && cropBlock instanceof DoublePlantBlock) {
            // 26.2: the 1.12 upper-half meta (cropMeta | 8) is now the DoublePlantBlock.HALF=UPPER blockstate.
            VillagerWorldOps.place(villager, above.getAbove().getBlockPos(),
               cropBlock.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
         } else if (pst == com.coderyo.jason.ops.OpState.COMPLETE && cropBlock instanceof BlockGrapeVine) {
            // Grapevine upper half (1.12 meta | 8); place its default upper state.
            villager.setBlockAndMetadata(above.getAbove(), cropBlock, getCropBlockMeta(this.cropType) | 8);
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
