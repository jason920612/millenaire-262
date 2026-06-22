package org.millenaire.common.ai.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Bounded A* over G0 with the lexicographic {@link LexCost} — the per-agent solver for UNIQUE goals (a
 * destination only one villager wants, so a shared {@link MillFlowField} would be wasteful). Same objective
 * as the field, so behaviour is consistent; bounded by {@code maxNodes} so it is cheap and amortisable.
 * Heuristic = Manhattan distance as a pure scalar (admissible: the illegal/repair/risk tiers are non-negative
 * add-ons, so a zero-tier distance estimate never overestimates).
 */
public final class MillLexAStar {
   private MillLexAStar() {
   }

   /** Shortest G0 path start→goal under LexCost, or {@code null} if none within {@code maxNodes} expansions. */
   public static List<BlockPos> findPath(Level level, EdgeCostProvider costs, BlockPos start, BlockPos goal, int maxNodes) {
      Map<Long, LexCost> g = new HashMap<>();
      Map<Long, BlockPos> cameFrom = new HashMap<>();
      PriorityQueue<Entry> open = new PriorityQueue<>((a, b) -> a.f.compareTo(b.f));
      g.put(start.asLong(), LexCost.ZERO);
      open.add(new Entry(start, LexCost.ZERO, heuristic(start, goal)));
      int expanded = 0;
      while (!open.isEmpty() && expanded < maxNodes) {
         Entry cur = open.poll();
         long curKey = cur.pos.asLong();
         LexCost known = g.get(curKey);
         if (known != null && cur.g.compareTo(known) > 0) {
            continue; // stale
         }
         if (cur.pos.closerThan(goal, 1.0)) {
            return reconstruct(cameFrom, cur.pos);
         }
         expanded++;
         for (BlockPos nb : costs.neighbors(level, cur.pos)) {
            LexCost tentative = cur.g.plus(costs.edgeCost(level, cur.pos, nb));
            long nbKey = nb.asLong();
            LexCost prev = g.get(nbKey);
            if (prev == null || tentative.compareTo(prev) < 0) {
               g.put(nbKey, tentative);
               cameFrom.put(nbKey, cur.pos);
               open.add(new Entry(nb.immutable(), tentative, tentative.plus(heuristic(nb, goal))));
            }
         }
      }
      return null;
   }

   private static LexCost heuristic(BlockPos from, BlockPos goal) {
      int d = Math.abs(from.getX() - goal.getX()) + Math.abs(from.getY() - goal.getY()) + Math.abs(from.getZ() - goal.getZ());
      return LexCost.normal(d);
   }

   private static List<BlockPos> reconstruct(Map<Long, BlockPos> cameFrom, BlockPos end) {
      List<BlockPos> path = new ArrayList<>();
      BlockPos cur = end;
      while (cur != null) {
         path.add(cur);
         cur = cameFrom.get(cur.asLong());
      }
      Collections.reverse(path);
      return path;
   }

   private record Entry(BlockPos pos, LexCost g, LexCost f) {
   }
}
