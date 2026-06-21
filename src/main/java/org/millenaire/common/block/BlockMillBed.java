package org.millenaire.common.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.millenaire.common.entity.TileEntityMillBed;
import org.millenaire.common.forge.MillRegistry;

/**
 * Millénaire bed: in 26.2 vanilla beds no longer carry a block entity, but Millénaire
 * keeps {@link TileEntityMillBed}, so this implements {@link EntityBlock} directly.
 * The 1.12 AABB keyed by a custom bed height becomes a {@link VoxelShape}.
 */
public class BlockMillBed extends BedBlock implements EntityBlock {
   // Real beds register with height 4 (MillBlocks.bed_straw / bed_charpoy), giving a low collision shape.
   // The codec must preserve that height on a round-trip; hardcoding 16 here rebuilt a full-block-tall bed.
   // Carry the height as an extra "height" field so the reconstructed block keeps the registered value.
   public static final MapCodec<BlockMillBed> CODEC = RecordCodecBuilder.mapCodec(
      i -> i.group(
            DyeColor.CODEC.fieldOf("color").forGetter(BedBlock::getColor),
            Codec.INT.optionalFieldOf("height", 4).forGetter(BlockMillBed::getBedHeight),
            propertiesCodec()
         )
         .apply(i, (color, height, props) -> new BlockMillBed(props, height)));
   private final VoxelShape bedShape;
   private final int bedHeight;

   public BlockMillBed(String blockName, int height) {
      this(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .strength(0.2F)
         .noOcclusion(), height);
   }

   private BlockMillBed(BlockBehaviour.Properties properties, int height) {
      super(DyeColor.RED, properties);
      this.bedHeight = height;
      this.bedShape = Block.box(0.0, 0.0, 0.0, 16.0, height, 16.0);
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new TileEntityMillBed(pos, state);
   }

   public int getBedHeight() {
      return this.bedHeight;
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return this.bedShape;
   }
}
