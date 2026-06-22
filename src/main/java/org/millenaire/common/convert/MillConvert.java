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

   /** Maps a 1.12 (name, meta) block to a modern placement + optional cost. */
   public static BlockSpec legacyBlockToSpec(LegacyBlock legacy) {
      BlockSpec spec = LegacyTables.get().blocks.get(legacy);
      if (spec == null) {
         throw MillCrash.fail("Convert", legacy + " not yet in conversion table");
      }
      return spec;
   }

   /** Maps a 1.12 (name, meta) item to a modern {@link ItemStack} of the requested count. */
   public static ItemStack legacyItemToStack(LegacyItem legacy) {
      ItemSpec spec = LegacyTables.get().items.get(legacy);
      if (spec == null) {
         throw MillCrash.fail("Convert", legacy + " not yet in conversion table");
      }
      return new ItemStack(spec.item(), legacy.count().value());
   }

   /** Maps a building-plan pixel colour to a modern placement + optional cost. */
   public static BlockSpec planColourToSpec(PlanColour colour) {
      BlockSpec spec = LegacyTables.get().planColours.get(colour);
      if (spec == null) {
         throw MillCrash.fail("Convert", colour + " not yet in conversion table");
      }
      return spec;
   }
}
