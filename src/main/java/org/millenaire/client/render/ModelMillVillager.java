package org.millenaire.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/**
 * Mill villager humanoid model.
 *
 * <p>26.2 PORT NOTE: 1.12 {@code ModelBiped}/{@code ModelRenderer} → {@link HumanoidModel} baked from
 * a {@link LayerDefinition} (deformation mesh). Typed over {@link MillVillagerRenderState} so the
 * renderer + cloth layers share the model generic. The 1.12 {@code setRotationAngles} held-item arm
 * poses for the travel-book mock villager are reapplied in {@link #setupAnim} from the held-item flags
 * the renderer puts on the render state.
 */
@Environment(EnvType.CLIENT)
public class ModelMillVillager extends HumanoidModel<MillVillagerRenderState> {
   public ModelMillVillager(ModelPart root) {
      super(root);
   }

   @Override
   public void setupAnim(MillVillagerRenderState state) {
      super.setupAnim(state);
      // 1.12: a travel-book mock villager "presents" a held item — the left arm raises for the main-hand
      // item and the right arm for the off-hand item (matches the original ModelMillVillager.func_78087_a).
      if (state.mockHoldingMainHand) {
         this.leftArm.xRot = -0.6F;
         this.leftArm.zRot = -0.2F;
      }
      if (state.mockHoldingOffHand) {
         this.rightArm.xRot = -0.5F;
         this.rightArm.zRot = 0.1F;
      }
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
      return LayerDefinition.create(mesh, 64, 32);
   }

   /**
    * Inflated copy of the body mesh for the cloth overlay layer (1.12's slightly-enlarged duplicate
    * model). {@code inflate} is the cube deformation in pixels.
    */
   public static LayerDefinition createClothLayer(float inflate) {
      MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(inflate), 0.0F);
      return LayerDefinition.create(mesh, 64, 32);
   }
}
