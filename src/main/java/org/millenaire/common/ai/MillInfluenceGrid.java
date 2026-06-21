package org.millenaire.common.ai;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

/**
 * Per-village danger / influence field — the single shared structure behind safety-aware pathfinding
 * (avoid high-danger cells) and tactical combat positioning (find safe attack spots) and ally cooperation
 * (a shared threat picture). One value per x-z column over the village footprint (+margin).
 *
 * <p>Mill-AI rewrite, layer "threat model". The grid is REBUILT on the server thread from the village's
 * nearby hostiles; the planner's async worker reads an immutable snapshot, so there is no shared mutable
 * state across threads. Danger from each hostile decays linearly with horizontal distance out to its
 * influence radius; cells stack danger from all hostiles (clamped). Higher danger = stay away.
 */
public final class MillInfluenceGrid {
   /** Default per-hostile influence radius (blocks) — beyond this a hostile contributes no danger. */
   public static final int DEFAULT_RADIUS = 12;
   /** Danger contributed by a hostile at distance 0 (decays to 0 at the radius edge). */
   public static final float PEAK_DANGER = 16.0F;

   private final int originX;
   private final int originZ;
   private final int width;
   private final int depth;
   /** Row-major danger field, indexed by (z - originZ) * width + (x - originX). Immutable after build. */
   private final float[] danger;

   private MillInfluenceGrid(int originX, int originZ, int width, int depth, float[] danger) {
      this.originX = originX;
      this.originZ = originZ;
      this.width = width;
      this.depth = depth;
      this.danger = danger;
   }

   /**
    * Build a snapshot covering [minX..maxX] x [minZ..maxZ] (inclusive, plus the hostile radius as margin),
    * stamping decaying danger from every hostile in {@code hostiles}. Pure / thread-safe once returned.
    */
   public static MillInfluenceGrid build(int minX, int minZ, int maxX, int maxZ, List<? extends LivingEntity> hostiles, int radius) {
      int ox = minX - radius;
      int oz = minZ - radius;
      int w = (maxX + radius) - ox + 1;
      int d = (maxZ + radius) - oz + 1;
      float[] grid = new float[Math.max(1, w * d)];
      for (LivingEntity hostile : hostiles) {
         stamp(grid, ox, oz, w, d, hostile.getBlockX(), hostile.getBlockZ(), radius);
      }
      return new MillInfluenceGrid(ox, oz, w, d, grid);
   }

   /** Empty grid (no danger anywhere) — used before the first build / when there are no hostiles. */
   public static MillInfluenceGrid empty() {
      return new MillInfluenceGrid(0, 0, 0, 0, new float[0]);
   }

   private static void stamp(float[] grid, int ox, int oz, int w, int d, int hx, int hz, int radius) {
      int r2 = radius * radius;
      for (int dz = -radius; dz <= radius; dz++) {
         int z = hz + dz;
         int gz = z - oz;
         if (gz < 0 || gz >= d) {
            continue;
         }
         for (int dx = -radius; dx <= radius; dx++) {
            int distSq = dx * dx + dz * dz;
            if (distSq > r2) {
               continue;
            }
            int x = hx + dx;
            int gx = x - ox;
            if (gx < 0 || gx >= w) {
               continue;
            }
            float dist = (float) Math.sqrt(distSq);
            float add = PEAK_DANGER * (1.0F - dist / radius); // linear falloff, 0 at the edge
            int idx = gz * w + gx;
            grid[idx] = Math.min(PEAK_DANGER, grid[idx] + add);
         }
      }
   }

   /** Danger at a world column (0 = safe). Out-of-bounds columns are treated as safe (0). */
   public float dangerAt(int worldX, int worldZ) {
      int gx = worldX - originX;
      int gz = worldZ - originZ;
      if (gx < 0 || gx >= width || gz < 0 || gz >= depth) {
         return 0.0F;
      }
      return danger[gz * width + gx];
   }

   public float dangerAt(BlockPos pos) {
      return dangerAt(pos.getX(), pos.getZ());
   }

   /** A column is "safe" for idle wandering / safe routing when its danger is below the threshold. */
   public boolean isSafe(int worldX, int worldZ, float threshold) {
      return dangerAt(worldX, worldZ) < threshold;
   }
}
