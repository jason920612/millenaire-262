package org.millenaire.client.render;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;

/**
 * Render state for {@link TESRPanel}: the board texture, the facing rotation, and the per-line
 * resolved text columns + icon item models.
 */
@Environment(EnvType.CLIENT)
public class PanelRenderState extends BlockEntityRenderState {
   /** One display line: text columns + up to three icon item models. */
   public static final class Line {
      public String fullLine = "";
      public String leftColumn = "";
      public String rightColumn = "";
      public boolean centerLine = true;
      public final ItemStackRenderState leftIcon = new ItemStackRenderState();
      public final ItemStackRenderState middleIcon = new ItemStackRenderState();
      public final ItemStackRenderState rightIcon = new ItemStackRenderState();
   }

   public Identifier texture = null;
   public float facingDegrees = 0.0F;
   public final List<Line> lines = new ArrayList<>();
}
