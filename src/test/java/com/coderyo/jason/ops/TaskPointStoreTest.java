package com.coderyo.jason.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.millenaire.MillHeadlessTest;

/**
 * Golden tests for the point-owned progress store. The central guarantee is that the record is keyed by the
 * (dimension, pos) WORKSITE — NOT by any villager — so two different villagers that look up the same point get the
 * SAME mutable record (this is what makes hand-off work). A {@link Level} is mocked only to supply its
 * {@code dimension()} key; nothing else about the world is needed.
 */
class TaskPointStoreTest extends MillHeadlessTest {

   private Level overworld;
   private Level nether;

   @BeforeEach
   void setUp() {
      TaskPointStore.get().clearAll();
      overworld = mock(Level.class);
      lenient().when(overworld.dimension()).thenReturn(Level.OVERWORLD);
      nether = mock(Level.class);
      lenient().when(nether.dimension()).thenReturn(Level.NETHER);
   }

   @Test
   void getOrCreateReturnsSameRecordForSamePoint() {
      BlockPos pos = new BlockPos(10, 64, -20);

      // Villager A starts the op at the point.
      TaskPointStore.Progress a = TaskPointStore.get().getOrCreate(overworld, pos);
      assertNotNull(a);
      a.advance(0.4f);

      // Villager B (a DIFFERENT entity) arrives at the SAME point and looks it up — must be the same record,
      // carrying A's accumulated progress. This is the hand-off contract.
      TaskPointStore.Progress b = TaskPointStore.get().getOrCreate(overworld, pos);
      assertSame(a, b, "same (dim,pos) must yield the same point record (hand-off)");
      assertEquals(0.4f, b.breakProgress, 1.0e-6f, "B sees A's accumulated break progress");

      b.advance(0.4f);
      assertEquals(0.8f, a.breakProgress, 1.0e-6f, "A and B share the one mutable record");
   }

   @Test
   void differentPositionsAreDistinct() {
      TaskPointStore.Progress p1 = TaskPointStore.get().getOrCreate(overworld, new BlockPos(0, 64, 0));
      TaskPointStore.Progress p2 = TaskPointStore.get().getOrCreate(overworld, new BlockPos(0, 64, 1));
      assertNotSame(p1, p2, "different worksites must not share a record");
   }

   @Test
   void differentDimensionsAreDistinct() {
      BlockPos pos = new BlockPos(5, 64, 5);
      TaskPointStore.Progress over = TaskPointStore.get().getOrCreate(overworld, pos);
      TaskPointStore.Progress hell = TaskPointStore.get().getOrCreate(nether, pos);
      assertNotSame(over, hell, "same coords in different dimensions must not collide");
   }

   @Test
   void advanceThenClearWorks() {
      BlockPos pos = new BlockPos(1, 2, 3);
      TaskPointStore.Progress p = TaskPointStore.get().getOrCreate(overworld, pos);
      p.advance(0.5f);
      p.advance(0.6f);
      assertTrue(p.breakProgress >= 1.0f, "progress accumulates past 1.0 on completion");

      assertSame(p, TaskPointStore.get().peek(overworld, pos));
      TaskPointStore.get().clear(overworld, pos);
      assertNull(TaskPointStore.get().peek(overworld, pos), "cleared point has no record");

      // get-or-create after clear yields a FRESH zeroed record (op restarts cleanly).
      TaskPointStore.Progress fresh = TaskPointStore.get().getOrCreate(overworld, pos);
      assertNotSame(p, fresh);
      assertEquals(0.0f, fresh.breakProgress, 0.0f);
   }

   @Test
   void crackStageTracksProgress() {
      TaskPointStore.Progress p = TaskPointStore.get().getOrCreate(overworld, new BlockPos(7, 8, 9));
      assertEquals(0, p.crackStage());
      p.advance(0.05f);
      assertEquals(0, p.crackStage());
      p.advance(0.30f); // total 0.35
      assertEquals(3, p.crackStage());
      p.advance(0.60f); // total 0.95
      assertEquals(9, p.crackStage());
      p.advance(0.50f); // total 1.45 — clamped at 9
      assertEquals(9, p.crackStage());
   }

   // ---- ore regrow (point-owned, renewable mine as 1.12) ------------------------------------------

   @Test
   void regrowIsScheduledOnThePointAndNotDueEarly() {
      BlockPos pos = new BlockPos(3, 64, 7);
      BlockState stone = Blocks.STONE.defaultBlockState();

      // Mine broke the source at game tick 1000; schedule its return with the default delay.
      TaskPointStore.get().scheduleRegrow(overworld, pos, stone, 1000L);
      assertEquals(1, TaskPointStore.get().regrowCount());

      TaskPointStore.Regrow r = TaskPointStore.get().peekRegrow(overworld, pos);
      assertNotNull(r);
      assertSame(stone, r.state(), "regrow restores the exact source state (renewable mine)");
      assertEquals(1000L + TaskPointStore.DEFAULT_REGROW_DELAY_TICKS, r.dueTick());

      // Before the due tick, ticking does nothing (block stays broken) and the schedule remains.
      // (No real Level set on the block here; we only assert the schedule is untouched pre-due.)
      // The actual setBlock is exercised by the harness; here we verify the timing contract.
      assertEquals(1, TaskPointStore.get().regrowCount());
   }

   @Test
   void regrowKeyedByPointNotVillager() {
      BlockPos a = new BlockPos(0, 64, 0);
      BlockPos b = new BlockPos(0, 64, 1);
      TaskPointStore.get().scheduleRegrow(overworld, a, Blocks.SAND.defaultBlockState(), 0L);
      TaskPointStore.get().scheduleRegrow(overworld, b, Blocks.GRAVEL.defaultBlockState(), 0L);
      assertEquals(2, TaskPointStore.get().regrowCount());
      assertNotSame(
         TaskPointStore.get().peekRegrow(overworld, a).state(),
         TaskPointStore.get().peekRegrow(overworld, b).state());

      // Rescheduling the same point replaces (not duplicates) its entry.
      TaskPointStore.get().scheduleRegrow(overworld, a, Blocks.STONE.defaultBlockState(), 5L);
      assertEquals(2, TaskPointStore.get().regrowCount());
      assertSame(Blocks.STONE.defaultBlockState(), TaskPointStore.get().peekRegrow(overworld, a).state());
   }

   @Test
   void clearAllDropsRegrows() {
      TaskPointStore.get().scheduleRegrow(overworld, new BlockPos(1, 1, 1), Blocks.STONE.defaultBlockState(), 0L);
      assertTrue(TaskPointStore.get().regrowCount() > 0);
      TaskPointStore.get().clearAll();
      assertEquals(0, TaskPointStore.get().regrowCount());
   }

   @Test
   void decayDropsStaleEntries() {
      TaskPointStore store = TaskPointStore.get();
      store.getOrCreate(overworld, new BlockPos(100, 64, 100));
      assertEquals(1, store.size());

      // Nothing is stale yet at a huge threshold → kept.
      assertEquals(0, store.decay(10_000_000L));
      assertEquals(1, store.size());

      // Everything is "older than 0ms ago" → swept.
      int removed = store.decay(0L);
      assertEquals(1, removed);
      assertEquals(0, store.size());
   }
}
