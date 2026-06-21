package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.millenaire.common.entity.TileEntityMillBed;

/**
 * Block-entity renderer for Mill beds.
 *
 * <p>26.2 PORT NOTE: 1.12 {@code TileEntitySpecialRenderer} → {@link BlockEntityRenderer} (the
 * render-state extraction model). The original renderer body was empty in 1.12 (beds render purely
 * via the blockstate/model JSON), so the {@link #submit} body is a no-op; the class is preserved so
 * {@code BlockEntityRenderers.register(MillBlockEntities.MILL_BED, TileEntityMillBedRenderer::new)}
 * still has a target.
 */
@Environment(EnvType.CLIENT)
public class TileEntityMillBedRenderer implements BlockEntityRenderer<TileEntityMillBed, BlockEntityRenderState> {
   public TileEntityMillBedRenderer(BlockEntityRendererProvider.Context context) {
   }

   @Override
   public BlockEntityRenderState createRenderState() {
      return new BlockEntityRenderState();
   }

   @Override
   public void submit(BlockEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
      // no-op: beds render from their blockstate/model JSON.
   }
}
