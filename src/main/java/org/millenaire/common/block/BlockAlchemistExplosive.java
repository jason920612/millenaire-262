package org.millenaire.common.block;

import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.utilities.WorldUtilities;

public class BlockAlchemistExplosive extends Block {
   private static final int EXPLOSION_RADIUS = 32;

   public BlockAlchemistExplosive(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .strength(1.5F, 10.0F));
   }

   private void alchemistExplosion(Level world, BlockPos pos) {
      int centreX = pos.getX();
      int centreY = pos.getY();
      int centreZ = pos.getZ();
      WorldUtilities.setBlockAndMetadata(world, centreX, centreY, centreZ, Blocks.AIR, 0, true, false);

      for (int dy = EXPLOSION_RADIUS; dy >= -EXPLOSION_RADIUS; dy--) {
         if (dy + centreY >= 0 && dy + centreY < 128) {
            for (int dx = -EXPLOSION_RADIUS; dx <= EXPLOSION_RADIUS; dx++) {
               for (int dz = -EXPLOSION_RADIUS; dz <= EXPLOSION_RADIUS; dz++) {
                  if (dx * dx + dy * dy + dz * dz <= 1024) {
                     Block block = WorldUtilities.getBlock(world, centreX + dx, centreY + dy, centreZ + dz);
                     if (block != Blocks.AIR) {
                        WorldUtilities.setBlockAndMetadata(world, centreX + dx, centreY + dy, centreZ + dz, Blocks.AIR, 0, true, false);
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
         BiConsumer<ItemStack, BlockPos> dropConsumer) {
      this.alchemistExplosion(level, pos);
   }
}
