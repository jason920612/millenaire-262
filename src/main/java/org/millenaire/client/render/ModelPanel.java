package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 24x24x2 sign board for the Mill information panel.
 *
 * <p>26.2 PORT: 1.12 {@code ModelBase}/{@code ModelRenderer.render(scale)} → {@code ModelPart}
 * baked from a {@link LayerDefinition} (built via {@link #createLayer()}, registered as a
 * {@code ModelLayerLocation}, baked in {@link TESRPanel}). The board geometry is submitted to the
 * 26.2 {@link SubmitNodeCollector} via {@link #renderSign}.
 */
@Environment(EnvType.CLIENT)
public class ModelPanel {
   private final ModelPart signBoard;

   public ModelPanel(ModelPart root) {
      this.signBoard = root.getChild("sign_board");
   }

   public static LayerDefinition createLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      root.addOrReplaceChild(
         "sign_board",
         CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -12.0F, -1.0F, 24, 24, 2),
         PartPose.ZERO);
      return LayerDefinition.create(mesh, 64, 32);
   }

   /** Submit the board geometry with the given texture render type to the 26.2 collector. */
   public void renderSign(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light) {
      collector.submitModelPart(this.signBoard, poseStack, renderType, light, OverlayTexture.NO_OVERLAY, null);
   }
}
