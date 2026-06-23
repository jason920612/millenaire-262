package org.millenaire.common.goal;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import com.coderyo.jason.ops.OpState;
import com.coderyo.jason.ops.VillagerWorldOps;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.pathing.atomicstryker.AStarConfig;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;

@DocumentedElement.Documentation("Sheer sheeps present around the villager's house.")
public class GoalShearSheep extends Goal {
   public GoalShearSheep() {
      this.buildingLimit.put(InvItem.createInvItem(Blocks.WOOL.pick(DyeColor.WHITE)), 1024);
      this.townhallLimit.put(InvItem.createInvItem(Blocks.WOOL.pick(DyeColor.WHITE)), 1024);
      this.icon = InvItem.createInvItem(Items.SHEARS);
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) throws Exception {
      Point pos = villager.getPos();
      Entity closestSheep = null;
      double sheepBestDist = Double.MAX_VALUE;

      for (Entity ent : WorldUtilities.getEntitiesWithinAABB(villager.level(), Sheep.class, villager.getHouse().getPos(), 30, 10)) {
         if (!((Sheep)ent).isSheared() && !((Sheep)ent).isBaby() && (closestSheep == null || pos.distanceTo(ent) < sheepBestDist)) {
            closestSheep = ent;
            sheepBestDist = pos.distanceTo(ent);
         }
      }

      return closestSheep != null ? this.packDest(null, villager.getHouse(), closestSheep) : null;
   }

   @Override
   public AStarConfig getPathingConfig(MillVillager villager) {
      return !villager.canVillagerClearLeaves() ? JPS_CONFIG_WIDE_NO_LEAVES : JPS_CONFIG_WIDE;
   }

   @Override
   public boolean isFightingGoal() {
      return true;
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) throws Exception {
      if (!villager.getHouse().containsTags("sheeps")) {
         return false;
      } else {
         List<? extends Entity> sheep = WorldUtilities.getEntitiesWithinAABB(villager.level(), Sheep.class, villager.getHouse().getPos(), 30, 10);
         if (sheep == null) {
            return false;
         } else {
            for (Entity ent : sheep) {
               Sheep asheep = (Sheep)ent;
               if (!asheep.isBaby() && !asheep.isRemoved() && !((Sheep)ent).isSheared()) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   @Override
   public boolean lookAtGoal() {
      return true;
   }

   @Override
   public boolean performAction(MillVillager villager) throws Exception {
      // O5 player-like refactor: shear sheep with the REAL vanilla Sheep.shear (the sheep actually becomes sheared
      // and drops 1-3 wool of its own colour via BuiltInLootTables.SHEAR_SHEEP), then the villager walks to and
      // collects the dropped wool — instead of 1.12's fake `addToInv(WOOL.pick(color), 3) + setSheared(true)`.
      // STRICT tool: with no shears available, VillagerWorldOps.shearTick returns BLOCKED and we DEFER (let the
      // tool-fetch path run) rather than faking the wool.
      for (Entity ent : WorldUtilities.getEntitiesWithinAABB(villager.level(), Sheep.class, villager.getPos(), 4, 4)) {
         if (ent.isRemoved()) {
            continue;
         }
         Sheep animal = (Sheep)ent;
         // shearTick itself skips non-ready (already-sheared / baby) sheep; the readyForShearing guard here keeps the
         // 1.12 selection intent explicit and avoids needless work.
         if (!animal.readyForShearing()) {
            continue;
         }

         OpState st = VillagerWorldOps.shearTick(villager, animal);
         if (st == OpState.BLOCKED) {
            // No shears: defer this tick (do NOT fake wool). The goal's getHeldItemsDestination/GoalGetTool supplies
            // the shears; we retry next tick once they are in hand.
            return false;
         }
         if (st == OpState.PICKING_UP) {
            // Real shear happened: walk to + collect the dropped wool (colour/count come from the sheep + loot table,
            // faithfully replacing 1.12's Blocks.WOOL.pick(getColor()) yield). Drive pickup to completion at the
            // sheep's spot, the worksite the wool dropped at.
            if (MillConfigValues.LogCattleFarmer >= 1 && villager.extraLog) {
               MillLog.major(this, "Shearing (real): " + ent + " colour=" + animal.getColor());
            }
            net.minecraft.core.BlockPos woolSpot = animal.blockPosition();
            int guard = 0;
            while (VillagerWorldOps.pickupTick(villager, woolSpot) == OpState.PICKING_UP && guard++ < 64) {
               // pickupTick re-issues navigation toward the nearest wool drop each call; bounded so one tick of the
               // goal can't loop forever if a drop is unreachable (it is retried next performAction tick).
               if (guard > 0 && !villager.getNavigation().isInProgress()) {
                  break;
               }
            }
         }
         // st == APPROACHING (out of reach) or COMPLETE (skipped) → move on to the next sheep; the goal re-runs.
      }

      return true;
   }

   @Override
   public int priority(MillVillager villager) throws Exception {
      return 50;
   }

   @Override
   public int range(MillVillager villager) {
      return 5;
   }
}
