package org.millenaire.common.convert;

/**
 * A dot-notation block specification, e.g. {@code oak_stairs.facing(south).half(top)}. This is the
 * canonical modern way to name a {@link net.minecraft.world.level.block.state.BlockState} in content
 * files; {@link MillConvert#dotSpecToBlockState(DotSpec)} resolves it.
 *
 * <p>M0 of the unified conversion protocol.</p>
 */
public record DotSpec(String text) {
}
