package org.millenaire.common.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.millenaire.MillHeadlessTest;

/**
 * Verifies the 1.12→26.2 block-state parsing that drives building placement (the metadata→BlockState
 * redesign — the central port hazard). Each test pins a 1.12 semantic the 26.2 port must preserve, so
 * a regression in the legacy-property mapping fails here instead of silently producing wrong buildings.
 */
class BlockStateUtilitiesTest extends MillHeadlessTest {

   @Test
   void parsesFacingProperty() {
      BlockState s = BlockStateUtilities.getBlockStateWithValues(Blocks.OAK_STAIRS.defaultBlockState(), "facing=north");
      assertEquals("north", BlockStateUtilities.getPropertyValueByName(s, "facing").toString());
   }

   @Test
   void legacySlabHalfMapsToType() {
      // 1.12 slabs used HALF (top/bottom); 26.2 SlabBlock renamed it to TYPE. The port aliases half→type.
      BlockState s = BlockStateUtilities.getBlockStateWithValues(Blocks.OAK_SLAB.defaultBlockState(), "half=top");
      assertEquals("top", BlockStateUtilities.getPropertyValueByName(s, "type").toString());
   }

   @Test
   void legacyLeavesDecayableMapsToInvertedPersistent() {
      // 1.12 leaves: decayable=false ⇒ 26.2 persistent=true (inverted). Unknown 1.12 props (check_decay)
      // must be skipped, not fail the whole block.
      BlockState s = BlockStateUtilities.getBlockStateWithValues(
         Blocks.OAK_LEAVES.defaultBlockState(), "decayable=false,check_decay=false");
      assertNotNull(s, "unknown legacy property must be skipped, not return null");
      assertEquals("true", BlockStateUtilities.getPropertyValueByName(s, "persistent").toString());
   }

   @Test
   void roundTripsThroughString() {
      BlockState orig = Blocks.OAK_STAIRS.defaultBlockState();
      String full = BlockStateUtilities.getStringFromBlockState(orig); // "minecraft:oak_stairs;facing=...,half=..."
      String values = full.substring(full.indexOf(';') + 1);
      BlockState rebuilt = BlockStateUtilities.getBlockStateWithValues(Blocks.OAK_STAIRS.defaultBlockState(), values);
      assertEquals(orig, rebuilt, "getStringFromBlockState → getBlockStateWithValues must round-trip");
   }
}
