package org.millenaire.common.utilities;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Parses/serialises blockstate property strings ("variant=mudbrick,facing=north"). Ported
 * to 26.2: IProperty→Property, StateDefinition→StateDefinition, withProperty→setValue,
 * getBlockState()→getStateDefinition(), getProperties()(map)→iterate getProperties()+getValue.
 * getPlankVariant removed (vanilla BlockPlanks.EnumType no longer exists).
 */
public class BlockStateUtilities {
   private static final Splitter COMMA_SPLITTER = Splitter.on(',');
   private static final Splitter EQUAL_SPLITTER = Splitter.on('=').limit(2);

   /**
    * 1.12-only block-state properties that 26.2 genuinely dropped (flattening / mechanic removal) and
    * which have NO modern equivalent on the target block. When a {@code prop=value} names one of these,
    * skipping it is correct (the legacy data still parses; the rest of the block places). Anything NOT
    * in this set that the block lacks is treated as a porting error and crashes (fail-fast) — that is
    * what would have caught the floating-torch bug (a real {@code facing} on {@code minecraft:torch}
    * silently dropped → floor torch).
    *
    * <p>Seeded from what the real blocklist/buildings/goals actually skip (harness "Skipping unknown
    * legacy property" sweep). Each entry is a 1.12 property whose information now lives in the flattened
    * block id itself, so dropping the property is lossless:</p>
    * <ul>
    *   <li>{@code check_decay} — 1.12 leaves decay-scheduling flag; 26.2 LeavesBlock has no such property
    *       (only PERSISTENT, which the {@code decayable} alias already handles).</li>
    *   <li>{@code variant} — 1.12 sub-type selector (e.g. {@code stone variant=diorite},
    *       {@code double_plant variant=double_rose}); 26.2 flattened every variant into its own block id,
    *       so the variant is encoded by the id and the leftover property is redundant.</li>
    * </ul>
    *
    * <p>Deliberately NOT whitelisted: {@code facing}, {@code half}, {@code type}, {@code axis}, … — these
    * are real 26.2 properties. If one is skipped on a concrete (non-AIR) block it means the property
    * mapping is wrong (the torch class of bug), so it must crash. (The harness sees {@code facing}/
    * {@code half}/{@code type} skips only on {@code minecraft:air}, i.e. an unresolved legacy block id —
    * handled by the AIR guard below, not by widening this whitelist.)</p>
    */
   private static final Set<String> DROPPED_1_12_PROPERTIES = Set.of(
      "check_decay", // 1.12 leaves decay flag — no 26.2 equivalent
      "variant"      // 1.12 sub-type selector — folded into the flattened block id
   );

   private static Map<Property<?>, Comparable<?>> getBlockStatePropertyValueMap(Block block, String values) {
      Map<Property<?>, Comparable<?>> map = Maps.newHashMap();
      // Empty/blank values (common after the 1.12 metadata→26.2 flattened-block migration, where the
      // old "variant=X" became the block id itself with no values) → no property changes, not an error.
      // A bare numeric value is a legacy 1.12 metadata int (now gone) → also just use the default state.
      if (values == null || values.trim().isEmpty() || values.trim().matches("\\d+")) {
         return map;
      }
      if ("default".equals(values)) {
         BlockState def = block.defaultBlockState();
         for (Property<?> p : def.getProperties()) {
            map.put(p, def.getValue(p));
         }
         return map;
      } else if (block == Blocks.AIR) {
         // The block id failed to resolve. In 26.2 BuiltInRegistries.BLOCK.getValue() never returns null
         // for an unknown id — it returns AIR — so a legacy id that 26.2 renamed (e.g. the unflattened
         // "red_flower", "yellow_flower", "double_plant" still used by some harvest/plant goal data)
         // silently arrives here as AIR with leftover properties (type=poppy, facing=north, …). AIR has
         // no properties, so every prop=value would otherwise be "skipped" and hide the unresolved-id
         // problem. Skip them all here (AIR cannot hold any state anyway) WITHOUT polluting the
         // property whitelist with real 26.2 names like facing/half/type. This keeps facing/half/type
         // fail-fast on concrete blocks (the torch class of bug) while tolerating the separate
         // unresolved-legacy-block-id data issue, which is fixed in the conversion tables, not here.
         MillLog.milldebug(
            "BlockStateUtilities",
            "Block id resolved to AIR; ignoring leftover properties '" + values + "' (likely an unflattened legacy block id)"
         );
         return map;
      } else {
         StateDefinition<Block, BlockState> stateDefinition = block.getStateDefinition();
         Iterator<String> iterator = COMMA_SPLITTER.split(values).iterator();

         while (iterator.hasNext()) {
            String s = iterator.next();
            Iterator<String> iterator1 = EQUAL_SPLITTER.split(s).iterator();
            if (!iterator1.hasNext()) {
               return null;
            }

            String propName = iterator1.next();
            if (!iterator1.hasNext()) {
               return null;
            }
            String rawValue = iterator1.next();

            Property<?> property = stateDefinition.getProperty(propName);
            if (property == null && "half".equals(propName)) {
               // 1.12 slabs used a HALF (top/bottom) property; 26.2 SlabBlock renamed it to TYPE.
               // (Stairs/doors still have "half", so only alias when the block has no half property.)
               property = stateDefinition.getProperty("type");
            }
            if (property == null && "decayable".equals(propName)) {
               // 1.12 leaves: decayable -> 26.2 LeavesBlock PERSISTENT (inverted: decayable=false => persistent=true).
               property = stateDefinition.getProperty("persistent");
               if (property != null) {
                  rawValue = "false".equals(rawValue) ? "true" : "false";
               }
            }
            if (property == null) {
               if (DROPPED_1_12_PROPERTIES.contains(propName)) {
                  // A genuinely-dropped 1.12 property (see DROPPED_1_12_PROPERTIES): skip it so the rest of
                  // the legacy building-data block still places. Logged (debug-mode) so a building that
                  // looks wrong can be traced back to the dropped property.
                  MillLog.milldebug(
                     "BlockStateUtilities",
                     "Skipping known-dropped 1.12 property '" + propName + "=" + rawValue + "' for block " + block
                  );
                  continue;
               }
               // FAIL-FAST: this block has no such property and it is NOT a known-dropped 1.12 one. The old
               // code silently skipped it, which is exactly how the floating-torch bug hid (a real
               // "facing" on minecraft:torch was dropped → floor torch). Consistent with
               // MillConvert.dotSpecToBlockState, crash loudly so the wrong block is never produced.
               throw MillCrash.fail(
                  "Convert",
                  block + " has no property '" + propName + "=" + rawValue
                     + "' (not a known-dropped 1.12 property — likely a porting error)"
               );
            }

            Comparable<?> comparable = getValueHelper(property, rawValue);
            if (comparable == null && ("true".equals(rawValue) || "false".equals(rawValue))) {
               // 1.12 walls/fences used boolean side properties (north=true); 26.2 walls use the WallSide
               // enum (none/low/tall). Map true -> low, false -> none.
               comparable = getValueHelper(property, "true".equals(rawValue) ? "low" : "none");
            }
            if (comparable == null) {
               // FAIL-FAST: the property IS real on this block but it rejects this value. Unlike a
               // dropped property, there is no "lossless skip" here — silently dropping it would place
               // the wrong state. Consistent with MillConvert.setNonIntegerValue, crash loudly.
               throw MillCrash.fail(
                  "Convert",
                  "property '" + property.getName() + "' on block " + block + " rejects value '" + rawValue + "'"
               );
            }

            map.put(property, comparable);
         }

         return map;
      }
   }

   @SuppressWarnings("unchecked")
   private static <T extends Comparable<T>> BlockState getBlockStateWithProperty(BlockState blockState, Property<T> property, Comparable<?> value) {
      return blockState.setValue(property, (T) value);
   }

   public static BlockState getBlockStateWithValues(BlockState blockState, String values) {
      Map<Property<?>, Comparable<?>> properties = getBlockStatePropertyValueMap(blockState.getBlock(), values);
      if (properties == null) {
         MillLog.error(null, "Could not parse values line of " + values + " for block " + blockState.getBlock());
      } else {
         for (Entry<Property<?>, Comparable<?>> entry : properties.entrySet()) {
            blockState = getBlockStateWithProperty(blockState, entry.getKey(), entry.getValue());
         }
      }

      return blockState;
   }

   public static Comparable<?> getPropertyValueByName(BlockState blockState, String propertyName) {
      Property<?> property = blockState.getBlock().getStateDefinition().getProperty(propertyName);
      return property != null ? blockState.getValue(property) : null;
   }

   public static String getStringFromBlockState(BlockState blockState) {
      StringBuilder properties = new StringBuilder();

      for (Property<?> property : blockState.getProperties()) {
         if (properties.length() > 0) {
            properties.append(",");
         }
         properties.append(property.getName()).append("=").append(blockState.getValue(property).toString());
      }

      return BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString() + ";" + properties;
   }

   @Nullable
   private static <T extends Comparable<T>> T getValueHelper(Property<T> property, String value) {
      return property.getValue(value).orElse(null);
   }

   public static boolean hasPropertyByName(BlockState blockState, String propertyName) {
      return blockState.getBlock().getStateDefinition().getProperty(propertyName) != null;
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   public static BlockState setPropertyValueByName(BlockState blockState, String propertyName, Comparable value) {
      Property property = blockState.getBlock().getStateDefinition().getProperty(propertyName);
      return property != null ? blockState.setValue(property, value) : null;
   }
}
