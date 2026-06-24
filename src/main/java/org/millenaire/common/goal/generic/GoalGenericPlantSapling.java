package org.millenaire.common.goal.generic;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.goal.Goal;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.BlockItemUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;

public class GoalGenericPlantSapling extends GoalGeneric {
   public static final String GOAL_TYPE = "plantsapling";

   @Override
   public void applyDefaultSettings() {
      this.duration = 2;
      this.lookAtGoal = true;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) throws MillLog.MillenaireException {
      List<Building> buildings = this.getBuildings(villager);
      List<Point> vp = new ArrayList<>();
      List<Point> buildingp = new ArrayList<>();

      for (Building grove : buildings) {
         Point p = grove.getResManager().getPlantingLocation();
         if (p != null) {
            vp.add(p);
            buildingp.add(grove.getPos());
         }
      }

      if (vp.isEmpty()) {
         return null;
      } else {
         Point p = vp.get(0);
         Point buildingP = buildingp.get(0);

         for (int i = 1; i < vp.size(); i++) {
            if (vp.get(i).horizontalDistanceToSquared(villager) < p.horizontalDistanceToSquared(villager)) {
               p = vp.get(i);
               buildingP = buildingp.get(i);
            }
         }

         return this.packDest(p, buildingP);
      }
   }

   @Override
   public ItemStack getIcon() {
      return this.icon != null ? this.icon.getItemStack() : InvItem.createInvItem(Blocks.OAK_SAPLING).getItemStack();
   }

   @Override
   public String getTypeLabel() {
      return "plantsapling";
   }

   @Override
   public boolean isDestPossibleSpecific(MillVillager villager, Building b) {
      return true;
   }

   @Override
   public boolean isPossibleGenericGoal(MillVillager villager) throws Exception {
      return this.getDestination(villager) != null;
   }

   @Override
   public boolean performAction(MillVillager villager) {
      Block block = WorldUtilities.getBlock(villager.level(), villager.getGoalDestPoint());
      if (block == Blocks.AIR
         || block == Blocks.SNOW
         || BlockItemUtilities.isBlockDecorativePlant(block) && !(block instanceof SaplingBlock)) {
         // An emergent MERGE/WAR absorb can DEMOTE the grove building while this goal still points at it, leaving
         // getGoalBuildingDest() null. That is a legitimate null (not an error): end the goal cleanly so it gets
         // re-picked, matching GoalFish's convention (return true).
         if (villager.getGoalBuildingDest() == null || villager.getGoalBuildingDest().getResManager() == null) {
            return true;
         }
         String saplingType = villager.getGoalBuildingDest().getResManager().getPlantingLocationType(villager.getGoalDestPoint());
         // 26.2: the 1.12 sapling meta-variants (SaplingBlock.TYPE + BlockPlanks.EnumType) are gone — each
         // wood type is a distinct sapling block. The planting-location type maps to the matching block.
         BlockState saplingBS = Blocks.OAK_SAPLING.defaultBlockState();
         if ("pinespawn".equals(saplingType)) {
            saplingBS = Blocks.SPRUCE_SAPLING.defaultBlockState();
         } else if ("birchspawn".equals(saplingType)) {
            saplingBS = Blocks.BIRCH_SAPLING.defaultBlockState();
         } else if ("junglespawn".equals(saplingType)) {
            saplingBS = Blocks.JUNGLE_SAPLING.defaultBlockState();
         } else if ("acaciaspawn".equals(saplingType)) {
            saplingBS = Blocks.ACACIA_SAPLING.defaultBlockState();
         } else if ("darkoakspawn".equals(saplingType)) {
            saplingBS = Blocks.DARK_OAK_SAPLING.defaultBlockState();
         } else if ("appletreespawn".equals(saplingType)) {
            saplingBS = MillBlocks.SAPLING_APPLETREE.defaultBlockState();
         } else if ("olivetreespawn".equals(saplingType)) {
            saplingBS = MillBlocks.SAPLING_OLIVETREE.defaultBlockState();
         } else if ("pistachiotreespawn".equals(saplingType)) {
            saplingBS = MillBlocks.SAPLING_PISTACHIO.defaultBlockState();
         } else if ("cherrytreespawn".equals(saplingType)) {
            saplingBS = MillBlocks.SAPLING_CHERRY.defaultBlockState();
         } else if ("sakuratreespawn".equals(saplingType)) {
            saplingBS = MillBlocks.SAPLING_SAKURA.defaultBlockState();
         }

         // Plant via the AI-invokable plant ACTION: reach-gate (scaffold-extend if needed; keep approaching until in
         // reach), STRICTLY verify the sapling can actually survive at the spot (a GENUINE plant, not one that pops
         // next tick), debit ONE matching sapling from stock, then place it with a real swing + place sound. STATELESS
         // — phase is the WORLD block state above, no per-goal field.
         net.minecraft.core.BlockPos pos = villager.getGoalDestPoint().getBlockPos();
         com.coderyo.jason.ops.OpState pst =
            com.coderyo.jason.ops.VillagerActions.plantBlock(villager, pos, saplingBS, saplingBS.getBlock().asItem(), 0);
         if (pst == com.coderyo.jason.ops.OpState.APPROACHING
            || pst == com.coderyo.jason.ops.OpState.EXTENDING_REACH) {
            return false; // still walking / scaffolding into reach — keep the goal and retry next tick.
         }
         if (pst == com.coderyo.jason.ops.OpState.COMPLETE && MillConfigValues.LogLumberman >= 3 && villager.extraLog) {
            MillLog.debug(this, "Planted at: " + villager.getGoalDestPoint());
         } else if (pst == com.coderyo.jason.ops.OpState.BLOCKED && MillConfigValues.LogLumberman >= 3 && villager.extraLog) {
            MillLog.debug(this, "Plant BLOCKED (sapling cannot survive) at: " + villager.getGoalDestPoint());
         }
      } else if (MillConfigValues.LogLumberman >= 3 && villager.extraLog) {
         MillLog.debug(this, "Failed to plant at: " + villager.getGoalDestPoint());
      }

      return true;
   }

   @Override
   public boolean validateGoal() {
      return true;
   }
}
