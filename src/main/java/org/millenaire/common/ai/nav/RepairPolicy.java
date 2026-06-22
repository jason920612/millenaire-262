package org.millenaire.common.ai.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The HARD constraint that keeps last-resort block place/break from griefing. A repair (G1) edge that would
 * touch a disallowed block simply DOES NOT EXIST — this is a feasibility gate, not a cost penalty. Anti-grief
 * is thus a constraint, never something the optimiser can "pay through".
 */
public interface RepairPolicy {
   /** May the villager BREAK the block at {@code pos}? (Not protected/valuable/a building block; in-area.) */
   boolean mayBreak(Level level, BlockPos pos);

   /** May the villager PLACE a block at {@code pos}? (Empty, in an allowed area, within the footprint cap.) */
   boolean mayPlace(Level level, BlockPos pos);

   /** Have we already spent this villager's modification budget this episode (rate-limit / max footprint)? */
   boolean budgetExhausted();

   /** Record that one block modification was made (advances the rate-limit / footprint counter). */
   void noteModification();
}
