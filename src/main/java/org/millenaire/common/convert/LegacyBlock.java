package org.millenaire.common.convert;

/**
 * A 1.12-era block reference: a registry name plus a metadata int. The (name, meta) pair is the
 * legacy key the conversion table maps to a modern {@link net.minecraft.world.level.block.state.BlockState}
 * (the central metadata→BlockState redesign).
 *
 * <p>M0 of the unified conversion protocol.</p>
 */
public record LegacyBlock(String name, int meta) {
}
