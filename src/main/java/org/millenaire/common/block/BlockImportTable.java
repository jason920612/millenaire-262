package org.millenaire.common.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.millenaire.common.entity.TileEntityImportTable;
import org.millenaire.common.forge.MillRegistry;

public class BlockImportTable extends BaseEntityBlock {
   public static final MapCodec<BlockImportTable> CODEC = simpleCodec(p -> {
      throw new UnsupportedOperationException("BlockImportTable is registered programmatically");
   });

   public BlockImportTable(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.WOOD)
         .strength(1.0F));
   }

   @Override
   protected MapCodec<BlockImportTable> codec() {
      return CODEC;
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new TileEntityImportTable(pos, state);
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
      if (org.millenaire.common.utilities.MillLog.debugOn()) {
         org.millenaire.common.utilities.MillLog.milldebug("Interaction", "player=" + player.getName().getString() + " right-clicked block=import_table at " + pos + " (client=" + level.isClientSide() + ") action=activate");
      }

      BlockEntity te = level.getBlockEntity(pos);
      if (te instanceof TileEntityImportTable importTable) {
         importTable.activate(player);
         return InteractionResult.SUCCESS;
      }
      return InteractionResult.PASS;
   }

   // 1.12 onBlockActivated fired regardless of held item; 26.2 splits item/empty-hand dispatch, so route the
   // with-item click to the same activation path so the import table opens even with an item held.
   @Override
   protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
      return this.useWithoutItem(state, level, pos, player, hitResult);
   }
}
