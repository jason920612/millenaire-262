package org.millenaire.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import org.millenaire.common.entity.EntityWallDecoration;

/**
 * Places a wall decoration (tapestry/statue/icon/carpet) hanging entity. 1.12 overrode
 * {@code onItemUse}; 26.2 uses {@code useOn(UseOnContext)}. {@code world.spawnEntity}→
 * {@code world.addFreshEntity}.
 */
public class ItemWallDecoration extends ItemMill {
   public int type;

   public ItemWallDecoration(String itemName, int type) {
      super(itemName);
      this.type = type;
   }

   @Override
   public InteractionResult useOn(UseOnContext context) {
      Player player = context.getPlayer();
      Level worldIn = context.getLevel();
      BlockPos pos = context.getClickedPos();
      Direction facing = context.getClickedFace();
      if (player == null) {
         return InteractionResult.FAIL;
      }

      ItemStack itemstack = player.getItemInHand(context.getHand());
      BlockPos blockpos = pos.relative(facing);
      if (facing != Direction.DOWN && facing != Direction.UP && player.mayUseItemAt(blockpos, facing, itemstack)) {
         EntityWallDecoration entityhanging = org.millenaire.common.entity.MillEntities.WALL_DECORATION
            .create(worldIn, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
         if (entityhanging != null) {
            entityhanging.initDecoration(blockpos, facing, this.type, false);
            if (entityhanging.survives()) {
               if (!worldIn.isClientSide()) {
                  entityhanging.playPlacementSound();
                  worldIn.addFreshEntity(entityhanging);
               }

               itemstack.shrink(1);
            }
         }

         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.FAIL;
      }
   }
}
