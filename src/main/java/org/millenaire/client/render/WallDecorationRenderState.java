package org.millenaire.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.Direction;

/**
 * Render state for {@link RenderWallDecoration}: the tapestry/sculpture type, the geometry
 * (size/offset in pixels), facing, and per-16px-cell packed light coords.
 */
@Environment(EnvType.CLIENT)
public class WallDecorationRenderState extends EntityRenderState {
   public int type = 1;
   public int sizeX = 16;
   public int sizeY = 16;
   public int texU = 0;
   public int texV = 0;
   public Direction direction = Direction.NORTH;
   /** Packed light coords per cell, indexed [cellX + cellY * cellsX]. */
   public int[] lightPerCell = new int[1];
   public int cellsX = 1;
   public int cellsY = 1;
}
