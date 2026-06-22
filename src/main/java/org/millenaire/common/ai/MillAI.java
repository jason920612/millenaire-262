package org.millenaire.common.ai;

import java.util.List;

import org.millenaire.common.entity.MillVillager;

/**
 * Per-villager decision engine for the Mill-AI rewrite (decision layer, custom lightweight behaviour engine
 * — the locked architecture choice). Each tick it picks the highest-priority {@link MillBehaviour} whose
 * preconditions hold and runs it; a running behaviour is only preempted by a STRICTLY higher priority, which
 * avoids per-tick thrashing. No vanilla Brain, no global hooks — one of these lives on each Mill villager.
 */
public final class MillAI {
   private final List<MillBehaviour> behaviours;
   private MillBehaviour active;

   public MillAI(List<MillBehaviour> behaviours) {
      this.behaviours = behaviours;
   }

   public MillBehaviour active() {
      return this.active;
   }

   /** Run one decision tick for {@code villager}. The villager AI fails LOUD — a behaviour error is reported
    *  prominently (with the active behaviour + stacktrace) and re-thrown, never silently swallowed, so AI bugs
    *  surface immediately instead of degrading into "villager quietly does nothing". */
   public void tick(MillVillager villager) {
      try {
         tickInternal(villager);
      } catch (Throwable t) {
         org.millenaire.common.utilities.MillLog.printException(
            "███ VILLAGER-AI ERROR ███ active=" + (this.active == null ? "none" : this.active.id()) + " on " + villager, t);
         throw t instanceof RuntimeException re ? re : new RuntimeException("Villager AI failed", t);
      }
   }

   private void tickInternal(MillVillager villager) {
      // 1. Find the best currently-runnable behaviour and its score.
      MillBehaviour best = null;
      int bestPriority = Integer.MIN_VALUE;
      for (int i = 0; i < this.behaviours.size(); i++) {
         MillBehaviour b = this.behaviours.get(i);
         if (b.canRun(villager)) {
            int p = b.priority(villager);
            if (p > bestPriority) {
               bestPriority = p;
               best = b;
            }
         }
      }

      // 2. Switch to it if there is no active behaviour, the active one can no longer run, or the best
      //    candidate is strictly higher priority than the active one (preemption without thrashing).
      if (this.active != best) {
         boolean activeStillValid = this.active != null && this.active.canRun(villager);
         boolean preempt = !activeStillValid || (best != null && bestPriority > this.active.priority(villager));
         if (preempt) {
            if (this.active != null) {
               this.active.onStop(villager);
            }
            this.active = best;
            if (this.active != null) {
               this.active.onStart(villager);
            }
         }
      }

      // 3. Advance the active behaviour; if it reports done, drop it so the next tick re-selects.
      if (this.active != null) {
         boolean keepRunning = this.active.tick(villager);
         if (!keepRunning) {
            this.active.onStop(villager);
            this.active = null;
         }
      }
   }
}
