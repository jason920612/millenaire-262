package com.coderyo.jason.ops;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import org.millenaire.common.entity.MillVillager;

/**
 * O4 — player-like fishing for a {@link MillVillager} with the FULL vanilla bobber animation.
 *
 * <p>Companion to {@code org.millenaire.mixin.FishingHookMixin}, which relaxes the Player-gating so a villager-owned
 * {@link FishingHook} survives + animates. This class owns the SIM-validated FSM
 * (task-ops-sim {@code run_fish}): {@code ensureTool(ROD) → CAST → WAIT → BITE → REEL → PICKUP}. State lives on the
 * goal's task POINT ({@link TaskPointStore.Progress}: {@code fishingPhase}, {@code fishingBobberId}, {@code timer}),
 * so a relieving villager re-adopts the same in-flight cast (hand-off) — the bobber entity carries the owner.
 *
 * <h2>The FSM ({@link #fishTick})</h2>
 * <ul>
 *   <li>{@link Phase#IDLE} → ensure the villager holds a {@code FISHING_ROD} (strict). Missing → {@code BLOCKED}
 *       (goal fetches one). Then CAST: face the water, swing, spawn a real villager-owned {@link FishingHook} over
 *       the spot's water surface, record its id on the point → {@link Phase#CASTING}.</li>
 *   <li>{@link Phase#CASTING}/{@link Phase#WAITING} → the bobber is live; the mixin runs the vanilla bobbing + bite
 *       FSM. We just keep the villager looking at it and wait. If the bobber dies unexpectedly (e.g. timed out),
 *       fall back to IDLE to recast. When the mixin catches a fish it calls {@link #reel} (spawning the FISHING loot
 *       toward the villager) and discards the hook → the point flips to {@link Phase#PICKUP}.</li>
 *   <li>{@link Phase#PICKUP} → reuse {@link VillagerWorldOps#pickupTick} so the villager walks to each dropped fish
 *       and collects it. When none remain → clear the point → {@link OpState#COMPLETE}.</li>
 * </ul>
 *
 * <h2>1.12 intent</h2>
 * 1.12 Millénaire's {@code GoalFish.addFishResults} gave a FIXED {@code 1× COD} (Inuit also a 1/4 {@code BONE_BLOCK})
 * with no animation. The user asked for the real player-like operation, so yields are now the REAL
 * {@code BuiltInLootTables.FISHING} loot the villager picks up — net more varied (junk/treasure/fish) but centred on
 * fish, matching the spirit of "a fisherman brings in fish". {@link GoalFishInuit} keeps its 1/4 {@code BONE_BLOCK}
 * bonus on top (added by the goal on COMPLETE), preserving that culture-specific 1.12 balance.
 */
public final class VillagerFishing {

   /**
    * Server-side registry of live villager-owned bobbers: {@code bobber entity id → owning MillVillager}. The
    * {@code FishingHookMixin} runs inside the hook's tick and needs the owner, but vanilla {@code Projectile.getOwner()}
    * resolves via an {@code EntityReference} UUID lookup that does not reliably return a Mob owner across ticks for a
    * villager-owned hook (it re-resolves to null after the first tick — the hook would then self-discard). This map is
    * the authoritative villager-owner link: {@link #cast} registers it, {@link #ownerOf} is the mixin's lookup, and
    * {@link #reel}/{@link #onHookExpired}/the FSM clear it. Keyed by entity id (world-unique among live entities).
    */
   private static final java.util.Map<Integer, MillVillager> BOBBER_OWNERS = new java.util.concurrent.ConcurrentHashMap<>();

   private VillagerFishing() {
   }

   /** The MillVillager that owns the bobber with this entity id, or {@code null} if it is not a villager bobber. */
   public static MillVillager ownerOf(int bobberId) {
      MillVillager v = BOBBER_OWNERS.get(bobberId);
      // Drop a stale link if the villager died/was removed (its hook then falls through to vanilla's discard).
      return (v != null && !v.isRemoved()) ? v : null;
   }

   /** Forget a bobber's villager-owner link (on catch, expiry, or recast). */
   public static void forget(int bobberId) {
      BOBBER_OWNERS.remove(bobberId);
   }

   /** FSM phases, stored as the ordinal in {@link TaskPointStore.Progress#fishingPhase}. */
   public enum Phase {
      IDLE,      // no cast in flight (fishingPhase == -1 also maps here).
      CASTING,   // bobber spawned, flying/settling onto the water.
      WAITING,   // bobber bobbing; vanilla bite FSM running (lure → hook → nibble).
      PICKUP     // a fish was caught + loot spawned; walk to the drops and collect them.
   }

   /** Horizontal radius (blocks) around the fishing-spot point to search for a castable water surface. */
   private static final int WATER_SCAN_RADIUS = 4;
   /** Vertical span (blocks, ± from the spot) to search for the water surface. */
   private static final int WATER_SCAN_VSPAN = 3;
   /** Hard safety cap (ticks) on a single cast before we force a recast (the mixin also times out at 1200). */
   private static final int CAST_SAFETY_TICKS = 1200;

   // ================================================================================================
   // FSM — driven per call from GoalFish.performAction (point-owned state).
   // ================================================================================================

   /**
    * Advance fishing for one step. {@code water} is the fishing-spot point (a marker at/near the water); we resolve
    * the actual castable water surface around it. Reads/writes the point's {@link TaskPointStore.Progress}.
    *
    * @return {@link OpState#BLOCKED} if no rod / no water (goal must replan), {@link OpState#IN_PROGRESS} while the
    *     bobber is in flight or biting, {@link OpState#PICKING_UP} while collecting the caught loot, and
    *     {@link OpState#COMPLETE} once the loot is all picked up (the goal then advances).
    */
   public static OpState fishTick(MillVillager v, BlockPos water) {
      Level level = v.level();
      if (level.isClientSide()) {
         return OpState.IN_PROGRESS; // server drives the FSM.
      }

      TaskPointStore.Progress p = TaskPointStore.get().getOrCreate(level, water);
      Phase phase = phaseOf(p);

      switch (phase) {
         case IDLE: {
            // Strict tool: must hold a fishing rod (the goal pre-sets it; this is the fail-fast guard).
            if (!VillagerWorldOps.ensureTool(v, VillagerWorldOps.ToolKind.ROD)) {
               return OpState.BLOCKED; // goal fetches a rod via GoalGetTool.
            }
            BlockPos surface = findWaterSurface(level, water);
            if (surface == null) {
               return OpState.BLOCKED; // no water at this spot — goal must replan.
            }
            FishingHook hook = cast(v, surface);
            if (hook == null) {
               return OpState.BLOCKED;
            }
            p.fishingBobberId = hook.getId();
            p.fishingPhase = Phase.CASTING.ordinal();
            p.timer = 0;
            return OpState.IN_PROGRESS;
         }

         case CASTING:
         case WAITING: {
            FishingHook hook = liveHook(level, p.fishingBobberId);
            if (hook == null) {
               // Bobber gone without a catch (timed out / removed). Recast next tick.
               toIdle(p);
               return OpState.IN_PROGRESS;
            }
            // Keep the villager looking at its bobber + face it (player-like). The mixin runs the animation.
            v.getLookControl().setLookAt(hook.getX(), hook.getY(), hook.getZ());
            if (phase == Phase.CASTING) {
               p.fishingPhase = Phase.WAITING.ordinal();
            }
            // Safety: if a cast somehow never resolves, force a recast (mirrors the mixin's life-timeout).
            if (++p.timer > CAST_SAFETY_TICKS) {
               forget(hook.getId());
               hook.discard();
               toIdle(p);
            }
            return OpState.IN_PROGRESS;
         }

         case PICKUP: {
            OpState pick = VillagerWorldOps.pickupTick(v, water);
            if (pick == OpState.COMPLETE) {
               TaskPointStore.get().clear(level, water);
               return OpState.COMPLETE;
            }
            return OpState.PICKING_UP;
         }

         default:
            toIdle(p);
            return OpState.IN_PROGRESS;
      }
   }

   private static Phase phaseOf(TaskPointStore.Progress p) {
      if (p.fishingPhase < 0 || p.fishingPhase >= Phase.values().length) {
         return Phase.IDLE;
      }
      return Phase.values()[p.fishingPhase];
   }

   private static void toIdle(TaskPointStore.Progress p) {
      p.fishingPhase = Phase.IDLE.ordinal();
      p.fishingBobberId = 0;
      p.timer = 0;
   }

   private static FishingHook liveHook(Level level, int id) {
      if (id == 0) {
         return null;
      }
      return level.getEntity(id) instanceof FishingHook h && h.isAlive() ? h : null;
   }

   // ================================================================================================
   // CAST — spawn a real villager-owned FishingHook over the water (player-like swing + sound).
   // ================================================================================================

   /**
    * Spawn a real {@link FishingHook} owned by the villager, positioned just above the {@code surface} water block,
    * with a small downward toss so it settles onto the surface and enters BOBBING (where the mixin runs the bite
    * FSM). Faces the villager at the water + swings the main hand, like a player casting.
    *
    * @return the spawned hook, or {@code null} if it could not be added to the world.
    */
   public static FishingHook cast(MillVillager v, BlockPos surface) {
      Level level = v.level();

      v.getLookControl().setLookAt(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5);
      v.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

      // The no-owner ctor (FishingHook.java:78) is public/AW-accessible; setOwner(LivingEntity) works (Projectile).
      EntityType<FishingHook> type = EntityTypes.FISHING_BOBBER;
      FishingHook hook = new FishingHook(type, level, 0, 0);
      hook.setOwner(v);
      // Place it just above the water surface, centred, so it falls a block then bobs (vanilla FLYING→BOBBING).
      hook.snapTo(surface.getX() + 0.5, surface.getY() + 1.0, surface.getZ() + 0.5, v.getYRot(), v.getXRot());
      hook.setDeltaMovement(new Vec3(0.0, -0.2, 0.0));

      if (!level.addFreshEntity(hook)) {
         return null;
      }
      // Authoritative villager-owner link for the mixin (vanilla getOwner() re-resolves to null for a Mob owner).
      BOBBER_OWNERS.put(hook.getId(), v);
      return hook;
   }

   // ================================================================================================
   // REEL — called BY the mixin on a bite: roll FISHING loot + spawn it flying toward the villager.
   // ================================================================================================

   /**
    * On a catch (the mixin detected {@code nibble > 0} — a fish is on the line), roll the real
    * {@code BuiltInLootTables.FISHING} loot table and spawn the resulting ItemEntities flying toward the villager,
    * mirroring vanilla {@code FishingHook.retrieve} :449-466 (minus the player-only XP/stats/advancement). Flips the
    * point to {@link Phase#PICKUP} so {@link #fishTick} then walks the villager to each drop.
    *
    * <p>Loot params mirror vanilla: {@code ORIGIN} = the hook position, {@code TOOL} = the villager's rod,
    * {@code THIS_ENTITY} = the hook, {@code LootContextParamSets.FISHING}. All public API.
    */
   public static void reel(MillVillager v, FishingHook hook) {
      Level level = v.level();
      if (!(level instanceof ServerLevel serverLevel)) {
         return;
      }
      // Mill villagers carry their rod in the Mill `heldItem` field (vanilla getMainHandItem() is empty for them);
      // use that as the loot TOOL param + the stack to apply durability to. Fall back to a plain rod for the params.
      ItemStack rod = (v.heldItem != null && v.heldItem.getItem() instanceof net.minecraft.world.item.FishingRodItem)
         ? v.heldItem : new ItemStack(net.minecraft.world.item.Items.FISHING_ROD);

      LootParams params = new LootParams.Builder(serverLevel)
         .withParameter(LootContextParams.ORIGIN, hook.position())
         .withParameter(LootContextParams.TOOL, rod)
         .withParameter(LootContextParams.THIS_ENTITY, hook)
         .create(LootContextParamSets.FISHING);
      LootTable lootTable = serverLevel.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
      List<ItemStack> items = lootTable.getRandomItems(params);

      for (ItemStack stack : items) {
         ItemEntity drop = new ItemEntity(level, hook.getX(), hook.getY(), hook.getZ(), stack);
         double xa = v.getX() - hook.getX();
         double ya = v.getY() - hook.getY();
         double za = v.getZ() - hook.getZ();
         drop.setDeltaMovement(xa * 0.1, ya * 0.1 + Math.sqrt(Math.sqrt(xa * xa + ya * ya + za * za)) * 0.08, za * 0.1);
         level.addFreshEntity(drop);
      }

      // Apply rod durability like a real cast-and-reel (LivingEntity-public hurtAndBreak path).
      if (!rod.isEmpty()) {
         rod.hurtAndBreak(1, v, net.minecraft.world.InteractionHand.MAIN_HAND);
      }

      org.millenaire.common.utilities.MillLog.major(null,
         "[MILLTEST] H5 FISHCYCLE reel(): villager-owned hook caught " + items.size()
            + " FISHING-loot stack(s); spawning them toward the villager (id=" + hook.getId() + ")");

      // Flip the OWNING point to PICKUP so fishTick collects the drops. We find the point by the bobber id we stored.
      forget(hook.getId());
      markPickup(level, hook.getId());
   }

   /** Called by the mixin when a villager's hook times out (grounded too long) without a catch — just resets. */
   public static void onHookExpired(MillVillager v, FishingHook hook) {
      // The point's fishTick will see the bobber gone (liveHook==null) next call and recast.
      forget(hook.getId());
   }

   /**
    * Find the task point whose {@code fishingBobberId} is this hook and flip it to {@link Phase#PICKUP}. The mixin
    * runs inside the hook's tick (no villager-point context), so we locate the owning record by the stored id.
    */
   private static void markPickup(Level level, int hookId) {
      TaskPointStore.get().forEach((pos, p) -> {
         if (p.fishingBobberId == hookId) {
            p.fishingPhase = Phase.PICKUP.ordinal();
            p.fishingBobberId = 0;
            p.timer = 0;
         }
      });
   }

   // ================================================================================================
   // WATER SURFACE RESOLUTION
   // ================================================================================================

   /**
    * Resolve a castable water-surface block near the {@code spot} marker: a WATER-source block with a
    * non-colliding (air-ish) block above it (so a bobber can sit on the surface). Searches outward from the spot.
    * Returns the closest such block, or {@code null} if no open water is near the spot.
    */
   public static BlockPos findWaterSurface(Level level, BlockPos spot) {
      BlockPos best = null;
      double bestSqr = Double.MAX_VALUE;
      for (int dy = WATER_SCAN_VSPAN; dy >= -WATER_SCAN_VSPAN; dy--) {
         for (int dx = -WATER_SCAN_RADIUS; dx <= WATER_SCAN_RADIUS; dx++) {
            for (int dz = -WATER_SCAN_RADIUS; dz <= WATER_SCAN_RADIUS; dz++) {
               BlockPos pos = spot.offset(dx, dy, dz);
               if (!isOpenWaterSurface(level, pos)) {
                  continue;
               }
               double d = pos.distSqr(spot);
               if (d < bestSqr) {
                  bestSqr = d;
                  best = pos.immutable();
               }
            }
         }
      }
      return best;
   }

   /** True if {@code pos} is a water-source block whose block above is air/replaceable (a fishable surface). */
   private static boolean isOpenWaterSurface(Level level, BlockPos pos) {
      FluidState fluid = level.getFluidState(pos);
      if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) {
         return false;
      }
      BlockState above = level.getBlockState(pos.above());
      return above.isAir() || above.canBeReplaced();
   }
}
