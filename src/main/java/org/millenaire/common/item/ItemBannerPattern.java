package org.millenaire.common.item;

/**
 * Millénaire banner-pattern item (applies a custom banner design). 1.12 was a metadata item: one item
 * with {@code hasSubtypes}, one stack per design (from {@code Mill.BANNER_SHORTNAMES}), and the design
 * registered as a {@code BannerPattern} enum via {@code EnumHelper.addEnum}.
 *
 * <p>26.2: item metadata is gone and banner patterns are data-driven. The Millénaire banner designs
 * (Mill.BANNER_SHORTNAMES) are registered as BannerPattern entries under
 * {@code data/millenaire/banner_pattern/*.json} (see {@link org.millenaire.common.forge.MillBannerPatterns}
 * and the Culture banner wiring), so the per-design sub-items and the old EnumHelper BannerPattern enum
 * are removed and this is a plain base item.
 */
public class ItemBannerPattern extends ItemMill {
   public ItemBannerPattern(String itemName) {
      super(itemName);
      // 26.2: nothing to do here — the banner designs are data-driven BannerPattern entries (see the
      // class javadoc); the 1.12 getSubItems/getTranslationKey(meta)/initModel metadata API is gone.
   }
}
