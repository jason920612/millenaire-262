package com.coderyo.jason.ops;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;

/**
 * PURE ore-vein / cave mining ENGINE for the emergent-civilization miner (Phase 1, #3). This is the direct Java
 * translation of the sim-validated {@code task-ops-sim/minesim.py}: scan for the nearest ore → plan a dig-tunnel to
 * the vein → FLOOD-FILL the connected vein → detect a natural cave's exposed ore → advance the mine FRONTIER outward
 * when local ore is exhausted; NEVER breach lava/bedrock/water — mark them as permanent DON'T-MINE HAZARDS and ROUTE
 * AROUND them (detour up/around, do not seal-to-stone, do not stop dead).
 *
 * <p>Like {@link org.millenaire.common.ai.nav.Mill3DPathfinder} (and the sim) the whole engine is expressed over a
 * minimal {@link MineView} abstraction — a read-only voxel classifier (is this cell ore / hazard / passable, where
 * is the nearest ore) — so it is fully UNIT-TESTABLE on synthetic stone bodies (the {@code minesim.py} world) with
 * NO Minecraft {@code Level} required, and the real-world driver ({@link OreVeinMiner}) plugs a {@code ServerLevel}
 * view in behind the same interface. The engine performs NO world writes itself; it returns PLANS (a dig path, a
 * vein cell list, a next-frontier target) that the driver executes one tick at a time via the player-like ops
 * ({@link VillagerWorldOps#breakTick}/{@code pickupTick}). State lives on the POINT ({@link TaskPointStore}).
 */
public final class MillMiningOps {

   /** Default radius (blocks) the miner scans around itself / the mine frontier for the nearest ore. */
   public static final int DEFAULT_SCAN_RADIUS = 12;
   /** Max ore cells flood-mined in one engine cycle (bounded so a huge vein never stalls the tick loop). */
   public static final int MAX_VEIN_PER_CYCLE = 48;
   /** Max length of a single planned dig-tunnel toward a vein (a longer lead is abandoned + re-planned). */
   public static final int MAX_DIG_PATH = 64;
   /** How far outward the frontier steps per advance when the local ore is exhausted. */
   public static final int FRONTIER_STEP = 6;

   private MillMiningOps() {
   }

   // ================================================================================================
   // MineView — the read-only voxel classifier the whole engine runs on (real world OR synthetic test).
   // ================================================================================================

   /**
    * Read-only classification of the world for the mining engine. The real implementation ({@link OreVeinMiner})
    * wraps a {@code ServerLevel}; the test implementation is an in-memory cell map (the {@code minesim.py} world).
    *
    * <p><b>HAZARD contract:</b> {@link #isHazard} reports cells that must NEVER be breached or entered (lava,
    * bedrock, water sources, and any cell the driver has permanently marked). The engine treats a hazard exactly
    * as {@code minesim.py} does: it never plans a dig step into one, and it routes the frontier AROUND it.
    */
   public interface MineView {
      /** True if the block at {@code pos} is an ore block (vanilla ore; a Mill ore if any exist). */
      boolean isOre(BlockPos pos);

      /**
       * True if {@code pos} is a permanent DON'T-MINE / DON'T-ENTER hazard: lava, bedrock, a water source, or a
       * cell the driver has marked. The engine never digs into or routes through a hazard.
       */
      boolean isHazard(BlockPos pos);

      /**
       * True if {@code pos} is solid rock/earth the miner CAN dig through (stone, deepslate, dirt, … — anything
       * breakable that is not air, not ore, not a hazard). Used to recognise a stone body to tunnel through.
       */
      boolean isDiggable(BlockPos pos);

      /** True if {@code pos} is open space the body can occupy / move through (air, or an already-dug cell). */
      boolean isPassable(BlockPos pos);
   }

   // ================================================================================================
   // ORE SCAN — nearest reachable ore within a radius (the "detect nearby ore" step).
   // ================================================================================================

   /**
    * The nearest ORE cell within {@code radius} (Chebyshev box) of {@code from}, by squared Euclidean distance, or
    * {@code null} if none. Hazard-marked cells are skipped (a hazard is never a mining target). This is the engine's
    * {@code scan_ore} step.
    */
   public static BlockPos findNearestOre(MineView view, BlockPos from, int radius) {
      BlockPos best = null;
      long bestD2 = Long.MAX_VALUE;
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               BlockPos p = from.offset(dx, dy, dz);
               if (view.isHazard(p) || !view.isOre(p)) {
                  continue;
               }
               long d2 = dist2(from, p);
               if (d2 < bestD2) {
                  bestD2 = d2;
                  best = p;
               }
            }
         }
      }
      return best;
   }

   // ================================================================================================
   // DIG PATH — plan a tunnel from the miner toward a target, routing AROUND hazards (never INTO them).
   // ================================================================================================

   /**
    * Plan a dig-tunnel from {@code from} toward {@code target}: a greedy axis-walk (X then Y then Z, like
    * {@code minesim.py}'s {@code step_to}) producing the ordered list of cells to clear so the miner can reach
    * the target. A step that would enter a HAZARD is NOT taken straight; instead the planner DETOURS (tries to go
    * up-and-over or side-step around it) — never stopping dead at the obstacle and never planning into the hazard.
    * The returned list excludes {@code from} and ends at (or adjacent to) {@code target}; it is bounded by
    * {@link #MAX_DIG_PATH}.
    *
    * @return the ordered cells to dig/walk; empty if already adjacent; the path stops short if fully boxed in by
    *     hazards (the driver then marks the lead dead and the frontier re-routes).
    */
   public static List<BlockPos> planDigPath(MineView view, BlockPos from, BlockPos target) {
      List<BlockPos> path = new ArrayList<>();
      BlockPos cur = from;
      int guard = 0;
      while (!cur.equals(target) && !adjacent(cur, target) && guard < MAX_DIG_PATH) {
         guard++;
         BlockPos next = greedyStep(cur, target);
         if (view.isHazard(next)) {
            // Never tunnel into a hazard — DETOUR around it (up-and-over, then sideways), as the sim does.
            BlockPos detour = detourAround(view, cur, target, next);
            if (detour == null) {
               break; // boxed in by hazards on every candidate — abandon this lead; the frontier re-routes.
            }
            next = detour;
         }
         path.add(next);
         cur = next;
      }
      return path;
   }

   /** Greedy single axis step from {@code cur} toward {@code target}: resolve X, then Y, then Z (sim's step_to). */
   private static BlockPos greedyStep(BlockPos cur, BlockPos target) {
      if (target.getX() != cur.getX()) {
         return cur.offset(Integer.signum(target.getX() - cur.getX()), 0, 0);
      }
      if (target.getY() != cur.getY()) {
         return cur.offset(0, Integer.signum(target.getY() - cur.getY()), 0);
      }
      return cur.offset(0, 0, Integer.signum(target.getZ() - cur.getZ()));
   }

   /**
    * Find a non-hazard detour step from {@code cur} toward {@code target} when the straight step {@code blocked}
    * is a hazard. Mirrors {@code minesim.py}'s frontier detour candidate order: prefer up-and-over, then the two
    * sideways cells, then straight-up. Returns {@code null} if every candidate is a hazard (genuinely boxed in).
    */
   private static BlockPos detourAround(MineView view, BlockPos cur, BlockPos target, BlockPos blocked) {
      int dx = Integer.signum(target.getX() - cur.getX());
      int dz = Integer.signum(target.getZ() - cur.getZ());
      // If we were stepping along X/Z keep that forward bias; bias sideways/up around the hazard.
      BlockPos[] candidates = {
         cur.offset(dx, 1, dz),      // up-and-over the obstacle, keeping forward progress
         cur.offset(dx, 0, 1),       // side-step +Z
         cur.offset(dx, 0, -1),      // side-step -Z
         cur.offset(1, 0, dz),       // side-step +X
         cur.offset(-1, 0, dz),      // side-step -X
         cur.above(),                // straight up
      };
      for (BlockPos c : candidates) {
         if (c.equals(blocked) || c.equals(cur)) {
            continue;
         }
         if (!view.isHazard(c)) {
            return c;
         }
      }
      return null;
   }

   // ================================================================================================
   // FLOOD-FILL VEIN — BFS the connected ore body from a start ore cell (the sim's flood_vein).
   // ================================================================================================

   /**
    * Flood-fill the connected ORE vein reachable from {@code start} via 6-neighbour adjacency, returning the ore
    * cells in BFS order (so the driver mines nearest-first). Bounded by {@link #MAX_VEIN_PER_CYCLE} so a huge vein
    * is mined across multiple cycles rather than stalling a tick. Hazard cells are never included. This is the
    * engine's {@code flood_vein} planning step — the driver then {@code breakTick}s each returned cell in order.
    *
    * @return ordered ore cells to mine; the first element is {@code start} if it is itself ore.
    */
   public static List<BlockPos> floodMineVein(MineView view, BlockPos start) {
      return floodMineVein(view, start, MAX_VEIN_PER_CYCLE);
   }

   /** {@link #floodMineVein(MineView, BlockPos)} with an explicit cap on the number of vein cells returned. */
   public static List<BlockPos> floodMineVein(MineView view, BlockPos start, int max) {
      List<BlockPos> vein = new ArrayList<>();
      if (!view.isOre(start) || view.isHazard(start)) {
         return vein;
      }
      Set<Long> seen = new HashSet<>();
      Deque<BlockPos> queue = new ArrayDeque<>();
      queue.add(start);
      seen.add(start.asLong());
      while (!queue.isEmpty() && vein.size() < max) {
         BlockPos p = queue.poll();
         if (view.isHazard(p) || !view.isOre(p)) {
            continue;
         }
         vein.add(p);
         for (BlockPos n : neighbours6(p)) {
            if (seen.add(n.asLong()) && view.isOre(n) && !view.isHazard(n)) {
               queue.add(n);
            }
         }
      }
      return vein;
   }

   // ================================================================================================
   // CAVE USE — ore exposed on the walls of a natural air cavern bordering the mine front.
   // ================================================================================================

   /**
    * Detect a natural cave bordering {@code from} and return the ore exposed on its walls. A "cave opening" is a
    * passable (air) neighbour of {@code from}; its own ore neighbours are the exposed wall ore the miner can mine
    * straight out of the cavern (the sim's {@code use_cave}). Returns the exposed ore cells (possibly empty).
    *
    * @param caveScan how many air cells out from the opening to scan for exposed ore (1 = just the opening's walls).
    */
   public static List<BlockPos> caveExposedOre(MineView view, BlockPos from, int caveScan) {
      List<BlockPos> exposed = new ArrayList<>();
      Set<Long> oreSeen = new HashSet<>();
      Set<Long> airSeen = new HashSet<>();
      Deque<BlockPos> airFront = new ArrayDeque<>();
      // Seed with the air neighbours of the mine front (the cave openings).
      for (BlockPos n : neighbours6(from)) {
         if (view.isPassable(n) && !view.isHazard(n) && airSeen.add(n.asLong())) {
            airFront.add(n);
         }
      }
      int depth = 0;
      while (!airFront.isEmpty() && depth < Math.max(1, caveScan)) {
         int layer = airFront.size();
         for (int i = 0; i < layer; i++) {
            BlockPos air = airFront.poll();
            for (BlockPos w : neighbours6(air)) {
               if (view.isOre(w) && !view.isHazard(w) && oreSeen.add(w.asLong())) {
                  exposed.add(w); // ore on the cave wall
               } else if (view.isPassable(w) && !view.isHazard(w) && airSeen.add(w.asLong())) {
                  airFront.add(w); // keep exploring the cavern
               }
            }
         }
         depth++;
      }
      return exposed;
   }

   // ================================================================================================
   // FRONTIER ADVANCE — when local ore is exhausted, push the mine outward, routing AROUND hazards.
   // ================================================================================================

   /**
    * Plan the next FRONTIER position outward from {@code frontier} in horizontal direction {@code (dirX, dirZ)},
    * stepping {@link #FRONTIER_STEP} cells but ROUTING AROUND hazards exactly like {@code minesim.py}'s
    * {@code mine_cycle} frontier loop: at each step prefer straight ahead, else up-and-over, else side-step; a
    * hazard candidate is marked (reported via {@code markHazard}) and the next detour candidate is tried. The
    * frontier never stops dead at lava and never plans through it. Returns the new frontier cell (the mine has
    * grown outward toward unexplored ground), or the last reachable cell if fully boxed in.
    *
    * @param markHazard callback invoked for every hazard cell encountered while routing (so the driver can record
    *     it as a permanent DON'T-MINE cell on the point's hazard set).
    */
   public static BlockPos advanceFrontier(MineView view, BlockPos frontier, int dirX, int dirZ,
         java.util.function.Consumer<BlockPos> markHazard) {
      BlockPos cur = frontier;
      for (int step = 0; step < FRONTIER_STEP; step++) {
         BlockPos[] candidates = {
            cur.offset(dirX, 0, dirZ),      // straight outward
            cur.offset(dirX, 1, dirZ),      // up-and-over
            cur.offset(dirX, 0, 1),         // side-step +Z
            cur.offset(dirX, 0, -1),        // side-step -Z
            cur.above(),                    // straight up
         };
         BlockPos moved = null;
         for (BlockPos c : candidates) {
            if (view.isHazard(c)) {
               if (markHazard != null) {
                  markHazard.accept(c); // permanent DON'T-MINE; try the next detour candidate
               }
               continue;
            }
            moved = c;
            break;
         }
         if (moved == null) {
            break; // boxed in by hazards on every candidate at this step — stop advancing this pass.
         }
         cur = moved;
      }
      return cur;
   }

   // ================================================================================================
   // helpers
   // ================================================================================================

   /** 6-neighbour (face-adjacent) cells of {@code p}. */
   public static BlockPos[] neighbours6(BlockPos p) {
      return new BlockPos[]{
         p.offset(1, 0, 0), p.offset(-1, 0, 0),
         p.offset(0, 1, 0), p.offset(0, -1, 0),
         p.offset(0, 0, 1), p.offset(0, 0, -1),
      };
   }

   /** Squared Euclidean distance between two block positions. */
   public static long dist2(BlockPos a, BlockPos b) {
      long dx = a.getX() - b.getX();
      long dy = a.getY() - b.getY();
      long dz = a.getZ() - b.getZ();
      return dx * dx + dy * dy + dz * dz;
   }

   /** True if {@code a} and {@code b} are face-adjacent (Manhattan distance 1). */
   public static boolean adjacent(BlockPos a, BlockPos b) {
      return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ()) == 1;
   }
}
