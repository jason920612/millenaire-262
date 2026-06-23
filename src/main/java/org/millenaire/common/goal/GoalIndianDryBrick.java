package org.millenaire.common.goal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import com.coderyo.jason.ops.OpState;
import com.coderyo.jason.ops.VillagerWorldOps;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;

@DocumentedElement.Documentation("Go and dry bricks in a building with the kiln tag.")
public class GoalIndianDryBrick extends Goal {
   public GoalIndianDryBrick() {
      this.maxSimultaneousInBuilding = 1;
      this.tags.add("tag_construction");
      this.icon = InvItem.createInvItem(MillBlocks.BS_WET_BRICK);
   }

   @Override
   public int actionDuration(MillVillager villager) {
      // Player-like place is driven per-tick (reach → real place): re-enter every tick (1).
      return 1;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) throws MillLog.MillenaireException {
      boolean minimumBricksNotRequired;
      if (villager.goalKey != null && villager.goalKey.equals(this.key)) {
         minimumBricksNotRequired = true;
      } else if (!villager.lastGoalTime.containsKey(this)) {
         minimumBricksNotRequired = true;
      } else {
         minimumBricksNotRequired = villager.level().getOverworldClockTime() > villager.lastGoalTime.get(this) + 2000L;
      }

      List<Point> vp = new ArrayList<>();
      List<Point> buildingp = new ArrayList<>();

      for (Building kiln : villager.getTownHall().getBuildingsWithTag("brickkiln")) {
         if (this.validateDest(villager, kiln)) {
            int nb = kiln.getResManager().getNbEmptyBrickLocation();
            boolean validTarget = false;
            if (nb > 0 && minimumBricksNotRequired) {
               validTarget = true;
            }

            if (nb > 4) {
               validTarget = true;
            }

            if (validTarget) {
               Point p = kiln.getResManager().getEmptyBrickLocation();
               if (p != null) {
                  vp.add(p);
                  buildingp.add(kiln.getPos());
               }
            }
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
      return new ItemStack[]{new ItemStack(MillItems.BRICK_MOULD, 1)};
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) throws MillLog.MillenaireException {
      return this.getDestination(villager) != null;
   }

   @Override
   public boolean lookAtGoal() {
      return true;
   }

   /**
    * Player-like brick-drying, driven one tick at a time (actionDuration == 1). STATELESS — phase derived from the
    * WORLD each tick (the slot is empty → place a wet brick; already filled → re-target), never a per-goal field
    * (this Goal is a shared SINGLETON).
    *
    * <p>1.12 mechanic + yield KEPT: 1.12 instantly set the empty kiln slot to the wet-brick block. Now the villager
    * does a REAL player-like place ({@link VillagerWorldOps#place} — reach-gated, swing, place sound) of the same
    * {@code BS_WET_BRICK} (which later dries to the mud brick via its own block tick, unchanged). No item is consumed
    * (1.12's brick-mould is a held tool, not stock), so the economy is unchanged; the change is ONLY the reach + the
    * real placement over the op primitive.
    */
   @Override
   public boolean performAction(MillVillager villager) throws MillLog.MillenaireException {
      Point dest = villager.getGoalDestPoint();
      if (dest == null) {
         return true;
      }
      BlockPos pos = dest.getBlockPos();

      if (WorldUtilities.getBlock(villager.level(), dest) == Blocks.AIR) {
         // Reach-gated real place. Out of reach → walk closer (scaffold-extend) and retry next tick.
         if (!VillagerWorldOps.withinReach(villager, pos)) {
            OpState reach = VillagerWorldOps.ensureReach(villager, pos);
            if (reach == OpState.BLOCKED) {
               return true; // can't reach the slot — abandon so the goal re-picks.
            }
            return false; // approaching / extending reach — retry next tick.
         }
         VillagerWorldOps.place(villager, pos, MillBlocks.BS_WET_BRICK);
      }

      if (villager.getGoalBuildingDest().getResManager().getNbEmptyBrickLocation() > 0) {
         villager.setGoalInformation(this.getDestination(villager));
         return false;
      } else {
         return true;
      }
   }

   @Override
   public int priority(MillVillager villager) {
      int p = 120;

      for (MillVillager v : villager.getTownHall().getKnownVillagers()) {
         if (this.key.equals(v.goalKey)) {
            p /= 2;
         }
      }

      return p;
   }

   @Override
   public boolean unreachableDestination(MillVillager villager) {
      return false;
   }
}
