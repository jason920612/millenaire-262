package org.millenaire.common.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.millenaire.common.utilities.Point;

public class EntityTargetedGhast extends Ghast {
   public Point target = null;

   public EntityTargetedGhast(EntityType<? extends Ghast> type, Level level) {
      super(type, level);
   }

   @Override
   public boolean removeWhenFarAway(double distSqr) {
      return false;
   }

   @Override
   public void tick() {
      // 1.12 did this in onUpdate(); 26.2's per-tick hook is tick(). getMoveHelper→getMoveControl,
      // setMoveTo→setWantedPosition, getAIMoveSpeed→getSpeed, rand→getRandom().
      if (this.target != null) {
         if (this.target.distanceTo(this) > 20.0) {
            this.getMoveControl().setWantedPosition(this.target.x, this.target.y, this.target.z, this.getSpeed());
         } else if (this.target.distanceTo(this) < 10.0) {
            this.getMoveControl()
               .setWantedPosition(
                  this.target.x + (this.getRandom().nextFloat() * 2.0F - 1.0F) * 16.0F,
                  this.target.y + (this.getRandom().nextFloat() * 2.0F - 1.0F) * 16.0F,
                  this.target.z + (this.getRandom().nextFloat() * 2.0F - 1.0F) * 16.0F,
                  this.getSpeed()
               );
         }
      }

      super.tick();
   }

   @Override
   protected void readAdditionalSaveData(ValueInput input) {
      super.readAdditionalSaveData(input);
      this.target = TileEntityLockedChest.readPoint(input, "targetPoint");
   }

   @Override
   protected void addAdditionalSaveData(ValueOutput output) {
      super.addAdditionalSaveData(output);
      TileEntityLockedChest.writePoint(output, "targetPoint", this.target);
   }
}
