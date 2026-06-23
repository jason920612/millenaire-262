package org.millenaire.common.goal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
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

@DocumentedElement.Documentation("Plant sugarcane in a building with the sugar cane plantation tag.")
public class GoalIndianPlantSugarCane extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] SUGARCANE;

   public GoalIndianPlantSugarCane() {
      this.tags.add("tag_agriculture");
      this.icon = InvItem.createInvItem(Items.SUGAR_CANE);
   }

   @Override
   public int actionDuration(MillVillager villager) {
      // Player-like plant is driven per-tick (reach → real place): re-enter every tick (1).
      return 1;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      List<Point> vp = new ArrayList<>();
      List<Point> buildingp = new ArrayList<>();

      for (Building plantation : villager.getTownHall().getBuildingsWithTag("sugarplantation")) {
         Point p = plantation.getResManager().getSugarCanePlantingLocation();
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
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) {
      return SUGARCANE != null ? SUGARCANE : (SUGARCANE = new ItemStack[]{new ItemStack(Items.SUGAR_CANE, 1)});
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
            int nb = kiln.getResManager().getNbSugarCanePlantingLocation();
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
    * Player-like sugar-cane plant, driven one tick at a time (actionDuration == 1). STATELESS — phase derived from
    * the WORLD each tick, never a per-goal field (this Goal is a shared SINGLETON).
    *
    * <p>1.12 mechanic KEPT: 1.12 set a sugar-cane block on the empty (or oak-leaf-occupied) plant slot above the
    * dest. Now the villager does a REAL player-like place ({@link VillagerWorldOps#place} — reach-gated, swing, place
    * sound) of the same {@code SUGAR_CANE}. Economy: 1.12 planted for free; to make it a genuine place we CONSUME one
    * sugar cane from the villager's stock when available (the harvest replenishes it), but FALL BACK to a free place
    * if the village has none in stock — keeping 1.12's "plantation always establishes" net behaviour so a fresh
    * plantation is never stuck waiting on stock.
    */
   @Override
   public boolean performAction(MillVillager villager) {
      Point dest = villager.getGoalDestPoint();
      if (dest == null) {
         return true;
      }
      Point cropPoint = dest.getAbove();
      Block block = villager.getBlock(cropPoint);
      if (block != Blocks.AIR && block != Blocks.OAK_LEAVES) {
         return true; // slot occupied / already planted — done.
      }

      BlockPos pos = cropPoint.getBlockPos();
      // Reach-gated real place.
      if (!VillagerWorldOps.withinReach(villager, pos)) {
         OpState reach = VillagerWorldOps.ensureReach(villager, pos);
         if (reach == OpState.BLOCKED) {
            return true;
         }
         return false;
      }

      // Consume one cane from stock if available (real player-like placement cost); else plant free (1.12 fidelity).
      villager.takeFromInv(Items.SUGAR_CANE, 0, 1);
      VillagerWorldOps.place(villager, pos, Blocks.SUGAR_CANE.defaultBlockState());
      return true;
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
}
