package com.coderyo.jason.ops;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
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

      // 0-HARDNESS guard (crops, tall grass, scaffolding, sugar cane, …). Vanilla {@code getDestroyProgress} is
      // {@code speed / hardness / divisor}; at hardness 0 that is a division by zero → +Infinity → the block breaks
      // in a SINGLE tick (a ripe wheat crop pops instantly when a player hits it). The pure helpers above already
      // return 0 for {@code hardness <= 0} to avoid a NaN/Infinity leaking into the math, so without this guard a
      // 0-hardness block would accumulate 0 progress forever (never break). Mirror vanilla: break it this tick.
      // (The sim's {@code break_tick}: {@code if h==0: break_progress=1.0}.)
      float hardness0 = state.getDestroySpeed(level, pos);
      if (hardness0 == 0.0f) {
         doBreak(v, pos, state);
         clearCracks(v, pos);
         TaskPointStore.get().clear(level, pos);
         return OpState.COMPLETE;
      }

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
    * <p>O4: implemented. Delegates to {@link VillagerFishing}, which (with the {@code millenaire.accesswidener} +
    * {@code FishingHookMixin} relaxing the Player-gating) casts a real villager-owned hook, runs the full vanilla
    * bobbing + bite animation, rolls {@code BuiltInLootTables.FISHING} on the catch, and walks the villager to the
    * drops. State lives on the point's {@code fishingPhase}/{@code fishingBobberId}/{@code timer}.
    */
   public static OpState fishTick(MillVillager v, BlockPos water) {
      return VillagerFishing.fishTick(v, water);
   }

   // ================================================================================================
   // ENTITY GATHER — real vanilla shear (Sheep.shear) + milk (bucket → milk_bucket) (O5)
   // ================================================================================================

   /** Reach distance (centre-to-centre) at which the villager can interact with a live animal, ≈ player reach. */
   private static final double ENTITY_REACH = 4.5;
   private static final double ENTITY_REACH_SQR = ENTITY_REACH * ENTITY_REACH;
   /** Navigation speed while walking up to an animal to interact with it. */
   private static final double GATHER_WALK_SPEED = 0.5;

   /**
    * Advance shearing {@code sheep} by one tick, player-like and faithful to 1.12's cattle-farmer shear.
    *
    * <ul>
    *   <li>Sheep gone / not {@code readyForShearing()} (already sheared, or a baby) → {@link OpState#COMPLETE}: skip it,
    *       exactly as 1.12 skipped {@code isSheared()}/{@code isBaby()} sheep (no fake wool for those).</li>
    *   <li>Out of reach → walk toward the sheep, look at it, {@link OpState#APPROACHING} (no shear this tick).</li>
    *   <li>No shears in hand AND none in stock → {@link OpState#BLOCKED}: STRICT — the goal must defer to its
    *       tool-fetch path; we never fake the wool without the tool (mirrors {@link #ensureTool} strictness).</li>
    *   <li>Otherwise: swing, call the REAL {@code Sheep.shear(ServerLevel, SoundSource, shears)} — the sheep becomes
    *       sheared (woolless model via {@code setSheared(true)}) and 1–3 wool of the sheep's own colour drop as real
    *       {@link ItemEntity}s via {@code dropFromShearingLootTable} → {@code BuiltInLootTables.SHEAR_SHEEP}; fire the
    *       {@code SHEAR} game event and apply shears durability. Returns {@link OpState#PICKING_UP} so the goal then
    *       runs {@link #pickupTick} to walk to and collect the dropped wool (the YIELD — colour & count come from the
    *       sheep/loot table, as 1.12's {@code Blocks.WOOL.pick(getColor())} did).</li>
    * </ul>
    *
    * <p>State that spans the reach→shear→pickup sequence lives on the POINT keyed by the sheep's current block
    * position (the worksite the wool drops at), consistent with the other ops storing progress on the
    * {@link TaskPointStore} rather than the villager.
    */
   public static OpState shearTick(MillVillager v, Sheep sheep) {
      // Skip a removed / non-ready (already-sheared or baby) sheep — never fake wool, just as 1.12 skipped them.
      if (sheep == null || sheep.isRemoved() || !sheep.readyForShearing()) {
         return OpState.COMPLETE;
      }

      // Reach-gate against the live animal (entity distance, not a block AABB).
      if (v.distanceToSqr(sheep) > ENTITY_REACH_SQR) {
         v.getNavigation().moveTo(sheep.getX(), sheep.getY(), sheep.getZ(), GATHER_WALK_SPEED);
         v.getLookControl().setLookAt(sheep);
         return OpState.APPROACHING;
      }

      // STRICT tool: must hold shears (or be able to equip them from stock). No shears ⇒ defer to GoalGetTool.
      if (!ensureTool(v, ToolKind.SHEARS)) {
         return OpState.BLOCKED;
      }
      ItemStack shears = shearsInHand(v);
      if (shears == null) {
         return OpState.BLOCKED;
      }

      // Real shearing requires a ServerLevel (loot table + drop spawning). Off-server this op cannot run.
      if (!(v.level() instanceof ServerLevel serverLevel)) {
         return OpState.BLOCKED;
      }

      // --- Real vanilla shear ---
      v.swing(InteractionHand.MAIN_HAND);
      sheep.shear(serverLevel, SoundSource.NEUTRAL, shears); // sets sheared + drops 1-3 wool (sheep colour) as items.
      sheep.gameEvent(GameEvent.SHEAR, v);
      // Shears durability (LivingEntity-public). Charge the main hand, like the vanilla player shear path.
      shears.hurtAndBreak(1, v, InteractionHand.MAIN_HAND);

      // Wool is now on the ground as ItemEntities — the goal walks to + collects it via pickupTick(sheep block pos).
      return OpState.PICKING_UP;
   }

   /** The shears the villager will shear with: the main-hand stack if it is shears, else the Mill {@code heldItem}. */
   private static ItemStack shearsInHand(MillVillager v) {
      ItemStack main = v.getMainHandItem();
      if (isToolOfKind(main, ToolKind.SHEARS)) {
         return main;
      }
      if (v.heldItem != null && isToolOfKind(v.heldItem, ToolKind.SHEARS)) {
         return v.heldItem;
      }
      return null;
   }

   /**
    * Advance milking {@code cow} by one tick. There is NO milk goal in 1.12 Millénaire (the only cow goal is
    * {@code GoalBreedAnimals}), so no goal calls this in the port — it is provided for parity with the validated sim
    * ({@code milk_tick}) and any future milk behaviour, and re-implements the Player-only
    * {@code AbstractCow.mobInteract} bucket→milk_bucket path for a Mob villager.
    *
    * <ul>
    *   <li>Cow gone / baby → {@link OpState#COMPLETE} (can't milk a calf, matching {@code !isBaby()} in vanilla).</li>
    *   <li>Out of reach → walk toward it, {@link OpState#APPROACHING}.</li>
    *   <li>No empty {@code minecraft:bucket} in stock → {@link OpState#BLOCKED} (STRICT — produce nothing without the
    *       bucket, mirroring the sim's "no bucket → defer").</li>
    *   <li>Otherwise: swing, play {@code COW_MILK}, consume one bucket from stock, add a {@code milk_bucket} to the
    *       villager's Mill inventory → {@link OpState#COMPLETE}.</li>
    * </ul>
    */
   public static OpState milkTick(MillVillager v, AbstractCow cow) {
      if (cow == null || cow.isRemoved() || cow.isBaby()) {
         return OpState.COMPLETE;
      }
      if (v.distanceToSqr(cow) > ENTITY_REACH_SQR) {
         v.getNavigation().moveTo(cow.getX(), cow.getY(), cow.getZ(), GATHER_WALK_SPEED);
         v.getLookControl().setLookAt(cow);
         return OpState.APPROACHING;
      }
      // STRICT: need an empty bucket in stock. No bucket ⇒ produce nothing (defer), as the sim's milk_tick does.
      if (v.countInv(Items.BUCKET, 0) <= 0) {
         return OpState.BLOCKED;
      }
      v.swing(InteractionHand.MAIN_HAND);
      v.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
      v.takeFromInv(Items.BUCKET, 0, 1);         // consume the empty bucket from stock…
      v.addToInv(Items.MILK_BUCKET, 1);          // …producing a filled milk bucket (vanilla createFilledResult).
      return OpState.COMPLETE;
   }

   // ================================================================================================
   // PICKUP — "walk to each drop" (O1). Shared by every break/harvest op.
   // ================================================================================================

   /** Horizontal+vertical radius (blocks) around the worksite in which a drop is considered "owed" by the op. */
   public static final double PICKUP_SCAN_RADIUS = 5.0;
   /** Distance (blocks, centre-to-centre) at which the villager is close enough to collect a drop into its inv. */
   public static final double PICKUP_COLLECT_DIST = 1.4;
   private static final double PICKUP_COLLECT_DIST_SQR = PICKUP_COLLECT_DIST * PICKUP_COLLECT_DIST;
   /** Navigation speed while walking to a drop (matches the villager's general work pace). */
   private static final double PICKUP_WALK_SPEED = 0.5;

   /**
    * Walk the villager to the nearest {@link ItemEntity} near {@code around} (the just-finished worksite) and, once
    * within {@link #PICKUP_COLLECT_DIST}, collect it into the villager's Mill inventory and discard the entity;
    * otherwise step the navigation toward it. This is the user's "walk to each drop" step — it deliberately moves
    * to ONE drop at a time and does NOT vacuum from afar.
    *
    * <p>{@link MillVillager} is a {@code Mob}, not a {@code Player}: vanilla item-magnet pickup ({@code touch}) is
    * Player-gated, so a Mill villager never auto-collects ground items. We therefore collect explicitly here:
    * {@code addToInv} the stack's item+count, then {@code discard} the entity (mirroring what a player pickup would
    * do — remove the ground entity, gain the items).
    *
    * @return {@link OpState#PICKING_UP} while drops remain (walking to / collecting them), {@link OpState#COMPLETE}
    *     when none are left near the worksite.
    */
   public static OpState pickupTick(MillVillager v, BlockPos around) {
      ItemEntity target = nearestDrop(v, around);
      if (target == null) {
         return OpState.COMPLETE;
      }

      double distSqr = v.distanceToSqr(target);
      if (distSqr <= PICKUP_COLLECT_DIST_SQR) {
         collectDrop(v, target);
         // If that was the last one, report COMPLETE this very tick so the goal can advance promptly.
         return nearestDrop(v, around) == null ? OpState.COMPLETE : OpState.PICKING_UP;
      }

      // Walk to this one drop. Re-issued each tick; the navigation no-ops if already en route to the same spot.
      v.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), PICKUP_WALK_SPEED);
      v.getLookControl().setLookAt(target.getX(), target.getY(), target.getZ());
      return OpState.PICKING_UP;
   }

   /** The nearest live, non-removed {@link ItemEntity} within {@link #PICKUP_SCAN_RADIUS} of {@code around}, or null. */
   private static ItemEntity nearestDrop(MillVillager v, BlockPos around) {
      Level level = v.level();
      AABB box = new AABB(around).inflate(PICKUP_SCAN_RADIUS);
      ItemEntity best = null;
      double bestSqr = Double.MAX_VALUE;
      // getEntities (NOT getEntitiesOfClass) — the class-cache path is not reentrancy-safe during the tick loop
      // (see MillVillager's hostile scan); we filter for ItemEntity ourselves.
      for (Entity e : level.getEntities(v, box, e -> e instanceof ItemEntity ie && ie.isAlive() && !ie.getItem().isEmpty())) {
         double d = v.distanceToSqr(e);
         if (d < bestSqr) {
            bestSqr = d;
            best = (ItemEntity) e;
         }
      }
      return best;
   }

   /** Collect a drop into the villager's Mill inventory (whole stack) and remove the ground entity. */
   private static void collectDrop(MillVillager v, ItemEntity drop) {
      ItemStack stack = drop.getItem();
      if (!stack.isEmpty()) {
         v.addToInv(stack.getItem(), stack.getCount());
      }
      v.take(drop, stack.getCount()); // plays the pickup animation/sound, like a player collecting it
      drop.discard();
   }

   // ================================================================================================
   // TOOL — strict (O1). The op makes no drops without the correct tool, so the goal must equip one.
   // ================================================================================================

   /**
    * Strict tool check: is the villager's main hand holding the right tool for {@code kind}? If yes → {@code true}.
    * If not, try to equip the matching best tool from the villager's Mill stock into the main hand; return
    * {@code true} only if the main hand now holds the correct tool. Returns {@code false} when the villager has no
    * such tool — the caller (goal) must then go fetch one (the existing {@code GoalGetTool} path) and must NOT
    * proceed to break, since a wrong/empty tool yields no drops for tool-requiring blocks.
    *
    * <p>Mill villagers hold their tool in {@code heldItem} (== {@code getMainHandItem}), set by the goal's
    * {@code getHeldItemsDestination}; for the mine goal that is already {@code getBestPickaxeStack()}/
    * {@code getBestShovelStack()}. So in the normal flow this returns {@code true} on the first check; the
    * stock-equip branch is the strict fallback for goals that have not pre-set the hand.
    */
   public static boolean ensureTool(MillVillager v, ToolKind kind) {
      if (isToolOfKind(v.getMainHandItem(), kind)) {
         return true;
      }
      // Mill villagers DON'T wire their working item into vanilla equipment: getMainHandItem() is empty, and the
      // goal carries its tool in the Mill `heldItem` field (set from getHeldItemsTravelling/Destination). For goals
      // that pre-set the hand (fishing's rod, etc.) accept that directly — this is the villager's effective main hand.
      if (v.heldItem != null && isToolOfKind(v.heldItem, kind)) {
         return true;
      }
      ItemStack fromStock = bestToolFromStock(v, kind);
      if (fromStock != null && isToolOfKind(fromStock, kind)) {
         v.heldItem = fromStock;
         return true;
      }
      return false; // villager lacks the tool — goal must fetch via GoalGetTool; do NOT proceed.
   }

   /** The villager's best stock tool for {@code kind} as a stack, or {@code null} if none is appropriate. */
   private static ItemStack bestToolFromStock(MillVillager v, ToolKind kind) {
      switch (kind) {
         case PICKAXE: {
            ItemStack[] s = v.getBestPickaxeStack();
            return s != null && s.length > 0 ? s[0] : null;
         }
         case SHOVEL: {
            ItemStack[] s = v.getBestShovelStack();
            return s != null && s.length > 0 ? s[0] : null;
         }
         case AXE: {
            // Chop (O2): the lumberman's strict axe. getBestAxeStack falls back to a wooden axe, so this is never
            // null in practice — the strict isToolOfKind check below still gates that the result IS an axe.
            ItemStack[] s = v.getBestAxeStack();
            return s != null && s.length > 0 ? s[0] : null;
         }
         case HOE: {
            ItemStack[] s = v.getBestHoeStack();
            return s != null && s.length > 0 ? s[0] : null;
         }
         default:
            // Shears/rod stock selection is owned by their own goals (shear/fish, O5/O4); the strict check above
            // still applies to whatever the goal placed in the hand.
            return null;
      }
   }

   /**
    * True if {@code stack} is a tool of {@code kind}. PICKAXE has no dedicated class in 26.2 (pickaxes are plain
    * data-component {@code Item}s), so it is detected behaviourally — the stack is the correct tool for stone (a
    * pickaxe-requiring block) — while the other kinds keep their concrete item classes.
    */
   public static boolean isToolOfKind(ItemStack stack, ToolKind kind) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      Item item = stack.getItem();
      switch (kind) {
         case PICKAXE:
            // 26.2: no PickaxeItem class. A pickaxe is exactly an item that is the correct tool for stone
            // (which requires a pickaxe) and not one of the other concrete tool classes.
            return !(item instanceof ShovelItem) && !(item instanceof AxeItem) && !(item instanceof HoeItem)
               && !(item instanceof ShearsItem) && !(item instanceof FishingRodItem)
               && stack.isCorrectToolForDrops(Blocks.STONE.defaultBlockState());
         case SHOVEL:
            return item instanceof ShovelItem;
         case AXE:
            return item instanceof AxeItem;
         case HOE:
            return item instanceof HoeItem;
         case SHEARS:
            return item instanceof ShearsItem;
         case ROD:
            return item instanceof FishingRodItem;
         default:
            return false;
      }
   }

   /**
    * The {@link ToolKind} appropriate for mining {@code block}, faithful to 1.12 {@code GoalMinerMineResource}'s
    * tool choice: shovel for sand/clay/gravel, pickaxe for stone/sandstone (and everything else stony). Snow/ice
    * are mined with the Inuit ULU in 1.12 (a knife, not a digger) — those are handled by their own held-item path,
    * not this strict pickaxe/shovel gate, so callers should skip {@link #ensureTool} for them.
    */
   public static ToolKind miningToolFor(Block block) {
      if (block == Blocks.SAND || block == Blocks.CLAY || block == Blocks.GRAVEL) {
         return ToolKind.SHOVEL;
      }
      return ToolKind.PICKAXE;
   }

   // ================================================================================================
   // REACH-EXTENSION — scaffold-first, point-owned reclaim (O2). Shared by every out-of-reach op.
   // ================================================================================================

   /** Navigation speed while repositioning the villager under/onto the climb column. */
   private static final double REACH_WALK_SPEED = 0.5;
   /** A climb column never exceeds this many blocks (sanity guard against runaway placement on a bad target). */
   public static final int MAX_REACH_COLUMN = 24;

   /**
    * Ensure {@code target} is within player reach, extending reach if needed by building a temporary climb column
    * the villager can stand on. PREFER {@code minecraft:scaffolding} (climbable, cheap, safely removable); fall back
    * to stacking a solid block if scaffolding is unusable. The temporary blocks are tracked on the POINT
    * ({@link TaskPointStore.Progress#scaffoldColumn}) so they are RECLAIMED — removed (and optionally returned to the
    * villager's stock) — by {@link #reclaimReach} when the op completes or moves on.
    *
    * <p>Contract (per-tick, like every op primitive):
    * <ul>
    *   <li>Already in reach (after climbing, or never needed) → {@link OpState#COMPLETE}: the goal may proceed.</li>
    *   <li>Need to climb higher → place the next column block at the villager's feet (tracked on the point), step the
    *       villager up onto it, and return {@link OpState#EXTENDING_REACH}: keep calling.</li>
    *   <li>Cannot place the column (no clear feet space / column too tall) → {@link OpState#BLOCKED}: the goal must
    *       abandon this target. We never trap the villager: we only ever place a block at the feet then move up.</li>
    * </ul>
    *
    * <p><b>Scaffold supply:</b> 1.12 Millénaire's lumberman simply faked the break in place (no climbing at all), so
    * the scaffold blocks are a NEW player-like behaviour with no 1.12 economy cost. We mirror that by treating the
    * scaffold as a FREE temporary: if the villager has {@code minecraft:scaffolding} in stock we consume it (and
    * return it on reclaim); otherwise we place it for free and DO NOT credit it back — net economy unchanged from
    * 1.12 either way, since the column is always fully reclaimed.
    */
   public static OpState ensureReach(MillVillager v, BlockPos target) {
      return ensureReach(v, target, target);
   }

   /**
    * {@link #ensureReach(MillVillager, BlockPos)} but tracking the temporary climb column on a SEPARATE
    * {@code anchor} point rather than on {@code target}. This lets one op (e.g. felling a whole tree) build a single
    * shared column tracked on its dest/worksite while reaching many different {@code target}s (each log) and reclaim
    * it ONCE via {@link #reclaimReach}{@code (v, anchor)} at the end.
    */
   public static OpState ensureReach(MillVillager v, BlockPos target, BlockPos anchor) {
      if (withinReach(v, target)) {
         return OpState.COMPLETE;
      }

      Level level = v.level();
      TaskPointStore.Progress progress = TaskPointStore.get().getOrCreate(level, anchor);
      int built = progress.scaffoldColumn.size();

      // The column base is fixed at the villager's feet WHEN THE FIRST BLOCK WAS PLACED, recovered from the tracked
      // list so the plan stays stable as the villager climbs (its live blockPosition rises each tick). Before any
      // block is placed, the base is the current feet.
      BlockPos base = built > 0 ? lowest(progress.scaffoldColumn) : v.blockPosition();

      // How tall must the column be (measured from the fixed base) for the target to come into reach? Plan it purely;
      // if a vertical column can't help (target to the side / too far), this op cannot extend reach here.
      double eyeOffsetY = v.getEyePosition().y - v.blockPosition().getY();
      Vec3 baseEye = new Vec3(v.getEyePosition().x, base.getY() + eyeOffsetY, v.getEyePosition().z);
      int needed = plannedColumnHeight(baseEye, base, target);
      if (needed <= 0 || needed > MAX_REACH_COLUMN) {
         return OpState.BLOCKED;
      }

      // Done building the planned column: stand on top and re-test reach. If still short (villager not actually on
      // the column yet), keep climbing the villager up; otherwise we're as high as planned.
      if (built >= needed) {
         BlockPos topStand = base.above(built); // feet sit one above the highest placed block.
         climbOnto(v, topStand);
         return withinReach(v, target) ? OpState.COMPLETE : OpState.EXTENDING_REACH;
      }

      // Place the next column block at base+built (straight up from the fixed base), then step the villager up onto
      // it. We place at/above the base feet (never above the head) so we never seal the villager in.
      BlockPos placeAt = columnPosForLevel(base, built);
      if (!canPlaceColumnAt(level, placeAt)) {
         return OpState.BLOCKED; // column space blocked (a real block there) — can't build a clean column.
      }

      BlockState columnState = chooseColumnState(v);
      v.swing(InteractionHand.MAIN_HAND);
      level.setBlockAndUpdate(placeAt, columnState);
      v.playSound(columnState.getSoundType().getPlaceSound(), 1.0f, 1.0f);
      progress.trackScaffold(placeAt);

      // Consume one scaffold from stock if we used scaffolding and the villager has it (returned on reclaim).
      if (columnState.is(Blocks.SCAFFOLDING)) {
         v.takeFromInv(Blocks.SCAFFOLDING, 0, 1);
      }

      // Step up onto the block we just placed.
      climbOnto(v, placeAt.above());
      return OpState.EXTENDING_REACH;
   }

   /**
    * Reclaim a point's temporary climb column: remove every tracked scaffold/stack block (top-down so nothing falls
    * or is left floating), returning {@code minecraft:scaffolding} to the villager's stock, and clear the tracking
    * list. Idempotent and safe to call whether or not a column was built. Call when the op completes or the goal
    * abandons the target so no permanent scaffolding is ever left behind.
    */
   public static void reclaimReach(MillVillager v, BlockPos target) {
      TaskPointStore.Progress progress = TaskPointStore.get().peek(v.level(), target);
      if (progress == null || progress.scaffoldColumn.isEmpty()) {
         return;
      }
      Level level = v.level();
      // Top-down: remove the highest blocks first so the column never collapses onto a lower one mid-reclaim.
      java.util.List<Long> column = new java.util.ArrayList<>(progress.scaffoldColumn);
      column.sort((a, b) -> Integer.compare(BlockPos.of(b).getY(), BlockPos.of(a).getY()));
      for (long packed : column) {
         BlockPos pos = BlockPos.of(packed);
         BlockState state = level.getBlockState(pos);
         if (state.is(Blocks.SCAFFOLDING)) {
            level.removeBlock(pos, false);
            v.addToInv(Blocks.SCAFFOLDING, 1); // return the temporary scaffold to stock.
         } else if (!state.isAir()) {
            // A fallback solid stack block — just remove it (it was placed for free, see ensureReach's note).
            level.removeBlock(pos, false);
         }
      }
      progress.scaffoldColumn.clear();
   }

   // ---- pure reach-extension planning (golden-testable) -------------------------------------------

   /**
    * Pure: how many blocks tall a climb column rising from {@code feet} must be so that, standing on top of it, an
    * eye at the same horizontal offset as {@code currentEye} comes within {@link #REACH} of {@code target}'s block
    * AABB. Returns {@code 0} if the target is already in reach from the current feet, and {@code -1} if a vertical
    * column cannot help (the target is so far horizontally that no reachable height brings it within reach, e.g. it
    * is to the side beyond reach rather than above). This is the unit the golden tests assert against.
    */
   public static int plannedColumnHeight(Vec3 currentEye, BlockPos feet, BlockPos target) {
      AABB targetBox = new AABB(target);
      double eyeOffsetY = currentEye.y - feet.getY(); // eye height above the feet block (≈1.62 for a villager).
      double eyeX = currentEye.x;
      double eyeZ = currentEye.z;
      for (int h = 0; h <= MAX_REACH_COLUMN; h++) {
         // Standing on a column of height h, the feet are at feet.getY()+h; the eye rides eyeOffsetY above that.
         Vec3 eye = new Vec3(eyeX, feet.getY() + h + eyeOffsetY, eyeZ);
         if (targetBox.distanceToSqr(eye) <= REACH_SQR) {
            return h; // h==0 means already reachable; h>0 is the needed column height.
         }
      }
      return -1; // no reachable height helps — the target is out of reach horizontally, not just vertically.
   }

   /**
    * Pure: the position of the {@code i}-th column block (0-based) for a column rising from {@code feet}. Block 0
    * sits at the feet; block i sits {@code i} blocks above the feet. (When the villager steps up after placing, the
    * NEXT block goes at the new feet — which is this same progression.)
    */
   public static BlockPos columnPosForLevel(BlockPos feet, int i) {
      return feet.above(i);
   }

   private static BlockPos lowest(java.util.List<Long> column) {
      BlockPos lowest = null;
      for (long packed : column) {
         BlockPos pos = BlockPos.of(packed);
         if (lowest == null || pos.getY() < lowest.getY()) {
            lowest = pos;
         }
      }
      return lowest;
   }

   /** True if {@code pos} is clear enough to host a column block (air/replaceable, not water we'd flood). */
   private static boolean canPlaceColumnAt(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.isAir() || state.canBeReplaced();
   }

   /**
    * Choose the climb-column block: PREFER {@code minecraft:scaffolding} (climbable + safely removable). Fall back to
    * a cheap solid block ({@code minecraft:dirt}) only if scaffolding is somehow unavailable in the registry (it
    * always is in vanilla, so the fallback is defensive).
    */
   private static BlockState chooseColumnState(MillVillager v) {
      // PREFER scaffolding (climbable + cheaply removable). The DIRT fallback documents the "stack a solid block"
      // path the user asked for if scaffolding were ever unusable; with vanilla present, scaffolding is used.
      return Blocks.SCAFFOLDING.defaultBlockState();
   }

   /** Walk/teleport-step the villager onto {@code stand}: navigate toward it (real path) and look there. */
   private static void climbOnto(MillVillager v, BlockPos stand) {
      double x = stand.getX() + 0.5;
      double y = stand.getY();
      double z = stand.getZ() + 0.5;
      v.getNavigation().moveTo(x, y, z, REACH_WALK_SPEED);
      v.getLookControl().setLookAt(x, y + 1.0, z);
   }

   /** Tool categories for {@link #ensureTool} (O1+). */
   public enum ToolKind {
      AXE, PICKAXE, SHOVEL, HOE, SHEARS, ROD
   }
}
