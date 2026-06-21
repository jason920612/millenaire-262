package org.millenaire.common.utilities;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
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
               // Unknown legacy 1.12 property (check_decay, age on leaves, variant, ...). 26.2 dropped it;
               // skip it so the rest of the legacy building-data block still places, instead of failing
               // the whole block (was logged as "Could not parse values ...").
               // Make the skip LOUD (debug-mode only) so a real-but-unhandled 26.2 property does not
               // get silently ignored: if a building looks wrong, these lines name the dropped property.
               MillLog.milldebug(
                  "BlockStateUtilities",
                  "Skipping unknown legacy property '" + propName + "=" + rawValue + "' for block " + block + " (no matching 26.2 property)"
               );
               continue;
            }

            Comparable<?> comparable = getValueHelper(property, rawValue);
            if (comparable == null && ("true".equals(rawValue) || "false".equals(rawValue))) {
               // 1.12 walls/fences used boolean side properties (north=true); 26.2 walls use the WallSide
               // enum (none/low/tall). Map true -> low, false -> none.
               comparable = getValueHelper(property, "true".equals(rawValue) ? "low" : "none");
            }
            if (comparable == null) {
               // Value still unparseable for this 26.2 property — skip just this property rather than
               // discarding the whole block. Logged (debug-mode) so a genuinely-wrong value is visible.
               MillLog.milldebug(
                  "BlockStateUtilities",
                  "Skipping unparseable value '" + rawValue + "' for property '" + property.getName() + "' on block " + block
               );
               continue;
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
