package com.coderyo.jason.ops;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.common.entity.MillVillager;

/**
 * High-level, AI-invokable facade over the per-tick {@link VillagerWorldOps} primitives — the clean reusable API the
 * villager AI / goals call instead of re-implementing the same ensure-tool → reach → break/place → pickup → reclaim
 * sequences inline. Each method advances ONE coherent player-like ACTION by a single tick and returns an
 * {@link OpState} the caller branches on, exactly like the primitives, but bundles the multi-step choreography so a
 * goal (or a future AI behaviour) just says "harvest this block" / "shear this sheep" and re-calls until COMPLETE.
 *
 * <h2>Why a facade (root-cause context)</h2>
 * The in-game gap that motivated this API was NOT that the primitives don't change the world — verified in a real
 * runtime, {@link VillagerWorldOps#breakTick}/{@link VillagerWorldOps#place}/{@link VillagerWorldOps#shearTick} all
 * genuinely mutate the {@code ServerLevel} for a villager (a {@code Mob}, never a {@code Player}) because they use
 * Mob-valid {@code ServerLevel} calls (the lone exception is fishing, which needs the {@code FishingHookMixin} +
 * access-widener to relax the bobber's Player-gating). The gap was that the choreography (equip the Mill {@code
 * heldItem} tool, walk into reach / scaffold up, act, walk to each drop, reclaim the scaffold) lived scattered in
 * each goal's {@code performAction}. Centralising it here makes the action path uniform and testable, and gives the
 * AI a single seam to drive these actions.
 *
 * <h2>Held tool, not vanilla equipment</h2>
 * A Mill villager carries its working tool in {@link MillVillager#heldItem} (== its effective main hand), NOT in
 * vanilla equipment — {@code getMainHandItem()} is typically {@code minecraft:air} even while the villager is "holding"
 * shears/axe/pickaxe. Every action here goes through {@link VillagerWorldOps#ensureTool} / the ops, which read
 * {@code heldItem}; callers must set {@code heldItem} (as goals do via {@code getHeldItemsDestination}) before acting.
 *
 * <p>Stateless: all progress lives on the {@link TaskPointStore} point (break progress, scaffold column), so any
 * villager that arrives mid-action continues it (hand-off). This facade adds no state of its own.
 */
public final class VillagerActions {

   private VillagerActions() {
   }

   // ================================================================================================
   // HARVEST A BLOCK — ensureTool → ensureReach(scaffold) → breakTick(over time) → pickup drops
   // ================================================================================================

   /**
    * Advance "mine/chop/harvest the block at {@code pos}" by one tick: ensure the correct tool is in hand, climb into
    * reach if needed (scaffold-first, tracked on {@code reachAnchor} so a multi-block job shares ONE column), break the
    * block over time with the real player destroy-math, then walk to and collect the real drops. Re-call until it
    * returns {@link OpState#COMPLETE}; reclaim the shared scaffold ONCE via {@link #finishHarvest} when the whole job
    * (e.g. a felled tree, a mined vein) is done.
    *
    * @param tool        the tool category the block needs (e.g. {@link VillagerWorldOps.ToolKind#AXE} for logs,
    *                    {@code PICKAXE}/{@code SHOVEL} for mining); {@code null} skips the strict tool gate (for
    *                    0-hardness / no-tool blocks like crops, where any/no tool yields drops).
    * @param reachAnchor the point the temporary climb column is tracked on (usually the goal's worksite/dest), so one
    *                    column serves many targets and is reclaimed once.
    * @return {@link OpState#BLOCKED} if the tool is missing (caller must fetch it via GoalGetTool) or the block is
    *     unbreakable / unreachable-by-column; {@link OpState#APPROACHING}/{@link OpState#EXTENDING_REACH} while moving
    *     into reach; {@link OpState#IN_PROGRESS} while breaking; {@link OpState#PICKING_UP} while collecting drops;
    *     {@link OpState#COMPLETE} once the block is gone and its drops are collected.
    */
   public static OpState harvestBlock(MillVillager v, BlockPos pos, VillagerWorldOps.ToolKind tool, BlockPos reachAnchor) {
      if (v.level().getBlockState(pos).isAir()) {
         // Already broken: nothing to mine; just finish any outstanding pickup at the worksite.
         return VillagerWorldOps.pickupTick(v, pos);
      }
      // STRICT tool gate (unless caller opted out). No tool ⇒ the op makes no drops, so defer to the fetch path.
      if (tool != null && !VillagerWorldOps.ensureTool(v, tool)) {
         return OpState.BLOCKED;
      }
      // Reach: climb a scaffold column (shared on reachAnchor) if the target is above/away.
      if (!VillagerWorldOps.withinReach(v, pos)) {
         OpState reach = VillagerWorldOps.ensureReach(v, pos, reachAnchor);
         if (reach != OpState.COMPLETE) {
            return reach; // EXTENDING_REACH / APPROACHING / BLOCKED — keep going / abandon.
         }
      }
      // Break over time; on COMPLETE, walk to + collect the real drops.
      OpState st = VillagerWorldOps.breakTick(v, pos);
      if (st == OpState.COMPLETE) {
         return VillagerWorldOps.pickupTick(v, pos); // PICKING_UP while drops remain, COMPLETE when collected.
      }
      return st; // APPROACHING / IN_PROGRESS / BLOCKED.
   }

   /** Convenience: harvest with the climb column tracked on the target itself (single-block jobs). */
   public static OpState harvestBlock(MillVillager v, BlockPos pos, VillagerWorldOps.ToolKind tool) {
      return harvestBlock(v, pos, tool, pos);
   }

   /**
    * Reclaim the temporary climb column tracked on {@code reachAnchor} (remove every scaffold/stack block, return
    * scaffolding to stock). Call ONCE when a multi-block harvest job (tree, vein, build row) is finished or abandoned,
    * so no scaffolding is ever left in the world. Idempotent.
    */
   public static void finishHarvest(MillVillager v, BlockPos reachAnchor) {
      VillagerWorldOps.reclaimReach(v, reachAnchor);
   }

   // ================================================================================================
   // PLACE A BLOCK — reach-gate → strict material consume → real placement
   // ================================================================================================

   /**
    * Advance "place {@code state} at {@code pos}" by one tick: climb into reach if needed (scaffold tracked on
    * {@code reachAnchor}), then lay the block, STRICTLY consuming the matching building material from the villager's
    * Mill stock (faithful to 1.12's construction economy — see
    * {@link VillagerWorldOps#place(MillVillager, BlockPos, BlockState, Item, int)}).
    *
    * @param material the block's own item (the construction material); {@code null}/AIR = material-free placement.
    * @return {@link OpState#EXTENDING_REACH}/{@link OpState#APPROACHING} while moving into reach;
    *     {@link OpState#BLOCKED} if the villager lacks the required material (NO_MATERIAL) or the column can't be built;
    *     {@link OpState#COMPLETE} once the block is placed.
    */
   public static OpState placeBlock(MillVillager v, BlockPos pos, BlockState state, Item material, int meta, BlockPos reachAnchor) {
      if (!VillagerWorldOps.withinReach(v, pos)) {
         OpState reach = VillagerWorldOps.ensureReach(v, pos, reachAnchor);
         if (reach != OpState.COMPLETE) {
            return reach;
         }
      }
      VillagerWorldOps.PlaceResult r = VillagerWorldOps.place(v, pos, state, material, meta);
      switch (r) {
         case PLACED:
            return OpState.COMPLETE;
         case NO_MATERIAL:
            return OpState.BLOCKED; // strict: no material ⇒ lay nothing (defer to resource fetch).
         case OUT_OF_REACH:
         default:
            return OpState.APPROACHING;
      }
   }

   /** Convenience: material-free placement (crop replant, scaffold), column tracked on the target. */
   public static OpState placeBlock(MillVillager v, BlockPos pos, BlockState state) {
      return placeBlock(v, pos, state, null, 0, pos);
   }

   // ================================================================================================
   // PLANT — reach-gate → strict plant-validity → (optional seed consume) → place a real sapling/crop
   // ================================================================================================

   /**
    * Advance "plant {@code plantState} at {@code pos}" by one tick, player-like: climb into reach if needed (scaffold
    * tracked on {@code pos}), STRICTLY verify {@code plantState} can actually survive there (the sapling/crop's own
    * vanilla {@code canSurvive}), consume one {@code seed} (item+meta) from the villager's Mill stock when a seed is
    * named, then place the plant block with a real swing + place sound. This is the single seam the crop / sapling
    * planting goals call instead of an instant silent {@code setBlock}: it guarantees the planted block is a GENUINE,
    * VALID plant (never laid on a surface where it would pop the next tick — the very "arrives but the action does
    * nothing real" failure mode this family had) and that the seed economy is paid.
    *
    * <p>{@code seed == null} (or {@code Items.AIR}) means "no separate seed debit" — the grove/lumberman sapling case
    * where the variant is decided by the planting-location type and only the sapling block itself is the resource. Pass
    * the seed item + meta to debit a distinct seed.
    *
    * @return {@link OpState#EXTENDING_REACH}/{@link OpState#APPROACHING} while moving into reach;
    *     {@link OpState#BLOCKED} if the plant cannot survive at {@code pos} (invalid surface) — the caller must replan;
    *     {@link OpState#COMPLETE} once the valid plant is placed (and the seed, if any, consumed).
    */
   public static OpState plantBlock(MillVillager v, BlockPos pos, BlockState plantState, Item seed, int seedMeta) {
      if (!VillagerWorldOps.withinReach(v, pos)) {
         OpState reach = VillagerWorldOps.ensureReach(v, pos, pos);
         if (reach != OpState.COMPLETE) {
            return reach;
         }
      }
      // STRICT plant-validity: the block must actually be able to LIVE here (vanilla canSurvive — the soil/light/space
      // a sapling or crop requires). We never lay a "plant" that would instantly pop: a valid, surviving plant is the
      // whole point of the action. An invalid surface ⇒ BLOCKED so the goal replans rather than fake-planting.
      if (!plantState.canSurvive(v.level(), pos)) {
         return OpState.BLOCKED;
      }
      // Seed economy: consume one matching seed from stock when a distinct seed is named (sapling callers pass null).
      if (seed != null && seed != net.minecraft.world.item.Items.AIR && v.countInv(seed, seedMeta) > 0) {
         v.takeFromInv(seed, seedMeta, 1);
      }
      // Real player-like place (swing + place sound) of the validated plant block.
      VillagerWorldOps.place(v, pos, plantState);
      return OpState.COMPLETE;
   }

   /** Convenience: plant {@code plantState} with no separate seed debit (sapling / grove planting). */
   public static OpState plantBlock(MillVillager v, BlockPos pos, BlockState plantState) {
      return plantBlock(v, pos, plantState, null, 0);
   }

   // ================================================================================================
   // FISH — full vanilla bobber bite FSM + BuiltInLootTables.FISHING, surfaced through the facade
   // ================================================================================================

   /**
    * Advance "fish from the spot marker {@code water}" by one tick. Delegates to the COMPLETE villager-fishing FSM
    * ({@link VillagerWorldOps#fishTick} → {@link VillagerFishing}): cast a real villager-owned {@link
    * net.minecraft.world.entity.projectile.FishingHook}, run the FULL vanilla bobbing + bite animation (via the
    * {@code FishingHookMixin} + access-widener relaxing the bobber's Player-gating), roll {@code
    * BuiltInLootTables.FISHING} on the catch, and walk the villager to + collect the dropped catch. Surfaced on the
    * facade so the fishing goal drives the action family uniformly with the other player-like ops.
    *
    * @return {@link OpState#BLOCKED} (no rod / no open water — replan), {@link OpState#IN_PROGRESS} (bobber in
    *     flight / biting), {@link OpState#PICKING_UP} (collecting the catch), {@link OpState#COMPLETE} (catch in inv).
    */
   public static OpState fish(MillVillager v, BlockPos water) {
      return VillagerWorldOps.fishTick(v, water);
   }

   // ================================================================================================
   // ENTITY GATHER — approach → shear / milk → pickup
   // ================================================================================================

   /**
    * Advance "shear {@code sheep}" by one tick: walk into reach, then perform the REAL vanilla {@code Sheep.shear}
    * (the sheep becomes sheared and drops 1–3 wool of its colour), then walk to and collect the wool. Re-call until
    * {@link OpState#COMPLETE}.
    *
    * @return {@link OpState#COMPLETE} if the sheep is gone / already sheared / a baby (skip, no fake wool) or once the
    *     wool is collected; {@link OpState#APPROACHING} while walking up; {@link OpState#BLOCKED} with no shears in
    *     hand or off-server; {@link OpState#PICKING_UP} while collecting the dropped wool.
    */
   public static OpState shearAnimal(MillVillager v, Sheep sheep) {
      OpState st = VillagerWorldOps.shearTick(v, sheep);
      if (st == OpState.PICKING_UP) {
         // Real shear happened: collect the wool dropped at the sheep's spot.
         return VillagerWorldOps.pickupTick(v, sheep.blockPosition());
      }
      return st; // COMPLETE / APPROACHING / BLOCKED.
   }

   /** Advance "milk {@code cow}" by one tick (bucket → milk_bucket). See {@link VillagerWorldOps#milkTick}. */
   public static OpState milkAnimal(MillVillager v, AbstractCow cow) {
      return VillagerWorldOps.milkTick(v, cow);
   }

   /** The {@link VillagerWorldOps.ToolKind} for mining {@code block} (shovel for soft, pickaxe otherwise). */
   public static VillagerWorldOps.ToolKind miningToolFor(Block block) {
      return VillagerWorldOps.miningToolFor(block);
   }

   /**
    * True if there is at least one ground {@link net.minecraft.world.entity.item.ItemEntity} (a real drop still to be
    * collected) near {@code worksite}, within the op's pickup scan radius. A harvest goal that folds break+pickup into
    * {@link #harvestBlock} uses this to keep driving the PICKUP phase after the harvested block is already AIR — i.e.
    * once the block-presence test that selected the worksite has flipped false — so the real drops are never abandoned.
    */
   public static boolean hasNearbyDrop(MillVillager v, BlockPos worksite) {
      net.minecraft.world.phys.AABB box =
         new net.minecraft.world.phys.AABB(worksite).inflate(VillagerWorldOps.PICKUP_SCAN_RADIUS);
      return !v.level().getEntitiesOfClass(
         net.minecraft.world.entity.item.ItemEntity.class, box,
         e -> e.isAlive() && !e.getItem().isEmpty()).isEmpty();
   }
}
