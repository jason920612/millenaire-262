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
}
