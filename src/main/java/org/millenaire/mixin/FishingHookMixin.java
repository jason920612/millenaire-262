package org.millenaire.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import com.coderyo.jason.ops.VillagerFishing;
import org.millenaire.common.entity.MillVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * O4 — player-like villager fishing with the FULL vanilla bobber animation.
 *
 * <h2>The problem</h2>
 * {@link FishingHook} is Player-bound. Its {@code tick()} fetches {@code getPlayerOwner()} and, when that is
 * {@code null}, {@code discard()}s the hook (FishingHook.java:157-159); {@code getPlayerOwner()} returns null
 * unless the owner {@code instanceof Player} (:533-535). {@code catchingFish}/{@code retrieve}/{@code shouldStopFishing}
 * are all Player-gated too. A {@link MillVillager} is a {@code Mob}, never a {@code Player}, so a villager-owned
 * bobber self-destructs on its very first tick and never animates.
 *
 * <h2>What this mixin relaxes (and ONLY for Mill villagers)</h2>
 * At the HEAD of {@code tick()} we check the owner. If it is a {@link MillVillager} (so vanilla would discard it),
 * we run a self-contained villager tick that mirrors the vanilla BOBBING physics + the bite FSM — reusing the
 * vanilla private {@code catchingFish(BlockPos)} (access-widened) so the lure/nibble timers, splash particles and
 * the {@code FISHING_BOBBER_SPLASH} "bite" sound are byte-for-byte the real animation — then {@code ci.cancel()}s
 * the vanilla body (skipping the player-gated discard + {@code shouldStopFishing}). On a bite ({@code nibble > 0})
 * the op {@link VillagerFishing} rolls {@code BuiltInLootTables.FISHING} and spawns the loot ItemEntities flying
 * toward the villager, mirroring {@code FishingHook.retrieve} :449-466 — and discards the hook.
 *
 * <p><b>Guard:</b> the injected logic runs ONLY when {@code getOwner() instanceof MillVillager}. A real player's
 * hook has a non-Mill owner, so this branch is never taken and vanilla fishing is completely unchanged. A hook with
 * no owner at all still hits vanilla's discard (we only intercept the villager case).
 *
 * <p>Only the FishingHook-DECLARED private members (the bite-FSM fields + {@code catchingFish}) are {@code @Shadow}n
 * (those are what the access-widener opens). Everything else the villager tick needs — {@code getDeltaMovement},
 * {@code setDeltaMovement}, {@code move}, {@code onGround}, {@code getX/Y/Z}, {@code blockPosition}, {@code level},
 * {@code getOwner}, {@code discard}, {@code getInterpolation} — is a PUBLIC Entity/Projectile/FishingHook method, so
 * we call it on a {@code (FishingHook)(Object)this} cast rather than shadowing inherited members (which Mixin can't
 * resolve against the target class without a refmap). The FLYING phase (the cast arc) is a tiny gravity fall here:
 * the villager casts essentially straight down onto its fishing-spot water, so the hook enters BOBBING within a
 * couple of ticks without the private block/entity collision sweep.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {

   // --- vanilla private FSM state, access-widened (millenaire.accesswidener); all DECLARED in FishingHook ---
   @Shadow private int nibble;
   @Shadow private int timeUntilHooked;
   @Shadow private boolean biting;
   @Shadow private boolean openWater;
   @Shadow private int outOfWaterTime;
   @Shadow private int life;

   /** Vanilla private per-in-water-tick bite math (lure/hook/nibble timers + particles + bite sound). */
   @Shadow public abstract void catchingFish(BlockPos blockPos);

   @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
   private void millenaire$villagerTick(CallbackInfo ci) {
      FishingHook self = (FishingHook) (Object) this;
      // GUARD: only Mill-villager-owned hooks. The authoritative villager-owner link is VillagerFishing's registry
      // (keyed by entity id) — NOT vanilla getOwner(), whose EntityReference re-resolves to null for a Mob owner after
      // the first tick (which is exactly why a villager-owned hook self-discards in vanilla). A real player's hook has
      // no registry entry, so this returns null and we fall through to the untouched vanilla body.
      MillVillager villager = VillagerFishing.ownerOf(self.getId());
      if (villager == null) {
         return;
      }

      // Run the interpolation bookkeeping (vanilla tick :155) then OUR bobbing FSM in place of the player-gated body.
      self.getInterpolation().interpolate();

      if (self.level().isClientSide()) {
         // Client just animates from synced data; don't run the server FSM. Cancel so the client body doesn't
         // discard the (player-less) hook either.
         ci.cancel();
         return;
      }

      // --- keep-alive: a grounded hook eventually times out (vanilla life>=1200), like a player's. ---
      if (self.onGround()) {
         this.life++;
         if (this.life >= 1200) {
            VillagerFishing.onHookExpired(villager, self);
            self.discard();
            ci.cancel();
            return;
         }
      } else {
         this.life = 0;
      }

      // --- water detection (vanilla tick :171-178) ---
      BlockPos pos = self.blockPosition();
      FluidState fluidState = self.level().getFluidState(pos);
      float liquidHeight = fluidState.is(FluidTags.WATER) ? fluidState.getHeight(self.level(), pos) : 0.0F;
      boolean isInWater = liquidHeight > 0.0F;

      if (!isInWater) {
         // FLYING / falling toward the water: simple gravity (the villager casts ~straight down onto its spot).
         this.outOfWaterTime = Math.min(10, this.outOfWaterTime + 1);
         self.setDeltaMovement(self.getDeltaMovement().add(0.0, -0.03, 0.0));
      } else {
         // BOBBING (vanilla tick :207-233): float on the surface + run the bite FSM via the real catchingFish.
         this.outOfWaterTime = Math.max(0, this.outOfWaterTime - 1);
         Vec3 movement = self.getDeltaMovement();
         double force = self.getY() + movement.y - pos.getY() - liquidHeight;
         if (Math.abs(force) < 0.01) {
            force += Math.signum(force) * 0.1;
         }
         self.setDeltaMovement(new Vec3(movement.x * 0.9, movement.y - force * villager.getRandom().nextFloat() * 0.2, movement.z * 0.9));
         this.openWater = this.nibble <= 0 && this.timeUntilHooked <= 0
            || this.openWater && this.outOfWaterTime < 10;
         if (this.biting) {
            self.setDeltaMovement(self.getDeltaMovement().add(0.0, -0.1 * villager.getRandom().nextFloat() * villager.getRandom().nextFloat(), 0.0));
         }
         // THE REAL ANIMATION: vanilla lure/hook/nibble FSM (splash particles, bite splash sound, biting flag).
         // catchingFish sets nibble=20-40 when a bite LANDS (FishingHook:346) and DECREMENTS it on later ticks
         // (:309-315). A villager has no player right-click to retrieve(), so we auto-reel the instant a fish is on
         // the line. We sample nibble BOTH before and after catchingFish so a just-decremented-to-0 nibble (or a
         // forced one) still triggers the catch — vanilla's retrieve() likewise fires on any nibble>0.
         boolean biteBefore = this.nibble > 0;
         this.catchingFish(pos);
         if (biteBefore || this.nibble > 0) {
            VillagerFishing.reel(villager, self);
            self.discard();
            ci.cancel();
            return;
         }
      }

      // Movement integration (vanilla tick :240, :248) so the bobber actually bobs on the surface. We use only the
      // PUBLIC move(...) + setDeltaMovement(...); the protected updateRotation/applyEffectsFromBlocks/reapplyPosition
      // are cosmetic-only and skipped to avoid shadowing inherited protected members.
      self.move(MoverType.SELF, self.getDeltaMovement());
      self.setDeltaMovement(self.getDeltaMovement().scale(0.92));

      ci.cancel();
   }
}
