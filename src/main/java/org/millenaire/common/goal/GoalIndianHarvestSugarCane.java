package org.millenaire.common.goal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import com.coderyo.jason.ops.OpState;
import com.coderyo.jason.ops.VillagerWorldOps;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;

@DocumentedElement.Documentation("Gather sugar cane from a building with the sugar cane plantation tag.")
public class GoalIndianHarvestSugarCane extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] SUGARCANE;

   public GoalIndianHarvestSugarCane() {
      this.tags.add("tag_agriculture");
      this.icon = InvItem.createInvItem(Items.SUGAR_CANE);
   }

   @Override
   public int actionDuration(MillVillager villager) {
      // Player-like harvest is driven per-tick (reach → break upper segments top-down → pickup): re-enter each tick.
      return 1;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      List<Point> vp = new ArrayList<>();
      List<Point> buildingp = new ArrayList<>();

      for (Building plantation : villager.getTownHall().getBuildingsWithTag("sugarplantation")) {
         Point p = plantation.getResManager().getSugarCaneHarvestLocation();
         if (p != null) {
            vp.add(p);
            buildingp.add(plantation.getPos());
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
      return SUGARCANE != null ? SUGARCANE : (SUGARCANE = new ItemStack[]{new ItemStack(Items.SUGAR_CANE, 1)});
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) {
      return villager.getBestHoeStack();
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) {
      int nbsimultaneous = 0;

      for (MillVillager v : villager.getTownHall().getKnownVillagers()) {
         if (v != villager && this.key.equals(v.goalKey)) {
            nbsimultaneous++;
         }
      }

      if (nbsimultaneous > 2) {
         return false;
      } else {
         boolean delayOver;
         if (!villager.lastGoalTime.containsKey(this)) {
            delayOver = true;
         } else {
            delayOver = villager.level().getOverworldClockTime() > villager.lastGoalTime.get(this) + 2000L;
         }

         for (Building kiln : villager.getTownHall().getBuildingsWithTag("sugarplantation")) {
            int nb = kiln.getResManager().getNbSugarCaneHarvestLocation();
            if (nb > 0 && delayOver) {
               return true;
            }

            if (nb > 4) {
               return true;
            }
         }

         return false;
      }
   }

   @Override
   public boolean lookAtGoal() {
      return true;
   }

   /**
    * Player-like sugar-cane harvest, driven one tick at a time (actionDuration == 1). STATELESS — phase derived from
    * the WORLD each tick (which cane segments still stand), never a per-goal field (this Goal is a shared SINGLETON).
    *
    * <p><b>SIM-VALIDATED keep-bottom (opsim run_cane):</b> a Mill cane column has a BOTTOM block at
    * {@code dest+1} and up to two upper segments at {@code dest+2} and {@code dest+3}. The harvest breaks the UPPER
    * segments TOP-DOWN ({@code +3} before {@code +2}) and KEEPS the bottom block ({@code +1}) — vanilla sugar cane
    * regrows from a surviving bottom, so leaving it makes the plantation renewable (exactly the validated sim's
    * {@code run_cane}: harvest the segments above the bottom, keep the bottom, pick up the cane). Each segment is a
    * REAL break (0-hardness → one breakTick, swing + break sound) dropping one real sugar-cane item the villager then
    * WALKS to and collects.
    *
    * <p>1.12 yield KEPT: 1.12 granted, per harvested segment, 1 cane + a chance of +1 from village irrigation. The
    * real break drops exactly 1 cane per segment (collected via pickup = the base yield); we KEEP the 1.12 irrigation
    * BONUS by rolling it and granting the extra cane on top. Net yield per segment == 1.12.
    */
   @Override
   public boolean performAction(MillVillager villager) {
      Point dest = villager.getGoalDestPoint();
      if (dest == null) {
         return true;
      }

      // Highest standing UPPER segment first (top-down: +3 before +2). The bottom (+1) is NEVER targeted — it stays
      // so the cane regrows (validated keep-bottom).
      Point top = dest.getRelative(0.0, 3.0, 0.0);
      Point mid = dest.getRelative(0.0, 2.0, 0.0);
      Point segment = null;
      if (villager.getBlock(top) == Blocks.SUGAR_CANE) {
         segment = top;
      } else if (villager.getBlock(mid) == Blocks.SUGAR_CANE) {
         segment = mid;
      }

      if (segment != null) {
         BlockPos pos = segment.getBlockPos();
         // Reach-gate (the upper segment sits ~3 blocks up; scaffold-extend if out of reach).
         if (!VillagerWorldOps.withinReach(villager, pos)) {
            OpState reach = VillagerWorldOps.ensureReach(villager, pos);
            if (reach == OpState.BLOCKED) {
               return true;
            }
            return false;
         }
         OpState st = VillagerWorldOps.breakTick(villager, pos); // cane is 0-hardness → breaks this tick, drops 1 cane.
         switch (st) {
            case APPROACHING:
            case EXTENDING_REACH:
            case IN_PROGRESS:
               return false;
            case BLOCKED:
               return true;
            case COMPLETE:
               // KEPT-1.12 irrigation bonus: chance of +1 cane on top of the real dropped cane for this segment.
               grantIrrigationBonus(villager);
               return false; // next tick: break the next-lower standing segment, or move to pickup when none remain.
            default:
               return false;
         }
      }

      // No upper segment left standing (bottom kept) — WALK to + collect the real dropped cane around the column.
      OpState pst = VillagerWorldOps.pickupTick(villager, dest.getRelative(0.0, 2.0, 0.0).getBlockPos());
      if (pst != OpState.COMPLETE) {
         return false;
      }
      return true;
   }

   /** KEPT-1.12 yield: a village-irrigation chance of one EXTRA cane per harvested segment (real drop is the base 1). */
   private void grantIrrigationBonus(MillVillager villager) {
      float irrigation = villager.getTownHall().getVillageIrrigation();
      if (Math.random() < irrigation / 100.0F) {
         villager.addToInv(Items.SUGAR_CANE, 1);
      }
   }

   @Override
   public int priority(MillVillager villager) {
      int p = 200 - villager.getTownHall().nbGoodAvailable(Items.SUGAR_CANE, 0, false, false, false) * 4;

      for (MillVillager v : villager.getTownHall().getKnownVillagers()) {
         if (this.key.equals(v.goalKey)) {
            p /= 2;
         }
      }

      return p;
   }
}
