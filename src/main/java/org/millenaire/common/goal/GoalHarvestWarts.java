package org.millenaire.common.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import com.coderyo.jason.ops.OpState;
import com.coderyo.jason.ops.VillagerWorldOps;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.Point;

@DocumentedElement.Documentation("Harvest grown nether warts froom home.")
public class GoalHarvestWarts extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] WARTS;

   public GoalHarvestWarts() {
      this.icon = InvItem.createInvItem(Items.NETHER_WART);
   }

   @Override
   public int actionDuration(MillVillager villager) {
      // Player-like harvest is driven per-tick (reach → break → pickup): re-enter every tick (1).
      return 1;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      return this.packDest(villager.getHouse().getResManager().getNetherWartsHarvestLocation(), villager.getHouse());
   }

   @Override
   public ItemStack[] getHeldItemsOffHandDestination(MillVillager villager) {
      return WARTS != null ? WARTS : (WARTS = new ItemStack[]{new ItemStack(Items.NETHER_WART, 1)});
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
    * Player-like nether-wart harvest, driven one tick at a time (actionDuration == 1). STATELESS — phase derived ONLY
    * from the WORLD each tick (mature wart present → break; now air → pickup); no per-goal field holds any per-villager
    * phase (this Goal is a SINGLETON shared across villagers, like the migrated mine/chop/harvest-crop goals).
    *
    * <p>1.12 maturity + yield KEPT: only a MATURE wart ({@code AGE == 3}, the 1.12 ripe-meta test) is harvested. 1.12
    * instantly set it to AIR and stored exactly 1 nether wart in the HOUSE building. Now the villager REALLY breaks the
    * mature wart (0-hardness → one breakTick, swing + break sound; vanilla drops 2-4 warts) and WALKS to pick up the
    * real warts into its own inventory. The kept-1.12 ECONOMY yield is still exactly 1 wart credited to the HOUSE
    * (unchanged); the picked-up real warts are the worksite by-product the villager carries, exactly as the migrated
    * harvest-crop goal keeps the vanilla break-drops it picks up alongside the authored Mill yield.
    *
    * <p><b>Kept-1.12-yield vs real-drop note:</b> 1.12 credited the HOUSE with 1 wart and gave the villager nothing;
    * here the house still gets exactly 1 (the authoritative yield) and the villager additionally carries the real 2-4
    * dropped warts (which flow into village storage via the normal deposit goals) — the same by-product treatment the
    * O3 harvest-crop migration uses, with the balance-defining credit kept identical to 1.12.
    */
   @Override
   public boolean performAction(MillVillager villager) {
      Point dest = villager.getGoalDestPoint();
      if (dest == null) {
         return true;
      }
      Point cropPoint = dest.getAbove();
      BlockPos pos = cropPoint.getBlockPos();

      // Phase 1 — mature wart standing: reach-gate, then REALLY break it (0-hardness → this tick).
      if (villager.getBlock(cropPoint) == Blocks.NETHER_WART) {
         BlockState wartState = cropPoint.getBlockActualState(villager.level());
         if (wartState.getValue(NetherWartBlock.AGE) == 3) {
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
                  // The wart just broke; the kept-1.12 yield is credited to the house exactly once, here at the break
                  // (not per pickup tick), so it stays exactly 1 per harvest regardless of how many ticks pickup takes.
                  villager.getHouse().storeGoods(Items.NETHER_WART, 1);
                  return false; // next tick the point is air → walk-to-pickup the real by-product warts.
               default:
                  return false;
            }
         }
         return true; // not mature — leave to grow.
      }

      // Phase 2 — broken (point air): WALK to + collect the real warts (worksite by-product) into villager inventory.
      if (villager.level().getBlockState(pos).isAir()) {
         OpState pst = VillagerWorldOps.pickupTick(villager, pos);
         if (pst != OpState.COMPLETE) {
            return false;
         }
      }
      return true;
   }

   @Override
   public int priority(MillVillager villager) {
      return 100 - villager.getHouse().countGoods(Items.NETHER_WART) * 4;
   }
}
