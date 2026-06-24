package com.coderyo.jason.ops;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import org.millenaire.common.ai.nav.Mill3DNavigator;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.utilities.MillLog;

/**
 * REAL ore-vein / cave mining DRIVER for a {@link MillVillager} — the world-facing half of the Phase 1 (#3) miner,
 * pairing the sim-validated pure {@link MillMiningOps} engine with the player-like world ops
 * ({@link VillagerWorldOps#breakTick}/{@code pickupTick}/{@code ensureTool}) and the ground-up 3D navigation
 * ({@link Mill3DNavigator}, which already routes around obstacles). It REPLACES the old regrowing-source-block
 * heuristic with genuine excavation:
 *
 * <ol>
 *   <li><b>scan</b> the nearest reachable ore around the mine frontier ({@link MillMiningOps#findNearestOre});</li>
 *   <li><b>tunnel</b> a dig-path to the vein — navigate there, {@code breakTick} the stone along the way;</li>
 *   <li><b>flood-mine</b> the whole connected vein ({@link MillMiningOps#floodMineVein}), {@code breakTick} each
 *       ore + walk to its drops ({@code pickupTick}) — the REAL drops the villager carries home;</li>
 *   <li><b>cave use</b>: mine ore exposed on the walls of a natural air cavern bordering the front;</li>
 *   <li><b>frontier advance</b>: when local ore is exhausted, push the mine FRONTIER outward
 *       ({@link MillMiningOps#advanceFrontier}), routing AROUND hazards — the seed of mining-driven expansion;</li>
 *   <li><b>light</b> the tunnel: place a wall torch periodically along the dug path.</li>
 * </ol>
 *
 * <p><b>HAZARD safety (the user's hard requirement):</b> lava, bedrock and water sources are PERMANENT
 * DON'T-MINE / DON'T-ENTER hazards. {@link VillagerWorldOps#breakTick} already refuses bedrock (hardness &lt; 0);
 * this driver additionally REFUSES to break lava/water and marks them on the mine's {@link TaskPointStore.MineState}
 * hazard set, and the engine NEVER plans a dig or a frontier step through a hazard cell — it detours up/around. The
 * miner therefore never breaches lava (no death-by-lava), and hazards are NOT sealed-to-stone (which would get
 * re-mined) but left in place and routed around.
 *
 * <p><b>State</b> lives on the POINT: the per-mine frontier + hazard set on {@link TaskPointStore.MineState} keyed
 * by the mine anchor (the village's source point), so this driver is STATELESS and shared across villagers.
 */
public final class OreVeinMiner {

   /** Walk speed while navigating to the mine frontier / a vein. Matches the general work pace. */
   private static final double MINE_WALK_SPEED = 0.5;
   /** Place a wall torch roughly every this-many dug tunnel cells (tunnel lighting). */
   private static final int TORCH_SPACING = 5;
   /** One mining navigator per villager (drives the MoveControl); created lazily, kept on the villager. */
   private static final java.util.Map<Integer, Mill3DNavigator> NAVS = new java.util.concurrent.ConcurrentHashMap<>();

   private OreVeinMiner() {
   }

   /**
    * The outcome of one {@link #mineTick}, so the goal knows whether to stay in-action, give up (fall back to the
    * old heuristic), or report the cycle done.
    */
   public enum MineResult {
      /** Working: navigating to the frontier, tunnelling, flood-mining, or picking up. Stay in-action. */
      WORKING,
      /** Needs a pickaxe the villager does not have — defer to GoalGetTool (do NOT break without a tool). */
      NEED_TOOL,
      /** No real ore reachable from this mine right now — caller may fall back to the regrowing-source heuristic. */
      NO_ORE,
      /** A full mine cycle completed (vein flood-mined / frontier advanced); the goal may re-pick. */
      CYCLE_DONE
   }

   // ================================================================================================
   // TICK DRIVER
   // ================================================================================================

   /**
    * Advance the real ore-vein mine by one tick for {@code villager}, anchored at {@code mineAnchor} (the village
    * source point / mine entrance). Drives the full scan → tunnel → flood-vein → cave → frontier cycle one step at
    * a time using the player-like ops, and is hazard-safe throughout. Returns a {@link MineResult} the goal acts on.
    */
   public static MineResult mineTick(MillVillager villager, BlockPos mineAnchor) {
      Level level = villager.level();
      if (!(level instanceof ServerLevel)) {
         return MineResult.NO_ORE; // real mining needs a server world (drops/loot); off-server: nothing to do.
      }
      TaskPointStore.MineState mine = TaskPointStore.get().getOrCreateMine(level, mineAnchor);
      mine.lastTickTouched = level.getGameTime();
      LevelMineView view = new LevelMineView(level, mine);

      // Strict tool: real ore needs a pickaxe. No pickaxe → defer (the goal lets GoalGetTool fetch one).
      if (!VillagerWorldOps.ensureTool(villager, VillagerWorldOps.ToolKind.PICKAXE)) {
         return MineResult.NEED_TOOL;
      }

      // (1) SCAN for the nearest ore around the current frontier (then around the villager as a fallback).
      BlockPos searchFrom = mine.frontier;
      BlockPos ore = MillMiningOps.findNearestOre(view, searchFrom, MillMiningOps.DEFAULT_SCAN_RADIUS);
      if (ore == null) {
         ore = MillMiningOps.findNearestOre(view, villager.blockPosition(), MillMiningOps.DEFAULT_SCAN_RADIUS);
      }

      if (ore != null) {
         return mineVeinStep(villager, mine, view, ore);
      }

      // (2) CAVE: no scanned ore — is ore exposed on a natural cavern wall at the frontier?
      List<BlockPos> caveOre = MillMiningOps.caveExposedOre(view, mine.frontier, 3);
      if (caveOre.isEmpty()) {
         caveOre = MillMiningOps.caveExposedOre(view, villager.blockPosition(), 3);
      }
      if (!caveOre.isEmpty()) {
         logMine(villager, mineAnchor, "CAVE opening near frontier " + mine.frontier + " — "
            + caveOre.size() + " ore exposed on cavern walls");
         return mineCellsStep(villager, mine, view, caveOre, true);
      }

      // (3) FRONTIER ADVANCE: local ore exhausted — push the mine outward, routing AROUND hazards.
      return advanceFrontierStep(villager, mine, view, mineAnchor);
   }

   // ---- (1)/(2) mine a vein / a set of exposed ore cells, one cell at a time -----------------------

   /** Flood the connected vein from {@code ore} and mine it cell-by-cell (navigate → breakTick → pickup). */
   private static MineResult mineVeinStep(MillVillager villager, TaskPointStore.MineState mine,
         LevelMineView view, BlockPos ore) {
      List<BlockPos> vein = MillMiningOps.floodMineVein(view, ore);
      if (vein.isEmpty()) {
         return MineResult.NO_ORE;
      }
      if (mine.lastVeinStartLogged != ore.asLong()) {
         // Log once per fresh vein-start (throttled on the point) so the evidence isn't spammed each tick.
         mine.lastVeinStartLogged = ore.asLong();
         logMine(villager, mine.anchor, "VEIN found: tunnelling to ore @ " + ore + " (vein size "
            + vein.size() + (vein.size() >= MillMiningOps.MAX_VEIN_PER_CYCLE ? "+" : "") + ")");
      }
      return mineCellsStep(villager, mine, view, vein, false);
   }

   /**
    * Mine the first not-yet-air cell of {@code cells}: navigate within reach (3D nav, routes around obstacles),
    * {@code breakTick} it (refusing + marking lava/water hazards), then {@code pickupTick} its drops. Lights the
    * tunnel periodically. When every cell is air, the vein/cave is cleared → CYCLE_DONE (so the frontier advances
    * next cycle). One cell per call keeps the tick bounded.
    */
   private static MineResult mineCellsStep(MillVillager villager, TaskPointStore.MineState mine,
         LevelMineView view, List<BlockPos> cells, boolean cave) {
      Level level = villager.level();
      for (BlockPos cell : cells) {
         // HAZARD safety: never break lava/water — mark it permanently and skip (the engine already excludes
         // marked hazards from future scans/plans, so it is routed around, not sealed to re-minable stone).
         if (isHazardBlock(level, cell)) {
            if (!mine.isHazard(cell)) {
               mine.markHazard(cell);
               logMine(villager, mine.anchor, "HAZARD marked @ " + cell + " ("
                  + level.getBlockState(cell).getBlock() + ") — DON'T-MINE, routing around (lava NEVER breached)");
            }
            continue;
         }
         BlockState state = level.getBlockState(cell);
         if (state.isAir()) {
            continue; // already mined this cell — move to the next ore in the vein.
         }

         // Navigate toward the cell if out of reach (the 3D navigator routes around obstacles for us, rather than the
         // facade's vertical scaffold — a mine tunnels horizontally, so we keep the 3D-nav approach here).
         if (!VillagerWorldOps.withinReach(villager, cell)) {
            navTo(villager, cell);
            return MineResult.WORKING;
         }

         boolean wasOre = view.isOreBlock(state);
         // HARVEST via the AI-invokable facade: now in reach, it breaks the cell over the real player destroy-math
         // (tool-aware drops) and walks to + collects them. The climb column is anchored on the mine anchor (shared
         // across the whole excavation) — but a horizontal tunnel rarely needs one (we already 3D-navved into reach).
         OpState st = VillagerActions.harvestBlock(villager, cell, VillagerWorldOps.ToolKind.PICKAXE, mine.anchor);
         // Count the ore + light the tunnel on the exact tick the block goes air (the break completed this tick).
         if (wasOre && level.getBlockState(cell).isAir() && mine.lastBrokenCell != cell.asLong()) {
            mine.lastBrokenCell = cell.asLong();
            mine.oreMined++;
            maybeLightTunnel(villager, mine, cell);
         }
         switch (st) {
            case APPROACHING:
            case EXTENDING_REACH:
            case IN_PROGRESS:
            case PICKING_UP:
               return MineResult.WORKING; // breaking / closing in / collecting the drops next tick.
            case BLOCKED:
               // Unbreakable (bedrock) — permanent hazard, route around it (never stop dead / seal to stone).
               mine.markHazard(cell);
               logMine(villager, mine.anchor, "HAZARD marked @ " + cell + " (unbreakable "
                  + state.getBlock() + ") — routing around");
               continue;
            case COMPLETE:
               // This cell is fully mined + its drops collected; loop on to the next vein cell this same call.
               continue;
            default:
               return MineResult.WORKING;
         }
      }
      // Every cell in this vein/cave is air now — it is fully mined. Reclaim any climb column the facade built while
      // reaching a cell (anchored on the mine anchor) so no temporary scaffolding is left in the tunnel.
      VillagerActions.finishHarvest(villager, mine.anchor);
      logMine(villager, mine.anchor, (cave ? "CAVE" : "VEIN") + " cleared — total ore mined by this mine = "
         + mine.oreMined + "; advancing frontier next cycle");
      return MineResult.CYCLE_DONE;
   }

   // ---- (3) advance the frontier outward, hazard-routed --------------------------------------------

   private static MineResult advanceFrontierStep(MillVillager villager, TaskPointStore.MineState mine,
         LevelMineView view, BlockPos mineAnchor) {
      BlockPos before = mine.frontier;
      BlockPos next = MillMiningOps.advanceFrontier(view, mine.frontier, mine.dirX, mine.dirZ, mine::markHazard);
      if (next.equals(before)) {
         // Boxed in on the current heading — rotate the outward direction 90° and try again next cycle.
         rotateDir(mine);
         logMine(villager, mineAnchor, "FRONTIER blocked at " + before + " — rotating outward dir to ("
            + mine.dirX + "," + mine.dirZ + ") to route around hazards");
         return MineResult.CYCLE_DONE;
      }
      mine.frontier = next;
      mine.frontierAdvances++;
      logMine(villager, mineAnchor, "FRONTIER advanced " + before + " -> " + next + " (advance #"
         + mine.frontierAdvances + ", outward dir " + mine.dirX + "," + mine.dirZ
         + ", hazards avoided=" + mine.hazards.size() + ")");
      // Walk the villager toward the new frontier so it digs there next cycle (3D nav routes around obstacles).
      navTo(villager, next);
      return MineResult.CYCLE_DONE;
   }

   /** Rotate the outward frontier direction 90° clockwise (so a boxed-in heading re-routes around the obstacle). */
   private static void rotateDir(TaskPointStore.MineState mine) {
      int ndx = mine.dirZ;
      int ndz = -mine.dirX;
      if (ndx == 0 && ndz == 0) {
         ndx = 1;
      }
      mine.dirX = ndx;
      mine.dirZ = ndz;
   }

   // ================================================================================================
   // tunnel lighting
   // ================================================================================================

   /**
    * Place a wall torch periodically along the dug tunnel so it is lit (player-like). Torches go on a solid wall
    * adjacent to the just-mined cell, every {@link #TORCH_SPACING} ore cells. Reuses {@link VillagerWorldOps#place}.
    */
   private static void maybeLightTunnel(MillVillager villager, TaskPointStore.MineState mine, BlockPos minedCell) {
      if ((mine.oreMined % TORCH_SPACING) != 0) {
         return;
      }
      Level level = villager.level();
      // Find a solid horizontal wall to mount a wall torch on (vanilla WallTorchBlock faces AWAY from the wall).
      for (Direction face : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
         BlockPos wall = minedCell.relative(face.getOpposite());
         if (level.getBlockState(wall).isSolidRender() && level.getBlockState(minedCell).isAir()) {
            BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, face);
            if (VillagerWorldOps.place(villager, minedCell, torch)) {
               logMine(villager, mine.anchor, "light: placed wall torch @ " + minedCell + " on " + face + " wall");
            }
            return;
         }
      }
   }

   // ================================================================================================
   // navigation (3D nav — routes around obstacles + hazards)
   // ================================================================================================

   /** Drive the villager toward {@code goal} using its own mining {@link Mill3DNavigator} (one per villager). */
   private static void navTo(MillVillager villager, BlockPos goal) {
      Mill3DNavigator nav = NAVS.computeIfAbsent(villager.getId(), id -> new Mill3DNavigator());
      villager.activeNav3d = nav; // publish so the door-open driver can read the path nodes
      nav.navigateTo(villager, goal, MINE_WALK_SPEED);
      villager.getLookControl().setLookAt(goal.getX() + 0.5, goal.getY() + 0.5, goal.getZ() + 0.5);
   }

   // ================================================================================================
   // ore / hazard classification (the real-world MineView)
   // ================================================================================================

   /**
    * The set of vanilla ore blocks the miner targets. Built from the per-metal {@link BlockTags} that 26.2 exposes
    * as {@code TagKey<Block>} ({@code IRON_ORES}/{@code GOLD_ORES}/{@code COPPER_ORES}) plus the explicit known ore
    * blocks for the metals 26.2 does NOT expose as a block-tag constant (coal/diamond/emerald/lapis/redstone, both
    * stone + deepslate variants) and the nether ores + ancient debris. A {@code state.is(tag)} check is preferred
    * where a constant exists (so datapack-added ores in those tags are covered); the explicit set covers the rest.
    */
   private static final Set<Block> KNOWN_ORES = Set.of(
      Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
      Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
      Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
      Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
      Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
      Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.ANCIENT_DEBRIS);

   /** True if {@code state} is an ore the miner targets: vanilla ores (per-metal tags + the known-ore set). */
   public static boolean isOreBlock(BlockState state) {
      if (state == null || state.isAir()) {
         return false;
      }
      if (state.is(BlockTags.IRON_ORES) || state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.COPPER_ORES)) {
         return true;
      }
      return KNOWN_ORES.contains(state.getBlock());
   }

   /**
    * True if {@code (level, pos)} is a PERMANENT hazard the miner must never breach or enter: lava (block or
    * fluid), a water source (block or fluid), or bedrock (unbreakable). These are detected on the live world; the
    * mine's marked-hazard set (driver-recorded once encountered) is layered on in {@link LevelMineView}.
    */
   public static boolean isHazardBlock(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      Block b = state.getBlock();
      if (b == Blocks.BEDROCK) {
         return true; // unbreakable — never minable, route around.
      }
      FluidState fluid = state.getFluidState();
      if (!fluid.isEmpty()) {
         // Any lava is a hazard; a SOURCE water block is a hazard (flowing water alone won't flood the tunnel as
         // catastrophically, but treating sources as hazards is the fail-safe the user asked for).
         if (fluid.is(net.minecraft.world.level.material.Fluids.LAVA)
            || fluid.is(net.minecraft.world.level.material.Fluids.FLOWING_LAVA)) {
            return true;
         }
         if (fluid.is(net.minecraft.world.level.material.Fluids.WATER) && fluid.isSource()) {
            return true;
         }
      }
      return false;
   }

   /**
    * Adapts a live {@code Level} + the mine's {@link TaskPointStore.MineState} to the engine's
    * {@link MillMiningOps.MineView}. Ore = {@link #isOreBlock}; hazard = a live lava/water/bedrock cell OR a cell
    * the mine has permanently marked; diggable = breakable non-air non-ore non-hazard rock; passable = air/fluid
    * the body can be in (but never a hazard for routing).
    */
   static final class LevelMineView implements MillMiningOps.MineView {
      private final Level level;
      private final TaskPointStore.MineState mine;

      LevelMineView(Level level, TaskPointStore.MineState mine) {
         this.level = level;
         this.mine = mine;
      }

      boolean isOreBlock(BlockState state) {
         return OreVeinMiner.isOreBlock(state);
      }

      @Override
      public boolean isOre(BlockPos pos) {
         return OreVeinMiner.isOreBlock(level.getBlockState(pos));
      }

      @Override
      public boolean isHazard(BlockPos pos) {
         return mine.isHazard(pos) || OreVeinMiner.isHazardBlock(level, pos);
      }

      @Override
      public boolean isDiggable(BlockPos pos) {
         if (isHazard(pos) || isOre(pos)) {
            return false;
         }
         BlockState state = level.getBlockState(pos);
         if (state.isAir()) {
            return false;
         }
         float hardness = state.getDestroySpeed(level, pos);
         return hardness >= 0.0f; // breakable rock/earth.
      }

      @Override
      public boolean isPassable(BlockPos pos) {
         if (isHazard(pos)) {
            return false;
         }
         BlockState state = level.getBlockState(pos);
         return state.isAir() || (!state.getFluidState().isEmpty() && !isHazard(pos))
            || state.getCollisionShape(level, pos).isEmpty();
      }
   }

   // ================================================================================================
   // observation
   // ================================================================================================

   /**
    * Emit a greppable mining-evidence line. Routed through {@link MillLog#major} with the SIM tag when the headless
    * observer is running (so {@code MILLENAIRE_SIM} shows the miner finding veins, tunnelling, flood-mining, using
    * caves, advancing the frontier, and NEVER breaching lava), and gated behind {@code LogMiner} otherwise.
    */
   static void logMine(MillVillager villager, BlockPos anchor, String msg) {
      String line = "███ SIM MINE villager='" + villager.firstName + " " + villager.familyName + "' anchor=" + anchor
         + " :: " + msg;
      if (com.coderyo.jason.sim.MillSimObserver.isEnabled()) {
         MillLog.major(null, line);
      } else if (org.millenaire.common.config.MillConfigValues.LogMiner >= 2) {
         MillLog.debug("OreVeinMiner", msg + " (anchor=" + anchor + ")");
      }
   }

   /** Forget a villager's mining navigator (call when the villager is removed) to avoid a small leak. */
   public static void forget(MillVillager villager) {
      NAVS.remove(villager.getId());
   }

   /**
    * A read-only {@link MillMiningOps.MineView} over the live {@code level} + the mine state at {@code anchor}, for
    * callers (the headless mining demonstration) that want to query ore/hazard classification or pre-scan ore the
    * same way {@link #mineTick} does. Reuses the exact real-world classification the driver uses.
    */
   public static MillMiningOps.MineView viewFor(Level level, BlockPos anchor) {
      return new LevelMineView(level, TaskPointStore.get().getOrCreateMine(level, anchor));
   }

   /** Exposed for the observer's summary: total ore mined + frontier advances across all live mines. */
   public static long totalOreMined(Level level, Set<BlockPos> anchors) {
      long total = 0;
      for (BlockPos a : anchors) {
         TaskPointStore.MineState m = TaskPointStore.get().peekMine(level, a);
         if (m != null) {
            total += m.oreMined;
         }
      }
      return total;
   }
}
