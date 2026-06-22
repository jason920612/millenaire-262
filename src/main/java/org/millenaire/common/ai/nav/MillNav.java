package org.millenaire.common.ai.nav;

import net.minecraft.core.BlockPos;

import org.millenaire.common.entity.MillVillager;

/**
 * Server-side navigation service: the shared {@link MillFieldCache} + the per-tick "which way to the goal?"
 * query villagers use. Many villagers heading to the same destination share ONE cached flow field, so the
 * reverse-Dijkstra runs once per goal per TTL rather than per villager per path — the value-field rewrite's
 * hundreds-of-agents TPS win. Single-threaded (server villager ticks); no locking needed.
 */
public final class MillNav {
   private static final MillFieldCache CACHE = new MillFieldCache();
   private static final MillEdgeCostProvider COSTS = new MillEdgeCostProvider();

   private MillNav() {
   }

   /**
    * The next standable cell to step toward {@code dest} via the shared flow field, or {@code null} if the
    * villager isn't covered by the field (caller should fall back to direct pathing). O(1) for repeat callers
    * within the field's TTL.
    */
   public static BlockPos stepToward(MillVillager villager, BlockPos dest) {
      if (villager.level().isClientSide()) {
         return null;
      }
      long now = villager.level().getGameTime();
      // Price the (shared) field with the village danger field so it routes safely too. Climbing free.
      COSTS.configure(villager.getAiInfluence(), 1.0F, 0.3F);
      NavField field = CACHE.field(villager.level(), COSTS, dest, now);
      BlockPos here = villager.blockPosition();
      if (here.equals(dest) || here.closerThan(dest, 1.5)) {
         return null; // arrived / let the caller finish
      }
      return field.covers(here) ? field.nextStep(here) : null;
   }

   public static void clear() {
      CACHE.clear();
   }
}
