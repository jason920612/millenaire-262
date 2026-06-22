package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import org.millenaire.common.block.BlockPanel;
import org.millenaire.common.entity.TileEntityPanel;

/**
 * Renders the Mill information panel (sign board + icon rows + wrapped text).
 *
 * <p>26.2 PORT: 1.12 was a Forge {@code TileEntitySpecialRenderer} drawing the {@link ModelPanel}
 * board, 2D item icons ({@code RenderItem.renderItem}) and {@code FontRenderer} text via the removed
 * {@code GlStateManager} stack. Reimplemented on the 26.2 render-state pipeline: the board is the
 * baked {@link ModelPanel} part submitted with the panel texture, icons are resolved through the
 * {@link ItemModelResolver} and submitted FIXED, and each line's {@code fullLine}/{@code leftColumn}/
 * {@code rightColumn} text is submitted via {@code submitText}. The board is rotated by the panel
 * {@link BlockPanel#FACING} (1.12 keyed off block metadata; here off the Direction property). The
 * line layout (board scale 2/3, text offset 0.24 / 0.046, text scale 1/96, per-line 10px stride and
 * the −15 baseline, icon columns at x −0.54/0.08/0.54 and y −0.74+0.15·row) is ported 1:1.
 */
@Environment(EnvType.CLIENT)
public class TESRPanel implements BlockEntityRenderer<TileEntityPanel, PanelRenderState> {
   private static final Identifier PANEL_TEXTURE = Identifier.fromNamespaceAndPath("millenaire", "textures/entity/panels/default.png");

   public static final BlockEntityRendererProvider<TileEntityPanel, PanelRenderState> FACTORY = TESRPanel::new;

   private final ModelPanel model;
   private final Font font;
   private final ItemModelResolver itemModelResolver;
   /** [MILLDEBUG] panel positions already logged on first render this session. */
   private static final java.util.Set<Long> DEBUG_LOGGED = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

   public TESRPanel(BlockEntityRendererProvider.Context context) {
      this.model = new ModelPanel(context.bakeLayer(MillModelLayers.PANEL));
      this.font = context.font();
      this.itemModelResolver = context.itemModelResolver();
   }

   @Override
   public PanelRenderState createRenderState() {
      return new PanelRenderState();
   }

   @Override
   public void extractRenderState(TileEntityPanel be, PanelRenderState state, float partialTicks, Vec3 cameraPosition,
                                  ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
      BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
      state.texture = be.texture != null ? be.texture : PANEL_TEXTURE;

      BlockState bs = be.getBlockState();
      // 1.12 metadata → rotation: south=180, west=90, east=-90, north=0. Direction.toYRot() gives
      // S=0,W=90,N=180,E=270; the original applied -f2. Map via the facing's opposite-face Y rot.
      if (bs.getBlock() instanceof BlockPanel) {
         state.facingDegrees = -bs.getValue(HorizontalDirectionalBlock.FACING).toYRot();
      } else {
         state.facingDegrees = 0.0F;
      }

      be.translateLines(this.font);
      state.lines.clear();
      for (TileEntityPanel.PanelDisplayLine src : be.displayLines) {
         PanelRenderState.Line line = new PanelRenderState.Line();
         line.fullLine = src.fullLine;
         line.leftColumn = src.leftColumn;
         line.rightColumn = src.rightColumn;
         line.centerLine = src.centerLine;
         resolveIcon(line.leftIcon, src.leftIcon, be);
         resolveIcon(line.middleIcon, src.middleIcon, be);
         resolveIcon(line.rightIcon, src.rightIcon, be);
         state.lines.add(line);
      }

      if (org.millenaire.common.utilities.MillLog.debugOn() && DEBUG_LOGGED.add(be.getBlockPos().asLong())) {
         org.millenaire.common.utilities.MillLog.milldebug(
            "Render",
            "panel FIRST render at " + be.getBlockPos() + " boundTexture=" + state.texture
               + " modelResolved=" + (this.model != null) + " lines=" + state.lines.size() + " facingDeg=" + state.facingDegrees
         );
      }
   }

   private void resolveIcon(ItemStackRenderState iconState, ItemStack stack, TileEntityPanel be) {
      this.itemModelResolver.updateForTopItem(iconState, stack, ItemDisplayContext.FIXED, be.getLevel(), null, 0);
   }

   @Override
   public void submit(PanelRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
      poseStack.pushPose();
      poseStack.translate(0.5F, 0.5F, 0.5F);
      poseStack.mulPose(Axis.YP.rotationDegrees(state.facingDegrees));
      poseStack.translate(0.0F, 0.0F, -0.4375F);

      // Board: scale 2/3 (Y/Z flipped as in the original), submit the sign-board part.
      poseStack.pushPose();
      poseStack.scale(0.6666667F, -0.6666667F, -0.6666667F);
      this.model.renderSign(poseStack, collector, RenderTypes.entitySolid(state.texture), state.lightCoords);
      poseStack.translate(0.0F, 0.24F, 0.0F);

      // Icons (within the board's local space, like the original drawIcon calls).
      for (int row = 0; row < state.lines.size(); row++) {
         PanelRenderState.Line line = state.lines.get(row);
         submitIcon(line.leftIcon, row, -0.54F, poseStack, collector, state.lightCoords);
         submitIcon(line.middleIcon, row, 0.08F, poseStack, collector, state.lightCoords);
         submitIcon(line.rightIcon, row, 0.54F, poseStack, collector, state.lightCoords);
      }
      poseStack.popPose();

      // Text: independent transform (matches the original's separate scale/translate block).
      poseStack.pushPose();
      poseStack.translate(0.0F, 0.25F, 0.046666667F);
      poseStack.scale(0.010416667F, -0.010416667F, 0.010416667F);
      for (int row = 0; row < state.lines.size(); row++) {
         PanelRenderState.Line line = state.lines.get(row);
         int y = row * 10 - 15;
         if (line.centerLine) {
            submitText(collector, poseStack, line.fullLine, -this.font.width(line.fullLine) / 2.0F, y, state.lightCoords);
         } else {
            submitText(collector, poseStack, line.fullLine, -29.0F, y, state.lightCoords);
         }
         submitText(collector, poseStack, line.leftColumn, -29.0F, y, state.lightCoords);
         submitText(collector, poseStack, line.rightColumn, 11.0F, y, state.lightCoords);
      }
      poseStack.popPose();

      poseStack.popPose();
   }

   private void submitText(SubmitNodeCollector collector, PoseStack poseStack, String text, float x, float y, int light) {
      if (text == null || text.isEmpty()) {
         return;
      }
      FormattedCharSequence seq = Component.literal(text).getVisualOrderText();
      collector.submitText(poseStack, x, y, seq, false, Font.DisplayMode.POLYGON_OFFSET, light, 0xFF000000, 0, 0);
   }

   private static void submitIcon(ItemStackRenderState iconState, int row, float xTranslate, PoseStack poseStack,
                                  SubmitNodeCollector collector, int light) {
      if (iconState.isEmpty()) {
         return;
      }
      poseStack.pushPose();
      poseStack.translate(xTranslate, -0.74F + row * 0.15F, -0.09F);
      poseStack.scale(0.3F, 0.3F, 0.3F);
      iconState.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
      poseStack.popPose();
   }
}
