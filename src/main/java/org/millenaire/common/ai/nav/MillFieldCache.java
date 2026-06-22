package org.millenaire.common.ai.nav;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Per-village cache of {@link MillFlowField}s keyed by goal. Many villagers heading to the same hot
 * destination (a bed, a work point, the town-hall, a raid rally) share ONE cached field, rebuilt only every
 * TTL — so the expensive reverse-Dijkstra runs once per goal per ~10s, not once per villager per path. This
 * is where the hundreds-of-agents TPS budget is actually saved.
 */
public final class MillFieldCache {
   /** Rebuild a field at most this often (ticks) — the world rarely changes faster than a villager walks. */
   private static final int TTL = 200;
   /** Bounded flood size per field (caps build cost + memory). */
   private static final int MAX_CELLS = 4096;

   private final Map<Long, Cached> fields = new HashMap<>();

   /** The shared field toward {@code goal}, (re)built lazily; cheap O(1) for repeat callers within the TTL. */
   public NavField field(Level level, EdgeCostProvider costs, BlockPos goal, long now) {
      long key = goal.asLong();
      Cached c = this.fields.get(key);
      if (c == null || now - c.builtTick >= TTL) {
         c = new Cached(MillFlowField.build(level, costs, goal, MAX_CELLS), now);
         this.fields.put(key, c);
      }
      return c.field;
   }

   /** Drop a goal's field (e.g. the destination is gone or its area changed). */
   public void invalidate(BlockPos goal) {
      this.fields.remove(goal.asLong());
   }

   public void clear() {
      this.fields.clear();
   }

   private record Cached(NavField field, long builtTick) {
   }
}
