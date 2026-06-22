package org.millenaire.common.convert;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import org.millenaire.common.utilities.MillCrash;

/**
 * Unified Millénaire conversion protocol (M0).
 *
 * <p>One place that turns the various legacy/external representations of a block, item or content file
 * into the modern 26.2 equivalents:</p>
 * <ul>
 *   <li>{@link #dotSpecToBlockState(DotSpec)} — the dot-notation block-state parser (the core; fully
 *       implemented here).</li>
 *   <li>{@link #contentFileToReader(ContentFile)} — UTF-8 / Windows-1252 encoding detection.</li>
 *   <li>{@link #legacyBlockToSpec(LegacyBlock)}, {@link #legacyItemToStack(LegacyItem)},
 *       {@link #planColourToSpec(PlanColour)} — table-backed (data lands in M2).</li>
 * </ul>
 *
 * <p>Fail-fast throughout: any unresolvable name, unknown property or out-of-range value crashes
 * loudly via {@link MillCrash} rather than silently degrading to a wrong block.</p>
 */
public final class MillConvert {

   private MillConvert() {
   }

   // ------------------------------------------------------------------------------------------------
   // 1. Dot-notation block-state parser
   // ------------------------------------------------------------------------------------------------

   /**
    * Resolves a dot-notation block spec to a concrete {@link BlockState}.
    *
    * <p>Syntax: {@code <block>.<property>(<value>).<property>(<value>)…}, e.g.
    * {@code oak_stairs.facing(south).half(top)}, {@code wall_torch.facing(east)},
    * {@code oak_log.axis(y)}, {@code red_wool} (no properties).</p>
    *
    * <p>Numeric (integer) properties are NEVER a bare number: use {@code value/declaredMax} (validated
    * against the property's real max) or the keyword {@code min} / {@code max}.</p>
    */
   public static BlockState dotSpecToBlockState(DotSpec spec) {
      MillCrash.check(spec != null && spec.text() != null && !spec.text().isBlank(),
         "Convert", "empty dot-spec");
      String text = spec.text().trim();

      // The block id is everything before the FIRST '.' (block ids never contain '.'; property tokens
      // and their values are appended as ".name(value)").
      int firstDot = text.indexOf('.');
      String blockName = firstDot < 0 ? text : text.substring(0, firstDot);

      Block block = resolveBlock(blockName, text);
      BlockState state = block.defaultBlockState();

      if (firstDot < 0) {
         return state; // no properties
      }

      for (PropToken token : parseTokens(text.substring(firstDot + 1), text)) {
         state = applyProperty(state, block, token, text);
      }
      return state;
   }

   private static Block resolveBlock(String blockName, String spec) {
      Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockName));
      // getValue never returns null in 26.2 (missing → AIR default), so detect AIR explicitly. "air" is a
      // legitimate spec; anything else resolving to AIR is an unknown block id.
      if (block == Blocks.AIR && !"minecraft:air".equals(blockName) && !"air".equals(blockName)) {
         throw MillCrash.fail("Convert", spec + ": unknown block '" + blockName + "'");
      }
      return block;
   }

   /** One {@code name(value)} property token. */
   private record PropToken(String name, String value) {
   }

   /**
    * Splits {@code facing(south).half(top)} into ordered {@code (name,value)} tokens. Robust: a value
    * is the text between '(' and the matching ')'; tokens are separated by the '.' that follows a ')'.
    * Property values contain no nested parentheses, so a simple paren-depth scan suffices.
    */
   private static List<PropToken> parseTokens(String props, String spec) {
      List<PropToken> tokens = new ArrayList<>();
      int i = 0;
      int n = props.length();
      while (i < n) {
         int open = props.indexOf('(', i);
         MillCrash.check(open >= 0, "Convert", spec + ": malformed property token near '" + props.substring(i) + "'");
         String name = props.substring(i, open).trim();
         MillCrash.check(!name.isEmpty(), "Convert", spec + ": empty property name");

         int close = props.indexOf(')', open + 1);
         MillCrash.check(close >= 0, "Convert", spec + ": unclosed '(' in property '" + name + "'");
         String value = props.substring(open + 1, close).trim();
         MillCrash.check(!value.isEmpty(), "Convert", spec + ": empty value for property '" + name + "'");

         tokens.add(new PropToken(name, value));

         // Advance past ')'; the next token (if any) must be introduced by '.'.
         i = close + 1;
         if (i < n) {
            MillCrash.check(props.charAt(i) == '.', "Convert",
               spec + ": expected '.' after property '" + name + "' but found '" + props.charAt(i) + "'");
            i++;
         }
      }
      return tokens;
   }

   private static BlockState applyProperty(BlockState state, Block block, PropToken token, String spec) {
      Property<?> property = block.getStateDefinition().getProperty(token.name());
      if (property == null) {
         throw MillCrash.fail("Convert", spec + ": block '" + block + "' has no property '" + token.name() + "'");
      }
      if (property instanceof IntegerProperty intProp) {
         int resolved = resolveIntegerValue(intProp, token.name(), token.value(), spec);
         return state.setValue(intProp, resolved);
      }
      return setNonIntegerValue(state, property, token.name(), token.value(), spec);
   }

   /** Enum / boolean property: {@code property.getValue(value)} → empty means the value is invalid. */
   private static <T extends Comparable<T>> BlockState setNonIntegerValue(
         BlockState state, Property<T> property, String name, String value, String spec) {
      Optional<T> parsed = property.getValue(value);
      if (parsed.isEmpty()) {
         throw MillCrash.fail("Convert", spec + ": property '" + name + "' rejects '" + value + "'");
      }
      return state.setValue(property, parsed.get());
   }

   /**
    * Resolves an integer-property value. Bare numbers are forbidden; callers must write
    * {@code value/declaredMax} (validated) or the keyword {@code min} / {@code max}.
    */
   private static int resolveIntegerValue(IntegerProperty property, String name, String value, String spec) {
      int min = Integer.MAX_VALUE;
      int max = Integer.MIN_VALUE;
      for (Integer v : property.getPossibleValues()) {
         min = Math.min(min, v);
         max = Math.max(max, v);
      }

      if ("max".equals(value)) {
         return max;
      }
      if ("min".equals(value)) {
         return min;
      }

      int slash = value.indexOf('/');
      if (slash < 0) {
         throw MillCrash.fail("Convert", spec + ": integer property '" + name
            + "' needs value/max or min/max (got bare '" + value + "'); use e.g. '" + value + "/" + max + "'");
      }

      int requested = parseInt(value.substring(0, slash), name, value, spec);
      int declaredMax = parseInt(value.substring(slash + 1), name, value, spec);

      if (declaredMax != max) {
         throw MillCrash.fail("Convert", spec + ": property '" + name + "' wrong max: wrote /"
            + declaredMax + " but '" + name + "' max is " + max);
      }
      if (requested < min || requested > max) {
         throw MillCrash.fail("Convert", spec + ": property '" + name + "' value " + requested
            + " out of range [" + min + "," + max + "]");
      }
      return requested;
   }

   private static int parseInt(String s, String name, String value, String spec) {
      try {
         return Integer.parseInt(s.trim());
      } catch (NumberFormatException e) {
         throw MillCrash.fail("Convert", spec + ": property '" + name + "' has non-numeric part in '" + value + "'");
      }
   }

   // ------------------------------------------------------------------------------------------------
   // 2. Content-file reader (UTF-8 → Windows-1252 detection)
   // ------------------------------------------------------------------------------------------------

   /**
    * Opens a content file as a {@link BufferedReader}, detecting its encoding per-file. This is the
    * canonical version of the former {@code MillCommonUtilities.getReader}.
    *
    * <p>Strict UTF-8 is tried first: modern / translation content (including CJK like the Chinese
    * villager speech) decodes cleanly. On a {@link CharacterCodingException} the bytes are decoded as
    * Windows-1252 — legacy Millénaire content (culture name lists, sentences, dialogues) uses single-byte
    * accents (e.g. ü=0xFC) that are invalid UTF-8 and produced the "Türkoglu"→"T?rkoglu" mojibake.</p>
    */
   public static BufferedReader contentFileToReader(ContentFile contentFile) throws IOException {
      byte[] bytes;
      try (FileInputStream fis = new FileInputStream(contentFile.file())) {
         bytes = fis.readAllBytes();
      }

      String content;
      try {
         content = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString();
      } catch (CharacterCodingException notUtf8) {
         content = new String(bytes, Charset.forName("windows-1252"));
      }

      return new BufferedReader(new StringReader(content));
   }

   // ------------------------------------------------------------------------------------------------
   // 3. Table-backed conversions (API shape only; data lands in M2)
   // ------------------------------------------------------------------------------------------------

   /**
    * Maps a 1.12 {@code (name, meta)} block to a modern placement + optional cost.
    *
    * <p>Resolution order, both branches identical to the 1.12 {@code Block.getStateFromMeta(meta)} the
    * former {@code WorldUtilities.legacyMetaToBlockState} reproduced:</p>
    * <ol>
    *   <li><b>Fixed-variant families</b> (logs/slabs/stairs/ladders/torches/beds): the exact
    *       {@code name:meta} pair is looked up in the declarative {@code legacy-blocks.txt} table.</li>
    *   <li><b>Unbounded numeric families</b> (crop {@code AGE_7}, nether-wart {@code AGE_3}, farmland
    *       {@code MOISTURE}, generic pillar {@code AXIS}): the meta is applied property-driven via
    *       {@link #applyNumericMeta}, since their meta space cannot be enumerated by name.</li>
    * </ol>
    *
    * <p>Fail-fast: a {@code (name, meta)} that is in neither branch (i.e. an unknown legacy block or a
    * meta the family does not support) crashes loudly via {@link MillCrash} naming it — never a silent
    * AIR fallback.</p>
    */
   public static BlockSpec legacyBlockToSpec(LegacyBlock legacy) {
      BlockSpec spec = LegacyTables.get().blocks.get(legacy);
      if (spec != null) {
         return spec;
      }
      // Not a fixed-variant entry: resolve the modern block by name and apply the meta property-driven.
      Block block = resolveBlock(legacy.name(), legacy.name() + ":" + legacy.meta());
      BlockState state = applyNumericMeta(block, legacy.meta(), legacy.toString());
      return new BlockSpec(state, Optional.of(new ItemSpec(state.getBlock().asItem(), new Count(1))));
   }

   /**
    * Convenience: the resolved modern {@link BlockState} for a 1.12 {@code (name, meta)} block. This is
    * the single entry point the building system / {@code WorldUtilities} use in place of the former
    * ad-hoc meta switch.
    */
   public static BlockState blockState(String name, int meta) {
      return legacyBlockToSpec(new LegacyBlock(name, meta)).state();
   }

   /**
    * The single entry point {@code WorldUtilities.setBlockAndMetadata} uses when it already holds a
    * resolved modern {@link Block} (the building system stores 26.2 blocks, not legacy names) and a 1.12
    * meta. Tries the declarative fixed-variant table by the block's registry name first, then falls back
    * to the property-driven numeric families. Behaviour-identical to the former
    * {@code WorldUtilities.legacyMetaToBlockState}.
    */
   public static BlockState blockState(Block block, int meta) {
      Identifier id = BuiltInRegistries.BLOCK.getKey(block);
      if (id != null) {
         LegacyBlock byName = new LegacyBlock(id.getPath(), meta);
         BlockSpec spec = LegacyTables.get().blocks.get(byName);
         if (spec != null) {
            return spec.state();
         }
      }
      return applyNumericMeta(block, meta, block + ":" + meta);
   }

   /**
    * Applies an unbounded numeric 1.12 meta to a block's matching modern property. Covers exactly the
    * families the former {@code WorldUtilities.legacyMetaToBlockState} did: crop {@code AGE_3} /
    * {@code AGE_7}, farmland {@code MOISTURE}, and generic pillar {@code AXIS} (meta&12: Y=0, X=4, Z=8).
    * A {@code meta <= 0} keeps the default state (the 1.12 fast path). Any meta on a block with none of
    * these properties keeps the default state too — matching 1.12, where {@code getStateFromMeta} on a
    * block whose meta is unused returns the default.
    */
   private static BlockState applyNumericMeta(Block block, int meta, String spec) {
      BlockState state = block.defaultBlockState();
      if (meta <= 0) {
         return state;
      }
      var AGE3 = net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_3;
      var AGE7 = net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7;
      var MOIST = net.minecraft.world.level.block.state.properties.BlockStateProperties.MOISTURE;
      var AXIS = net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS;
      try {
         if (state.hasProperty(AGE3)) {
            return state.setValue(AGE3, Math.min(meta, 3));
         }
         if (state.hasProperty(AGE7)) {
            return state.setValue(AGE7, Math.min(meta, 7));
         }
         if (state.hasProperty(MOIST)) {
            return state.setValue(MOIST, Math.min(meta & 7, 7));
         }
         if (state.hasProperty(AXIS)) {
            net.minecraft.core.Direction.Axis axis = (meta & 12) == 4
               ? net.minecraft.core.Direction.Axis.X
               : (meta & 12) == 8 ? net.minecraft.core.Direction.Axis.Z : net.minecraft.core.Direction.Axis.Y;
            return state.setValue(AXIS, axis);
         }
      } catch (Exception metaMappingException) {
         // FAIL-FAST: every setValue above is guarded by hasProperty and range-clamped, so a throw here
         // means an unexpected property mismatch that would silently reset the block — surface it loudly.
         throw MillCrash.fail("Convert", spec + ": failed to apply legacy meta " + meta + ": " + metaMappingException);
      }
      return state;
   }

   /**
    * Reconstructs the 1.12 "block metadata" int from a 26.2 {@link BlockState}, using the same bit-packing
    * the legacy vanilla block used in {@code getMetaFromState}. This is the documented inverse of
    * {@link #blockState(Block, int)} / {@link #blockState(String, int)}: it lets ported call sites that still
    * think in terms of legacy meta (and the building export, which persists schematics as block+meta) keep
    * orientation/variant/age information.
    *
    * <p>Pure function: BlockState in, int out — no world/level access. Only the encodings actually relied
    * upon by Millénaire are reconstructed; everything else falls back to 0. Behaviour-identical to the former
    * {@code WorldUtilities.blockStateToLegacyMeta} inline switch (now a thin delegate to here).</p>
    */
   public static int blockStateToLegacyMeta(BlockState state) {
      if (state == null) {
         return 0;
      }

      net.minecraft.world.level.block.Block block = state.getBlock();

      // --- Age / growth (crops, nether wart, cocoa) -------------------------------------------------
      if (state.hasProperty(net.minecraft.world.level.block.NetherWartBlock.AGE)) {
         return state.getValue(net.minecraft.world.level.block.NetherWartBlock.AGE);
      }
      if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7)) {
         return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7);
      }
      if (state.hasProperty(net.minecraft.world.level.block.FarmlandBlock.MOISTURE)) {
         return state.getValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE);
      }

      // --- Wool: legacy meta == DyeColor ordinal (variants are distinct blocks now) -----------------
      int wool = woolColorMeta(block);
      if (wool >= 0) {
         return wool;
      }

      // --- Logs / pillars: meta&12 = axis (Y=0, X=4, Z=8) -------------------------------------------
      if (state.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)) {
         return switch (state.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)) {
            case X -> 4;
            case Z -> 8;
            default -> 0;
         };
      }

      // --- Stairs: 4*(half==TOP) | (5 - facing.get3DDataValue()) ------------------------------------
      if (block instanceof net.minecraft.world.level.block.StairBlock) {
         int i = 0;
         if (state.getValue(net.minecraft.world.level.block.StairBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.TOP) {
            i |= 4;
         }
         return i | 5 - state.getValue(net.minecraft.world.level.block.StairBlock.FACING).get3DDataValue();
      }

      // --- Ladder: meta = facing.get3DDataValue() (NORTH=2, SOUTH=3, WEST=4, EAST=5) -----------------
      if (block instanceof net.minecraft.world.level.block.LadderBlock) {
         return state.getValue(net.minecraft.world.level.block.LadderBlock.FACING).get3DDataValue();
      }

      // --- Torch: standing=5; wall torch EAST=1, WEST=2, SOUTH=3, NORTH=4 ---------------------------
      if (block instanceof net.minecraft.world.level.block.WallTorchBlock) {
         return switch (state.getValue(net.minecraft.world.level.block.WallTorchBlock.FACING)) {
            case EAST -> 1;
            case WEST -> 2;
            case SOUTH -> 3;
            case NORTH -> 4;
            default -> 5;
         };
      }
      if (block instanceof net.minecraft.world.level.block.TorchBlock) {
         return 5;
      }

      // --- Slab: TOP=8, BOTTOM=0 (variant bits removed; variants are distinct blocks) ---------------
      if (state.hasProperty(net.minecraft.world.level.block.SlabBlock.TYPE)) {
         return state.getValue(net.minecraft.world.level.block.SlabBlock.TYPE)
               == net.minecraft.world.level.block.state.properties.SlabType.TOP ? 8 : 0;
      }

      // --- Door: upper = 8 | (hinge RIGHT?1) | (powered?2); lower = facing.clockWise.2D | (open?4) ---
      if (block instanceof net.minecraft.world.level.block.DoorBlock) {
         int i = 0;
         if (state.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            i |= 8;
            if (state.getValue(net.minecraft.world.level.block.DoorBlock.HINGE) == net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT) {
               i |= 1;
            }
            if (state.getValue(net.minecraft.world.level.block.DoorBlock.POWERED)) {
               i |= 2;
            }
         } else {
            i |= state.getValue(net.minecraft.world.level.block.DoorBlock.FACING).getClockWise().get2DDataValue();
            if (state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) {
               i |= 4;
            }
         }
         return i;
      }

      // --- Bed: meta&8 = HEAD part (foot=0); meta&3 = facing.get2DDataValue() -----------------------
      if (block instanceof net.minecraft.world.level.block.BedBlock) {
         int i = state.getValue(net.minecraft.world.level.block.BedBlock.FACING).get2DDataValue();
         if (state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
            i |= 8;
         }
         return i;
      }

      // --- Lever: orientation index | (powered?8) ---------------------------------------------------
      if (block instanceof net.minecraft.world.level.block.LeverBlock) {
         int i = leverOrientationMeta(state);
         if (state.getValue(net.minecraft.world.level.block.LeverBlock.POWERED)) {
            i |= 8;
         }
         return i;
      }

      // --- Button: face/facing index | (powered?8) --------------------------------------------------
      if (block instanceof net.minecraft.world.level.block.ButtonBlock) {
         int i = buttonFacingMeta(state);
         if (state.getValue(net.minecraft.world.level.block.ButtonBlock.POWERED)) {
            i |= 8;
         }
         return i;
      }

      // --- Generic horizontal-facing blocks: meta = facing.get2DDataValue() -------------------------
      if (state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
         return state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).get2DDataValue();
      }
      if (state.hasProperty(net.minecraft.world.level.block.DirectionalBlock.FACING)) {
         return state.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING).get3DDataValue();
      }

      return 0;
   }

   /** Legacy wool dye meta for the 16 distinct 26.2 wool blocks, or -1 if not a wool block. */
   private static int woolColorMeta(net.minecraft.world.level.block.Block block) {
      // 26.2 wool is a ColorCollection (Blocks.WOOL.pick(DyeColor)), not 16 named constants. The 1.12 wool
      // meta == the dye id (white=0 … black=15), which DyeColor.getId() reproduces.
      for (net.minecraft.world.item.DyeColor c : net.minecraft.world.item.DyeColor.values()) {
         if (block == Blocks.WOOL.pick(c)) {
            return c.getId();
         }
      }
      return -1;
   }

   /** 1.12 BlockLever.EnumOrientation index for a 26.2 lever state. */
   private static int leverOrientationMeta(BlockState state) {
      net.minecraft.world.level.block.state.properties.AttachFace face =
         state.getValue(net.minecraft.world.level.block.LeverBlock.FACE);
      net.minecraft.core.Direction facing = state.getValue(net.minecraft.world.level.block.LeverBlock.FACING);
      if (face == net.minecraft.world.level.block.state.properties.AttachFace.FLOOR) {
         // UP_Z=5, UP_X=6 depending on facing axis
         return facing.getAxis() == net.minecraft.core.Direction.Axis.X ? 6 : 5;
      } else if (face == net.minecraft.world.level.block.state.properties.AttachFace.CEILING) {
         // DOWN_X=0, DOWN_Z=7 depending on facing axis
         return facing.getAxis() == net.minecraft.core.Direction.Axis.X ? 0 : 7;
      } else {
         return switch (facing) {
            case EAST -> 1;
            case WEST -> 2;
            case SOUTH -> 3;
            case NORTH -> 4;
            default -> 1;
         };
      }
   }

   /** 1.12 BlockButton facing index for a 26.2 button state. */
   private static int buttonFacingMeta(BlockState state) {
      net.minecraft.world.level.block.state.properties.AttachFace face =
         state.getValue(net.minecraft.world.level.block.ButtonBlock.FACE);
      net.minecraft.core.Direction facing = state.getValue(net.minecraft.world.level.block.ButtonBlock.FACING);
      if (face == net.minecraft.world.level.block.state.properties.AttachFace.FLOOR) {
         return facing.getAxis() == net.minecraft.core.Direction.Axis.X ? 1 : 5;
      } else if (face == net.minecraft.world.level.block.state.properties.AttachFace.CEILING) {
         return 0;
      } else {
         return switch (facing) {
            case EAST -> 1;
            case WEST -> 2;
            case SOUTH -> 3;
            case NORTH -> 4;
            default -> 1;
         };
      }
   }

   /**
    * Maps a 1.12 {@code (name, meta)} item to a modern {@link ItemStack} of the requested count. This is
    * the single entry point that replaced the ad-hoc {@code BuiltInRegistries.ITEM.getValue} /
    * {@code BLOCK.getValue} resolution in {@code InvItem.loadInvItemList}.
    *
    * <p>Resolution order, behaviour-identical to pre-M4:</p>
    * <ol>
    *   <li><b>Flattened-variant families</b> (dye/wool/carpet/glass-pane/planks/sapling/log + 1:1
    *       renames): the exact {@code name:meta} pair is looked up in the declarative
    *       {@code legacy-items.txt} table, which maps it to the distinct 26.2 registry id.</li>
    *   <li><b>Plain items</b> (not in the table): the {@code name} is resolved directly against the live
    *       item registry; the meta is irrelevant to the registry id (it is carried on the InvItem as a
    *       logical variant — e.g. Mill {@code stone_deco}/{@code wood_deco} — or as the {@code -1}
    *       wildcard). The 1.12 itemlist.txt id is lowercased for the modern registry (Identifier rejects
    *       upper-case), matching {@code loadInvItemList}.</li>
    * </ol>
    *
    * <p>Fail-fast: a {@code name} that resolves to neither a table entry nor a registered item/block
    * crashes loudly via {@link MillCrash} naming it — never a silent AIR/empty stack.</p>
    */
   public static ItemStack legacyItemToStack(LegacyItem legacy) {
      Item item = legacyItem(legacy.name(), legacy.meta());
      return new ItemStack(item, legacy.count().value());
   }

   /**
    * Resolves a 1.12 {@code (name, meta)} item to its modern {@link Item}, without a count. Used by both
    * {@link #legacyItemToStack(LegacyItem)} and {@code InvItem} when it needs only the resolved item.
    */
   public static Item legacyItem(String name, int meta) {
      // The legacy-items.txt flattening table keys on the bare 1.12 name (no "minecraft:" namespace, the
      // same convention as legacy-blocks.txt); itemlist.txt writes the vanilla ids with the prefix, so
      // strip a leading "minecraft:" for the table-key match. Mod-namespaced legacy ids (millenaire:...)
      // are never flattened (they resolve by name), so only the vanilla namespace is normalised.
      String tableName = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
      // Count is not part of the resolution; key the table lookup with a placeholder count.
      ItemSpec spec = LegacyTables.get().items.get(new LegacyItem(tableName, meta, new Count(0)));
      if (spec != null) {
         return spec.item();
      }
      // Not a flattened-variant entry: the modern registry id is the (lowercased) legacy name itself.
      return resolveItemByName(name, name + ":" + meta);
   }

   /**
    * Resolves a modern registry id (the right-hand side of a {@code legacy-items.txt} line, already a
    * 26.2 id) to its {@link Item}. Tries the item registry, then the block registry's item, failing fast
    * on an id that is neither.
    */
   static Item resolveItem(String modernId, String context) {
      return resolveItemByName(modernId, context);
   }

   private static Item resolveItemByName(String name, String context) {
      // 1.12 itemlist.txt carries camelCase ids (e.g. millenaire:normanAxe); 26.2 registry ids are all
      // lower-case and Identifier rejects [A-Z]. Lowercase so the legacy content resolves — exactly what
      // InvItem.loadInvItemList did before delegating here.
      String id = name.toLowerCase(java.util.Locale.ROOT);
      Identifier key = Identifier.parse(id);
      Item item = BuiltInRegistries.ITEM.getValue(key);
      if (item != net.minecraft.world.item.Items.AIR) {
         return item;
      }
      // Not a registered item id: try as a block id and take its BlockItem (1.12 fell back to Block too).
      Block block = BuiltInRegistries.BLOCK.getValue(key);
      if (block != Blocks.AIR) {
         Item blockItem = block.asItem();
         if (blockItem != net.minecraft.world.item.Items.AIR) {
            return blockItem;
         }
      }
      throw MillCrash.fail("Convert", context + ": unknown legacy item/block id '" + name + "'");
   }

   /**
    * Maps a building-plan pixel colour to a modern placement + optional cost, from the centrally-parsed
    * {@code blocklist.txt} table. The table must have been loaded via
    * {@link LegacyTables#loadPlanColours(java.io.File)} first; an unknown colour or unloaded table
    * crashes loudly via {@link MillCrash} rather than degrading to AIR.
    */
   public static BlockSpec planColourToSpec(PlanColour colour) {
      return LegacyTables.planColourToSpec(colour);
   }

   // ------------------------------------------------------------------------------------------------
   // 4. Goal-data block-state strings ("red_flower;type=poppy")
   // ------------------------------------------------------------------------------------------------

   /**
    * Resolves a goal-data block-state string of the form {@code <block>;<prop>=<value>,…} (the
    * {@code harvestblockstate} / {@code plantblockstate} / etc. parameters parsed by
    * {@code ValueIO.BlockStateIO} / {@code BlockStateAddIO}) to a concrete 26.2 {@link BlockState}.
    *
    * <p>This is the single point that applies the 1.12→26.2 <b>flower flattening</b>. In 1.12 the small
    * flowers were a single {@code red_flower} block with a {@code type} property and the single
    * {@code yellow_flower} block (dandelion); the tall flowers were a single {@code double_plant} block
    * with a {@code variant} property. 26.2 flattened every variant into its own block id. Goal data still
    * ships the 1.12 names, and {@code BuiltInRegistries.BLOCK.getValue} returns {@code AIR} (never null)
    * for an unknown id — so without this step a flower goal silently resolves to AIR and the farmer
    * harvests / plants nothing.</p>
    *
    * <p>Mapping (1.12-faithful):</p>
    * <ul>
    *   <li>{@code red_flower;type=poppy} → {@code minecraft:poppy}; {@code blue_orchid}/{@code allium}/
    *       {@code red_tulip}/{@code orange_tulip}/{@code white_tulip}/{@code pink_tulip}/{@code oxeye_daisy}
    *       map 1:1 to the same-named block; {@code houstonia} → {@code azure_bluet}.</li>
    *   <li>{@code yellow_flower} (with or without {@code type=dandelion}) → {@code minecraft:dandelion}.</li>
    *   <li>{@code double_plant;variant=…}: {@code sunflower}→{@code sunflower}, {@code syringa}→{@code lilac},
    *       {@code double_grass}→{@code tall_grass}, {@code double_fern}→{@code large_fern},
    *       {@code double_rose}→{@code rose_bush}, {@code paeonia}→{@code peony}. The 1.12 {@code half}
    *       (lower/upper) is carried over to the 26.2 {@code DoublePlantBlock} {@code half} property; the
    *       leftover {@code facing}/{@code variant} are dropped (facing was never used on these and variant
    *       is now encoded by the id). The lower half is kept as the plant/harvest target, exactly as 1.12
    *       did.</li>
    * </ul>
    *
    * <p>The same flattening also covers the other 1.12 ids the goal data still ships that 26.2 renamed
    * or split into per-variant ids: {@code stone;variant=…} (diorite/granite/andesite + polished, and
    * the no-op {@code variant=stone}) and {@code snow_layer} → {@code snow}. They are the identical
    * "unflattened id silently resolves to AIR" bug as the flowers, in the {@code sourceblockstate} mining
    * goals.</p>
    *
    * <p>Fail-fast: a flower / stone variant not in the mapping above, or any other id that still resolves
    * to AIR, crashes loudly via {@link MillCrash} naming it — never a silent AIR.</p>
    */
   public static BlockState goalBlockState(String value) {
      MillCrash.check(value != null && !value.isBlank(), "Convert", "empty goal block-state");
      String[] params = value.split(";", 2);
      String blockName = params[0].trim().toLowerCase(java.util.Locale.ROOT);
      String props = params.length > 1 ? params[1].trim() : "";

      String flattened = flattenLegacyGoalBlock(blockName, props, value);
      if (flattened != null) {
         // The flattening fully determines the id; only the carried-over properties (e.g. the
         // double-plant half) remain in 'props', scrubbed of the now-redundant variant/type/facing.
         blockName = flattened;
         props = residualProps(props);
      }

      Block block = resolveBlock(blockName, value);
      if (props.isEmpty()) {
         return block.defaultBlockState();
      }
      return org.millenaire.common.utilities.BlockStateUtilities.getBlockStateWithValues(block.defaultBlockState(), props);
   }

   /** 1.12 {@code red_flower} {@code type} → 26.2 flower block id. */
   private static final java.util.Map<String, String> RED_FLOWER = java.util.Map.of(
      "poppy", "poppy",
      "blue_orchid", "blue_orchid",
      "allium", "allium",
      "houstonia", "azure_bluet",
      "red_tulip", "red_tulip",
      "orange_tulip", "orange_tulip",
      "white_tulip", "white_tulip",
      "pink_tulip", "pink_tulip",
      "oxeye_daisy", "oxeye_daisy"
   );

   /** 1.12 {@code double_plant} {@code variant} → 26.2 tall-flower block id. */
   private static final java.util.Map<String, String> DOUBLE_PLANT = java.util.Map.of(
      "sunflower", "sunflower",
      "syringa", "lilac",
      "double_grass", "tall_grass",
      "double_fern", "large_fern",
      "double_rose", "rose_bush",
      "paeonia", "peony"
   );

   /** 1.12 {@code stone} {@code variant} → 26.2 stone block id (1.12 BlockStone.EnumType). */
   private static final java.util.Map<String, String> STONE_VARIANT = java.util.Map.of(
      "stone", "stone",
      "granite", "granite",
      "smooth_granite", "polished_granite",
      "diorite", "diorite",
      "smooth_diorite", "polished_diorite",
      "andesite", "andesite",
      "smooth_andesite", "polished_andesite"
   );

   /**
    * Returns the flattened 26.2 block id for a 1.12 goal-data block reference, or {@code null} if
    * {@code blockName} is not one of the legacy families that 26.2 split/renamed (so the caller resolves
    * it normally). Fails fast on a family member with an unknown / missing variant.
    */
   private static String flattenLegacyGoalBlock(String blockName, String props, String spec) {
      String name = blockName.startsWith("minecraft:") ? blockName.substring("minecraft:".length()) : blockName;
      switch (name) {
         case "red_flower" -> {
            String type = propValue(props, "type");
            MillCrash.check(type != null, "Convert", spec + ": red_flower without a 'type' property");
            String id = RED_FLOWER.get(type);
            MillCrash.check(id != null, "Convert", spec + ": unknown red_flower type '" + type + "'");
            return id;
         }
         case "yellow_flower" -> {
            // 1.12 had a single yellow_flower (dandelion); type is optional and, if present, must be dandelion.
            String type = propValue(props, "type");
            MillCrash.check(type == null || "dandelion".equals(type),
               "Convert", spec + ": unknown yellow_flower type '" + type + "'");
            return "dandelion";
         }
         case "double_plant" -> {
            String variant = propValue(props, "variant");
            MillCrash.check(variant != null, "Convert", spec + ": double_plant without a 'variant' property");
            String id = DOUBLE_PLANT.get(variant);
            MillCrash.check(id != null, "Convert", spec + ": unknown double_plant variant '" + variant + "'");
            return id;
         }
         case "stone" -> {
            // 1.12 stone with no variant is plain stone; with a variant it is the flattened per-variant id.
            String variant = propValue(props, "variant");
            if (variant == null) {
               return "stone";
            }
            String id = STONE_VARIANT.get(variant);
            MillCrash.check(id != null, "Convert", spec + ": unknown stone variant '" + variant + "'");
            return id;
         }
         case "snow_layer" -> {
            // 1.12 minecraft:snow_layer (the thin layer) → 26.2 minecraft:snow (the layer block; the 1.12
            // full block minecraft:snow became 26.2 minecraft:snow_block). Keep any layers count property.
            return "snow";
         }
         default -> {
            return null;
         }
      }
   }

   /**
    * After flattening, keep only the properties the flattened block still understands. The 1.12 sub-type
    * selectors {@code variant}/{@code type} and the unused {@code facing} are dropped (the variant is now
    * encoded by the id); {@code half} (double_plant lower/upper) and {@code layers} (snow) carry over.
    */
   private static String residualProps(String props) {
      if (props.isEmpty()) {
         return "";
      }
      StringBuilder kept = new StringBuilder();
      for (String part : props.split(",")) {
         String key = part.split("=", 2)[0].trim();
         if (key.equals("half") || key.equals("layers")) {
            if (kept.length() > 0) {
               kept.append(",");
            }
            kept.append(part.trim());
         }
      }
      return kept.toString();
   }

   /** Reads a single {@code key=value} out of a comma-separated property string, or {@code null}. */
   private static String propValue(String props, String key) {
      if (props == null || props.isEmpty()) {
         return null;
      }
      for (String part : props.split(",")) {
         String[] kv = part.split("=", 2);
         if (kv.length == 2 && kv[0].trim().equals(key)) {
            return kv[1].trim();
         }
      }
      return null;
   }
}
