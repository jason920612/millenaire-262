package org.millenaire.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

import org.millenaire.common.block.BlockPath;
import org.millenaire.common.block.BlockPathSlab;
import org.millenaire.common.block.IBlockPath;
import org.millenaire.common.forge.MillRegistry;

/**
 * BlockItem for the path slabs. Like vanilla double-slabs, clicking a second path slab onto a matching
 * half forms the full ({@link BlockPath}, STABLE) path block. Vanilla {@link net.minecraft.world.level
 * .block.SlabBlock} can't do this because Mill path slabs are plain blocks (not SlabBlock subclasses)
 * and the "full" form is a distinct block, so the 1.12 hand-rolled merge is faithfully reproduced in
 * {@link #useOn}; anything else falls back to normal {@link BlockItem} placement of the half slab.
 */
public class ItemPathSlab extends BlockItem {
   private final BlockPathSlab singleSlab;
   private final BlockPath doubleSlab;

   public ItemPathSlab(BlockPathSlab halfBlock, BlockPath fullBlock) {
      super(halfBlock, new Item.Properties().setId(MillRegistry.itemKeyFor(halfBlock)));
      this.singleSlab = halfBlock;
      this.doubleSlab = fullBlock;
   }

   @Override
   public InteractionResult useOn(UseOnContext context) {
      Player player = context.getPlayer();
      Level world = context.getLevel();
      BlockPos pos = context.getClickedPos();
      Direction facing = context.getClickedFace();
      ItemStack itemstack = context.getItemInHand();

      if (player != null && !itemstack.isEmpty() && player.mayUseItemAt(pos.relative(facing), facing, itemstack)) {
         BlockState clicked = world.getBlockState(pos);
         if (clicked.getBlock() == this.singleSlab) {
            Half half = clicked.getValue(BlockPathSlab.HALF);
            // Clicking the open face of an existing half slab completes it into the full path block.
            if ((facing == Direction.UP && half == Half.BOTTOM) || (facing == Direction.DOWN && half == Half.TOP)) {
               BlockState doubleState = this.doubleSlab.defaultBlockState().setValue(IBlockPath.STABLE, true);
               if (world.setBlock(pos, doubleState, 11)) {
                  SoundType soundtype = doubleState.getSoundType();
                  world.playSound(
                     player, pos, soundtype.getPlaceSound(), SoundSource.BLOCKS,
                     (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F
                  );
                  itemstack.shrink(1);
               }

               return InteractionResult.SUCCESS;
            }
         }
      }

      return super.useOn(context);
   }
}
