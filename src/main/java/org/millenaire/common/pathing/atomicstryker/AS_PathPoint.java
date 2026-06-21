package org.millenaire.common.pathing.atomicstryker;

/**
 * Standalone path-point data holder. In 1.12 this extended {@code net.minecraft.pathfinding.PathPoint}
 * and poked private fields via reflection. In 26.2 the vanilla {@code Node} fields are different and the
 * vanilla {@code Path} is final, so Millénaire keeps its own lightweight point type with plain fields.
 */
public class AS_PathPoint {
   public final int x;
   public final int y;
   public final int z;
   private int index;
   private float totalPathDistance;
   private float distanceToNext;
   private float distanceToTarget;
   private AS_PathPoint previous;

   public AS_PathPoint(int par1, int par2, int par3) {
      this.x = par1;
      this.y = par2;
      this.z = par3;
   }

   public int getIndex() {
      return this.index;
   }

   public float getTotalPathDistance() {
      return this.totalPathDistance;
   }

   public float getDistanceToNext() {
      return this.distanceToNext;
   }

   public float getDistanceToTarget() {
      return this.distanceToTarget;
   }

   public AS_PathPoint getPrevious() {
      return this.previous;
   }

   public void setDistanceToNext(float f) {
      this.distanceToNext = f;
   }

   public void setDistanceToTarget(float f) {
      this.distanceToTarget = f;
   }

   public void setIndex(int i) {
      this.index = i;
   }

   public void setPrevious(AS_PathPoint pp) {
      this.previous = pp;
   }

   public void setTotalPathDistance(float f) {
      this.totalPathDistance = f;
   }
}
