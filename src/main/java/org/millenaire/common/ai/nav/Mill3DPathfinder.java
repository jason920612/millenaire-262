package org.millenaire.common.ai.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.core.BlockPos;

/**
 * Ground-up 3D pathfinder (no vanilla nav): A* over a rich movement-action graph against a {@link Voxel} —
 * walk (8 horizontal), step-up one, drop down up to 3, and jump a 1-block gap. Costs are the lexicographic
 * {@link LexCost} (climbing free, drops/jumps mildly penalised), so it has true vertical/3D awareness and
 * explores all routes for the optimal one. Decoupled from Minecraft so it is unit-testable on synthetic
 * obstacle courses.
 */
public final class Mill3DPathfinder {
   private static final double DIAG = 1.4142;
   private static final double JUMP_GAP = 2.5;
   private static final double DROP = 0.5;
   private static final int[][] H8 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
   private static final int[][] H4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

   private Mill3DPathfinder() {
   }

   /** Pure-terrain shortest 3D path (no danger bias). */
   public static List<BlockPos> findPath(Voxel v, BlockPos start, BlockPos goal, int maxNodes) {
      return findPath(v, start, goal, maxNodes, CostField.ZERO);
   }

   /**
    * Lexicographic-shortest 3D path start→goal under terrain + a danger {@link CostField} (extra walk-block
    * cost per cell entered, e.g. proximity to hostiles/lava), so the route is SAFE as well as 3D. {@code null}
    * if none within {@code maxNodes} expansions.
    */
   public static List<BlockPos> findPath(Voxel v, BlockPos start, BlockPos goal, int maxNodes, CostField danger) {
      Map<Long, LexCost> g = new HashMap<>();
      Map<Long, BlockPos> from = new HashMap<>();
      PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> a.f.compareTo(b.f));
      g.put(start.asLong(), LexCost.ZERO);
      open.add(new Node(start, LexCost.ZERO, heuristic(start, goal)));
      int expanded = 0;
      // Track the closest-to-goal node seen, so that when the goal can't be fully reached within the node
      // budget (a long trip) we return a BEST-EFFORT PARTIAL path toward it rather than null. The villager
      // then advances + re-plans the next leg — incremental long-range progress, so "no route" only ever
      // means genuinely zero progress possible (truly walled in → the escape behaviour handles it).
      BlockPos best = start;
      double bestDist = heuristic(start, goal).scalarCost;
      while (!open.isEmpty() && expanded < maxNodes) {
         Node cur = open.poll();
         long ck = cur.pos.asLong();
         LexCost known = g.get(ck);
         if (known != null && cur.g.compareTo(known) > 0) {
            continue;
         }
         double h = heuristic(cur.pos, goal).scalarCost;
         if (h < bestDist) {
            bestDist = h;
            best = cur.pos;
         }
         // Reached: standing ON the goal, OR — when the goal is a NON-standable block (a log/crop/ore the
         // villager works AT, or any occupied cell) — standing in an adjacent cell within reach. Without this
         // most routes "failed" because the goal was an occupied work block nobody can stand on.
         if (cur.pos.equals(goal)
            || (cur.pos.closerThan(goal, 1.8) && !v.standable(goal.getX(), goal.getY(), goal.getZ()))) {
            return reconstruct(from, start, cur.pos);
         }
         expanded++;
         forEachNeighbor(v, cur.pos, (nb, stepCost) -> {
            // Add the danger of ENTERING nb (safety routing — req 4) on top of the terrain edge cost.
            LexCost t = cur.g.plus(stepCost).plus(LexCost.normal(danger.extra(nb.getX(), nb.getY(), nb.getZ())));
            long nk = nb.asLong();
            LexCost prev = g.get(nk);
            if (prev == null || t.compareTo(prev) < 0) {
               g.put(nk, t);
               from.put(nk, cur.pos);
               open.add(new Node(nb, t, t.plus(heuristic(nb, goal))));
            }
         });
      }
      // Goal not fully reached within budget → best-effort partial path toward it.
      if (!best.equals(start)) {
         return reconstruct(from, start, best);
      }
      // No progress toward the goal at all: step to ANY walkable neighbour and re-plan next leg, so we never
      // report "no route" while the villager can still move. null only if the start is fully encased (zero
      // neighbours) — a genuine trap the escape behaviour handles.
      BlockPos[] step = {null};
      forEachNeighbor(v, start, (nb, c) -> {
         if (step[0] == null) {
            step[0] = nb;
         }
      });
      return step[0] == null ? null : List.of(start, step[0]);
   }

   /** Enumerate the 3D move edges out of a standable cell, calling {@code out} with each (neighbour, cost). */
   public static void forEachNeighbor(Voxel v, BlockPos p, Neighbor out) {
      int x = p.getX();
      int y = p.getY();
      int z = p.getZ();
      for (int[] d : H8) {
         int nx = x + d[0];
         int nz = z + d[1];
         boolean diag = d[0] != 0 && d[1] != 0;
         double base = diag ? DIAG : 1.0;
         // diagonals must not cut a solid corner
         if (diag && (v.isSolid(nx, y, z) || v.isSolid(x, y, nz) || v.isSolid(nx, y + 1, z) || v.isSolid(x, y + 1, nz))) {
            continue;
         }
         if (v.standable(nx, y, nz)) {
            out.accept(new BlockPos(nx, y, nz), LexCost.normal(base)); // walk same level
         } else if (v.standable(nx, y + 1, nz) && v.clearBody(x, y + 2, z) && !v.isSolid(nx, y + 2, nz)) {
            out.accept(new BlockPos(nx, y + 1, nz), LexCost.normal(base)); // step / jump up one (climbing free)
         } else if (v.clearBody(nx, y, nz)) {
            for (int dy = -1; dy >= -5; dy--) { // drop down up to 5 (lets a villager step off a ledge/pillar)
               if (v.standable(nx, y + dy, nz)) {
                  out.accept(new BlockPos(nx, y + dy, nz), LexCost.normal(base + DROP * (-dy)));
                  break;
               }
               if (v.isSolid(nx, y + dy - 1, nz)) {
                  break; // landed-on something not standable / blocked
               }
            }
         }
      }
      // jump a 1-block gap (cardinal): gap cell air + same-level landing 2 out, with head clearance
      for (int[] d : H4) {
         int gx = x + d[0];
         int gz = z + d[1];
         int lx = x + 2 * d[0];
         int lz = z + 2 * d[1];
         if (!v.standable(gx, y, gz) && v.clearBody(gx, y, gz) && v.standable(lx, y, lz)
            && v.clearBody(lx, y, lz) && !v.isSolid(gx, y + 1, gz)) {
            out.accept(new BlockPos(lx, y, lz), LexCost.normal(JUMP_GAP));
         }
      }
      // RUNNING JUMP over a 2-block gap (cardinal): two air cells, land 3 out — for streams / 2-wide paths /
      // small ravines that a 1-gap jump can't clear. Pricier than the 1-gap so it's a considered choice.
      for (int[] d : H4) {
         int g1x = x + d[0];
         int g1z = z + d[1];
         int g2x = x + 2 * d[0];
         int g2z = z + 2 * d[1];
         int lx = x + 3 * d[0];
         int lz = z + 3 * d[1];
         if (!v.standable(g1x, y, g1z) && v.clearBody(g1x, y, g1z)
            && !v.standable(g2x, y, g2z) && v.clearBody(g2x, y, g2z)
            && v.standable(lx, y, lz) && v.clearBody(lx, y, lz)
            && !v.isSolid(g1x, y + 1, g1z) && !v.isSolid(g2x, y + 1, g2z)) {
            out.accept(new BlockPos(lx, y, lz), LexCost.normal(JUMP_GAP + 1.5));
         }
      }
   }

   private static LexCost heuristic(BlockPos a, BlockPos b) {
      int d = Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
      return LexCost.normal(d);
   }

   private static List<BlockPos> reconstruct(Map<Long, BlockPos> from, BlockPos start, BlockPos goal) {
      List<BlockPos> path = new ArrayList<>();
      BlockPos cur = goal;
      while (cur != null) {
         path.add(cur);
         if (cur.equals(start)) {
            break;
         }
         cur = from.get(cur.asLong());
      }
      Collections.reverse(path);
      return path;
   }

   /** Callback for {@link #forEachNeighbor}. */
   public interface Neighbor {
      void accept(BlockPos pos, LexCost cost);
   }

   /** Extra per-cell cost (in walk-blocks) layered on terrain — e.g. a danger field around hostiles/hazards. */
   public interface CostField {
      CostField ZERO = (x, y, z) -> 0.0;

      double extra(int x, int y, int z);
   }

   private record Node(BlockPos pos, LexCost g, LexCost f) {
   }
}
