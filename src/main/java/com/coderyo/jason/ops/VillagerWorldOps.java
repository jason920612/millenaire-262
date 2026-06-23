package com.coderyo.jason.ops;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.millenaire.common.entity.MillVillager;

/**
 * Player-like world-operation primitives for a {@link MillVillager}, driven one tick at a time and reading/writing
 * the {@link TaskPointStore} (state on the POINT, not the villager — see {@link TaskPointStore}).
 *
 * <p>{@link MillVillager} extends {@code PathfinderMob} — it is a {@code Mob}, never a {@code Player} — so the
 * vanilla mining loop (which is {@code ServerPlayer}-only) is unavailable. The break math here re-implements the
 * Player-only {@code getDestroyProgress}: it is all public-API math
 * ({@code ItemStack#getDestroySpeed}, {@code BlockState#getDestroySpeed}, {@code ItemStack#isCorrectToolForDrops})
 * and is fully golden-testable headlessly (see the pure helpers below).
 *
 * <h2>Reach</h2>
 * "Player reach" is approximated as 4.5 blocks from the villager's eye position to the target block's AABB
 * (squared comparison, like vanilla block-interaction range checks).
 *
 * <h2>Break-over-time</h2>
 * Each tick within reach, {@link #breakTick} swings the arm, accumulates the POINT's {@code breakProgress} by
 * {@link #destroyProgressPerTick}, shows cracks keyed by the villager's id (so multiple villagers' cracks on the
 * same point coincide), plays the hit sound, and — when progress reaches {@code 1.0} — performs the real break
 * (particles, break sound, {@code removeBlock}, {@code dropResources} with the held tool, and tool durability via
 * {@code Item#mineBlock}). Because progress lives on the point, a second villager that arrives mid-break continues
 * the same accumulation (hand-off).
 */
public final class VillagerWorldOps {

   /** Player block-interaction range, blocks. Squared for distance comparisons. */
   public static final double REACH = 4.5;
   private static final double REACH_SQR = REACH * REACH;

   /** Vanilla per-tick break divisor when the held tool is the correct one for the block. */
   private static final float CORRECT_TOOL_DIVISOR = 30.0f;
   /** Vanilla per-tick break divisor when the tool is wrong / a bare hand (slower). */
   private static final float WRONG_TOOL_DIVISOR = 100.0f;

   private VillagerWorldOps() {
   }

   // ================================================================================================
   // BREAK — fully implemented (O0)
   // ================================================================================================

   /**
    * Advance breaking the block at {@code pos} by one tick. State (the accumulated break progress) lives on the
    * POINT in {@link TaskPointStore}, so any villager within reach advances the same record (hand-off).
    *
    * <ul>
    *   <li>Unbreakable block (hardness &lt; 0) → {@link OpState#BLOCKED} (and any stray record/cracks cleared).</li>
    *   <li>Out of reach → {@link OpState#APPROACHING} (no progress; goal must walk closer).</li>
    *   <li>Otherwise: swing, accumulate the point's progress, show cracks, hit sound. Still {@code < 1.0} →
    *       {@link OpState#IN_PROGRESS}. Reached {@code 1.0} → real break + clear the record → {@link OpState#COMPLETE}.</li>
    * </ul>
    */
   public static OpState breakTick(MillVillager v, BlockPos pos) {
      Level level = v.level();
      BlockState state = level.getBlockState(pos);

      // Air / already gone: nothing to break, treat as done so the goal advances.
      if (state.isAir()) {
         clearCracks(v, pos);
         TaskPointStore.get().clear(level, pos);
         return OpState.COMPLETE;
      }

      // Unbreakable (bedrock, etc.): hardness < 0. Fail-fast for the goal.
      float hardness = state.getDestroySpeed(level, pos);
      if (hardness < 0.0f) {
         clearCracks(v, pos);
         TaskPointStore.get().clear(level, pos);
         return OpState.BLOCKED;
      }

      if (!withinReach(v, pos)) {
         return OpState.APPROACHING;
      }

      // Player-like swing.
      v.swing(InteractionHand.MAIN_HAND);

      TaskPointStore.Progress progress = TaskPointStore.get().getOrCreate(level, pos);
      float perTick = destroyProgressPerTick(v, pos);
      float total = progress.advance(perTick);

      if (total < 1.0f) {
         // Cracks keyed by the villager id (vanilla destroyBlockProgress contract); hit sound each tick.
         level.destroyBlockProgress(v.getId(), pos, progress.crackStage());
         v.playSound(state.getSoundType().getHitSound(), 0.25f, 1.0f);
         return OpState.IN_PROGRESS;
      }

      // --- Real break ---
      doBreak(v, pos, state);
      clearCracks(v, pos);
      TaskPointStore.get().clear(level, pos);
      return OpState.COMPLETE;
   }

   /**
    * Performs the real block break: particles, break sound, remove the block, spawn real tool-aware drops, and
    * apply tool durability. Drops are left on the ground for the pickup step ({@code pickupTick}, later phase).
    */
   private static void doBreak(MillVillager v, BlockPos pos, BlockState state) {
      Level level = v.level();
      ItemStack tool = v.getMainHandItem();

      // Block-break particles (vanilla effect 2001 = block destroy).
      level.levelEvent(2001, pos, Block.getId(state));
      v.playSound(state.getSoundType().getBreakSound(), 1.0f, 1.0f);

      // Capture the block entity (chest/etc. contents) before removing, for correct drops.
      var blockEntity = level.getBlockEntity(pos);
      level.removeBlock(pos, false);

      // Real drops: tool-aware (fortune/silk/correct-tool), spawns ItemEntities the villager will pick up.
      Block.dropResources(state, level, pos, blockEntity, v, tool);

      // Tool durability (LivingEntity-public; applies hurtAndBreak to the held tool).
      if (!tool.isEmpty()) {
         tool.getItem().mineBlock(tool, level, state, pos, v);
      }
   }

   private static void clearCracks(MillVillager v, BlockPos pos) {
      v.level().destroyBlockProgress(v.getId(), pos, -1);
   }

   // ================================================================================================
   // PLACE — implemented; material-consumption hook is a TODO into Mill inventory (O7)
   // ================================================================================================

   /**
    * Place {@code state} at {@code pos} (construction, crop replant, scaffold). Reach-gated.
    *
    * @return {@code true} if the block was placed (in reach), {@code false} if out of reach (caller should approach).
    *     <p><b>TODO (O7):</b> consume the matching building material from the villager's Mill inventory before
    *     placing (currently the material is not deducted — placement always "succeeds" on material). Wire into
    *     Mill's existing inventory ({@code MillVillager#countInv}/{@code takeFromInv}) when construction migrates.
    */
   public static boolean place(MillVillager v, BlockPos pos, BlockState state) {
      if (!withinReach(v, pos)) {
         return false;
      }
      Level level = v.level();
      // TODO(O7): consume the building material from the villager's Mill inventory here.
      v.swing(InteractionHand.MAIN_HAND);
      level.setBlockAndUpdate(pos, state);
      v.playSound(state.getSoundType().getPlaceSound(), 1.0f, 1.0f);
      return true;
   }

   // ================================================================================================
   // PURE HELPERS — golden-testable, no side effects
   // ================================================================================================

   /** True if {@code pos}'s block AABB is within {@link #REACH} of the villager's eye position (squared compare). */
   public static boolean withinReach(MillVillager v, BlockPos pos) {
      return withinReach(v.getEyePosition(), pos);
   }

   /** Pure overload for golden tests: reach test from an explicit eye position to a block pos. */
   public static boolean withinReach(Vec3 eye, BlockPos pos) {
      return new AABB(pos).distanceToSqr(eye) <= REACH_SQR;
   }

   /**
    * Re-implementation of the Player-only per-tick destroy progress for the villager's held tool against the block
    * at {@code pos}: {@code (toolSpeed / hardness) / (correctTool ? 30 : 100)}. Pure (reads world state, no writes).
    *
    * <p>Returns {@code 0} if the block is unbreakable (hardness &lt;= 0) — callers treat that as BLOCKED.
    */
   public static float destroyProgressPerTick(MillVillager v, BlockPos pos) {
      Level level = v.level();
      BlockState state = level.getBlockState(pos);
      float hardness = state.getDestroySpeed(level, pos);
      return destroyProgressPerTick(v.getMainHandItem(), state, hardness);
   }

   /**
    * Pure core of {@link #destroyProgressPerTick(MillVillager, BlockPos)} — the re-implemented Player-only formula
    * {@code (toolSpeed / hardness) / (correctTool ? 30 : 100)}, expressed only in terms of the held {@code tool},
    * the target {@code state}, and its {@code hardness} (= {@code state.getDestroySpeed(level, pos)}). This is the
    * golden-testable unit: no level/villager needed. Returns {@code 0} if {@code hardness <= 0} (unbreakable).
    */
   public static float destroyProgressPerTick(ItemStack tool, BlockState state, float hardness) {
      if (hardness <= 0.0f) {
         return 0.0f;
      }
      float toolSpeed = tool.getDestroySpeed(state);
      float divisor = hasCorrectTool(tool, state) ? CORRECT_TOOL_DIVISOR : WRONG_TOOL_DIVISOR;
      return (toolSpeed / hardness) / divisor;
   }

   /**
    * True if the villager's held tool will produce drops for the block at {@code pos}: either the block does not
    * require a correct tool, or the held tool is correct for it. Pure.
    */
   public static boolean hasCorrectTool(MillVillager v, BlockPos pos) {
      return hasCorrectTool(v.getMainHandItem(), v.level().getBlockState(pos));
   }

   /** Pure core of {@link #hasCorrectTool(MillVillager, BlockPos)}: held {@code tool} vs target {@code state}. */
   public static boolean hasCorrectTool(ItemStack tool, BlockState state) {
      return !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
   }

   /** Ticks needed to break the block at {@code pos} with the current tool: {@code ceil(1 / perTick)}. Pure. */
   public static int ticksToBreak(MillVillager v, BlockPos pos) {
      Level level = v.level();
      BlockState state = level.getBlockState(pos);
      return ticksToBreak(v.getMainHandItem(), state, state.getDestroySpeed(level, pos));
   }

   /** Pure overload: ticks to break = {@code ceil(1 / perTick)}; {@code Integer.MAX_VALUE} if unbreakable. */
   public static int ticksToBreak(ItemStack tool, BlockState state, float hardness) {
      float perTick = destroyProgressPerTick(tool, state, hardness);
      if (perTick <= 0.0f) {
         return Integer.MAX_VALUE;
      }
      return (int) Math.ceil(1.0f / perTick);
   }

   // ================================================================================================
   // SKELETONS — signatures only; implemented in later phases. Not wired in O0.
   // ================================================================================================

   /**
    * Advance fishing by one tick: cast a real {@code FishingHook}, run the vanilla bite FSM/animation, and on a
    * catch roll {@code BuiltInLootTables.FISHING} → spawn ItemEntities for the pickup step.
    *
    * <p>// O4: NOT IMPLEMENTED. Needs an access-widener + a FishingHook mixin to relax the {@code instanceof Player}
    * gating so a Mob-owned hook ticks. Reads/writes the point's {@code fishingPhase}/{@code timer}.
    */
   public static OpState fishTick(MillVillager v, BlockPos water) {
      throw new UnsupportedOperationException("fishTick is O4 (AW + FishingHook mixin); not implemented in O0");
   }

   /**
    * Walk the villager to the nearest {@link ItemEntity} owed by the just-finished op and collect it into its Mill
    * inventory; repeat until none remain.
    *
    * <p>// O1+: NOT IMPLEMENTED. This is the "walk to each drop" step that turns real drops into inventory.
    */
   public static OpState pickupTick(MillVillager v, BlockPos around) {
      throw new UnsupportedOperationException("pickupTick is O1+ (drop walk-and-collect); not implemented in O0");
   }

   /**
    * Ensure the villager holds the correct tool for {@code kind} (axe/pickaxe/shovel/hoe/shears/rod); strict — if
    * missing, trigger {@code GoalGetTool} to fetch one.
    *
    * <p>// O1+: NOT IMPLEMENTED. Strict tool policy + GoalGetTool integration.
    */
   public static boolean ensureTool(MillVillager v, ToolKind kind) {
      throw new UnsupportedOperationException("ensureTool is O1+ (strict tool + GoalGetTool); not implemented in O0");
   }

   /**
    * Ensure {@code pos} is reachable, extending reach if needed: PREFER placing {@code minecraft:scaffolding} to
    * climb, fallback to stacking a block; reclaim afterwards.
    *
    * <p>// O2/O7: NOT IMPLEMENTED (scaffold-first reach-extension). Returns the state the goal should act on.
    */
   public static OpState ensureReach(MillVillager v, BlockPos pos) {
      throw new UnsupportedOperationException("ensureReach is O2/O7 (scaffold-first reach-extension); not implemented in O0");
   }

   /** Tool categories for {@link #ensureTool} (O1+). */
   public enum ToolKind {
      AXE, PICKAXE, SHOVEL, HOE, SHEARS, ROD
   }
}
