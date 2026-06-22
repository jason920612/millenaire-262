package org.millenaire.common.ai.nav;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Generates the G0 movement edges out of a standable cell and prices each as a {@link LexCost} — the single
 * place all "cost of moving" logic lives (replacing the per-behaviour, scattered heuristics). The scalar tier
 * folds distance + danger + fall-risk + traversal-effort + congestion, all in equivalent walk-blocks.
 *
 * <p>This is G0 ONLY: walk / step-up / jump / climb / swim / drop. It never emits place/break edges — those
 * belong to the separate {@link RepairPolicy}-gated G1 fallback, used only when G0 is infeasible.
 */
public interface EdgeCostProvider {
   /** Neighbouring standable cells reachable by one G0 movement from {@code from}. */
   List<BlockPos> neighbors(Level level, BlockPos from);

   /**
    * The lexicographic cost of the single G0 edge {@code from → to}. Returns {@link LexCost#INFINITE} if the
    * move is not actually traversable. Never returns a repair cost.
    */
   LexCost edgeCost(Level level, BlockPos from, BlockPos to);
}
