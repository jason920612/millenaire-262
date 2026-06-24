package org.millenaire.common.goal;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.Items;
import com.coderyo.jason.ops.OpState;
import com.coderyo.jason.ops.VillagerActions;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.pathing.atomicstryker.AStarConfig;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;

/**
 * Milk adult cows around a cattle-farmer's house (the thin REAL goal for the player-like milk ACTION).
 *
 * <h2>Why this goal exists</h2>
 * 1.12 Millénaire had NO milk goal (the only cattle behaviour was {@link GoalBreedAnimals}); milking was never a
 * villager activity. The player-like {@link VillagerActions#milkAnimal} primitive (bucket → milk_bucket, mirroring
 * vanilla {@code AbstractCow.mobInteract} for a Mob villager) therefore had no in-game driver. This goal is that
 * driver: a faithful, real goal modelled on {@link GoalShearSheep} — find an adult cow near the villager's cattle
 * house, navigate to it, and on arrival run the REAL milk action — so a cattle farmer with an empty bucket actually
 * brings home milk. It is STRICT: with no empty {@code minecraft:bucket} in stock the action returns
 * {@link OpState#BLOCKED} and the goal defers (it never fakes milk).
 *
 * <p>Like the shear goal it scans for the cow at the goal's own {@link #range(MillVillager)} (5), NOT a narrower box,
 * so a villager the navigation deems "arrived" (range 5) but standing 4–5 blocks from the cow still finds + milks it
 * (the arrive-but-not-act trap that a too-small scan box creates).
 */
@DocumentedElement.Documentation("Milk adult cows present around the cattle-farmer's house, filling buckets with milk.")
public class GoalMilkCow extends Goal {
   public GoalMilkCow() {
      this.icon = InvItem.createInvItem(Items.BUCKET);
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) throws Exception {
      Point pos = villager.getPos();
      Entity closestCow = null;
      double cowBestDist = Double.MAX_VALUE;

      for (Entity ent : WorldUtilities.getEntitiesWithinAABB(villager.level(), Cow.class, villager.getHouse().getPos(), 30, 10)) {
         Cow cow = (Cow) ent;
         if (!cow.isBaby() && !cow.isRemoved() && (closestCow == null || pos.distanceTo(ent) < cowBestDist)) {
            closestCow = ent;
            cowBestDist = pos.distanceTo(ent);
         }
      }

      return closestCow != null ? this.packDest(null, villager.getHouse(), closestCow) : null;
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
      // Only cattle houses (the same "cattle" tag GoalBreedAnimals keys cows on), and only when the villager has an
      // empty bucket to fill and there is an adult cow to milk.
      if (!villager.getHouse().containsTags("cattle")) {
         return false;
      }
      if (villager.countInv(Items.BUCKET, 0) <= 0) {
         return false;
      }
      List<? extends Entity> cows = WorldUtilities.getEntitiesWithinAABB(villager.level(), Cow.class, villager.getHouse().getPos(), 30, 10);
      if (cows == null) {
         return false;
      }
      for (Entity ent : cows) {
         Cow cow = (Cow) ent;
         if (!cow.isBaby() && !cow.isRemoved()) {
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
   public boolean performAction(MillVillager villager) throws Exception {
      // Scan at the goal's range (5), matching the navigation "arrived" radius, so a just-arrived villager 4-5 blocks
      // from the cow still finds it (a narrower box would let the villager arrive but never act).
      for (Entity ent : WorldUtilities.getEntitiesWithinAABB(villager.level(), Cow.class, villager.getPos(), this.range(villager), 4)) {
         if (ent.isRemoved()) {
            continue;
         }
         Cow cow = (Cow) ent;
         if (cow.isBaby()) {
            continue; // can't milk a calf (matches the vanilla !isBaby() gate).
         }

         OpState st = VillagerActions.milkAnimal(villager, cow);
         if (st == OpState.BLOCKED) {
            // No empty bucket: defer this tick (do NOT fake milk). The goal's precondition / household supply provides
            // a bucket; retry next tick once one is in stock.
            return false;
         }
         if (st == OpState.COMPLETE && MillConfigValues.LogCattleFarmer >= 1 && villager.extraLog) {
            MillLog.major(this, "Milked (real bucket -> milk_bucket): " + ent);
         }
         // st == APPROACHING (out of reach) → keep walking; the goal re-runs. Only milk one cow per pass.
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
