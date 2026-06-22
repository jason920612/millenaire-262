package org.millenaire.common.ai.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.utilities.Point;

/**
 * The hard anti-grief gate for last-resort G1 repair edges (place/break). A repair that fails this gate is
 * treated as a non-existent edge — terrain modification is a CONSTRAINT, never a cost the optimiser can pay
 * through. One instance per villager per escape episode tracks the footprint/rate limit.
 */
public final class MillRepairPolicy implements RepairPolicy {
   /** Max blocks a villager may modify in one escape episode (footprint cap). */
   private static final int MAX_MODS = 16;

   private final MillVillager villager;
   private int modsThisEpisode;

   public MillRepairPolicy(MillVillager villager) {
      this.villager = villager;
   }

   @Override
   public boolean mayBreak(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
         return false; // nothing there, or unbreakable (bedrock)
      }
      // Never touch a Mill building block (the "medium" anti-grief rule protects all village structures).
      Point p = new Point(pos.getX(), pos.getY(), pos.getZ());
      return this.villager.mw == null || this.villager.mw.getBuilding(p) == null;
   }

   @Override
   public boolean mayPlace(Level level, BlockPos pos) {
      // Only fill empty space, and not inside a building footprint.
      if (!level.getBlockState(pos).isAir()) {
         return false;
      }
      Point p = new Point(pos.getX(), pos.getY(), pos.getZ());
      return this.villager.mw == null || this.villager.mw.getBuilding(p) == null;
   }

   @Override
   public boolean budgetExhausted() {
      return this.modsThisEpisode >= MAX_MODS;
   }

   @Override
   public void noteModification() {
      this.modsThisEpisode++;
   }

   /** Reset the footprint counter when a new escape episode begins (villager freed / no longer trapped). */
   public void resetEpisode() {
      this.modsThisEpisode = 0;
   }
}
