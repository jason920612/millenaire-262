package org.millenaire.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/**
 * Render state for {@link TESRFirePit}: the resolved item models for fuel + 3 cooking + 3 cooked
 * slots, the block alignment angle, and the position-based fuel rotation seed.
 */
@Environment(EnvType.CLIENT)
public class FirePitRenderState extends BlockEntityRenderState {
   public final ItemStackRenderState fuel = new ItemStackRenderState();
   public final ItemStackRenderState[] cooking = {new ItemStackRenderState(), new ItemStackRenderState(), new ItemStackRenderState()};
   public final ItemStackRenderState[] cooked = {new ItemStackRenderState(), new ItemStackRenderState(), new ItemStackRenderState()};
   public double alignmentAngle = 0.0;
   public int fuelRotationSteps = 0;
}
