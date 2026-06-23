package com.coderyo.jason.ops;

import java.util.Iterator;
import java.util.Map;
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

   /** Test/diagnostic: drop everything (e.g. between unit tests, or on server stop). */
   public void clearAll() {
      records.clear();
      regrows.clear();
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
      /** Reserved for the fishing FSM (O4): which phase the bobber is in. {@code -1} = not casting. */
      public int fishingPhase = -1;
      /** Reserved generic timer (O4 fishing bite countdown, O1 ore-regrow delay, …), in ticks. */
      public int timer;

      private long lastTouchedMs = System.currentTimeMillis();

      Progress() {
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
}
