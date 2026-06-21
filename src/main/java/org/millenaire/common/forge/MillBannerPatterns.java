package org.millenaire.common.forge;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BannerPattern;

/**
 * Millénaire's own 28 culture banner patterns.
 *
 * <p>1.12 injected these into the vanilla {@code BannerPattern} enum via {@code EnumHelper}; each
 * had a long enum-name ("byzantine", "byzantine_1", …) and a short "hashname" (byz/by1/… — see
 * {@link Mill#BANNER_SHORTNAMES}) used as the {@code Pattern} code in the banner NBT.
 *
 * <p>26.2 makes banner patterns a <b>datapack registry</b> ({@link Registries#BANNER_PATTERN}). The
 * patterns themselves are now declared as JSON under {@code data/millenaire/banner_pattern/<long>.json}
 * (matching {@code BannerPattern.DIRECT_CODEC}'s {@code asset_id}/{@code translation_key} fields) with
 * matching textures at {@code assets/millenaire/textures/entity/banner/<long>.png} (the {@code asset_id}
 * {@code millenaire:<long>} resolves to that path via {@code Sheets.BANNER_MAPPER} = "entity/banner").
 *
 * <p>This class provides the runtime bridge the old enum used to give: the mapping from the short
 * "hashname" code (the {@code Pattern:} value in culture/village banner JSON) to the
 * {@code ResourceKey<BannerPattern>} for {@code millenaire:<long>}, so banner designs can be resolved
 * against the live registry into {@code BannerPatternLayers}.
 */
public final class MillBannerPatterns {
   /**
    * The 28 long names (registry path / asset_id / texture file name), parallel to
    * {@link Mill#BANNER_SHORTNAMES}. Order matches the 1.12 EnumHelper.addEnum sequence exactly.
    */
   public static final String[] BANNER_LONGNAMES = new String[]{
      "byzantine",
      "byzantine_1",
      "byzantine_2",
      "seljuk",
      "mayan",
      "inuit",
      "indian",
      "indian_1",
      "indian_2",
      "indian_3",
      "indian_4",
      "indian_5",
      "norman",
      "mayan_1",
      "mayan_2",
      "mayan_3",
      "mayan_4",
      "inuit_1",
      "inuit_2",
      "inuit_3",
      "inuit_4",
      "japanese",
      "japanese_agr",
      "japanese_mil",
      "japanese_rel",
      "japanese_tra",
      "seljuk_rel",
      "seljuk_mil"
   };

   /** Short hashname (the banner JSON {@code Pattern:} code) → {@code millenaire:<long>} registry key. */
   private static final Map<String, ResourceKey<BannerPattern>> SHORTNAME_TO_KEY = new HashMap<>();

   static {
      for (int i = 0; i < Mill.BANNER_SHORTNAMES.length && i < BANNER_LONGNAMES.length; i++) {
         SHORTNAME_TO_KEY.put(
            Mill.BANNER_SHORTNAMES[i],
            ResourceKey.create(Registries.BANNER_PATTERN, Identifier.fromNamespaceAndPath("millenaire", BANNER_LONGNAMES[i]))
         );
      }
   }

   /**
    * Resolves a Millénaire banner-pattern hashname (e.g. "sjkr", "byz") to its 26.2
    * {@code ResourceKey<BannerPattern>} ({@code millenaire:<long>}), or {@code null} if the code is not
    * one of Millénaire's own patterns.
    */
   public static ResourceKey<BannerPattern> getKey(String shortname) {
      return SHORTNAME_TO_KEY.get(shortname);
   }

   private MillBannerPatterns() {
   }
}
