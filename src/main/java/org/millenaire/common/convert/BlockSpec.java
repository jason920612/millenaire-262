package org.millenaire.common.convert;

import java.util.Optional;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A resolved modern block placement — the target of {@link LegacyBlock} / {@link PlanColour}
 * conversion. {@code cost} is the optional build cost the village economy charges for placing it.
 *
 * <p>M0 of the unified conversion protocol.</p>
 */
public record BlockSpec(BlockState state, Optional<ItemSpec> cost) {
}
