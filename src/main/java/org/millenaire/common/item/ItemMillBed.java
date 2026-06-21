package org.millenaire.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import org.millenaire.common.entity.TileEntityMillBed;
import org.millenaire.common.forge.MillRegistry;

/**
 * Places a Millénaire two-block bed (straw / charpoy) and copies its display values into the bed
 * block entity. 1.12 overrode {@code onItemUse}; 26.2 routes through {@code useOn(UseOnContext)}.
 * {@code BlockBed.EnumPartType}→{@link BedPart}; {@code Direction.byHorizontalIndex(Mth.floor(yaw..))}
 * →{@code Direction.fromYRot(getYRot())}; {@code setBlockState}→{@code setBlock}; the
 * {@code isReplaceable}/{@code isTopSolid}/{@code notifyNeighborsRespectDebug} hooks are simplified.
 */
public class ItemMillBed extends BlockItem {
   public ItemMillBed(Block bed) {
      super(bed, new Item.Properties().setId(MillRegistry.itemKeyFor(bed)));
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

      if (worldIn.isClientSide()) {
         return InteractionResult.SUCCESS;
      } else if (facing != Direction.UP) {
         return InteractionResult.FAIL;
      } else {
         BlockState clicked = worldIn.getBlockState(pos);
         boolean replaceable = clicked.canBeReplaced();
         if (!replaceable) {
            pos = pos.above();
         }

         Direction enumfacing = Direction.fromYRot(player.getYRot());
         BlockPos blockpos = pos.relative(enumfacing);
         ItemStack itemstack = player.getItemInHand(context.getHand());
         if (player.mayUseItemAt(pos, facing, itemstack) && player.mayUseItemAt(blockpos, facing, itemstack)) {
            BlockState headSpace = worldIn.getBlockState(blockpos);
            boolean footFree = replaceable || worldIn.getBlockState(pos).isAir();
            boolean headFree = headSpace.canBeReplaced() || headSpace.isAir();
            if (footFree
               && headFree
               && worldIn.getBlockState(pos.below()).isFaceSturdy(worldIn, pos.below(), Direction.UP)
               && worldIn.getBlockState(blockpos.below()).isFaceSturdy(worldIn, blockpos.below(), Direction.UP)) {
               BlockState foot = this.getBlock()
                  .defaultBlockState()
                  .setValue(BedBlock.OCCUPIED, false)
                  .setValue(HorizontalDirectionalBlock.FACING, enumfacing)
                  .setValue(BedBlock.PART, BedPart.FOOT);
               worldIn.setBlock(pos, foot, 10);
               worldIn.setBlock(blockpos, foot.setValue(BedBlock.PART, BedPart.HEAD), 10);

               BlockEntity headEntity = worldIn.getBlockEntity(blockpos);
               if (headEntity instanceof TileEntityMillBed) {
                  ((TileEntityMillBed)headEntity).setItemValues(itemstack);
               }

               BlockEntity footEntity = worldIn.getBlockEntity(pos);
               if (footEntity instanceof TileEntityMillBed) {
                  ((TileEntityMillBed)footEntity).setItemValues(itemstack);
               }

               worldIn.updateNeighborsAt(pos, this.getBlock());
               worldIn.updateNeighborsAt(blockpos, headSpace.getBlock());
               itemstack.shrink(1);
               return InteractionResult.SUCCESS;
            } else {
               return InteractionResult.FAIL;
            }
         } else {
            return InteractionResult.FAIL;
         }
      }
   }
}
