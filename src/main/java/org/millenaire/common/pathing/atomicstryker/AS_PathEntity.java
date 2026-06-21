package org.millenaire.common.pathing.atomicstryker;

import net.minecraft.world.level.pathfinder.Node;

/**
 * Standalone path container used by Millénaire's villagers. In 1.12 this extended vanilla
 * {@code net.minecraft.pathfinding.Path}; in 26.2 vanilla {@code Path} is {@code final} and uses a
 * different node/index model, so Millénaire keeps its own path object built from {@link Node} waypoints
 * and tracks its own current index.
 */
public class AS_PathEntity {
   private long timeLastPathIncrement = 0L;
   public final Node[] pointsCopy;
   private int pathIndexCopy;

   public AS_PathEntity(Node[] points) {
      this.timeLastPathIncrement = System.currentTimeMillis();
      this.pointsCopy = points;
      this.pathIndexCopy = 0;
   }

   public void advancePathIndex() {
      this.timeLastPathIncrement = System.currentTimeMillis();
      this.pathIndexCopy++;
   }

   /** Number of nodes in this path (1.12 {@code getCurrentPathLength}). */
   public int getNodeCount() {
      return this.pointsCopy.length;
   }

   /** Last node of the path (1.12 {@code getFinalPathPoint}); null if empty. */
   public Node getEndNode() {
      return this.pointsCopy.length == 0 ? null : this.pointsCopy[this.pointsCopy.length - 1];
   }

   public int getCurrentPathIndex() {
      return this.pathIndexCopy;
   }

   public boolean isFinished() {
      return this.pathIndexCopy >= this.pointsCopy.length;
   }

   public Node getCurrentTargetPathPoint() {
      return this.isFinished() ? null : this.pointsCopy[this.getCurrentPathIndex()];
   }

   public Node getFuturePathPoint(int jump) {
      return this.getCurrentPathIndex() >= this.pointsCopy.length - jump ? null : this.pointsCopy[this.getCurrentPathIndex() + jump];
   }

   public Node getNextTargetPathPoint() {
      return this.getCurrentPathIndex() >= this.pointsCopy.length - 1 ? null : this.pointsCopy[this.getCurrentPathIndex() + 1];
   }

   public Node getPastTargetPathPoint(int jump) {
      return this.getCurrentPathIndex() >= jump && this.pointsCopy.length != 0 ? this.pointsCopy[this.getCurrentPathIndex() - jump] : null;
   }

   public Node getPreviousTargetPathPoint() {
      return this.getCurrentPathIndex() >= 1 && this.pointsCopy.length != 0 ? this.pointsCopy[this.getCurrentPathIndex() - 1] : null;
   }

   public long getTimeSinceLastPathIncrement() {
      return System.currentTimeMillis() - this.timeLastPathIncrement;
   }

   public void setCurrentPathIndex(int par1) {
      this.timeLastPathIncrement = System.currentTimeMillis();
      this.pathIndexCopy = par1;
   }
}
