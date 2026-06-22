package org.millenaire.common.convert;

/**
 * A 1.12-era item reference: registry name, metadata/damage int, and a quantity. The (name, meta)
 * pair is the legacy key the conversion table maps to a modern {@link net.minecraft.world.item.ItemStack}.
 *
 * <p>M0 of the unified conversion protocol.</p>
 */
public record LegacyItem(String name, int meta, Count count) {
}
