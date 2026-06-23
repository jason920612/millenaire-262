package com.coderyo.jason.ops;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-side progress store for player-like world operations, keyed by the WORKSITE POINT — NOT by the villager.
 *
 * <p><b>Why state lives on the point:</b> the refactor's source of truth for an in-progress op is the goal's task
 * point (the worksite block/pos), not the villager. Any villager that comes within reach of a point advances the
 * <em>same</em> {@link Progress} record. This is what makes villager HAND-OFF work: if villager A starts breaking a
 * block and wanders off (or dies), villager B that arrives picks up the accumulated {@code breakProgress} exactly
 * where A left it, because both look up the record by (dimension, pos) — never by their own id.
 *
 * <p>The store is a plain {@link ConcurrentHashMap} keyed by a {@code (dimension key, BlockPos)} {@link Key}. It is
 * intentionally simple and global (server-side); there is at most one server world tick driving it. The
 * {@link Progress} record is mutable and small, with room for fields that later phases need (fishing phase/timer).
 *
 * <p>Lifecycle: a goal calls {@link #getOrCreate} each tick while within reach to fetch/advance the point's record;
 * on completion (or abandonment) the op calls {@link #clear} to drop it; {@link #decay} sweeps stale entries that no
 * villager has touched recently so the map does not leak when worksites are abandoned mid-op.
 */
public final class TaskPointStore {

   /** Default staleness threshold for {@link #decay}: a record untouched for this many ms is dropped. */
   public static final long DEFAULT_STALE_MS = 60_000L;

   /**
    * Default ore-regrow delay in ticks. 1.12 Millénaire never actually removed a mine source block — the miner
    * faked the break (sound + {@code addToInv}) and the source stayed in place, so the village always had a
    * renewable supply. Now that the block is REALLY broken (real reach mining + real drops), we restore that
    * net effect by regrowing the source after a delay so the mine stays renewable. 600 ticks = 30s, a balance
    * value: short enough that the village's supply is effectively continuous (matching 1.12), long enough that
    * the break+regrow reads as a real, deliberate cycle rather than an instant respawn.
    */
   public static final int DEFAULT_REGROW_DELAY_TICKS = 600;

   private static final TaskPointStore INSTANCE = new TaskPointStore();

   private final Map<Key, Progress> records = new ConcurrentHashMap<>();
   /** Point-owned regrow schedule: a broken mine source's target state + the game tick it is due to return. */
   private final Map<Key, Regrow> regrows = new ConcurrentHashMap<>();
   /** Per-MINE excavation state (frontier + permanent hazards + tally), keyed by the mine ANCHOR point. */
   private final Map<Key, MineState> mines = new ConcurrentHashMap<>();

   private TaskPointStore() {
   }

   /** The single server-side store. */
   public static TaskPointStore get() {
      return INSTANCE;
   }

   // ---- API ---------------------------------------------------------------------------------------

   /**
    * Returns the existing progress record for {@code (level, pos)}, creating a fresh one if none exists. The SAME
    * record is returned for the same point regardless of which villager asks — this is the hand-off mechanism.
    * Touching a record (get-or-create or {@link Progress#advance}) refreshes its last-touched timestamp.
    */
   public Progress getOrCreate(Level level, BlockPos pos) {
      Key key = keyOf(level, pos);
      Progress p = records.computeIfAbsent(key, k -> new Progress());
      p.touch();
      return p;
   }

   /** Returns the existing record, or {@code null} if the point has none (no op in progress there). */
   public Progress peek(Level level, BlockPos pos) {
      return records.get(keyOf(level, pos));
   }

   /** Drops the record for a point. Called when the op finishes (COMPLETE) or the point is abandoned. */
   public void clear(Level level, BlockPos pos) {
      records.remove(keyOf(level, pos));
   }

   /** Removes every record older than {@code staleMs}. Returns the number dropped. Call periodically per world. */
   public int decay(long staleMs) {
      long cutoff = System.currentTimeMillis() - staleMs;
      int removed = 0;
      Iterator<Map.Entry<Key, Progress>> it = records.entrySet().iterator();
      while (it.hasNext()) {
         if (it.next().getValue().lastTouchedMs <= cutoff) {
            it.remove();
            removed++;
         }
      }
      return removed;
   }

   /** Convenience: decay with {@link #DEFAULT_STALE_MS}. */
   public int decay() {
      return decay(DEFAULT_STALE_MS);
   }

   /** Test/diagnostic: number of live records. */
   public int size() {
      return records.size();
   }

   /**
    * Visit every live record with its worksite {@link BlockPos}. Used by the fishing op (O4) to locate the point
    * whose bobber id matches a hook from inside the FishingHook mixin (which has no point context). The key's
    * dimension is not exposed here — fishing bobber ids are world-unique within a server tick, so matching on the
    * id alone is sufficient for the single overworld case; callers that care about dimension should re-check.
    */
   public void forEach(java.util.function.BiConsumer<BlockPos, Progress> visitor) {
      for (Map.Entry<Key, Progress> e : records.entrySet()) {
         visitor.accept(BlockPos.of(e.getKey().packedPos()), e.getValue());
      }
   }

   /** Test/diagnostic: drop everything (e.g. between unit tests, or on server stop). */
   public void clearAll() {
      records.clear();
      regrows.clear();
      mines.clear();
   }

   // ---- per-mine excavation state (point-owned, keyed by the mine ANCHOR) -------------------------

   /**
    * The {@link MineState} for the mine anchored at {@code (level, anchor)}, creating a fresh one (with its
    * frontier seeded at the anchor) if none exists. The anchor is the village's mine entrance / source point;
    * keying the excavation state here makes the FRONTIER and the permanent HAZARD set shared across every
    * villager that mines this mine (hand-off), and survives the working villager wandering off — exactly like
    * the break {@link Progress} records. This is the seed of mining-driven outward expansion (Phase 1, #3).
    */
   public MineState getOrCreateMine(Level level, BlockPos anchor) {
      Key key = keyOf(level, anchor);
      return mines.computeIfAbsent(key, k -> new MineState(anchor));
   }

   /** The mine state for an anchor, or {@code null} if this mine has never been worked. Test/diagnostic. */
   public MineState peekMine(Level level, BlockPos anchor) {
      return mines.get(keyOf(level, anchor));
   }

   /** Test/diagnostic: number of live mines being tracked. */
   public int mineCount() {
      return mines.size();
   }

   /** Visit every live {@link MineState} (the observer folds these into its mining summary). */
   public void forEachMine(java.util.function.Consumer<MineState> visitor) {
      for (MineState m : mines.values()) {
         visitor.accept(m);
      }
   }

   // ---- ore regrow (point-owned) ------------------------------------------------------------------

   /**
    * Schedule the mine source at {@code (level, pos)} to regrow back to {@code sourceState} after
    * {@link #DEFAULT_REGROW_DELAY_TICKS}. State lives on the POINT (same key space as the break record), so the
    * schedule survives the breaking villager wandering off — any later tick of the store performs the regrow.
    *
    * @param nowGameTime the current game tick ({@code Level#getGameTime}); the due tick is {@code now + delay}.
    */
   public void scheduleRegrow(Level level, BlockPos pos, BlockState sourceState, long nowGameTime) {
      scheduleRegrow(level, pos, sourceState, nowGameTime, DEFAULT_REGROW_DELAY_TICKS);
   }

   /** {@link #scheduleRegrow(Level, BlockPos, BlockState, long)} with an explicit delay (ticks). */
   public void scheduleRegrow(Level level, BlockPos pos, BlockState sourceState, long nowGameTime, int delayTicks) {
      regrows.put(keyOf(level, pos), new Regrow(sourceState, nowGameTime + delayTicks));
   }

   /** The scheduled regrow for a point, or {@code null} if none is pending. Test/diagnostic. */
   public Regrow peekRegrow(Level level, BlockPos pos) {
      return regrows.get(keyOf(level, pos));
   }

   /** Test/diagnostic: number of pending regrows. */
   public int regrowCount() {
      return regrows.size();
   }

   /**
    * Performs every regrow for {@code level} whose due tick has passed at {@code nowGameTime}: sets the source
    * block back to its stored state (only if the spot is currently air/replaceable, so we never clobber a player
    * build there) and drops the schedule entry. Called once per server tick per world. Returns the number regrown.
    */
   public int tickRegrow(Level level, long nowGameTime) {
      ResourceKey<Level> dim = level.dimension();
      Identifier dimId = dim != null ? dim.identifier() : Identifier.withDefaultNamespace("headless");
      int regrown = 0;
      Iterator<Map.Entry<Key, Regrow>> it = regrows.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Key, Regrow> e = it.next();
         Key key = e.getKey();
         if (!key.dimension().equals(dimId)) {
            continue;
         }
         Regrow r = e.getValue();
         if (nowGameTime < r.dueTick()) {
            continue;
         }
         BlockPos pos = BlockPos.of(key.packedPos());
         // Only regrow into air/replaceable so a player who built on the broken spot keeps their block.
         BlockState current = level.getBlockState(pos);
         if (current.isAir() || current.canBeReplaced()) {
            level.setBlockAndUpdate(pos, r.state());
            regrown++;
         }
         it.remove();
      }
      return regrown;
   }

   private static Key keyOf(Level level, BlockPos pos) {
      ResourceKey<Level> dim = level.dimension();
      // Defend against a null/headless dimension key so the store is usable in golden tests.
      Identifier dimId = dim != null ? dim.identifier() : Identifier.withDefaultNamespace("headless");
      return new Key(dimId, pos.asLong());
   }

   // ---- the per-point mutable record --------------------------------------------------------------

   /**
    * Mutable in-progress op state for one worksite point. Small by design; fields beyond {@code breakProgress} are
    * placeholders that later phases will use (fishing phase/timer, regrow timers).
    */
   public static final class Progress {
      /** Accumulated break fraction in {@code [0, 1)}; the block breaks once this reaches {@code 1.0}. */
      public float breakProgress;
      /**
       * The fishing FSM phase (O4): which phase the point's bobber is in. {@code -1} = idle (not casting).
       * Ordinal of {@link com.coderyo.jason.ops.VillagerFishing.Phase}; stored as an int so this record stays a
       * plain mutable struct. Lives on the POINT so a relieving villager picks up an in-flight cast (hand-off).
       */
      public int fishingPhase = -1;
      /** Generic timer (O4 fishing safety/cast-cooldown countdown, O1 ore-regrow delay, …), in ticks. */
      public int timer;
      /**
       * Entity id of the live {@link net.minecraft.world.entity.projectile.FishingHook} this point's cast spawned,
       * or {@code 0} if none. Point-owned (not villager-owned) so the bobber is recoverable across hand-off; the
       * entity itself carries the villager owner, so a relieving villager just re-adopts the same hook.
       */
      public int fishingBobberId;

      /**
       * Point-owned reach-extension scaffold column (O2): the temporary climb blocks this op placed to reach an
       * out-of-reach target, lowest-first (the order they should be reclaimed: top-down). Empty when no reach
       * extension is active. Because the list lives on the POINT, the reclaim survives the building villager
       * wandering off — any villager/finalisation that clears the point removes the temporary blocks. Stored as
       * packed {@link BlockPos} longs (matching the store's key space).
       *
       * @see com.coderyo.jason.ops.VillagerWorldOps#ensureReach
       */
      public final java.util.List<Long> scaffoldColumn = new java.util.ArrayList<>();

      private long lastTouchedMs = System.currentTimeMillis();

      Progress() {
      }

      /** Records a temporary scaffold/stack block this op placed (for later reclaim). De-duplicates. */
      public void trackScaffold(BlockPos pos) {
         long packed = pos.asLong();
         if (!this.scaffoldColumn.contains(packed)) {
            this.scaffoldColumn.add(packed);
         }
         touch();
      }

      /** Adds {@code delta} to the break progress, refreshes the touch timestamp, and returns the new total. */
      public float advance(float delta) {
         this.breakProgress += delta;
         touch();
         return this.breakProgress;
      }

      /** Vanilla crack stage {@code 0..9} for the current break progress (clamped). */
      public int crackStage() {
         int stage = (int) (this.breakProgress * 10.0f);
         if (stage < 0) {
            return 0;
         }
         return Math.min(stage, 9);
      }

      /** Resets all op state on this record (kept alive but logically empty). */
      public void reset() {
         this.breakProgress = 0.0f;
         this.fishingPhase = -1;
         this.timer = 0;
         this.fishingBobberId = 0;
         touch();
      }

      void touch() {
         this.lastTouchedMs = System.currentTimeMillis();
      }
   }

   /** Composite key: dimension id + packed BlockPos long. */
   private record Key(Identifier dimension, long packedPos) {
   }

   /** A pending ore regrow: the source block state to restore and the game tick it becomes due. */
   public record Regrow(BlockState state, long dueTick) {
   }

   /**
    * Per-MINE excavation state for the real ore-vein miner ({@link OreVeinMiner}), keyed by the mine ANCHOR in
    * {@link #getOrCreateMine}. Holds the outward {@code frontier} (which advances when local ore is exhausted —
    * the mine growing), the permanent {@code hazards} set (lava/bedrock/water cells the miner found and must
    * NEVER mine or route through — they are NOT converted to re-minable stone, so they stay avoided), the
    * outward {@code dirX/dirZ} the frontier pushes, and tallies for observation. Mutable struct, shared across
    * every villager working this mine (hand-off) and across ticks.
    */
   public static final class MineState {
      /** The mine anchor (village mine entrance / source point) this state belongs to. */
      public final BlockPos anchor;
      /** The current outward mine frontier (advances when the local vein is exhausted). */
      public BlockPos frontier;
      /** Permanent DON'T-MINE / DON'T-ENTER cells (lava/bedrock/water), stored as packed BlockPos longs. */
      public final Set<Long> hazards = ConcurrentHashMap.newKeySet();
      /** Horizontal outward direction the frontier advances (defaults to +X; set on first advance). */
      public int dirX = 1;
      public int dirZ = 0;
      /** Total ore cells this mine has flood-mined (observation / future resource-intake accounting). */
      public int oreMined;
      /** Total times the frontier has advanced outward (the mine's outward reach — feeds expansion later). */
      public int frontierAdvances;
      /** Game tick this mine state was last touched (for the observer's throttling). */
      public long lastTickTouched;
      /** Packed pos of the last vein-start logged, so the "VEIN found" line isn't spammed every tick. */
      public long lastVeinStartLogged = Long.MIN_VALUE;

      MineState(BlockPos anchor) {
         this.anchor = anchor;
         this.frontier = anchor;
      }

      /** Permanently mark {@code pos} as a hazard never to be mined or routed through. */
      public void markHazard(BlockPos pos) {
         this.hazards.add(pos.asLong());
      }

      /** True if {@code pos} has been permanently marked a hazard for this mine. */
      public boolean isHazard(BlockPos pos) {
         return this.hazards.contains(pos.asLong());
      }
   }
}
