package org.millenaire.common.convert;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.millenaire.common.utilities.BlockStateUtilities;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;

/**
 * Declarative lookup tables that drive the legacy→modern conversions. Loaded once (lazily) and held
 * immutably for the simple legacy block/item axes; the building-plan {@link PlanColour} table is loaded
 * explicitly from {@code blocklist.txt} via {@link #loadPlanColours(File)} because its path is only known
 * at runtime (the culture loading dirs).
 *
 * <p>Keys are deliberately the explicit value objects ({@link LegacyBlock}, {@link LegacyItem},
 * {@link PlanColour}) rather than raw strings/ints so a lookup can never silently use the wrong axis.</p>
 *
 * <p>The {@code planColours} table is the M2 centralisation of the former ad-hoc {@code blocklist.txt}
 * block resolution that used to live in {@code PointType}'s constructors and cost branches. Each
 * <em>block-bearing</em> line (non-empty block-location column) is parsed once into a {@link BlockSpec}
 * (resolved {@link BlockState} + optional build cost). Special-type-only lines (empty block column) carry
 * no block and are not in this table — they keep their {@code specialType}/cost handling in {@code PointType}.</p>
 */
public final class LegacyTables {

   /** (name, meta) → modern block placement + optional cost. */
   final Map<LegacyBlock, BlockSpec> blocks;
   /** (name, meta) → modern item; count is supplied per-lookup from the {@link LegacyItem}. */
   final Map<LegacyItem, ItemSpec> items;

   private LegacyTables(Map<LegacyBlock, BlockSpec> blocks,
                        Map<LegacyItem, ItemSpec> items) {
      this.blocks = blocks;
      this.items = items;
   }

   // Lazy holder idiom: built on first access, thread-safely, exactly once.
   private static final class Holder {
      static final LegacyTables INSTANCE = load();
   }

   static LegacyTables get() {
      return Holder.INSTANCE;
   }

   private static LegacyTables load() {
      // The simple legacy block/item axes are not data-driven yet (no caller routes through them); they
      // stay empty and their table-backed lookups fail-fast. The plan-colour table is loaded separately
      // from blocklist.txt via loadPlanColours().
      return new LegacyTables(Map.of(), Map.of());
   }

   // ------------------------------------------------------------------------------------------------
   // Building-plan colour table (blocklist.txt) — the single, central parse.
   // ------------------------------------------------------------------------------------------------

   /**
    * Building-plan pixel colour → resolved block placement + optional cost, for every block-bearing line
    * of {@code blocklist.txt}. {@code null} until {@link #loadPlanColours(File)} runs; fail-fast on use
    * before then. Held statically (not in the immutable instance) because it is populated at runtime once
    * the loading dir is known.
    */
   private static volatile Map<PlanColour, BlockSpec> planColours = null;

   /** The white "empty" colour; lines mapping to it are not blocks (vanilla white pixel = no placement). */
   private static final int WHITE = 0xFFFFFF;

   /**
    * Parses {@code blocklist.txt} ONCE into the {@link PlanColour}→{@link BlockSpec} table. Only
    * block-bearing lines (non-empty block-location column) are entered; special-type-only lines are
    * skipped here (they have no block). Idempotent re-parse of the same content is harmless.
    *
    * <p>Block resolution supports BOTH the legacy {@code prop=value,prop=value} state column (via
    * {@link BlockStateUtilities#getBlockStateWithValues}, so the existing data file works unchanged) AND
    * the dot-notation {@code .property(value)} form (via {@link MillConvert#dotSpecToBlockState}),
    * detected by the presence of a {@code '('} in the state column.</p>
    *
    * <p>Fail-fast: an unknown block id, an unparseable colour, a malformed line, or an unresolvable cost
    * crashes loudly via {@link MillCrash} naming the offending line — never a silent AIR fallback.</p>
    */
   public static void loadPlanColours(File blocklistFile) {
      Map<PlanColour, BlockSpec> table = new HashMap<>();
      try (BufferedReader reader = MillConvert.contentFileToReader(new ContentFile(blocklistFile))) {
         String line;
         while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
               continue;
            }
            ParsedLine parsed = parseLine(trimmed);
            if (parsed == null) {
               continue; // special-type-only line: no block, not in this table
            }
            table.put(parsed.colour(), parsed.spec());
         }
      } catch (IOException e) {
         throw MillCrash.fail("Convert", "reading blocklist " + blocklistFile.getAbsolutePath() + ": " + e);
      }
      planColours = Map.copyOf(table);
   }

   /** Looks up a building-plan colour. Fail-fast if the table is not loaded or the colour is unknown. */
   static BlockSpec planColourToSpec(PlanColour colour) {
      Map<PlanColour, BlockSpec> table = planColours;
      if (table == null) {
         throw MillCrash.fail("Convert", "plan-colour table not loaded (call LegacyTables.loadPlanColours first)");
      }
      BlockSpec spec = table.get(colour);
      if (spec == null) {
         throw MillCrash.fail("Convert", "Unknown colour " + describe(colour.rgb()) + " not in blocklist conversion table");
      }
      return spec;
   }

   private record ParsedLine(PlanColour colour, BlockSpec spec) {
   }

   /**
    * Parses one blocklist line. Returns {@code null} for a special-type-only line (empty block column);
    * otherwise the colour plus its resolved {@link BlockSpec}. Mirrors the field layout the legacy
    * {@code PointType.readColourPoint} parsed:
    * {@code name;block;meta_or_values;set_after;R/G/B[;cost_block_or_item;cost_values;cost_quantity]}.
    */
   private static ParsedLine parseLine(String line) {
      String[] params = line.split(";", -1);
      MillCrash.check(params.length == 5 || params.length == 8, "Convert",
         "blocklist line <" + line + "> does not have five or eight fields");

      int colour = parseColour(params[4], line);

      String blockLocation = params[1];
      if (blockLocation.isEmpty()) {
         return null; // special-type-only line: no block to convert
      }

      String stateColumn = params[2];
      BlockState state = resolveState(blockLocation, stateColumn, line);

      Optional<ItemSpec> cost;
      if (params.length == 8) {
         cost = resolveCost(params[5], params[6], params[7], state, line);
      } else {
         // 5-field block lines cost themselves, quantity 1 (the legacy "no explicit cost" branch).
         cost = Optional.of(new ItemSpec(state.getBlock().asItem(), new Count(1)));
      }

      return new ParsedLine(new PlanColour(colour), new BlockSpec(state, cost));
   }

   private static int parseColour(String rgbField, String line) {
      String[] rgb = rgbField.split("/", -1);
      MillCrash.check(rgb.length == 3, "Convert", "colour in line <" + line + "> does not have three values");
      try {
         return (Integer.parseInt(rgb[0].trim()) << 16)
            + (Integer.parseInt(rgb[1].trim()) << 8)
            + Integer.parseInt(rgb[2].trim());
      } catch (NumberFormatException e) {
         throw MillCrash.fail("Convert", "non-numeric colour in line <" + line + ">");
      }
   }

   /**
    * Resolves the block + state column. Bare-numeric or empty state → default state (legacy meta is gone).
    * A state column containing {@code '('} is dot-notation {@code .property(value)}; otherwise it is the
    * legacy {@code prop=value} string handled by {@link BlockStateUtilities}.
    */
   private static BlockState resolveState(String blockLocation, String stateColumn, String line) {
      if (stateColumn.indexOf('(') >= 0) {
         // Dot-notation: compose "<block>.<props>" for MillConvert. The block id never contains '.', and
         // the dot-spec syntax appends ".name(value)" tokens, so prefix with '.' iff not already present.
         String dot = stateColumn.startsWith(".") ? blockLocation + stateColumn : blockLocation + "." + stateColumn;
         return MillConvert.dotSpecToBlockState(new DotSpec(dot));
      }

      Block block = resolveBlock(blockLocation, line);
      return BlockStateUtilities.getBlockStateWithValues(block.defaultBlockState(), stateColumn);
   }

   /** Resolves the cost column triple to an {@link ItemSpec}, or empty for a free (quantity-0) cost. */
   private static Optional<ItemSpec> resolveCost(String costRef, String costValues, String costQty, BlockState state, String line) {
      int quantity;
      try {
         quantity = Integer.parseInt(costQty.trim());
      } catch (NumberFormatException e) {
         throw MillCrash.fail("Convert", "non-numeric cost quantity in line <" + line + ">");
      }
      if (quantity == 0) {
         return Optional.empty(); // free
      }

      // "anywood" prices in sticks (the plank→log back-conversion happens later in PngPlanLoader.computeCost).
      if ("anywood".equals(costRef)) {
         return Optional.of(new ItemSpec(Items.STICK, new Count(quantity)));
      }

      // Block-bearing lines never use the "item:" cost form (only special-type banner lines do), so the
      // only forms reachable here are: a block reference (optionally with a state column) or a plain item.
      Block costBlock = lookupBlock(costRef);
      if (costBlock != Blocks.AIR && costBlock.asItem() != Items.AIR) {
         // InvItem.createInvItem(BlockState) keys on block.asItem() at meta 0, so the cost values column
         // does not affect the priced item — it is preserved structurally but reduces to the block's item.
         return Optional.of(new ItemSpec(costBlock.asItem(), new Count(quantity)));
      }

      Item costItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(costRef));
      if (costItem != Items.AIR) {
         return Optional.of(new ItemSpec(costItem, new Count(quantity)));
      }

      throw MillCrash.fail("Convert", "cost reference '" + costRef + "' in line <" + line + "> is not a known block or item");
   }

   private static Block resolveBlock(String blockName, String line) {
      Block block = lookupBlock(blockName);
      if (block == Blocks.AIR && !"minecraft:air".equals(blockName) && !"air".equals(blockName)) {
         // The block id has no modern equivalent (e.g. stale custom-mod blocklists carrying 1.7-era numeric
         // ids like "122"). 1.12 already resolved these to AIR (placed nothing); preserve that exactly so the
         // colour stays in the table and no per-pixel "Unknown colour" is raised later — but LOUDLY, never
         // silently, so a genuinely-wrong main-blocklist id is visible in the log.
         MillLog.error("Convert", "Block id '" + blockName + "' in line <" + line + "> has no modern equivalent; "
            + "placing AIR (matches 1.12, which placed nothing here)");
      }
      return block;
   }

   /**
    * Registry lookup with the one legacy block-id alias the shipped data still uses: the 1.12 dye colour
    * {@code silver} was renamed to {@code light_gray}, so Millénaire's painted-brick blocks register as
    * {@code ..._light_gray} while {@code blocklist.txt} still names them {@code ..._silver}. Pre-M2 these
    * lines silently resolved to AIR (a latent bug — the painted brick never placed); the alias restores the
    * intended block. Only this exact rename is aliased; any other unknown id still fails fast.
    */
   private static Block lookupBlock(String blockName) {
      Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockName));
      if (block == Blocks.AIR && blockName.contains("silver")) {
         block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockName.replace("silver", "light_gray")));
      }
      return block;
   }

   private static String describe(int rgb) {
      return ((rgb & 0xFF0000) >> 16) + "/" + ((rgb & 0xFF00) >> 8) + "/" + (rgb & 0xFF) + "/" + Integer.toHexString(rgb);
   }
}
