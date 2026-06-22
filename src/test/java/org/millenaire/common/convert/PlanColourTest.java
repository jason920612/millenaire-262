package org.millenaire.common.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.millenaire.MillHeadlessTest;

/**
 * Golden tests for the M2 centralised building-plan colour resolution
 * ({@link MillConvert#planColourToSpec(PlanColour)}, backed by {@link LegacyTables#loadPlanColours}).
 *
 * <p>The deterministic goldens use a fixture of representative lines taken verbatim from the shipped
 * {@code blocklist.txt}, restricted to vanilla blocks (always present in the headless registry). The
 * expected {@link BlockSpec}s are derived directly from that data and 1.12 intent: a colour resolves to
 * the same block + state the pre-M2 ad-hoc {@code PointType} parse produced, with the build cost expressed
 * as the priced item ({@code InvItem.createInvItem} keys it at meta 0). A separate test walks the entire
 * shipped file (when the Mill blocks it references are registered in this JVM) to prove the parse covers
 * exactly the colours the pre-M2 path produced blocks for — guarding the 1.12.2-semantics invariant.</p>
 */
class PlanColourTest extends MillHeadlessTest {

   // Representative lines copied verbatim from blocklist.txt (vanilla blocks only).
   private static final String FIXTURE =
      "// representative fixture\n"
         + "planks pine;minecraft:spruce_planks;;false;66/47/27\n"          // 5-field, self-cost
         + "torchLeft;minecraft:wall_torch;facing=south;true;255/255/3;anywood;;1\n" // state + stick cost
         + "stone;minecraft:stone;;false;102/102/102\n"                     // plain block, self-cost
         + "stonebricks;minecraft:stone_bricks;;false;90/90/90;minecraft:stone;;1\n" // explicit block cost
         + "water;minecraft:water;0;true;0/255/255;;;0\n"                   // free (qty 0)
         + "sleepingPos;;0;;0/128/255;;;0\n"                                // special-type-only: skipped
         + "torchDot;minecraft:wall_torch;facing(south);true;1/2/3;anywood;;1\n"; // dot-notation state column

   private static int rgb(int r, int g, int b) {
      return (r << 16) + (g << 8) + b;
   }

   private static BlockSpec specFromFixture(int colour) throws IOException {
      Path tmp = Files.createTempFile("blocklist-fixture", ".txt");
      try {
         Files.write(tmp, FIXTURE.getBytes(StandardCharsets.UTF_8));
         LegacyTables.loadPlanColours(tmp.toFile());
         return MillConvert.planColourToSpec(new PlanColour(colour));
      } finally {
         Files.deleteIfExists(tmp);
      }
   }

   // ---- happy path: state resolution ------------------------------------------------------------

   @Test
   void sprucePlanksSelfCost() throws IOException {
      BlockSpec s = specFromFixture(rgb(66, 47, 27));
      assertEquals(Blocks.SPRUCE_PLANKS.defaultBlockState(), s.state());
      // 5-field line: costs itself, quantity 1.
      assertTrue(s.cost().isPresent());
      assertEquals(Blocks.SPRUCE_PLANKS.asItem(), s.cost().get().item());
      assertEquals(1, s.cost().get().count().value());
   }

   @Test
   void wallTorchFacingStateAndStickCost() throws IOException {
      BlockSpec s = specFromFixture(rgb(255, 255, 3));
      BlockState expected = Blocks.WALL_TORCH.defaultBlockState()
         .setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.SOUTH);
      assertEquals(expected, s.state());
      // "anywood" prices in sticks.
      assertEquals(Items.STICK, s.cost().get().item());
      assertEquals(1, s.cost().get().count().value());
   }

   @Test
   void plainStoneSelfCost() throws IOException {
      BlockSpec s = specFromFixture(rgb(102, 102, 102));
      assertEquals(Blocks.STONE.defaultBlockState(), s.state());
      assertEquals(Blocks.STONE.asItem(), s.cost().get().item());
      assertEquals(1, s.cost().get().count().value());
   }

   @Test
   void stoneBricksWithExplicitBlockCost() throws IOException {
      BlockSpec s = specFromFixture(rgb(90, 90, 90));
      assertEquals(Blocks.STONE_BRICKS.defaultBlockState(), s.state());
      // Explicit cost "minecraft:stone;;1" -> priced as the stone item, quantity 1.
      assertEquals(Blocks.STONE.asItem(), s.cost().get().item());
      assertEquals(1, s.cost().get().count().value());
   }

   @Test
   void freeBlockHasNoCost() throws IOException {
      BlockSpec s = specFromFixture(rgb(0, 255, 255));
      assertEquals(Blocks.WATER.defaultBlockState(), s.state());
      // Cost quantity 0 -> no priced cost (free).
      assertTrue(s.cost().isEmpty());
   }

   // ---- dot-notation state column (new format) accepted alongside the legacy prop=value form -----

   @Test
   void dotNotationStateColumnResolves() throws IOException {
      // Same wall-torch placement, but the state column written in dot-notation rather than "facing=south".
      BlockSpec s = specFromFixture(rgb(1, 2, 3));
      BlockState expected = Blocks.WALL_TORCH.defaultBlockState()
         .setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.SOUTH);
      assertEquals(expected, s.state());
      assertEquals(Items.STICK, s.cost().get().item());
   }

   // ---- fail-fast --------------------------------------------------------------------------------

   @Test
   void unknownColourFailsFast() throws IOException {
      // A colour that does not appear in the table must crash, never silently resolve to AIR.
      specFromFixture(rgb(102, 102, 102)); // load the fixture
      assertThrows(RuntimeException.class, () -> MillConvert.planColourToSpec(new PlanColour(rgb(7, 11, 13))));
   }

   @Test
   void deadLegacyBlockIdResolvesToAirMatching112() throws IOException {
      // A block id with no modern equivalent (stale 1.7-era custom-mod content) is logged loudly and mapped
      // to AIR — exactly what 1.12 did (it placed nothing). The colour stays in the table, so no per-pixel
      // "Unknown colour" is raised when a plan uses it. This preserves pre-M2 behaviour precisely.
      String content = "dragonEgg;122;0;false;5/6/7\n";
      Path tmp = Files.createTempFile("blocklist-deadid", ".txt");
      try {
         Files.write(tmp, content.getBytes(StandardCharsets.UTF_8));
         LegacyTables.loadPlanColours(tmp.toFile()); // does not throw
         BlockSpec s = MillConvert.planColourToSpec(new PlanColour(rgb(5, 6, 7)));
         assertEquals(Blocks.AIR.defaultBlockState(), s.state());
      } finally {
         Files.deleteIfExists(tmp);
      }
   }

   // ---- 1.12.2 semantics: every shipped block-bearing colour resolves to its declared block ------

   @Test
   void everyShippedBlockBearingColourResolvesToItsBlock() throws IOException {
      // Verifies the centralised parse covers exactly the colours the pre-M2 path produced blocks for, and
      // each resolves to the block named in column 1 (state ignored). Skips gracefully if the Mill blocks
      // the file references are not registered in this headless JVM (registry-frozen harness).
      File blocklist = new File("src/main/resources/todeploy/millenaire/blocklist.txt");
      Assumptions.assumeTrue(blocklist.exists(), "shipped blocklist.txt present");
      Assumptions.assumeTrue(millBlocksRegistered(), "Mill blocks registered in this headless JVM");

      LegacyTables.loadPlanColours(blocklist);

      int checked = 0;
      for (String line : Files.readAllLines(blocklist.toPath())) {
         String t = line.trim();
         if (t.isEmpty() || t.startsWith("//")) {
            continue;
         }
         String[] p = t.split(";", -1);
         if ((p.length != 5 && p.length != 8) || p[1].isEmpty()) {
            continue; // malformed (skipped by loader) or special-type-only (not block-bearing)
         }
         String[] c = p[4].split("/", -1);
         int colour = (Integer.parseInt(c[0].trim()) << 16) + (Integer.parseInt(c[1].trim()) << 8) + Integer.parseInt(c[2].trim());
         BlockSpec s = MillConvert.planColourToSpec(new PlanColour(colour));
         String expectedId = p[1].contains(":") ? p[1] : "minecraft:" + p[1];
         assertEquals(Identifier.parse(expectedId), BuiltInRegistries.BLOCK.getKey(s.state().getBlock()),
            "block mismatch for line <" + t + ">");
         checked++;
      }
      assertTrue(checked > 400, "expected to verify many block-bearing colours, only checked " + checked);
   }

   /** True iff a representative Mill block from blocklist.txt is registered in this JVM. */
   private static boolean millBlocksRegistered() {
      Block b = BuiltInRegistries.BLOCK.getValue(Identifier.parse("millenaire:painted_brick_silver"));
      return b != Blocks.AIR;
   }
}
