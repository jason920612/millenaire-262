package org.millenaire.common.convert;

/**
 * A building-plan pixel colour (packed 0xRRGGBB). Building plans are PNGs where each colour keys a
 * block; the conversion table maps a {@code PlanColour} to a modern
 * {@link net.minecraft.world.level.block.state.BlockState}.
 *
 * <p>M0 of the unified conversion protocol.</p>
 */
public record PlanColour(int rgb) {
}
