package org.millenaire.common.goal;

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

@DocumentedElement.Documentation("Plant nether warts at home (for free).")
public class GoalPlantNetherWarts extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] WARTS;

   public GoalPlantNetherWarts() {
      this.icon = InvItem.createInvItem(Items.NETHER_WART);
   }

   @Override
   public int actionDuration(MillVillager villager) {
      // Player-like plant is driven per-tick (reach → real place): re-enter every tick (1).
      return 1;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      return this.packDest(villager.getHouse().getResManager().getNetherWartsPlantingLocation(), villager.getHouse());
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) {
      return WARTS != null ? WARTS : (WARTS = new ItemStack[]{new ItemStack(Items.NETHER_WART, 1)});
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
    * Player-like nether-wart plant, driven one tick at a time (actionDuration == 1). STATELESS — phase derived from
    * the WORLD each tick, never a per-goal field (shared SINGLETON).
    *
    * <p>1.12 mechanic + economy KEPT: 1.12 planted nether warts at home "for free" (no stock consumed) by setting a
    * fresh {@code NETHER_WART} block on the empty soil above the dest. Now the villager does a REAL player-like place
    * ({@link VillagerWorldOps#place} — reach-gated, swing, place sound) of the same fresh (age-0) wart, still free —
    * the economy is unchanged; the only change vs 1.12 is the reach + the real placement over the op primitive.
    */
   @Override
   public boolean performAction(MillVillager villager) {
      Point dest = villager.getGoalDestPoint();
      if (dest == null) {
         return true;
      }
      Point cropPoint = dest.getAbove();
      if (villager.getBlock(cropPoint) != Blocks.AIR) {
         return true; // slot occupied / already planted — done.
      }

      BlockPos pos = cropPoint.getBlockPos();
      if (!VillagerWorldOps.withinReach(villager, pos)) {
         OpState reach = VillagerWorldOps.ensureReach(villager, pos);
         if (reach == OpState.BLOCKED) {
            return true;
         }
         return false;
      }
      // 1.12-faithful free place (no stock consumed). Fresh age-0 wart.
      VillagerWorldOps.place(villager, pos, Blocks.NETHER_WART.defaultBlockState());
      return true;
   }

   @Override
   public int priority(MillVillager villager) {
      return 100;
   }
}
