package org.millenaire.common.ai;

import org.millenaire.common.entity.MillVillager;

/**
 * One composable unit of villager behaviour in the Mill-AI rewrite (decision layer). The {@link MillAI}
 * engine ticks each villager, picks the highest-priority behaviour whose {@link #canRun} holds, and runs its
 * {@link #tick} until it finishes or is preempted. Behaviours are small and self-contained (GoTo, Work,
 * Gather, Sleep, Wander, Combat …); group/tactical behaviour emerges from each unit reading the shared
 * {@link MillInfluenceGrid} rather than from a central controller.
 *
 * <p>All behaviours are scoped to Mill villagers only (req 7) — they never touch other entities' AI.
 */
public interface MillBehaviour {

   /** Preconditions: can this behaviour start / keep running for this villager right now? */
   boolean canRun(MillVillager villager);

   /**
    * Selection score — higher wins when several behaviours can run. Combat/defence outrank work; work
    * outranks idle wander. A running behaviour is only preempted by a strictly higher priority.
    */
   int priority(MillVillager villager);

   /**
    * Advance this behaviour one tick. Return {@code true} to keep running, {@code false} when finished
    * (so the engine re-selects). Must NEVER teleport — request movement through the navigation and fail
    * gracefully (return false) if a target is unreachable.
    */
   boolean tick(MillVillager villager);

   /** Called once when this becomes the active behaviour. */
   default void onStart(MillVillager villager) {
   }

   /** Called once when this stops being active (finished or preempted). */
   default void onStop(MillVillager villager) {
   }

   /** Stable id for logging/debug. */
   default String id() {
      return this.getClass().getSimpleName();
   }
}
