package org.millenaire.common.ai.nav;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * A shared cost-to-go field toward one goal, built ONCE by a bounded reverse-Dijkstra flood over G0 and then
 * read by arbitrarily many villagers at O(1) each (step down the gradient). This is the key to holding 20 TPS
 * with hundreds of agents: 30 villagers heading to the town-hall share ONE field instead of running 30 paths.
 */
public final class MillFlowField implements NavField {
   private final BlockPos goal;
   private final Map<Long, LexCost> cost = new HashMap<>();
   private final Map<Long, BlockPos> step = new HashMap<>(); // from a cell, the next cell toward the goal

   private MillFlowField(BlockPos goal) {
      this.goal = goal;
   }

   /** Bounded reverse-Dijkstra: flood outward from {@code goal} over G0, recording each cell's cost-to-go and
    *  the next step that achieves it. Capped at {@code maxCells} so the build stays cheap and amortisable. */
   public static MillFlowField build(Level level, EdgeCostProvider costs, BlockPos goal, int maxCells) {
      MillFlowField f = new MillFlowField(goal);
      PriorityQueue<Entry> open = new PriorityQueue<>((a, b) -> a.cost.compareTo(b.cost));
      f.cost.put(goal.asLong(), LexCost.ZERO);
      open.add(new Entry(goal, LexCost.ZERO));
      while (!open.isEmpty() && f.cost.size() < maxCells) {
         Entry cur = open.poll();
         LexCost known = f.cost.get(cur.pos.asLong());
         if (known != null && cur.cost.compareTo(known) > 0) {
            continue; // stale queue entry
         }
         // For every cell that can move INTO cur (its neighbours), relax cost-to-go via cur.
         for (BlockPos nb : costs.neighbors(level, cur.pos)) {
            LexCost edge = costs.edgeCost(level, nb, cur.pos); // cost of moving nb → cur
            LexCost viaCur = edge.plus(cur.cost);
            long key = nb.asLong();
            LexCost prev = f.cost.get(key);
            if (prev == null || viaCur.compareTo(prev) < 0) {
               f.cost.put(key, viaCur);
               f.step.put(key, cur.pos);
               open.add(new Entry(nb.immutable(), viaCur));
            }
         }
      }
      return f;
   }

   @Override
   public BlockPos goal() {
      return this.goal;
   }

   @Override
   public LexCost costToGo(BlockPos pos) {
      return this.cost.getOrDefault(pos.asLong(), LexCost.INFINITE);
   }

   @Override
   public BlockPos nextStep(BlockPos pos) {
      return this.step.get(pos.asLong());
   }

   @Override
   public boolean covers(BlockPos pos) {
      return this.cost.containsKey(pos.asLong());
   }

   private record Entry(BlockPos pos, LexCost cost) {
   }
}
