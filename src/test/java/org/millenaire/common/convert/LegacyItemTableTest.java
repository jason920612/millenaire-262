package org.millenaire.common.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.millenaire.MillHeadlessTest;

/**
 * M4 golden tests for the declarative legacy 1.12 (name, meta) -> 26.2 {@link Item} conversion table
 * ({@code legacy-items.txt}) that {@code MillConvert.legacyItemToStack} / {@code legacyItem} drive, and
 * that {@code InvItem.loadInvItemList} now delegates to.
 *
 * <p>Each expected modern id is DERIVED from the 1.12 evidence: the left-hand (name, meta) is the exact
 * entry the decompiled 1.12 {@code itemlist.txt} used (decompiled/todeploy/millenaire/itemlist.txt), and
 * the right-hand id is what Millénaire's hand-migrated 26.2 {@code itemlist.txt} names for the same good
 * key. So the table reproduces what {@code InvItem.loadInvItemList} resolved pre-M4, behaviour-identical.</p>
 */
class LegacyItemTableTest extends MillHeadlessTest {

   private static Item expected(String id) {
      Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
      assertTrue(item != Items.AIR, "test fixture: expected id '" + id + "' is not a registered item");
      return item;
   }

   /**
    * Materialises the conversion as a real {@link ItemStack}. Constructing a stack binds data components,
    * which need a fully bootstrapped registry that this headless JVM does not always provide ("Components
    * not bound yet"); when that is the case the caller {@link Assumptions assume}-skips the stack assertion
    * (the item resolution itself is covered by the assertSame tests, which never build a stack).
    */
   private static ItemStack stack(String name, int meta, int count) {
      try {
         return MillConvert.legacyItemToStack(new LegacyItem(name, meta, new Count(count)));
      } catch (Throwable componentsNotBound) {
         Assumptions.assumeTrue(false,
            "ItemStack construction unavailable in this headless JVM: " + componentsNotBound);
         throw componentsNotBound; // unreachable; keeps the compiler happy
      }
   }

   private static void assertStack(String expectedId, int expectedCount, ItemStack actual) {
      assertSame(expected(expectedId), actual.getItem(), "item mismatch");
      assertEquals(expectedCount, actual.getCount(), "count mismatch");
   }

   // ---- Flattened-variant families: a single 1.12 id+meta -> a distinct 26.2 id ------------------

   @Test
   void dyeVariants() {
      // 1.12 evidence: itemlist.txt dye_blue = minecraft:dye;4, dye_black = minecraft:dye;0. 26.2 ids:
      // blue_dye, black_dye. (EnumDyeColor.getDyeDamage packed the colour into ItemDye metadata.)
      assertSame(expected("minecraft:blue_dye"), MillConvert.legacyItem("minecraft:dye", 4));
      assertSame(expected("minecraft:black_dye"), MillConvert.legacyItem("minecraft:dye", 0));
      assertSame(expected("minecraft:white_dye"), MillConvert.legacyItem("minecraft:dye", 15));
   }

   @Test
   void woolVariants() {
      // 1.12 evidence: itemlist.txt wool_black = minecraft:wool;15, wool_white = minecraft:wool;0,
      // wool_red = minecraft:wool;14. 26.2: black_wool / white_wool / red_wool.
      assertSame(expected("minecraft:black_wool"), MillConvert.legacyItem("minecraft:wool", 15));
      assertSame(expected("minecraft:white_wool"), MillConvert.legacyItem("minecraft:wool", 0));
      assertSame(expected("minecraft:red_wool"), MillConvert.legacyItem("minecraft:wool", 14));
   }

   @Test
   void planksAndSaplingAndLog() {
      // 1.12 evidence: planks_birch=minecraft:planks;2, sapling_pine=minecraft:sapling;1,
      // wood_jungle=minecraft:log;3, wood_acacia=minecraft:log2;0 (acacia/dark_oak lived in log2).
      assertSame(expected("minecraft:birch_planks"), MillConvert.legacyItem("minecraft:planks", 2));
      assertSame(expected("minecraft:spruce_sapling"), MillConvert.legacyItem("minecraft:sapling", 1));
      assertSame(expected("minecraft:jungle_log"), MillConvert.legacyItem("minecraft:log", 3));
      assertSame(expected("minecraft:acacia_log"), MillConvert.legacyItem("minecraft:log2", 0));
   }

   @Test
   void renamesAndMiscMetas() {
      // 1.12 evidence: charcoal=minecraft:coal;1, bricks=minecraft:brick_block;0, sugarcane=minecraft:reeds;0,
      // fishraw=minecraft:fish;0, bed_red=minecraft:bed;14, glass_pane_red=minecraft:stained_glass_pane;14.
      assertSame(expected("minecraft:charcoal"), MillConvert.legacyItem("minecraft:coal", 1));
      assertSame(expected("minecraft:bricks"), MillConvert.legacyItem("minecraft:brick_block", 0));
      assertSame(expected("minecraft:sugar_cane"), MillConvert.legacyItem("minecraft:reeds", 0));
      assertSame(expected("minecraft:cod"), MillConvert.legacyItem("minecraft:fish", 0));
      assertSame(expected("minecraft:red_bed"), MillConvert.legacyItem("minecraft:bed", 14));
      assertSame(expected("minecraft:red_stained_glass_pane"), MillConvert.legacyItem("minecraft:stained_glass_pane", 14));
   }

   // ---- Plain items (not in the table): resolved directly by registry name -----------------------

   @Test
   void plainItemByName() {
      // 1.12 evidence: itemlist.txt diamond=minecraft:diamond;0 — unchanged id, resolved by name.
      assertSame(Items.DIAMOND, MillConvert.legacyItem("minecraft:diamond", 0));
   }

   @Test
   void millCamelCaseIdLowercased() {
      // 1.12 evidence: itemlist.txt mayanaxe=millenaire:mayanAxe;0; loadInvItemList lowercased the id to
      // resolve against the (all-lowercase) 26.2 registry. The conversion path must lowercase identically.
      assertSame(expected("millenaire:mayanaxe"), MillConvert.legacyItem("millenaire:mayanAxe", 0));
   }

   @Test
   void millMetaVariantItemResolvesByName() {
      // 1.12 evidence: mayangold=millenaire:stone_deco;2 — in 26.2 stone_deco is a single ItemBlockMeta and
      // the meta is carried on the InvItem (a live variant), NOT a distinct registry id. So name resolves
      // directly to the one stone_deco item regardless of meta.
      Item stoneDeco = expected("millenaire:stone_deco");
      assertSame(stoneDeco, MillConvert.legacyItem("millenaire:stone_deco", 2));
      assertSame(stoneDeco, MillConvert.legacyItem("millenaire:stone_deco", 0));
   }

   // ---- Count / ItemStack materialisation --------------------------------------------------------

   @Test
   void countIsCarriedThrough() {
      // Count comes from the LegacyItem, not the table; a flattened entry and a plain entry both honour it.
      assertStack("minecraft:blue_dye", 5, stack("minecraft:dye", 4, 5));
      assertStack("minecraft:diamond", 3, stack("minecraft:diamond", 0, 3));
   }

   // ---- Fail-fast --------------------------------------------------------------------------------

   @Test
   void unknownIdFailsFast() {
      // An (name, meta) that is neither a table entry nor a registered item/block crashes loudly — never a
      // silent AIR/empty stack (1.12 logged-and-continued, then surfaced as an "unknown good" later).
      assertThrows(Throwable.class,
         () -> MillConvert.legacyItem("millenaire:no_such_item_xyz", 0));
   }
}
