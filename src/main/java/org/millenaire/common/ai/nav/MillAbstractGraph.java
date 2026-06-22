package org.millenaire.common.ai.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.core.BlockPos;

/**
 * The HPA* high-level layer: a coarse graph of village waypoints (building entrances, road junctions, gates,
 * bridges, ladders) with {@link LexCost} edges between connected ones. A long trip plans over this handful of
 * waypoints first (per-query cost ~independent of village size), then each leg is refined by the local flow
 * field / {@link MillLexAStar}. Built once + cached; a block change invalidates only the affected cluster.
 *
 * <p>This class is the reusable abstract search; the concrete cluster/portal extraction from the world wires
 * it. Edges are directed — add both directions for a two-way road segment.
 */
public final class MillAbstractGraph {
   private final Map<Long, List<Edge>> adj = new HashMap<>();

   public void addEdge(BlockPos from, BlockPos to, LexCost cost) {
      this.adj.computeIfAbsent(from.asLong(), k -> new ArrayList<>()).add(new Edge(to.immutable(), cost));
   }

   public void addBidirectional(BlockPos a, BlockPos b, LexCost cost) {
      addEdge(a, b, cost);
      addEdge(b, a, cost);
   }

   public boolean isEmpty() {
      return this.adj.isEmpty();
   }

   /** Lexicographic-shortest waypoint path start→goal (inclusive), or {@code null} if disconnected. */
   public List<BlockPos> shortestPath(BlockPos start, BlockPos goal) {
      Map<Long, LexCost> dist = new HashMap<>();
      Map<Long, BlockPos> prev = new HashMap<>();
      PriorityQueue<Entry> open = new PriorityQueue<>((x, y) -> x.cost.compareTo(y.cost));
      dist.put(start.asLong(), LexCost.ZERO);
      open.add(new Entry(start, LexCost.ZERO));
      while (!open.isEmpty()) {
         Entry cur = open.poll();
         long key = cur.pos.asLong();
         LexCost known = dist.get(key);
         if (known != null && cur.cost.compareTo(known) > 0) {
            continue;
         }
         if (cur.pos.equals(goal)) {
            return reconstruct(prev, start, goal);
         }
         for (Edge e : this.adj.getOrDefault(key, Collections.emptyList())) {
            LexCost nd = cur.cost.plus(e.cost);
            long nk = e.to.asLong();
            LexCost pd = dist.get(nk);
            if (pd == null || nd.compareTo(pd) < 0) {
               dist.put(nk, nd);
               prev.put(nk, cur.pos);
               open.add(new Entry(e.to, nd));
            }
         }
      }
      return null;
   }

   private static List<BlockPos> reconstruct(Map<Long, BlockPos> prev, BlockPos start, BlockPos goal) {
      List<BlockPos> path = new ArrayList<>();
      BlockPos cur = goal;
      while (cur != null) {
         path.add(cur);
         if (cur.equals(start)) {
            break;
         }
         cur = prev.get(cur.asLong());
      }
      Collections.reverse(path);
      return path;
   }

   private record Edge(BlockPos to, LexCost cost) {
   }

   private record Entry(BlockPos pos, LexCost cost) {
   }
}
