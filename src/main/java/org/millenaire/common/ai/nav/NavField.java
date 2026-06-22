package org.millenaire.common.ai.nav;

import net.minecraft.core.BlockPos;

/**
 * A cost-to-go (value) field toward a single goal — the shared product of one reverse-Dijkstra flood from the
 * goal. MANY villagers heading to the same destination read the same field at O(1) per agent: each just steps
 * down the gradient. This is the key to holding 20 TPS with hundreds of agents (one field, not N searches).
 */
public interface NavField {
   /** The goal this field flows toward. */
   BlockPos goal();

   /** Lexicographic cost-to-go from {@code pos} to the goal, or {@link LexCost#INFINITE} if unreachable here. */
   LexCost costToGo(BlockPos pos);

   /** The next standable cell to step to (the local gradient descent of the field), or null if none/at goal. */
   BlockPos nextStep(BlockPos pos);

   /** Whether {@code pos} is covered by this field (within the flooded region). */
   boolean covers(BlockPos pos);
}
