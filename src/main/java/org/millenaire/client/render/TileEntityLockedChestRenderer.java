package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import org.millenaire.common.block.BlockLockedChest;
import org.millenaire.common.entity.TileEntityLockedChest;

/**
 * Block-entity renderer for the Mill locked chest.
 *
 * <p>26.2 PORT: 1.12 used a {@code TileEntitySpecialRenderer} drawing {@code ModelChest}/
 * {@code ModelLargeChest} with {@code GlStateManager}. Reimplemented on the 26.2 render pipeline:
 * a vanilla {@link ChestModel} baked from {@link ModelLayers#CHEST}, textured via
 * {@link Sheets#chooseSprite} (regular/christmas), rotated by the chest {@link BlockLockedChest#FACING}
 * and submitted through {@link SubmitNodeCollector#submitModel}. The Christmas-window override is
 * preserved via {@link ChestRenderer#xmasTextures()}.
 *
 * <p>The lid-open animation is driven by {@link TileEntityLockedChest#getOpenNess} (the BE now
 * implements {@code LidBlockEntity}). Single-chest only: Mill chests never form a double chest, so the
 * 1.12 double-chest pairing is intentionally not reproduced.
 */
@Environment(EnvType.CLIENT)
public class TileEntityLockedChestRenderer implements BlockEntityRenderer<TileEntityLockedChest, ChestRenderState> {
   private final ChestModel model;
   private final SpriteGetter sprites;
   private final boolean xmas;

   public TileEntityLockedChestRenderer(BlockEntityRendererProvider.Context context) {
      this.model = new ChestModel(context.bakeLayer(ModelLayers.CHEST));
      this.sprites = context.sprites();
      this.xmas = net.minecraft.client.renderer.blockentity.ChestRenderer.xmasTextures();
   }

   @Override
   public ChestRenderState createRenderState() {
      return new ChestRenderState();
   }

   @Override
   public void extractRenderState(TileEntityLockedChest be, ChestRenderState state, float partialTicks, Vec3 cameraPosition,
                                  ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
      BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
      BlockState bs = be.getBlockState();
      state.type = ChestType.SINGLE;
      state.facing = bs.getBlock() instanceof BlockLockedChest ? bs.getValue(HorizontalDirectionalBlock.FACING) : Direction.SOUTH;
      state.material = this.xmas ? ChestRenderState.ChestMaterialType.CHRISTMAS : ChestRenderState.ChestMaterialType.REGULAR;
      state.open = be.getOpenNess(partialTicks);
   }

   @Override
   public void submit(ChestRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
      poseStack.pushPose();
      // Rotate around the block centre by the facing (matches vanilla ChestRenderer.modelTransformation).
      poseStack.translate(0.5F, 0.5F, 0.5F);
      poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
      poseStack.translate(-0.5F, -0.5F, -0.5F);

      float open = state.open;
      open = 1.0F - open;
      open = 1.0F - open * open * open;
      SpriteId spriteId = Sheets.chooseSprite(state.material, state.type);
      collector.submitModel(this.model, open, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, spriteId, this.sprites, 0, state.breakProgress);
      poseStack.popPose();
   }
}
