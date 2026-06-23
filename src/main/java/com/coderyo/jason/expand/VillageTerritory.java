package com.coderyo.jason.expand;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;

/**
 * PER-VILLAGE claimed-radius (territory) store for the emergent-civilization systems (Phase 3 expansion,
 * Phase 4 merge/found, Phase 5 war).
 *
 * <h2>Why this exists — the bug it fixes</h2>
 * The emergent code used to read/write a village's CLAIMED RADIUS via {@code townHall.villageType.radius}. But a
 * {@link org.millenaire.common.culture.VillageType} is a SHARED per-CULTURE singleton — every village of a culture
 * holds a reference to the SAME {@code VillageType} object (the types are parsed once per culture at load). So
 * mutating {@code villageType.radius} for one village's territory mutated it for EVERY same-culture village. Two
 * neighbouring villages of the same culture would report and share ONE radius; an expansion, a war territory grab,
 * or a retreat would clobber the shared field — so a war's loser retreat would overwrite the winner's just-grown
 * radius (the {@code territoryGrew=false} symptom), and same-type wars mis-tracked territory entirely.
 *
 * <p>This store makes the emergent claimed radius PER-VILLAGE: it is keyed by the TOWN HALL point
 * {@code (dimension, packed townHallPos)} — mirroring {@link com.coderyo.jason.ops.TaskPointStore}'s
 * {@code (dimension, BlockPos)} keying — so each village has its OWN independent radius. The very first time a
 * village's territory is read, the entry is lazily seeded from that village's ORIGINAL {@code villageType.radius}
 * (the worldgen footprint of its type) — so behaviour starts identical, but from then on growth/shrink only ever
 * touch THAT village's entry. {@code villageType.radius} is never mutated for emergent territory anymore.
 *
 * <p>STRICT no-fallback / no-grant: the seed is the real worldgen radius (not a fabricated default), and an
 * absorbed village's entry is {@link #clear cleared} (its territory transferred into the survivor by the
 * survivor's own grow). The store is server-side and global (one server tick drives it).
 */
public final class VillageTerritory {

   private static final VillageTerritory INSTANCE = new VillageTerritory();

   /** Per-village claimed radius, keyed by (dimension, packed town-hall pos). */
   private final Map<Key, Integer> radii = new ConcurrentHashMap<>();

   private VillageTerritory() {
   }

   /** The single server-side territory store. */
   public static VillageTerritory get() {
      return INSTANCE;
   }

   // ---- API ---------------------------------------------------------------------------------------

   /**
    * The village's CURRENT claimed radius. On the first access for a village the entry is lazily seeded from the
    * village's ORIGINAL worldgen {@code villageType.radius} (so behaviour begins identical to the old shared-field
    * code), and every subsequent grow/shrink only touches THIS village's entry — independent of any same-culture
    * neighbour. Returns {@code 0} if the town hall has no resolvable type/position.
    */
   public int get(Building townHall) {
      Key key = keyOf(townHall);
      if (key == null) {
         return 0;
      }
      Integer r = radii.get(key);
      if (r != null) {
         return r;
      }
      int seed = seedRadius(townHall);
      // computeIfAbsent so concurrent first-accesses agree on the same seeded value.
      return radii.computeIfAbsent(key, k -> seed);
   }

   /**
    * Set the village's claimed radius to an explicit value (the emergent code clamps to its own MIN/MAX before
    * calling). Seeds the entry if absent (the new value is authoritative). No-op for an unresolvable town hall.
    */
   public void set(Building townHall, int radius) {
      Key key = keyOf(townHall);
      if (key == null) {
         return;
      }
      radii.put(key, radius);
   }

   /**
    * Grow the village's claimed radius by {@code delta} (relative to its current per-village radius) and return the
    * new value. Seeds from the worldgen radius first if needed so the growth is relative to the right base.
    */
   public int grow(Building townHall, int delta) {
      int next = get(townHall) + delta;
      set(townHall, next);
      return next;
   }

   /**
    * Shrink the village's claimed radius by {@code delta}, floored at {@code minRadius}, and return the new value.
    * Seeds from the worldgen radius first if needed so the shrink is relative to the right base.
    */
   public int shrink(Building townHall, int delta, int minRadius) {
      int next = Math.max(minRadius, get(townHall) - delta);
      set(townHall, next);
      return next;
   }

   /**
    * Drop a village's territory entry. Called when a village is ABSORBED (its claimed area is now part of the
    * survivor, whose own radius was grown to cover it) or on unload, so the map does not leak and a demoted town
    * hall can never report a stale independent claim.
    */
   public void clear(Building townHall) {
      Key key = keyOf(townHall);
      if (key != null) {
         radii.remove(key);
      }
   }

   /** Test/diagnostic: number of villages with a live territory entry. */
   public int size() {
      return radii.size();
   }

   /** Test/diagnostic: drop everything (e.g. between unit tests, or on server stop). */
   public void clearAll() {
      radii.clear();
   }

   /** Test/diagnostic: the raw stored radius for a village, or {@code null} if it has never been accessed. */
   public Integer peek(Building townHall) {
      Key key = keyOf(townHall);
      return key == null ? null : radii.get(key);
   }

   /**
    * Run {@code rebuild} with this village's PER-VILLAGE radius TRANSIENTLY applied to its (shared) {@code
    * villageType.radius}, then restore the original shared value.
    *
    * <p>This is the bridge to the UPSTREAM, non-emergent map-bounding / building-placement code in
    * {@link Building#updateWorldInfo} and {@code findBuildingLocation}, which read the claim size from
    * {@code villageType.radius} directly. Because that field is a SHARED per-culture singleton, we must NOT leave
    * it mutated; instead we apply this village's independent radius only for the duration of the rebuild (so the
    * map re-bounds to THIS village's claim and new-ring buildings are sized off it), then put the shared field
    * back exactly as it was. Net effect: the upstream code sees the per-village radius during the rebuild, and no
    * other same-culture village is affected. No grant — the value applied is the village's own stored radius.
    */
   public void withRadiusApplied(Building townHall, Runnable rebuild) {
      if (townHall == null || townHall.villageType == null) {
         if (rebuild != null) {
            rebuild.run();
         }
         return;
      }
      int original = townHall.villageType.radius;
      int perVillage = get(townHall);
      try {
         townHall.villageType.radius = perVillage;
         if (rebuild != null) {
            rebuild.run();
         }
      } finally {
         townHall.villageType.radius = original; // restore the shared field — never leave it mutated
      }
   }

   // ---- helpers -----------------------------------------------------------------------------------

   /**
    * The village's ORIGINAL worldgen radius — its {@code villageType.radius} (the footprint of its type). This is
    * the REAL seed for the per-village entry (no fabricated default): we read the type's radius ONCE here to
    * initialise the independent per-village value, and never write it back.
    */
   private static int seedRadius(Building townHall) {
      try {
         return townHall.villageType != null ? townHall.villageType.radius : 0;
      } catch (Throwable t) {
         return 0;
      }
   }

   private static Key keyOf(Building townHall) {
      if (townHall == null) {
         return null;
      }
      Point p = townHall.getPos();
      if (p == null) {
         return null;
      }
      Level world = townHall.world;
      Identifier dimId;
      if (world != null && world.dimension() != null) {
         ResourceKey<Level> dim = world.dimension();
         dimId = dim.identifier();
      } else {
         // Headless / unit-test world with no dimension key — match TaskPointStore's headless fallback so the
         // store is usable in golden tests. This is a stable key, not a grant of territory.
         dimId = Identifier.withDefaultNamespace("headless");
      }
      return new Key(dimId, p.getBlockPos().asLong());
   }

   /** Composite key: dimension id + packed town-hall BlockPos long (mirrors TaskPointStore.Key). */
   private record Key(Identifier dimension, long packedPos) {
   }
}
