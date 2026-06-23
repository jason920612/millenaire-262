package org.millenaire.common.goal;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import com.coderyo.jason.ops.OpState;
import com.coderyo.jason.ops.VillagerWorldOps;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.MillRandom;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;

/**
 * Fish from a village fishing spot — O4 player-like refactor.
 *
 * <h2>1.12 → 26.2 (before → after)</h2>
 * <b>Before</b> ({@code performAction}): {@code addFishResults(addToInv 1× COD) + swing} — a fake instant catch with
 * no bobber and a hardcoded fish.
 * <p><b>After</b>: drives {@link VillagerWorldOps#fishTick} against the fishing-spot point. The op (with the
 * {@code millenaire.accesswidener} + {@code FishingHookMixin} relaxing the Player-gating) casts a REAL villager-owned
 * {@code FishingHook} over the spot's water, runs the FULL vanilla bobbing + bite animation, and on the catch rolls
 * {@code BuiltInLootTables.FISHING} + spawns the loot, which the villager then WALKS to and picks up. Yields are the
 * real FISHING loot (centred on fish) rather than the 1.12 fixed COD — the user chose the genuine player-like
 * operation; the "fisherman brings in fish" intent is preserved.
 *
 * <p>The FSM spans many ticks, so {@link #actionDuration} is short (the goal re-enters {@code performAction} roughly
 * each tick) and {@code performAction} returns {@code false} until the op reports {@link OpState#COMPLETE}.
 */
@DocumentedElement.Documentation("Fish from fishing holes at home, bringing in standard fish.")
public class GoalFish extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] fishingRod;

   public GoalFish() {
      this.buildingLimit.put(InvItem.createInvItem(Items.COD, 0), 128);
      this.buildingLimit.put(InvItem.createInvItem(Items.COOKED_COD, 0), 128);
      this.icon = InvItem.createInvItem(Items.FISHING_ROD);
   }

   @Override
   public int actionDuration(MillVillager villager) {
      // The fishing FSM (cast → bite animation → reel → pickup) runs across many ticks, advanced one step per
      // performAction. A short duration makes the goal re-enter performAction promptly so the bobber animates
      // smoothly instead of once per 500 ticks (the old fake-catch cadence).
      return 1;
   }

   /**
    * Cultural fishing bonus added on a successful catch (on top of the real FISHING loot the villager picks up).
    * Base fisherman: none (the FISHING loot IS the catch). {@link GoalFishInuit} overrides to add its 1/4
    * {@code BONE_BLOCK}, preserving that 1.12 culture-specific balance.
    */
   protected void addFishResults(MillVillager villager) {
      // No fixed bonus for the base fisherman — the real FISHING loot rolled by the op is the catch.
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) throws Exception {
      List<Building> vb = villager.getTownHall().getBuildingsWithTag("fishingspot");
      Building closest = null;

      for (Building b : vb) {
         if (closest == null
            || villager.getPos().horizontalDistanceToSquared(b.getResManager().getSleepingPos())
               < villager.getPos().horizontalDistanceToSquared(closest.getResManager().getSleepingPos())) {
            closest = b;
         }
      }

      return closest != null && closest.getResManager().fishingspots.size() != 0
         ? this.packDest(closest.getResManager().fishingspots.get(MillRandom.randomInt(closest.getResManager().fishingspots.size())), closest)
         : null;
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) throws Exception {
      return fishingRod != null ? fishingRod : (fishingRod = new ItemStack[]{new ItemStack(Items.FISHING_ROD, 1)});
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) throws Exception {
      for (Building b : villager.getTownHall().getBuildings()) {
         if (b.getResManager().fishingspots.size() > 0) {
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
   public int range(MillVillager villager) {
      // Wider than the default 3 so performAction keeps being called while the villager walks (a few blocks) to the
      // dropped fish during the PICKUP phase — otherwise it would freeze the moment a drop is outside range.
      return 6;
   }

   @Override
   public boolean stopMovingWhileWorking() {
      // The villager must be free to walk to each dropped fish during PICKUP; freezing the nav each tick (the
      // default) would strand it. Casting/waiting still keeps it in place via lookAtGoal + the short range.
      return false;
   }

   @Override
   public boolean performAction(MillVillager villager) throws Exception {
      Point spot = this.getCurrentGoalTarget(villager);
      if (spot == null) {
         return true; // no target — let the goal end.
      }
      BlockPos water = spot.getBlockPos();

      OpState state = VillagerWorldOps.fishTick(villager, water);
      switch (state) {
         case COMPLETE:
            // The real FISHING loot has been rolled + picked up; add any culture-specific bonus, then end the goal.
            this.addFishResults(villager);
            return true;
         case BLOCKED:
            // No rod / no water — end this attempt; the goal system will re-evaluate (fetch a rod, pick a new spot).
            return true;
         default:
            // IN_PROGRESS / PICKING_UP / APPROACHING — keep driving the FSM next tick.
            return false;
      }
   }

   @Override
   public int priority(MillVillager villager) throws Exception {
      return villager.getGoalBuildingDest() == null ? 20 : 100 - villager.getGoalBuildingDest().countGoods(Items.COD);
   }

   @Override
   public boolean stuckAction(MillVillager villager) throws Exception {
      return this.performAction(villager);
   }
}
