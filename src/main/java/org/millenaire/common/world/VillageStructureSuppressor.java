package org.millenaire.common.world;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import org.millenaire.common.utilities.MillLog;

/**
 * Suppresses vanilla village generation, the 26.2 equivalent of the 1.12 {@code stopDefaultVillages}
 * config (which set {@code MapGenVillage.VILLAGE_SPAWN_BIOMES = Collections.emptyList()} so villages
 * could spawn in no biome at all).
 *
 * <p>26.2 worldgen is data-driven: villages are placed by the {@code minecraft:villages} structure set
 * (a registry entry referencing the village structures + a {@code RandomSpreadStructurePlacement}).
 * There is no longer a mutable spawn-biome list, and the structure-set registry is frozen at world
 * load, so neither the Fabric {@code BiomeModifications} API (which only edits biome generation
 * settings/spawns, not structure placement) nor {@code DynamicRegistrySetupCallback} (which exposes the
 * already-frozen registry) can remove it.
 *
 * <p>The single runtime hook that maps the original "villages spawn nowhere" semantics is the
 * per-level {@link ChunkGeneratorStructureState#possibleStructureSets()} list — the structure
 * generator iterates exactly this list to decide what to place. We filter the {@code minecraft:villages}
 * holder out of it right after the server starts (before any chunk generates), reproducing the original
 * behaviour faithfully. The list field is private, so this uses reflection (the only available seam);
 * it fails soft (logs + no-op) if the field layout ever changes, leaving vanilla villages enabled.
 */
public final class VillageStructureSuppressor {

   private static Field POSSIBLE_STRUCTURE_SETS_FIELD;
   private static boolean lookupFailed = false;

   private VillageStructureSuppressor() {
   }

   /** Remove the vanilla {@code minecraft:villages} structure set from this level's generator state. */
   public static void suppressVanillaVillages(ServerLevel level) {
      ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
      List<Holder<StructureSet>> current = state.possibleStructureSets();
      boolean hasVillages = current.stream().anyMatch(h -> h.is(BuiltinStructureSets.VILLAGES));
      if (!hasVillages) {
         return; // e.g. the nether/end, or already filtered.
      }

      List<Holder<StructureSet>> filtered = current.stream()
         .filter(h -> !h.is(BuiltinStructureSets.VILLAGES))
         .collect(Collectors.toList());

      Field field = possibleStructureSetsField();
      if (field == null) {
         return;
      }
      try {
         field.set(state, List.copyOf(filtered));
         MillLog.major(null, "stopDefaultVillages: removed the vanilla 'villages' structure set from "
            + level.dimension().identifier() + " (" + current.size() + " -> " + filtered.size() + " structure sets).");
      } catch (IllegalAccessException e) {
         MillLog.printException("Failed to suppress vanilla villages", e);
      }
   }

   private static Field possibleStructureSetsField() {
      if (POSSIBLE_STRUCTURE_SETS_FIELD != null) {
         return POSSIBLE_STRUCTURE_SETS_FIELD;
      }
      if (lookupFailed) {
         return null;
      }
      for (Field f : ChunkGeneratorStructureState.class.getDeclaredFields()) {
         // The only List<Holder<StructureSet>> field is possibleStructureSets.
         if (List.class.isAssignableFrom(f.getType())) {
            try {
               f.setAccessible(true);
               POSSIBLE_STRUCTURE_SETS_FIELD = f;
               return f;
            } catch (Exception e) {
               // keep scanning
            }
         }
      }
      lookupFailed = true;
      MillLog.error(null, "stopDefaultVillages: could not locate ChunkGeneratorStructureState.possibleStructureSets; "
         + "vanilla villages will NOT be suppressed.");
      return null;
   }
}
