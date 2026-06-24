package org.millenaire.common.goal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.pathing.atomicstryker.AStarConfig;
import org.millenaire.common.utilities.BlockItemUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;

@DocumentedElement.Documentation("Go plant saplings in a grove.")
public class GoalLumbermanPlantSaplings extends Goal {
   public GoalLumbermanPlantSaplings() {
      this.maxSimultaneousInBuilding = 1;
      this.icon = InvItem.createInvItem(Blocks.OAK_SAPLING);
   }

   @Override
   public int actionDuration(MillVillager villager) {
      return 20;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      List<Point> vp = new ArrayList<>();
      List<Point> buildingp = new ArrayList<>();

      for (Building grove : villager.getTownHall().getBuildingsWithTag("grove")) {
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
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) {
      // An emergent MERGE/WAR absorb can DEMOTE the grove while this goal still points at it, leaving
      // getGoalBuildingDest() null. That is a legitimate null: show the default sapling rather than NPE.
      if (villager.getGoalBuildingDest() == null || villager.getGoalBuildingDest().getResManager() == null) {
         return new ItemStack[]{new ItemStack(Blocks.OAK_SAPLING, 1)};
      }
      String saplingType = villager.getGoalBuildingDest().getResManager().getPlantingLocationType(villager.getGoalDestPoint());
      int meta = 0;
      if ("pinespawn".equals(saplingType)) {
         meta = 1;
      }

      if ("birchspawn".equals(saplingType)) {
         meta = 2;
      }

      if ("junglespawn".equals(saplingType)) {
         meta = 3;
      }

      if ("acaciaspawn".equals(saplingType)) {
         meta = 4;
      }

      if ("darkoakspawn".equals(saplingType)) {
         meta = 5;
      }

      return new ItemStack[]{new ItemStack(Blocks.OAK_SAPLING, 1) /* 26.2: item metadata removed — the variant is the item itself */};
   }

   @Override
   public AStarConfig getPathingConfig(MillVillager villager) {
      return !villager.canVillagerClearLeaves() ? JPS_CONFIG_WIDE_NO_LEAVES : JPS_CONFIG_WIDE;
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) {
      for (Building grove : villager.getTownHall().getBuildingsWithTag("grove")) {
         Point p = grove.getResManager().getPlantingLocation();
         if (p != null) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean lookAtGoal() {
      return true;
   }

   /**
    * Player-like sapling plant, reach-gated and real (mirrors the migrated plant goals — crop/cacao/sugarcane).
    * STATELESS — phase is the WORLD block state at the dest each tick (shared SINGLETON, no per-goal field).
    *
    * <p>1.12 mechanic + economy KEPT: the variant comes from the grove's planting-location type and one matching
    * sapling is consumed from stock. The ONLY change vs 1.12 is the genuine player-like gate: the villager must be
    * within player REACH (walk closer / scaffold-extend if not) before the place, then it SWINGS and places via the
    * real {@code place} primitive (swing + place sound) instead of an instant silent setBlock.
    */
   @Override
   public boolean performAction(MillVillager villager) {
      Block block = WorldUtilities.getBlock(villager.level(), villager.getGoalDestPoint());
      if (block == Blocks.AIR
         || block == Blocks.SNOW
         || BlockItemUtilities.isBlockDecorativePlant(block) && !(block instanceof SaplingBlock)) {
         // An emergent MERGE/WAR absorb can DEMOTE the grove building while this goal still points at it, leaving
         // getGoalBuildingDest() null. That is a legitimate null (not an error): end the goal cleanly so it gets
         // re-picked against the villager's current buildings, matching GoalFish's convention (return true).
         if (villager.getGoalBuildingDest() == null || villager.getGoalBuildingDest().getResManager() == null) {
            return true;
         }
         String saplingType = villager.getGoalBuildingDest().getResManager().getPlantingLocationType(villager.getGoalDestPoint());
         // 26.2: the 1.12 OAK_SAPLING meta-variants are gone — each wood type is a distinct sapling block. Map the
         // grove's planting-location type to the matching block (same mapping as GoalGenericPlantSapling).
         net.minecraft.world.level.block.state.BlockState saplingBS = Blocks.OAK_SAPLING.defaultBlockState();
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
         }

         // Plant via the AI-invokable plant ACTION: reach-gate (walk within reach / scaffold-extend), STRICTLY verify
         // the sapling can actually survive at the spot (a GENUINE plant), debit ONE matching sapling from stock, then
         // place it with a real swing + place sound.
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
   public int priority(MillVillager villager) {
      return 120;
   }

   @Override
   public int range(MillVillager villager) {
      return 5;
   }
}
