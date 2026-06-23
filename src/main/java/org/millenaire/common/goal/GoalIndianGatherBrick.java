package org.millenaire.common.goal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import com.coderyo.jason.ops.OpState;
import com.coderyo.jason.ops.VillagerWorldOps;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.BlockItemUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;

@DocumentedElement.Documentation("Pick up dried bricks from a kiln.")
public class GoalIndianGatherBrick extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] MUD_BRICK;

   public GoalIndianGatherBrick() {
      this.maxSimultaneousInBuilding = 1;
      this.townhallLimit.put(InvItem.createInvItem(MillBlocks.BS_MUD_BRICK), 4096);
      this.tags.add("tag_construction");
      this.icon = InvItem.createInvItem(MillBlocks.BS_MUD_BRICK);
   }

   @Override
   public int actionDuration(MillVillager villager) {
      // Player-like gather is driven per-tick (reach → break-over-time → pickup): re-enter every tick (1).
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
            int nb = kiln.getResManager().getNbFullBrickLocation();
            boolean validTarget = false;
            if (nb > 0 && minimumBricksNotRequired) {
               validTarget = true;
            }

            if (nb > 4) {
               validTarget = true;
            }

            if (validTarget) {
               Point p = kiln.getResManager().getFullBrickLocation();
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
   public ItemStack[] getHeldItemsOffHandDestination(MillVillager villager) {
      return MUD_BRICK != null ? MUD_BRICK : (MUD_BRICK = new ItemStack[]{BlockItemUtilities.getItemStackFromBlockState(MillBlocks.BS_MUD_BRICK, 1)});
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) {
      return villager.getBestPickaxeStack();
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
    * Player-like brick gather, driven one tick at a time (actionDuration == 1). STATELESS — phase derived from the
    * WORLD each tick (the dried-brick block is present → break it; now air → pickup), never a per-goal field (this
    * Goal is a shared SINGLETON). Mirrors the migrated mine/chop goals.
    *
    * <p>1.12 mechanic + yield KEPT: 1.12 instantly set the dried {@code BS_MUD_BRICK} block to AIR and granted one
    * {@code BS_MUD_BRICK} item. Now the villager really BREAKS the block over time (reach-gated, cracks, break sound,
    * tool durability — the goal equips a pickaxe; BS_MUD_BRICK requires one) and WALKS to pick up the real drop. The
    * {@code stone_deco} loot table drops exactly the {@code millenaire:stone_deco} block item, which IS the
    * {@code BS_MUD_BRICK} item 1.12 granted — so the real picked-up drop EQUALS the 1.12 yield (no separate grant,
    * the SAND/STONE-equivalent clean case for the miner). Net yield + targeted block are unchanged from 1.12.
    */
   @Override
   public boolean performAction(MillVillager villager) throws MillLog.MillenaireException {
      Point dest = villager.getGoalDestPoint();
      if (dest == null) {
         return true;
      }
      BlockPos pos = dest.getBlockPos();

      // Phase 1 — the dried brick is still there: ensure the pickaxe, then REALLY break it over time.
      if (WorldUtilities.getBlockState(villager.level(), dest) == MillBlocks.BS_MUD_BRICK) {
         if (!VillagerWorldOps.ensureTool(villager, VillagerWorldOps.ToolKind.PICKAXE)) {
            return false; // no pickaxe → no drop; stay in-goal so GoalGetTool fetches one (no faked yield).
         }
         OpState st = VillagerWorldOps.breakTick(villager, pos);
         switch (st) {
            case APPROACHING:
            case EXTENDING_REACH:
            case IN_PROGRESS:
               return false; // keep breaking / approaching next tick.
            case BLOCKED:
               return true; // shouldn't happen for a brick; abandon so the goal re-picks.
            case COMPLETE:
               return false; // broke this tick; next tick the point is air → pickup branch.
            default:
               return false;
         }
      }

      // Phase 2 — the brick is broken (point air): WALK to + collect the real drop (== the 1.12 BS_MUD_BRICK yield).
      if (villager.level().getBlockState(pos).isAir()) {
         OpState pst = VillagerWorldOps.pickupTick(villager, pos);
         if (pst != OpState.COMPLETE) {
            return false; // still walking to / collecting the dropped brick item.
         }
         // fall through to re-target the next full-brick location.
      }

      if (villager.getGoalBuildingDest().getResManager().getNbFullBrickLocation() > 0) {
         villager.setGoalInformation(this.getDestination(villager));
         return false;
      } else {
         return true;
      }
   }

   @Override
   public int priority(MillVillager villager) {
      int p = 100 - villager.getTownHall().nbGoodAvailable(MillBlocks.BS_MUD_BRICK, false, false, false) * 2;

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
