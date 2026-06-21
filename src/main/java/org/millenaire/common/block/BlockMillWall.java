package org.millenaire.common.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.millenaire.common.forge.MillRegistry;

/**
 * Millénaire wall: 1.12 had a hand-rolled wall block with custom UP/NORTH/EAST/SOUTH/WEST
 * boolean connection properties + AABB tables + getActualState connection logic. 26.2's
 * {@link WallBlock} handles all of that (UP + per-side {@link net.minecraft.world.level.block.state.properties.WallSide}
 * + waterlogging + voxel shapes), so this only carries the base-block properties and delegates
 * right-click to the base block.
 */
public class BlockMillWall extends WallBlock {
   public static final MapCodec<BlockMillWall> CODEC = simpleCodec(p -> {
      throw new UnsupportedOperationException("BlockMillWall is registered programmatically");
   });
   private final Block baseBlock;

   public BlockMillWall(String blockName, Block baseBlock) {
      // ofFullCopy copies the base block's behaviour, INCLUDING its mapColor function. Variant blocks
      // like BlockDecorativeStone use a state-lambda `mapColor(s -> s.getValue(VARIANT)…)`; copied onto
      // a WallBlock (whose states have no VARIANT) that lambda throws at state construction. Override it
      // with a constant equal to the base block's default-state colour so the wall keeps a matching tint.
      super(BlockBehaviour.Properties.ofFullCopy(baseBlock).mapColor(baseBlock.defaultMapColor()).setId(MillRegistry.blockKey(blockName)));
      this.baseBlock = baseBlock;
   }

   public Block getBaseBlock() {
      return this.baseBlock;
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
      return this.baseBlock.defaultBlockState().useWithoutItem(level, player, hitResult);
   }
}
