package org.millenaire.mixin.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;

import org.millenaire.common.entity.MillVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O4 client renderer companion: render a MILL-VILLAGER-owned bobber (model + rod→bobber line).
 *
 * <p>Vanilla {@link FishingHookRenderer#shouldRender} hard-gates on {@code getPlayerOwner() != null}
 * (FishingHookRenderer.java:34) and {@code extractRenderState} computes the line origin only for a Player owner
 * (:143-152). A villager owner is never a Player, so without this mixin the bobber model would not draw at all.
 *
 * <ul>
 *   <li>{@code shouldRender}: also render when the owner is a {@link MillVillager} (so the bobber MODEL + bobbing
 *       animation are visible).</li>
 *   <li>{@code extractRenderState} (TAIL): when {@code getPlayerOwner()==null} but the owner is a MillVillager, set
 *       the line origin from the villager's body (a hand-height point in front of it) so the fishing LINE draws from
 *       the villager to the bobber. A simplified analogue of vanilla's {@code getPlayerHandPos} non-first-person
 *       branch — good enough for a third-person NPC; never first-person.</li>
 * </ul>
 *
 * <p>Guarded to MillVillager owners; real-player fishing rendering is untouched.
 */
@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererMixin {

   @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
   private void millenaire$renderVillagerHook(
      FishingHook entity, Frustum culler, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
      if (cir.getReturnValueZ()) {
         return; // already rendering (real-player hook).
      }
      // Vanilla returned false only because there's no Player owner. A MillVillager-owned bobber should still render;
      // we trust the dispatcher's own visibility/distance handling (the extra frustum micro-cull is a perf detail,
      // not correctness — over-rendering a tiny bobber is harmless).
      if (entity.getOwner() instanceof MillVillager) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "extractRenderState", at = @At("TAIL"))
   private void millenaire$villagerLineOrigin(FishingHook entity, FishingHookRenderState state, float partialTicks, CallbackInfo ci) {
      if (entity.getPlayerOwner() != null) {
         return; // real player: vanilla already set the line origin.
      }
      Entity owner = entity.getOwner();
      if (!(owner instanceof MillVillager villager)) {
         return;
      }
      // Hand-height point ~0.8 in front of the villager, mirroring getPlayerHandPos's non-first-person geometry.
      float yRot = Mth.lerp(partialTicks, villager.yBodyRotO, villager.yBodyRot) * (float) (Math.PI / 180.0);
      double sin = Mth.sin(yRot);
      double cos = Mth.cos(yRot);
      double rightOffset = 0.35;
      double forwardOffset = 0.8;
      Vec3 handPos = villager.getEyePosition(partialTicks)
         .add(-cos * rightOffset - sin * forwardOffset, -0.45, -sin * rightOffset + cos * forwardOffset);
      Vec3 hookPos = entity.getPosition(partialTicks).add(0.0, 0.25, 0.0);
      state.lineOriginOffset = handPos.subtract(hookPos);
   }
}
