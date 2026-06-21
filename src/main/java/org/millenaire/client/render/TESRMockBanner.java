package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import org.millenaire.common.entity.TileEntityMockBanner;

/**
 * Block-entity renderer for Mill "mock" banners (decorative banners placed by the building system).
 *
 * <p>26.2 PORT: 1.12 used {@code TileEntitySpecialRenderer} + {@code ModelBanner} +
 * {@code BannerTextures.BANNER_DESIGNS} + {@code GlStateManager}. Reimplemented by reusing the
 * vanilla {@link BannerRenderer} machinery: the base colour is read off the mock banner block
 * ({@link AbstractBannerBlock#getColor()}) and the pattern layers off the stored banner item's
 * {@link DataComponents#BANNER_PATTERNS} component, then the banner is submitted via
 * {@link BannerRenderer#submitSpecial} (which bakes the flag layers + pattern sprites + wave
 * animation). The standing/wall transform is resolved from {@link BannerRenderer#TRANSFORMATIONS}
 * keyed by {@link BannerBlock#ROTATION} / {@link WallBannerBlock#FACING}.
 */
@Environment(EnvType.CLIENT)
public class TESRMockBanner implements BlockEntityRenderer<TileEntityMockBanner, MockBannerRenderState> {
   private final BannerRenderer bannerRenderer;
   /** [MILLDEBUG] banner positions already logged on first render this session. */
   private static final java.util.Set<Long> DEBUG_LOGGED = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

   public TESRMockBanner(BlockEntityRendererProvider.Context context) {
      this.bannerRenderer = new BannerRenderer(context);
   }

   @Override
   public MockBannerRenderState createRenderState() {
      return new MockBannerRenderState();
   }

   @Override
   public void extractRenderState(TileEntityMockBanner be, MockBannerRenderState state, float partialTicks, Vec3 cameraPosition,
                                  ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
      BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
      BlockState bs = be.getBlockState();

      // Base colour from the banner block.
      state.baseColor = bs.getBlock() instanceof AbstractBannerBlock banner ? banner.getColor() : DyeColor.WHITE;

      // Pattern layers from the stored banner item (data-driven in 26.2).
      ItemStack item = be.getBannerItem();
      state.patterns = item != null && !item.isEmpty()
         ? item.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)
         : BannerPatternLayers.EMPTY;

      // Attachment type + transform: wall banner vs standing banner.
      if (bs.getBlock() instanceof WallBannerBlock) {
         state.attachmentType = BannerBlock.AttachmentType.WALL;
         state.transformation = BannerRenderer.TRANSFORMATIONS.wallTransformation(bs.getValue(WallBannerBlock.FACING));
      } else {
         state.attachmentType = BannerBlock.AttachmentType.GROUND;
         int rotation = bs.hasProperty(BannerBlock.ROTATION) ? bs.getValue(BannerBlock.ROTATION) : 0;
         state.transformation = BannerRenderer.TRANSFORMATIONS.freeTransformations(rotation);
      }

      if (org.millenaire.common.utilities.MillLog.debugOn() && DEBUG_LOGGED.add(be.getBlockPos().asLong())) {
         org.millenaire.common.utilities.MillLog.milldebug(
            "Render",
            "banner FIRST render at " + be.getBlockPos() + " baseColor=" + state.baseColor
               + " attachment=" + state.attachmentType + " patternCount=" + state.patterns.layers().size()
         );
      }
   }

   @Override
   public void submit(MockBannerRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
      poseStack.pushPose();
      poseStack.mulPose(state.transformation);
      this.bannerRenderer.submitSpecial(
         state.attachmentType, poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY,
         state.baseColor, state.patterns, 0);
      poseStack.popPose();
   }
}
