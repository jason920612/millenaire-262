package org.millenaire.common.pathing.atomicstryker;

// A single node in the A* search graph: a world block position plus the standard A* cost values.
//   g = cost of the path from the start node to this node (accumulated movement distance)
//   h = heuristic estimate of the remaining cost from this node to the target
//   f = g + h, the value used to order nodes in the open priority queue
public class AStarNode implements Comparable {
   public final int x;
   public final int y;
   public final int z;
   final AStarNode target;
   public AStarNode parent;
   private int g;
   private double h;

   public AStarNode(int blockX, int blockY, int blockZ) {
      this.x = blockX;
      this.y = blockY;
      this.z = blockZ;
      this.g = 0;
      this.parent = null;
      this.target = null;
   }

   public AStarNode(int blockX, int blockY, int blockZ, int gCost, AStarNode parentNode) {
      this.x = blockX;
      this.y = blockY;
      this.z = blockZ;
      this.g = gCost;
      this.parent = parentNode;
      this.target = null;
   }

   public AStarNode(int blockX, int blockY, int blockZ, int gCost, AStarNode parentNode, AStarNode targetNode) {
      this.x = blockX;
      this.y = blockY;
      this.z = blockZ;
      this.g = gCost;
      this.parent = parentNode;
      this.target = targetNode;
      this.updateTargetCostEstimate();
   }

   public AStarNode clone() {
      return new AStarNode(this.x, this.y, this.z, this.g, this.parent);
   }

   // Ordering used by the open priority queue: nodes with the lowest f-cost come first.
   @Override
   public int compareTo(Object o) {
      if (o instanceof AStarNode) {
         AStarNode other = (AStarNode)o;
         if (this.getF() < other.getF()) {
            return -1;
         }

         if (this.getF() > other.getF()) {
            return 1;
         }
      }

      return 0;
   }

   // Two nodes are considered equal when they refer to the same block position (cost is ignored),
   // so the search can recognise when it reaches an already-discovered position.
   @Override
   public boolean equals(Object checkagainst) {
      if (checkagainst instanceof AStarNode) {
         AStarNode check = (AStarNode)checkagainst;
         if (check.x == this.x && check.y == this.y && check.z == this.z) {
            return true;
         }
      }

      return false;
   }

   public double getF() {
      return this.g + this.h;
   }

   public int getG() {
      return this.g;
   }

   @Override
   public int hashCode() {
      return this.x << 16 ^ this.z ^ this.y << 24;
   }

   @Override
   public String toString() {
      return this.parent == null
         ? String.format("[%d|%d|%d], dist %d, F: %f", this.x, this.y, this.z, this.g, this.getF())
         : String.format(
            "[%d|%d|%d], dist %d, parent [%d|%d|%d], F: %f", this.x, this.y, this.z, this.g, this.parent.x, this.parent.y, this.parent.z, this.getF()
         );
   }

   // If a cheaper route to this position has been found, adopt it (lower g-cost and the new parent)
   // and refresh the heuristic. Returns true when an improvement was applied.
   public boolean updateDistance(int checkingDistance, AStarNode parentOtherNode) {
      if (checkingDistance < this.g) {
         this.g = checkingDistance;
         this.parent = parentOtherNode;
         this.updateTargetCostEstimate();
         return true;
      } else {
         return false;
      }
   }

   // Recompute the heuristic h: straight-line distance to the target, weighted x10 to bias the
   // search towards the goal. With no target set the node has no heuristic (h = 0).
   private void updateTargetCostEstimate() {
      if (this.target != null) {
         this.h = this.g + AStarStatic.getDistanceBetweenNodes(this, this.target) * 10.0;
      } else {
         this.h = 0.0;
      }
   }
}
