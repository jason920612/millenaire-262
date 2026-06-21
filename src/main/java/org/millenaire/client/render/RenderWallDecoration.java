package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import org.millenaire.common.entity.EntityWallDecoration;

/**
 * Renderer for Mill wall decorations (tapestries / sculptures — painting-like hanging entities).
 *
 * <p>26.2 PORT: 1.12 {@code Render}/{@code RenderManager} drew the decoration face-by-face with a
 * {@code Tessellator}/{@code BufferBuilder} (per-cell lightmap lookups) inside a {@code GlStateManager}
 * transform. Reimplemented on the 26.2 render-state pipeline: per-cell light coords are extracted in
 * {@link #extractRenderState} (mirroring vanilla {@code PaintingRenderer}) and the front + back quad
 * geometry is rebuilt in {@link #submit} via {@link SubmitNodeCollector#submitCustomGeometry}. UVs
 * are computed from the {@code EnumWallDecoration} size/offset over the 256px tapestry/sculpture
 * texture, exactly as the 1.12 {@code renderPainting} loop did.
 *
 * <p>NOTE: the 1.12 edge/side faces used fixed UVs in the texture's 0.75–0.8125 strip; here the
 * front and back faces are rendered (the visible surfaces). The thin side strips are omitted (they
 * were 1px borders, barely visible) — a precise but cosmetic simplification.
 */
@Environment(EnvType.CLIENT)
public class RenderWallDecoration extends EntityRenderer<EntityWallDecoration, WallDecorationRenderState> {
   public static final Identifier textureTapestries = Identifier.fromNamespaceAndPath("millenaire", "textures/painting/tapestry.png");
   public static final Identifier textureSculptures = Identifier.fromNamespaceAndPath("millenaire", "textures/painting/sculptures.png");

   public static final EntityRendererProvider<EntityWallDecoration> FACTORY_WALL_DECORATION = RenderWallDecoration::new;
   /** [MILLDEBUG] wall-decoration entity ids already logged on first render this session. */
   private static final java.util.Set<Integer> DEBUG_LOGGED = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

   public RenderWallDecoration(EntityRendererProvider.Context context) {
      super(context);
   }

   @Override
   public WallDecorationRenderState createRenderState() {
      return new WallDecorationRenderState();
   }

   @Override
   public void extractRenderState(EntityWallDecoration entity, WallDecorationRenderState state, float partialTicks) {
      super.extractRenderState(entity, state, partialTicks);
      EntityWallDecoration.EnumWallDecoration art = entity.millArt;
      state.type = entity.type;
      state.direction = entity.getDirection();
      if (art == null) {
         state.sizeX = 16;
         state.sizeY = 16;
         state.texU = 0;
         state.texV = 0;
      } else {
         state.sizeX = art.sizeX;
         state.sizeY = art.sizeY;
         state.texU = art.offsetX;
         state.texV = art.offsetY;
      }

      int cellsX = Math.max(1, state.sizeX / 16);
      int cellsY = Math.max(1, state.sizeY / 16);
      state.cellsX = cellsX;
      state.cellsY = cellsY;
      if (state.lightPerCell.length != cellsX * cellsY) {
         state.lightPerCell = new int[cellsX * cellsY];
      }

      // Per-cell lightmap (1.12 setLightmap): sample the block light at each cell centre, offset
      // along the wall by the cell's horizontal centre depending on facing.
      Level level = entity.level();
      float baseX = -state.sizeX / 2.0F;
      float baseY = -state.sizeY / 2.0F;
      Direction facing = state.direction;
      for (int cy = 0; cy < cellsY; cy++) {
         for (int cx = 0; cx < cellsX; cx++) {
            float midX = baseX + (cx + 0.5F) * 16.0F;
            float midY = baseY + (cy + 0.5F) * 16.0F;
            int bx = Mth.floor(entity.getX());
            int by = Mth.floor(entity.getY() + midY / 16.0F);
            int bz = Mth.floor(entity.getZ());
            switch (facing) {
               case NORTH -> bx = Mth.floor(entity.getX() + midX / 16.0F);
               case WEST -> bz = Mth.floor(entity.getZ() - midX / 16.0F);
               case SOUTH -> bx = Mth.floor(entity.getX() - midX / 16.0F);
               case EAST -> bz = Mth.floor(entity.getZ() + midX / 16.0F);
               default -> { }
            }
            state.lightPerCell[cx + cy * cellsX] = LightCoordsUtil.getLightCoords(level, new BlockPos(bx, by, bz));
         }
      }

      if (org.millenaire.common.utilities.MillLog.debugOn() && DEBUG_LOGGED.add(entity.getId())) {
         org.millenaire.common.utilities.MillLog.milldebug(
            "Render",
            "wall_decoration FIRST render entityId=" + entity.getId() + " type=" + state.type
               + " boundTexture=" + (state.type == 1 ? textureTapestries : textureSculptures)
               + " art=" + (art != null ? art.name() : "null") + " size=" + state.sizeX + "x" + state.sizeY
         );
      }
   }

   @Override
   public void submit(WallDecorationRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
      poseStack.pushPose();
      // 1.12 rotated by 180 - entityYaw; for a wall-mounted hanging entity that is the facing 2D angle.
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.direction.get2DDataValue() * 90.0F));
      poseStack.scale(0.0625F, 0.0625F, 0.0625F);

      Identifier texture = state.type == 1 ? textureTapestries : textureSculptures;
      renderDecoration(state, poseStack, collector, texture);

      poseStack.popPose();
      super.submit(state, poseStack, collector, camera);
   }

   private static void renderDecoration(WallDecorationRenderState state, PoseStack poseStack, SubmitNodeCollector collector, Identifier texture) {
      collector.submitCustomGeometry(poseStack, RenderTypes.entitySolidZOffsetForward(texture), (pose, buffer) -> {
         final int width = state.sizeX;
         final int height = state.sizeY;
         final int texU = state.texU;
         final int texV = state.texV;
         final float f = -width / 2.0F;
         final float f1 = -height / 2.0F;

         for (int i = 0; i < state.cellsX; i++) {
            for (int j = 0; j < state.cellsY; j++) {
               float x0 = f + (i + 1) * 16.0F;
               float x1 = f + i * 16.0F;
               float y0 = f1 + (j + 1) * 16.0F;
               float y1 = f1 + j * 16.0F;
               int light = state.lightPerCell[i + j * state.cellsX];
               // Front-face UVs (1.12 f19/f20/f21/f22), 256px texture.
               float u0 = (texU + width - i * 16) / 256.0F;
               float u1 = (texU + width - (i + 1) * 16) / 256.0F;
               float v0 = (texV + height - j * 16) / 256.0F;
               float v1 = (texV + height - (j + 1) * 16) / 256.0F;
               // Front face (normal -Z), matching the original winding.
               vertex(pose, buffer, x0, y1, -0.5F, u1, v0, 0, 0, -1, light);
               vertex(pose, buffer, x1, y1, -0.5F, u0, v0, 0, 0, -1, light);
               vertex(pose, buffer, x1, y0, -0.5F, u0, v1, 0, 0, -1, light);
               vertex(pose, buffer, x0, y0, -0.5F, u1, v1, 0, 0, -1, light);
               // Back face (normal +Z), using the original fixed back UV strip.
               vertex(pose, buffer, x0, y0, 0.5F, 0.75F, 0.0F, 0, 0, 1, light);
               vertex(pose, buffer, x1, y0, 0.5F, 0.8125F, 0.0F, 0, 0, 1, light);
               vertex(pose, buffer, x1, y1, 0.5F, 0.8125F, 0.0625F, 0, 0, 1, light);
               vertex(pose, buffer, x0, y1, 0.5F, 0.75F, 0.0625F, 0, 0, 1, light);
            }
         }
      });
   }

   private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z,
                              float u, float v, int nx, int ny, int nz, int light) {
      buffer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
   }
}
