package org.millenaire.common.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import com.coderyo.jason.ops.OpState;
import com.coderyo.jason.ops.VillagerWorldOps;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.Point;

@DocumentedElement.Documentation("Goal that harvests ripe cacao.")
public class GoalHarvestCacao extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] CACAO;

   public GoalHarvestCacao() {
      this.icon = InvItem.createInvItem(Items.COCOA_BEANS);
   }

   /** Vanilla ripe (AGE 3 — here AGE>=2) cocoa drops a fixed 3 cocoa beans; used to reconcile to the 1.12 net yield. */
   private static final int VANILLA_RIPE_COCOA_DROP = 3;
   /** 1.12 Mill base yield for a harvested ripe cocoa (before the irrigation bonus). */
   private static final int MILL_BASE_COCOA_YIELD = 2;

   @Override
   public int actionDuration(MillVillager villager) {
      // Player-like harvest is driven per-tick (reach → break → pickup): re-enter every tick (1).
      return 1;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      Point p = villager.getHouse().getResManager().getCocoaHarvestLocation();
      return this.packDest(p, villager.getHouse());
   }

   @Override
   public ItemStack[] getHeldItemsOffHandDestination(MillVillager villager) {
      return CACAO != null ? CACAO : (CACAO = new ItemStack[]{new ItemStack(Items.COCOA_BEANS, 1)});
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) {
      return villager.getBestHoeStack();
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) {
      return this.getDestination(villager).getDest() != null;
   }

   @Override
   public boolean lookAtGoal() {
      return true;
   }

   /**
    * Player-like cacao harvest, driven one tick at a time (actionDuration == 1). STATELESS — phase derived from the
    * WORLD each tick (ripe cocoa present → break it; now air → pickup), never a per-goal field (shared SINGLETON).
    *
    * <p>1.12 ripeness + yield KEPT: only ripe cocoa ({@code AGE >= 2}, the 1.12 ripeness test) is harvested. 1.12
    * instantly set it to AIR and granted 2 cocoa beans (+1 irrigation bonus). Now the villager REALLY breaks the ripe
    * pod (0-hardness → one breakTick, swing + break sound; vanilla drops 3 beans) and WALKS to pick up the real beans,
    * then we RECONCILE to the 1.12 NET YIELD: trim the real 3 down to the Mill base 2 and add the kept-1.12 irrigation
    * bonus. So the targeted block (ripe cocoa → gone) and the net yield (2 + bonus) match 1.12 exactly.
    */
   @Override
   public boolean performAction(MillVillager villager) {
      Point cropPoint = villager.getGoalDestPoint();
      if (cropPoint == null) {
         return true;
      }
      BlockPos pos = cropPoint.getBlockPos();

      // Phase 1 — ripe cocoa still standing: reach-gate, then REALLY break it (0-hardness → this tick).
      if (cropPoint.getBlock(villager.level()) == Blocks.COCOA) {
         BlockState bs = cropPoint.getBlockActualState(villager.level());
         if ((Integer) bs.getValue(CocoaBlock.AGE) >= 2) {
            if (!VillagerWorldOps.withinReach(villager, pos)) {
               OpState reach = VillagerWorldOps.ensureReach(villager, pos);
               if (reach == OpState.BLOCKED) {
                  return true;
               }
               return false;
            }
            OpState st = VillagerWorldOps.breakTick(villager, pos);
            switch (st) {
               case APPROACHING:
               case EXTENDING_REACH:
               case IN_PROGRESS:
                  return false;
               case BLOCKED:
                  return true;
               case COMPLETE:
                  return false; // broke this tick; next tick the point is air → pickup + reconcile.
               default:
                  return false;
            }
         }
         return true; // not ripe — leave it to grow.
      }

      // Phase 2 — broken (point air): WALK to + collect the real beans, then reconcile to the 1.12 net yield.
      if (villager.level().getBlockState(pos).isAir()) {
         OpState pst = VillagerWorldOps.pickupTick(villager, pos);
         if (pst != OpState.COMPLETE) {
            return false;
         }
         reconcileMillYield(villager);
      }
      return true;
   }

   /**
    * Reconcile the picked-up real cocoa drop to the 1.12 net yield: trim the vanilla 3 beans down to the Mill base 2,
    * then add the kept-1.12 irrigation bonus (a village-irrigation chance of one extra bean). Net == 1.12's 2+bonus.
    */
   private void reconcileMillYield(MillVillager villager) {
      villager.takeFromInv(Items.COCOA_BEANS, 0, VANILLA_RIPE_COCOA_DROP - MILL_BASE_COCOA_YIELD); // 3 → 2.
      float irrigation = villager.getTownHall().getVillageIrrigation();
      if (Math.random() < irrigation / 100.0F) {
         villager.addToInv(Items.COCOA_BEANS, 1);
      }
   }

   @Override
   public int priority(MillVillager villager) {
      return 100;
   }
}
