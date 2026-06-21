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

   @Override
   public boolean performAction(MillVillager villager) {
      Block block = WorldUtilities.getBlock(villager.level(), villager.getGoalDestPoint());
      if (block == Blocks.AIR
         || block == Blocks.SNOW
         || BlockItemUtilities.isBlockDecorativePlant(block) && !(block instanceof SaplingBlock)) {
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

         villager.takeFromInv(Blocks.OAK_SAPLING, meta, 1);
         villager.setBlockAndMetadata(villager.getGoalDestPoint(), Blocks.OAK_SAPLING, meta);
         villager.swing(InteractionHand.MAIN_HAND);
         if (MillConfigValues.LogLumberman >= 3 && villager.extraLog) {
            MillLog.debug(this, "Planted at: " + villager.getGoalDestPoint());
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
