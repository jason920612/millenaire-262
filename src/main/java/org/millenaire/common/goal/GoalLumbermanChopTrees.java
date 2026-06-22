package org.millenaire.common.goal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.pathing.atomicstryker.AStarConfig;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillRandom;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;

@DocumentedElement.Documentation("Go chop trees in a grove.")
public class GoalLumbermanChopTrees extends Goal {
   public GoalLumbermanChopTrees() {
      this.maxSimultaneousInBuilding = 1;
      this.townhallLimit.put(InvItem.createInvItem(Blocks.OAK_LOG, -1), 4096);
      this.icon = InvItem.createInvItem(Items.IRON_AXE);
   }

   @Override
   public int actionDuration(MillVillager villager) {
      int toolEfficiency = (int)villager.getBestAxe().getDestroySpeed(new ItemStack(villager.getBestAxe(), 1), Blocks.OAK_LOG.defaultBlockState());
      return 20 - toolEfficiency * 2;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      List<Point> woodPos = new ArrayList<>();
      List<Point> buildingp = new ArrayList<>();

      for (Building grove : villager.getTownHall().getBuildingsWithTag("grove")) {
         if (grove.getWoodCount() > 4) {
            Point p = grove.getWoodLocation();
            if (p != null) {
               woodPos.add(p);
               buildingp.add(grove.getPos());
               if (MillConfigValues.LogLumberman >= 3) {
                  MillLog.debug(this, "Found location in grove: " + p + ". Targeted block: " + p.getBlock(villager.level()));
               }
            }
         }
      }

      if (woodPos.isEmpty()) {
         return null;
      } else {
         Point p = woodPos.get(0);
         Point buildingP = buildingp.get(0);

         for (int i = 1; i < woodPos.size(); i++) {
            if (woodPos.get(i).horizontalDistanceToSquared(villager) < p.horizontalDistanceToSquared(villager)) {
               p = woodPos.get(i);
               buildingP = buildingp.get(i);
            }
         }

         if (MillConfigValues.LogLumberman >= 3) {
            MillLog.debug(this, "Going to gather wood around: " + p + ". Targeted block: " + p.getBlock(villager.level()));
         }

         return this.packDest(p, buildingP);
      }
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) {
      return villager.getBestAxeStack();
   }

   @Override
   public AStarConfig getPathingConfig(MillVillager villager) {
      return !villager.canVillagerClearLeaves() ? JPS_CONFIG_CHOPLUMBER_NO_LEAVES : JPS_CONFIG_CHOPLUMBER;
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) {
      return villager.countInv(Blocks.OAK_LOG, -1) > 64 ? false : this.getDestination(villager) != null;
   }

   @Override
   public boolean lookAtGoal() {
      return true;
   }

   @Override
   public boolean performAction(MillVillager villager) throws Exception {
      boolean woodFound = false;
      if (MillConfigValues.LogLumberman >= 3) {
         MillLog.debug(
            this,
            "Attempting to gather wood around: "
               + villager.getGoalDestPoint()
               + ", central block: "
               + villager.getGoalDestPoint().getBlock(villager.level())
         );
      }

      for (int deltaY = 12; deltaY > -12; deltaY--) {
         for (int deltaX = -3; deltaX < 4; deltaX++) {
            for (int deltaZ = -3; deltaZ < 4; deltaZ++) {
               Point p = villager.getGoalDestPoint().getRelative(deltaX, deltaY, deltaZ);
               Block block = villager.getBlock(p);
               if (block == Blocks.OAK_LOG || block == Blocks.ACACIA_LOG || block == Blocks.OAK_LEAVES || block == Blocks.ACACIA_LEAVES) {
                  if (woodFound) {
                     if (MillConfigValues.LogLumberman >= 3) {
                        MillLog.debug(this, "More wood found.");
                     }

                     return false;
                  }

                  if (block != Blocks.OAK_LOG && block != Blocks.ACACIA_LOG) {
                     // 26.2: leaf species is the leaf block identity (1.12 used meta&3). Give the matching sapling.
                     Block sapling = block == Blocks.ACACIA_LEAVES ? Blocks.ACACIA_SAPLING : Blocks.OAK_SAPLING;
                     if (block == Blocks.OAK_LEAVES) {
                        if (MillRandom.randomInt(4) == 0) {
                           villager.addToInv(sapling, 0, 1);
                        }
                     } else if (MillRandom.randomInt(4) == 0) {
                        villager.addToInv(sapling, 0, 1);
                     }

                     villager.setBlock(p, Blocks.AIR);
                     villager.swing(InteractionHand.MAIN_HAND);
                     if (MillConfigValues.LogLumberman >= 3) {
                        MillLog.debug(this, "Destroyed leaves at: " + p);
                     }
                  } else {
                     // 26.2: log species is the log block identity (1.12 used meta&3 within a single log block).
                     villager.setBlock(p, Blocks.AIR);
                     villager.swing(InteractionHand.MAIN_HAND);
                     if (block == Blocks.OAK_LOG) {
                        villager.addToInv(Blocks.OAK_LOG, 0, 1);
                     } else {
                        villager.addToInv(Blocks.ACACIA_LOG, 0, 1);
                     }

                     woodFound = true;
                     if (MillConfigValues.LogLumberman >= 3) {
                        MillLog.debug(this, "Gathered wood at: " + p);
                     }
                  }
               }
            }
         }
      }

      return true;
   }

   @Override
   public int priority(MillVillager villager) {
      return Math.max(10, 125 - villager.countInv(Blocks.OAK_LOG, -1));
   }

   @Override
   public int range(MillVillager villager) {
      return 8;
   }
}
