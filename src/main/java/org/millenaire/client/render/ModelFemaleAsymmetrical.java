package org.millenaire.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.PartPose;

/**
 * Asymmetrical female Mill villager model (narrower body + breast cube + asymmetrical-leg texture).
 *
 * <p>26.2 PORT NOTE: ported to a {@link HumanoidModel} {@link LayerDefinition}. The 1.12 model used
 * custom narrower arms (texOffs 36,17; 3x12x3) and an asymmetrical leg texture — the right leg at
 * texOffs (0,16) and the LEFT leg at the distinct texOffs (48,16), neither mirrored. Those are
 * restored below over the standard humanoid parts so the villager texture maps exactly as in 1.12.
 */
@Environment(EnvType.CLIENT)
public class ModelFemaleAsymmetrical extends ModelMillVillager {
   public ModelFemaleAsymmetrical(ModelPart root) {
      super(root);
   }

   public static LayerDefinition createBodyLayer() {
      return createMesh(CubeDeformation.NONE);
   }

   /** Inflated copy of the body mesh for the cloth overlay layer. */
   public static LayerDefinition createClothLayer(float inflate) {
      return createMesh(new CubeDeformation(inflate));
   }

   private static LayerDefinition createMesh(CubeDeformation deform) {
      MeshDefinition mesh = HumanoidModel.createMesh(deform, 0.0F);
      PartDefinition root = mesh.getRoot();
      PartDefinition body = root.addOrReplaceChild(
         "body", CubeListBuilder.create().texOffs(16, 17).addBox(-3.5F, 0.0F, -1.5F, 7, 12, 3, deform), PartPose.ZERO);
      body.addOrReplaceChild(
         "breast", CubeListBuilder.create().texOffs(17, 18).addBox(-3.5F, 0.75F, -3.0F, 7, 4, 2, deform), PartPose.ZERO);
      // Narrower Mill arms (texOffs 36,17; 3x12x3), the left mirrored — matches the 1.12 model.
      root.addOrReplaceChild(
         "right_arm", CubeListBuilder.create().texOffs(36, 17).addBox(-1.5F, -2.0F, -1.5F, 3, 12, 3, deform), PartPose.offset(-5.0F, 2.0F, 0.0F));
      root.addOrReplaceChild(
         "left_arm", CubeListBuilder.create().texOffs(36, 17).mirror().addBox(-1.5F, -2.0F, -1.5F, 3, 12, 3, deform), PartPose.offset(5.0F, 2.0F, 0.0F));
      // Asymmetrical legs: right at (0,16), left at the DISTINCT (48,16) — neither mirrored (1.12).
      root.addOrReplaceChild(
         "right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, deform), PartPose.offset(-2.0F, 12.0F, 0.0F));
      root.addOrReplaceChild(
         "left_leg", CubeListBuilder.create().texOffs(48, 16).addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, deform), PartPose.offset(2.0F, 12.0F, 0.0F));
      return LayerDefinition.create(mesh, 64, 32);
   }
}
