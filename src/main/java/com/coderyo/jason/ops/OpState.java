package com.coderyo.jason.ops;

/**
 * The result of advancing a player-like world operation by one tick (see {@link VillagerWorldOps}).
 *
 * <p>The operation primitives are <em>driven per-tick</em> by a {@link org.millenaire.common.goal.Goal}: the goal
 * keeps calling the op (e.g. {@code breakTick}) and inspects the returned state to decide what to do next, rather
 * than the op blocking. The states form the contract between a goal and the op layer:
 *
 * <ul>
 *   <li>{@link #APPROACHING} — the worksite is out of player reach (~4.5 blocks). The goal must walk the villager
 *       closer before the op can make progress. No progress was made this tick.</li>
 *   <li>{@link #EXTENDING_REACH} — the target is reachable only by climbing/scaffolding (upper tree logs, high
 *       build rows). The goal should run the reach-extension step ({@code ensureReach}) — scaffold-first. (Scaffold
 *       logic itself is later phases; the state exists so goals can branch on it now.)</li>
 *   <li>{@link #IN_PROGRESS} — the op advanced this tick but is not finished (e.g. break progress {@code < 1.0}).
 *       The goal should keep calling.</li>
 *   <li>{@link #PICKING_UP} — the destructive part is done; real {@link net.minecraft.world.entity.item.ItemEntity}
 *       drops are on the ground and the villager must now walk to each and collect it ({@code pickupTick}).</li>
 *   <li>{@link #COMPLETE} — the op (and any pickup) is finished. The point's progress record has been cleared. The
 *       goal can advance.</li>
 *   <li>{@link #BLOCKED} — the op cannot proceed at all (e.g. an unbreakable block, hardness &lt; 0). Fail-fast for
 *       the goal to abandon/replan; the op made no progress and will not on retry.</li>
 * </ul>
 */
public enum OpState {
   APPROACHING,
   EXTENDING_REACH,
   IN_PROGRESS,
   PICKING_UP,
   COMPLETE,
   BLOCKED
}
