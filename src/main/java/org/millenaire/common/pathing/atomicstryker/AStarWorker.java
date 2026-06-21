package org.millenaire.common.pathing.atomicstryker;

import java.util.ArrayList;
import java.util.PriorityQueue;
import net.minecraft.world.level.Level;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.ThreadSafeUtilities;

// Runs an A* path search, either on a background thread (run) or synchronously (runSync). The open set
// is the priority queue (ordered by node f-cost); closedNodes holds positions already expanded. When a
// path is found the result is handed back to the planner ("boss"); otherwise onNoPathAvailable fires.
public class AStarWorker implements Runnable {
   private final long SEARCH_TIME_LIMIT = 150L; // ms budget per search before it is abandoned
   public AStarPathPlannerJPS boss;
   AStarConfig config;
   public boolean isRunning = false;
   public final ArrayList<AStarNode> closedNodes; // positions already expanded (the A* closed set)
   private AStarNode startNode;
   protected AStarNode targetNode;
   protected Level world;
   private long timeLimit; // wall-clock time at which the current search must give up
   private final PriorityQueue<AStarNode> queue; // the A* open set, ordered by node f-cost
   private boolean isBusy = false;

   public AStarWorker() {
      this.boss = null;
      this.closedNodes = new ArrayList<>();
      this.queue = new PriorityQueue<>(500);
   }

   public AStarWorker(AStarPathPlannerJPS creator) {
      this.boss = creator;
      this.closedNodes = new ArrayList<>();
      this.queue = new PriorityQueue<>(500);
   }

   private void addToBinaryHeap(AStarNode node) {
      this.queue.offer(node);
   }

   // When standing on a ladder block, also offer the positions directly above and below as candidates
   // so the search can climb. (The empty isLadder if-blocks are inert leftovers from the original code.)
   private void checkPossibleLadder(AStarNode node) throws ThreadSafeUtilities.ChunkAccessException {
      int x = node.x;
      int y = node.y;
      int z = node.z;
      if (AStarStatic.isLadder(this.world, ThreadSafeUtilities.getBlock(this.world, x, y, z), x, y, z)) {
         AStarNode ladder = null;
         if (AStarStatic.isLadder(this.world, ThreadSafeUtilities.getBlock(this.world, x, y + 1, z), x, y + 1, z)) {
         }

         // Candidate: one block up the ladder.
         ladder = new AStarNode(x, y + 1, z, node.getG() + 2, node, this.targetNode);
         if (!this.tryToUpdateExistingHeapNode(node, ladder)) {
            this.addToBinaryHeap(ladder);
         }

         if (AStarStatic.isLadder(this.world, ThreadSafeUtilities.getBlock(this.world, x, y - 1, z), x, y - 1, z)) {
         }

         // Candidate: one block down the ladder.
         ladder = new AStarNode(x, y - 1, z, node.getG() + 2, node, this.targetNode);
         if (!this.tryToUpdateExistingHeapNode(node, ladder)) {
            this.addToBinaryHeap(ladder);
         }
      }
   }

   // Manhattan distance between two nodes, used as the step cost when relaxing closed nodes.
   private int getCostNodeToNode(AStarNode a, AStarNode b) {
      return Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z);
   }

   // Expand a node: generate every neighbour reachable in one step (using the relative offset table)
   // and feed each viable one into the open queue, while keeping costs of already-seen nodes up to date.
   public void getNextCandidates(AStarNode parent, boolean droppingAllowed) throws ThreadSafeUtilities.ChunkAccessException {
      int x = parent.x;
      int y = parent.y;
      int z = parent.z;
      // Each offset row is {dx, dy, dz, stepCost}; the "allow drops" table additionally permits falling down.
      int[][] offsets = droppingAllowed ? AStarStatic.candidates_allowdrops : AStarStatic.candidates;

      for (int i = 0; i < offsets.length; i++) {
         AStarNode neighbour = new AStarNode(x + offsets[i][0], y + offsets[i][1], z + offsets[i][2], parent.getG() + offsets[i][3], parent, this.targetNode);

         try {
            boolean found = false;

            // If we have already closed this position, just relax its cost via the current parent.
            for (AStarNode closed : this.closedNodes) {
               if (neighbour.equals(closed)) {
                  closed.updateDistance(neighbour.getG() + this.getCostNodeToNode(closed, neighbour), parent);
                  found = true;
                  break;
               }
            }

            // Otherwise, unless it already sits in the open queue, queue it when the move is viable.
            if (!found && !this.tryToUpdateExistingHeapNode(parent, neighbour) && AStarStatic.isViable(this.world, neighbour, offsets[i][1], this.config)) {
               this.addToBinaryHeap(neighbour);
            }
         } catch (Exception accessFailure) {
            if (MillConfigValues.LogChunkLoader >= 2) {
               MillLog.minor(this, accessFailure.getLocalizedMessage());
            }
         }
      }
   }

   // Core A* search: repeatedly take the cheapest open node, close it, and expand its neighbours until
   // the target is reached. Returns null if the queue drains (no path) or the time budget runs out.
   public ArrayList<AStarNode> getPath(AStarNode start, AStarNode end, boolean searchMode) throws ThreadSafeUtilities.ChunkAccessException {
      this.queue.offer(start);
      this.targetNode = end;

      AStarNode current;
      for (current = start; !this.isNodeEnd(current, end); current = this.queue.peek()) {
         this.closedNodes.add(this.queue.poll());
         this.getNextCandidates(current, searchMode);
         if (this.queue.isEmpty() || this.shouldInterrupt()) {
            return null;
         }
      }

      // Reconstruct the path by walking parent links back from the end node to the start.
      ArrayList<AStarNode> foundpath = new ArrayList<>();
      foundpath.add(current);

      while (current != start) {
         foundpath.add(current.parent);
         current = current.parent;
      }

      return foundpath;
   }

   public boolean isBusy() {
      return this.isBusy;
   }

   // Whether the coordinates count as "reached the goal". With tolerance enabled, arriving within the
   // configured horizontal/vertical slack is enough; otherwise the position must match exactly.
   protected boolean isCoordsEnd(int x, int y, int z, AStarNode end) {
      return this.config.tolerance
         ? Math.abs(x - end.x) <= this.config.toleranceHorizontal
            && Math.abs(z - end.z) <= this.config.toleranceHorizontal
            && Math.abs(y - end.y) <= this.config.toleranceVertical
         : x == end.x && y == end.y && z == end.z;
   }

   protected boolean isNodeEnd(AStarNode cn, AStarNode end) {
      return this.isCoordsEnd(cn.x, cn.y, cn.z, end);
   }

   @Override
   public void run() {
      this.isBusy = true;
      this.timeLimit = System.currentTimeMillis() + 150L;
      ArrayList<AStarNode> result = null;

      try {
         result = this.getPath(this.startNode, this.targetNode, this.config.allowDropping);
      } catch (ThreadSafeUtilities.ChunkAccessException chunkAccessFailure) {
         MillLog.error(this, "LevelChunk access violation while calculating a path for " + this.boss);
         this.boss.onNoPathAvailable();
      } catch (Throwable pathFailure) {
         MillLog.printException("Exception while calculating a path:", pathFailure);
         this.boss.onNoPathAvailable();
      }

      if (result == null) {
         this.boss.onNoPathAvailable();
      } else {
         this.boss.onFoundPath(result);
      }

      this.isBusy = false;
   }

   public ArrayList<AStarNode> runSync() {
      this.timeLimit = System.currentTimeMillis() + 150L;
      ArrayList<AStarNode> result = null;

      try {
         return this.getPath(this.startNode, this.targetNode, this.config.allowDropping);
      } catch (ThreadSafeUtilities.ChunkAccessException chunkAccessFailure) {
         MillLog.error(this, "LevelChunk access violation while calculating a path for " + this.boss);
         return null;
      } catch (Throwable pathFailure) {
         MillLog.printException("Exception while calculating a path:", pathFailure);
         return null;
      }
   }

   public void setup(Level winput, AStarNode start, AStarNode end, AStarConfig config) {
      this.world = winput;
      this.startNode = start;
      this.targetNode = end;
      this.config = config;
   }

   protected boolean shouldInterrupt() {
      return System.currentTimeMillis() > this.timeLimit;
   }

   // If the candidate position is already waiting in the open queue, relax its cost through the given
   // parent and report that it was handled (true), so the caller does not add a duplicate.
   private boolean tryToUpdateExistingHeapNode(AStarNode parent, AStarNode checkedOne) {
      for (AStarNode queued : this.queue) {
         if (queued.equals(checkedOne)) {
            queued.updateDistance(checkedOne.getG(), parent);
            return true;
         }
      }

      return false;
   }
}
