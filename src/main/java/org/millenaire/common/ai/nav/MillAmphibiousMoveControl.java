package org.millenaire.common.ai.nav;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Amphibious move control: on land it behaves EXACTLY like the vanilla {@link MoveControl} (super.tick), but
 * while in water it swims in real 3D toward the wanted position — pitching the body up/down and driving
 * forward + vertical thrust — the way {@code SmoothSwimmingMoveControl} (dolphins/axolotls) does, so a
 * villager actually swims like a player (rising, diving, crossing to shore) instead of bobbing helplessly.
 * Vanilla's ground MoveControl only does horizontal movement + a jump, so it can't lift an entity through
 * open water; this fills that gap without changing any land behaviour.
 */
public class MillAmphibiousMoveControl extends MoveControl {
   private final int maxTurnX;
   private final int maxTurnY;
   private final float inWaterSpeedModifier;

   public MillAmphibiousMoveControl(Mob mob, int maxTurnX, int maxTurnY, float inWaterSpeedModifier) {
      super(mob);
      this.maxTurnX = maxTurnX;
      this.maxTurnY = maxTurnY;
      this.inWaterSpeedModifier = inWaterSpeedModifier;
   }

   @Override
   public void tick() {
      if (!this.mob.isInWater()) {
         super.tick(); // identical vanilla ground movement
         return;
      }
      // Slight buoyancy so we don't sink while manoeuvring (mirrors SmoothSwimmingMoveControl).
      this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(0.0, 0.005, 0.0));
      if (this.operation != MoveControl.Operation.MOVE_TO) {
         // No active destination: idle in water (the buoyancy above keeps us afloat).
         this.mob.setSpeed(0.0F);
         this.mob.setXxa(0.0F);
         this.mob.setYya(0.0F);
         this.mob.setZza(0.0F);
         return;
      }
      double xd = this.wantedX - this.mob.getX();
      double yd = this.wantedY - this.mob.getY();
      double zd = this.wantedZ - this.mob.getZ();
      if (xd * xd + yd * yd + zd * zd < 2.5E-7) {
         this.mob.setZza(0.0F);
         return;
      }
      // Yaw toward the target (horizontal heading).
      float yRot = (float)(Mth.atan2(zd, xd) * 180.0F / (float)Math.PI) - 90.0F;
      this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yRot, this.maxTurnY));
      this.mob.yBodyRot = this.mob.getYRot();
      this.mob.yHeadRot = this.mob.getYRot();
      float speed = (float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)) * this.inWaterSpeedModifier;
      this.mob.setSpeed(speed);
      // Pitch up/down toward the target's height → this is what makes it dive/rise like a swimmer.
      double horiz = Math.sqrt(xd * xd + zd * zd);
      if (Math.abs(yd) > 1.0E-5 || Math.abs(horiz) > 1.0E-5) {
         float xRot = -((float)(Mth.atan2(yd, horiz) * 180.0F / (float)Math.PI));
         xRot = Mth.clamp(Mth.wrapDegrees(xRot), -this.maxTurnX, this.maxTurnX);
         this.mob.setXRot(this.rotateTowards(this.mob.getXRot(), xRot, 5.0F));
      }
      float cos = Mth.cos(this.mob.getXRot() * (float)(Math.PI / 180.0));
      float sin = Mth.sin(this.mob.getXRot() * (float)(Math.PI / 180.0));
      this.mob.setZza(cos * speed);    // forward thrust along the pitch
      this.mob.setYya(-sin * speed);   // vertical thrust (rise when pitched up, dive when down)
   }
}
