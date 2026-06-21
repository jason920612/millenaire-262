package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import org.millenaire.common.block.BlockFirePit;
import org.millenaire.common.entity.TileEntityFirePit;

/**
 * Renders the items cooking on / cooked beside the Mill fire pit.
 *
 * <p>26.2 PORT: 1.12 was a Forge {@code TileEntitySpecialRenderer} drawing held ItemStacks with the
 * removed {@code GlStateManager} + {@code RenderItem.renderItem} + {@code TRSRTransformation} stack.
 * Reimplemented on the 26.2 render-state extraction pipeline ({@link FirePitRenderState} resolved via
 * {@link ItemModelResolver#updateForTopItem}) + {@code submit(...)} with {@link PoseStack} transforms
 * — the 3 cooking + 3 cooked placement transforms and the spinning fuel pose are ported 1:1 from the
 * original TRSR matrices (translate, X/Y/Z rotation in degrees, uniform scale), and the whole pit is
 * rotated by {@link BlockFirePit.EnumAlignment#angle}.
 */
@Environment(EnvType.CLIENT)
public class TESRFirePit implements BlockEntityRenderer<TileEntityFirePit, FirePitRenderState> {

   private record Placement(float tx, float ty, float tz, float ax, float ay, float az, float scale) {}

   // 1.12 COOKING_POSITIONS / COOKED_POSITIONS (TRSRTransformation get(tx,ty,tz,ax,ay,az,s)).
   private static final Placement[] COOKING = {
      new Placement(0.5F, 1.0F, 0.4F, 25.0F, 180.0F, -45.0F, 0.35F),
      new Placement(0.5F, 0.9F, 0.5F, 0.0F, 45.0F, -45.0F, 0.35F),
      new Placement(0.5F, 1.0F, 0.6F, -25.0F, 180.0F, -45.0F, 0.35F),
   };
   private static final Placement[] COOKED = {
      new Placement(0.5F, 0.9F, 0.4F, 25.0F, 180.0F, -45.0F, 0.35F),
      new Placement(0.5F, 0.9F, 0.5F, 0.0F, -45.0F, -45.0F, 0.35F),
      new Placement(0.5F, 0.9F, 0.6F, -25.0F, 180.0F, -45.0F, 0.35F),
   };

   public static final BlockEntityRendererProvider<TileEntityFirePit, FirePitRenderState> FACTORY = TESRFirePit::new;

   private final ItemModelResolver itemModelResolver;
   /** [MILLDEBUG] fire-pit positions already logged on first render this session. */
   private static final java.util.Set<Long> DEBUG_LOGGED = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

   public TESRFirePit(BlockEntityRendererProvider.Context context) {
      this.itemModelResolver = context.itemModelResolver();
   }

   @Override
   public FirePitRenderState createRenderState() {
      return new FirePitRenderState();
   }

   @Override
   public void extractRenderState(TileEntityFirePit be, FirePitRenderState state, float partialTicks, Vec3 cameraPosition,
                                  ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
      BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
      BlockState bs = be.getBlockState();
      state.alignmentAngle = bs.getBlock() instanceof BlockFirePit ? bs.getValue(BlockFirePit.ALIGNMENT).angle : 0.0;
      BlockPos pos = be.getBlockPos();
      state.fuelRotationSteps = (pos.getX() + pos.getY() + pos.getZ()) & 3;

      // Slots: 0-2 cooking (inputs), 3 fuel, 4-6 cooked (outputs).
      resolve(state.fuel, be.getItem(3), be, 0);
      for (int i = 0; i < 3; i++) {
         resolve(state.cooking[i], be.getItem(i), be, i + 1);
         resolve(state.cooked[i], be.getItem(4 + i), be, i + 4);
      }

      if (org.millenaire.common.utilities.MillLog.debugOn() && DEBUG_LOGGED.add(pos.asLong())) {
         org.millenaire.common.utilities.MillLog.milldebug(
            "Render",
            "fire_pit FIRST render at " + pos + " block=" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(bs.getBlock())
               + " fuelEmpty=" + state.fuel.isEmpty() + " cooking0Empty=" + state.cooking[0].isEmpty() + " alignmentAngle=" + state.alignmentAngle
         );
      }
   }

   private void resolve(ItemStackRenderState itemState, net.minecraft.world.item.ItemStack stack, TileEntityFirePit be, int seedOffset) {
      this.itemModelResolver.updateForTopItem(itemState, stack, ItemDisplayContext.FIXED, be.getLevel(), null,
         (int) be.getBlockPos().asLong() + seedOffset);
   }

   @Override
   public void submit(FirePitRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
      poseStack.pushPose();
      // Rotate the whole pit around its centre by the alignment angle.
      poseStack.translate(0.5F, 0.5F, 0.5F);
      poseStack.mulPose(Axis.YP.rotationDegrees((float) state.alignmentAngle));
      poseStack.translate(-0.5F, -0.5F, -0.5F);

      // Fuel: lying flat, spinning by block position, half scale.
      if (!state.fuel.isEmpty()) {
         poseStack.pushPose();
         poseStack.translate(0.5F, 0.2F, 0.5F);
         poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
         poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F * state.fuelRotationSteps));
         poseStack.scale(0.5F, 0.5F, 0.5F);
         state.fuel.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
         poseStack.popPose();
      }

      for (int i = 0; i < 3; i++) {
         submitPlaced(state.cooking[i], COOKING[i], poseStack, collector, state.lightCoords);
         submitPlaced(state.cooked[i], COOKED[i], poseStack, collector, state.lightCoords);
      }

      poseStack.popPose();
   }

   private static void submitPlaced(ItemStackRenderState itemState, Placement p, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords) {
      if (itemState.isEmpty()) {
         return;
      }
      poseStack.pushPose();
      poseStack.translate(p.tx(), p.ty(), p.tz());
      // TRSRTransformation.quatFromXYZDegrees applies the rotations in X, then Y, then Z order.
      poseStack.mulPose(Axis.XP.rotationDegrees(p.ax()));
      poseStack.mulPose(Axis.YP.rotationDegrees(p.ay()));
      poseStack.mulPose(Axis.ZP.rotationDegrees(p.az()));
      poseStack.scale(p.scale(), p.scale(), p.scale());
      itemState.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
      poseStack.popPose();
   }
}
