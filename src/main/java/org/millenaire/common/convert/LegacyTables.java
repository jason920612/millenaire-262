package org.millenaire.common.convert;

import java.util.Map;

/**
 * Declarative lookup tables that drive the legacy→modern conversions. Loaded once (lazily) and held
 * immutably. M0 ships the API shape with EMPTY tables — the real data is populated in M2; until then
 * the table-backed {@link MillConvert} methods fail-fast for any key.
 *
 * <p>Keys are deliberately the explicit value objects ({@link LegacyBlock}, {@link LegacyItem},
 * {@link PlanColour}) rather than raw strings/ints so a lookup can never silently use the wrong axis.</p>
 */
final class LegacyTables {

   /** (name, meta) → modern block placement + optional cost. */
   final Map<LegacyBlock, BlockSpec> blocks;
   /** (name, meta) → modern item; count is supplied per-lookup from the {@link LegacyItem}. */
   final Map<LegacyItem, ItemSpec> items;
   /** building-plan pixel colour → modern block placement + optional cost. */
   final Map<PlanColour, BlockSpec> planColours;

   private LegacyTables(Map<LegacyBlock, BlockSpec> blocks,
                        Map<LegacyItem, ItemSpec> items,
                        Map<PlanColour, BlockSpec> planColours) {
      this.blocks = blocks;
      this.items = items;
      this.planColours = planColours;
   }

   // Lazy holder idiom: built on first access, thread-safely, exactly once.
   private static final class Holder {
      static final LegacyTables INSTANCE = load();
   }

   static LegacyTables get() {
      return Holder.INSTANCE;
   }

   private static LegacyTables load() {
      // M2: populate from legacy-blocks table (the 1.12 block/meta → 26.2 BlockState mapping derived
      //     from the decompiled BlockItemUtilities / building-plan colour map). For M0 the tables are
      //     intentionally empty so the conversion API shape is real and tested while the dot-notation
      //     parser carries the actual logic; table-backed lookups fail-fast until the data lands.
      return new LegacyTables(Map.of(), Map.of(), Map.of());
   }
}
