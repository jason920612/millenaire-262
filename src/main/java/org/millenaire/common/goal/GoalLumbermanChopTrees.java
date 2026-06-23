package org.millenaire.common.goal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import com.coderyo.jason.ops.OpState;
import com.coderyo.jason.ops.VillagerWorldOps;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.pathing.atomicstryker.AStarConfig;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;

@DocumentedElement.Documentation("Go chop trees in a grove.")
public class GoalLumbermanChopTrees extends Goal {
   /** Connected-tree search bounds, mirroring the 1.12 scan box (±3 horizontally, a tall vertical column). */
   private static final int TREE_RADIUS_H = 3;
   private static final int TREE_HEIGHT_UP = 16;
   private static final int TREE_HEIGHT_DOWN = 3;
   /** Hard cap on logs+leaves flooded for one tree, so a giant jungle never runs the flood unbounded. */
   private static final int FLOOD_CAP = 2048;

   public GoalLumbermanChopTrees() {
      this.maxSimultaneousInBuilding = 1;
      this.townhallLimit.put(InvItem.createInvItem(Blocks.OAK_LOG, -1), 4096);
      this.icon = InvItem.createInvItem(Items.IRON_AXE);
   }

   @Override
   public int actionDuration(MillVillager villager) {
      // Player-like chop is driven per-tick by performAction (ensureTool → per-log reach/scaffold → break-over-time
      // → pickup → clear leaves → reclaim scaffold). The real duration emerges from that cycle, so re-enter every
      // tick (1) rather than the 1.12 fixed countdown that instant-set blocks to air.
      return 1;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      List<Point> woodPos = new ArrayList<>();
      List<Point> buildingp = new ArrayList<>();

      for (Building grove : villager.getTownHall().getBuildingsWithTag("grove")) {
         if (grove.getWoodCount() > 4) {
            Point p = grove.getWoodLocation();
            if (p != null) {
               woodPos.add(p);
               buildingp.add(grove.getPos());
               if (MillConfigValues.LogLumberman >= 3) {
                  MillLog.debug(this, "Found location in grove: " + p + ". Targeted block: " + p.getBlock(villager.level()));
               }
            }
         }
      }

      if (woodPos.isEmpty()) {
         return null;
      } else {
         Point p = woodPos.get(0);
         Point buildingP = buildingp.get(0);

         for (int i = 1; i < woodPos.size(); i++) {
            if (woodPos.get(i).horizontalDistanceToSquared(villager) < p.horizontalDistanceToSquared(villager)) {
               p = woodPos.get(i);
               buildingP = buildingp.get(i);
            }
         }

         if (MillConfigValues.LogLumberman >= 3) {
            MillLog.debug(this, "Going to gather wood around: " + p + ". Targeted block: " + p.getBlock(villager.level()));
         }

         return this.packDest(p, buildingP);
      }
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) {
      return villager.getBestAxeStack();
   }

   @Override
   public AStarConfig getPathingConfig(MillVillager villager) {
      return !villager.canVillagerClearLeaves() ? JPS_CONFIG_CHOPLUMBER_NO_LEAVES : JPS_CONFIG_CHOPLUMBER;
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) {
      return villager.countInv(Blocks.OAK_LOG, -1) > 64 ? false : this.getDestination(villager) != null;
   }

   @Override
   public boolean lookAtGoal() {
      return true;
   }

   /**
    * Player-like chop cycle, driven one tick at a time (actionDuration == 1). Fells the WHOLE tree — every connected
    * log — and clears the leaves, returning {@code false} while mid-cycle (goal stays in action) and {@code true}
    * once the tree is gone (goal advances).
    *
    * <p>Cycle (the user's decisions):
    * <ol>
    *   <li>{@code ensureTool(AXE)} — strict: no axe ⇒ wait for {@code GoalGetTool} (do not fake-yield).</li>
    *   <li>Enumerate the connected tree at the dest point (all logs + their leaves), like 1.12's grove scan.</li>
    *   <li>For each LOG, lowest-first: {@code ensureReach} (scaffold up for tall trees, out of reach) →
    *       {@code breakTick} until it really breaks over time → {@code pickupTick} the real drops.</li>
    *   <li>Then clear the LEAVES (break them; pick up any sapling/apple/stick drops).</li>
    *   <li>Reclaim the scaffold column at the end so no temporary blocks are left behind.</li>
    * </ol>
    *
    * <p>Each tick recomputes the remaining logs/leaves from the WORLD (the broken ones are now air), so progress is
    * point-owned and restartable — a different villager can take over and continue felling the same tree.
    *
    * <p>1.12 fidelity: 1.12 faked the break ({@code setBlock(AIR) + addToInv}) and granted a small chance of a
    * SAPLING per leaf. The real break now drops the vanilla sapling/apple/stick the villager walks to and collects,
    * which is the same economic intent (saplings feed the separate replant goal); no fixed 1.12 yield is kept.
    */
   @Override
   public boolean performAction(MillVillager villager) throws Exception {
      Point destPoint = villager.getGoalDestPoint();
      if (destPoint == null) {
         return true; // no target — let the goal re-pick.
      }
      Level level = villager.level();
      BlockPos dest = destPoint.getBlockPos();

      // Strict axe: no drops without it. Stay in-goal (return false) so GoalGetTool can pre-empt and fetch one.
      if (!VillagerWorldOps.ensureTool(villager, VillagerWorldOps.ToolKind.AXE)) {
         if (MillConfigValues.LogLumberman >= 2) {
            MillLog.debug(this, "No axe to chop at " + destPoint + "; waiting for tool.");
         }
         return false;
      }

      // Enumerate the whole connected tree from the world each tick (broken blocks are now air, so they drop out).
      TreeBlocks tree = floodTree(level, dest, villager.canVillagerClearLeaves());

      // No logs left → the trunk is felled. Clear any remaining leaves, then reclaim the scaffold and finish.
      if (tree.logs.isEmpty()) {
         if (!tree.leaves.isEmpty()) {
            // Process the nearest remaining leaf this tick (break + pickup) — same per-tick primitive as logs.
            return chopOne(villager, dest, nearest(villager, tree.leaves), false);
         }
         // Whole tree (logs + leaves) gone: reclaim the climb column and let the goal advance.
         VillagerWorldOps.reclaimReach(villager, dest);
         if (MillConfigValues.LogLumberman >= 3) {
            MillLog.debug(this, "Tree felled + leaves cleared around " + dest + "; scaffold reclaimed.");
         }
         return true;
      }

      // Fell logs lowest-first (so the column we climb stays supported and we don't strand upper logs).
      BlockPos log = lowest(tree.logs);
      return chopOne(villager, dest, log, true);
   }

   /**
    * Drive one block (log or leaf) through the player-like sub-cycle this tick: reach-extend (scaffold for high
    * logs) → break-over-time → pickup. Returns {@code false} (stay in action) until this block is fully broken and
    * its drops collected, since the surrounding {@link #performAction} loop re-selects the next block next tick.
    */
   private boolean chopOne(MillVillager villager, BlockPos reachAnchor, BlockPos target, boolean isLog) {
      if (target == null) {
         return false;
      }
      // Reach-extend against the LOG/leaf itself (high trunk needs a scaffold column). Anchor the point-owned
      // scaffold tracking on the goal's dest so the whole tree shares (and reclaims) one column.
      if (!VillagerWorldOps.withinReach(villager, target)) {
         // Track the climb column on the goal's dest (reachAnchor) so the whole tree shares ONE column that is
         // reclaimed once at the end — not a separate column per log.
         OpState reach = VillagerWorldOps.ensureReach(villager, target, reachAnchor);
         switch (reach) {
            case COMPLETE:
               break; // now in reach — fall through to break.
            case EXTENDING_REACH:
               return false; // building/climbing the column; keep going next tick.
            case BLOCKED:
            default:
               // Can't reach even with a column (shouldn't happen for a tree) — skip via approach so the goal can
               // re-path; returning false keeps the goal active without spinning on an impossible block.
               if (MillConfigValues.LogLumberman >= 2) {
                  MillLog.debug(this, "Cannot reach " + (isLog ? "log" : "leaf") + " at " + target + "; approaching.");
               }
               return false;
         }
      }

      OpState st = VillagerWorldOps.breakTick(villager, target);
      switch (st) {
         case APPROACHING:
         case EXTENDING_REACH:
         case IN_PROGRESS:
            return false; // keep breaking / walking closer next tick.
         case BLOCKED:
            return false; // unbreakable (not expected for logs/leaves) — skip without faking a yield.
         case COMPLETE:
            // Block just broke; walk to + collect its real drops before moving on.
            return runPickup(villager, target);
         default:
            return false;
      }
   }

   /** Walk-to-each-drop pickup at {@code pos}; {@code false} while collecting, {@code false}→loop continues. */
   private boolean runPickup(MillVillager villager, BlockPos pos) {
      OpState pst = VillagerWorldOps.pickupTick(villager, pos);
      // Whether still picking up or done, we return false so performAction re-enters and re-selects the next
      // log/leaf next tick (the just-broken block is now air and drops out of the flood automatically).
      return false;
   }

   // ---- whole-tree enumeration (connected logs + their leaves), faithful to the 1.12 grove scan ----

   /** All connected logs (and the leaves to clear) of the tree rooted near {@code dest}. */
   private static final class TreeBlocks {
      final List<BlockPos> logs = new ArrayList<>();
      final List<BlockPos> leaves = new ArrayList<>();
   }

   /**
    * Flood-fill the connected tree: starting from the log(s) nearest {@code dest}, follow adjacent (incl. diagonal)
    * logs to collect EVERY log of the tree, and gather the leaves touching those logs (only if the villager clears
    * leaves). Bounded by {@link #TREE_RADIUS_H}/{@link #TREE_HEIGHT_UP}/{@link #FLOOD_CAP} so it mirrors 1.12's box
    * scan and never runs away on a jungle canopy.
    */
   private static TreeBlocks floodTree(Level level, BlockPos dest, boolean clearLeaves) {
      TreeBlocks out = new TreeBlocks();
      Set<Long> seen = new HashSet<>();
      Deque<BlockPos> frontier = new ArrayDeque<>();

      // Seed: scan the 1.12-style box for logs and queue them (handles the dest being a leaf or just-off a trunk).
      for (int dy = TREE_HEIGHT_UP; dy >= -TREE_HEIGHT_DOWN; dy--) {
         for (int dx = -TREE_RADIUS_H; dx <= TREE_RADIUS_H; dx++) {
            for (int dz = -TREE_RADIUS_H; dz <= TREE_RADIUS_H; dz++) {
               BlockPos p = dest.offset(dx, dy, dz);
               if (isLog(level.getBlockState(p).getBlock()) && seen.add(p.asLong())) {
                  frontier.add(p);
               }
            }
         }
      }

      // Flood the connected logs (26-neighbourhood), collecting leaves adjacent to each log.
      Set<Long> leafSeen = new HashSet<>();
      while (!frontier.isEmpty() && out.logs.size() + out.leaves.size() < FLOOD_CAP) {
         BlockPos log = frontier.poll();
         out.logs.add(log);
         for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
               for (int dz = -1; dz <= 1; dz++) {
                  if (dx == 0 && dy == 0 && dz == 0) {
                     continue;
                  }
                  BlockPos n = log.offset(dx, dy, dz);
                  Block b = level.getBlockState(n).getBlock();
                  if (isLog(b)) {
                     if (seen.add(n.asLong())) {
                        frontier.add(n);
                     }
                  } else if (clearLeaves && isLeaf(b) && leafSeen.add(n.asLong())) {
                     out.leaves.add(n);
                  }
               }
            }
         }
      }
      return out;
   }

   private static boolean isLog(Block b) {
      return b == Blocks.OAK_LOG || b == Blocks.ACACIA_LOG || b == Blocks.SPRUCE_LOG || b == Blocks.BIRCH_LOG
         || b == Blocks.JUNGLE_LOG || b == Blocks.DARK_OAK_LOG;
   }

   private static boolean isLeaf(Block b) {
      return b == Blocks.OAK_LEAVES || b == Blocks.ACACIA_LEAVES || b == Blocks.SPRUCE_LEAVES
         || b == Blocks.BIRCH_LEAVES || b == Blocks.JUNGLE_LEAVES || b == Blocks.DARK_OAK_LEAVES;
   }

   /** The lowest block in the list (fell logs lowest-first so the climb column stays supported). */
   private static BlockPos lowest(List<BlockPos> blocks) {
      BlockPos best = blocks.get(0);
      for (BlockPos p : blocks) {
         if (p.getY() < best.getY()) {
            best = p;
         }
      }
      return best;
   }

   /** The block nearest the villager (used to clear leaves in a natural walk order). */
   private static BlockPos nearest(MillVillager villager, List<BlockPos> blocks) {
      BlockPos best = null;
      double bestSqr = Double.MAX_VALUE;
      for (BlockPos p : blocks) {
         double d = villager.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
         if (d < bestSqr) {
            bestSqr = d;
            best = p;
         }
      }
      return best;
   }

   @Override
   public int priority(MillVillager villager) {
      return Math.max(10, 125 - villager.countInv(Blocks.OAK_LOG, -1));
   }

   @Override
   public int range(MillVillager villager) {
      return 8;
   }

   @Override
   public boolean stuckAction(MillVillager villager) throws Exception {
      return this.performAction(villager);
   }

   @Override
   public boolean swingArms() {
      return true;
   }
}
