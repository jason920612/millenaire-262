package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;

/**
 * Villager clothing overlay layer (Mill villagers have up to two cloth texture layers over the base
 * humanoid model).
 *
 * <p>26.2 PORT NOTE: 1.12 {@code LayerRenderer.doRenderLayer} drew a duplicated, slightly-inflated
 * {@code ModelMillVillager} bound to the cloth texture. Here the inflated cloth model is baked from a
 * dedicated {@code ModelLayerLocation} (see {@link MillModelLayers}) and submitted via
 * {@link RenderLayer#renderColoredCutoutModel}. The per-layer cloth texture is carried on the
 * {@link MillVillagerRenderState} (extracted from {@code MillVillager#getClothTexturePath(layer)}).
 */
@Environment(EnvType.CLIENT)
public class LayerVillagerClothes extends RenderLayer<MillVillagerRenderState, ModelMillVillager> {
   private final int layer;
   private final ModelMillVillager clothModel;

   public LayerVillagerClothes(RenderLayerParent<MillVillagerRenderState, ModelMillVillager> renderer, int layer, ModelMillVillager clothModel) {
      super(renderer);
      this.layer = layer;
      this.clothModel = clothModel;
   }

   @Override
   public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, MillVillagerRenderState state, float yRot, float xRot) {
      if (this.layer < 0 || this.layer >= state.clothTextures.length) {
         return;
      }
      Identifier texture = state.clothTextures[this.layer];
      if (texture == null || state.isInvisible) {
         return;
      }
      // Match the body pose for this frame, then submit the inflated cloth model with its texture.
      this.clothModel.setupAnim(state);
      renderColoredCutoutModel(this.clothModel, texture, poseStack, collector, lightCoords, state, -1, 0);
   }
}
