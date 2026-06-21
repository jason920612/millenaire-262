package org.millenaire.common.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.millenaire.common.utilities.Point;

public class EntityTargetedBlaze extends Blaze {
   public Point target = null;

   public EntityTargetedBlaze(EntityType<? extends Blaze> type, Level level) {
      super(type, level);
   }

   @Override
   public boolean removeWhenFarAway(double distSqr) {
      // 1.12 canDespawn()==false → never despawn.
      return false;
   }

   @Override
   public boolean isInWaterOrRain() {
      // 1.12 overrode isWet()→false so rain wouldn't extinguish it.
      return false;
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
