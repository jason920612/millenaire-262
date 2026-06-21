package org.millenaire.common.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.world.MillTreeGenerator;
import org.millenaire.common.world.WorldGenAppleTree;
import org.millenaire.common.world.WorldGenCherry;
import org.millenaire.common.world.WorldGenOliveTree;
import org.millenaire.common.world.WorldGenPistachio;
import org.millenaire.common.world.WorldGenSakura;

/**
 * Millénaire fruit-tree sapling. {@link #generateTree} grows the matching Mill custom tree via the
 * ported {@code common/world} generators (WorldGenAppleTree/Cherry/Olive/Pistachio/Sakura). The
 * removed Forge {@code TerrainGen.saplingGrowTree} hook is simply dropped. Block-API parts (STAGE
 * property, randomTick, bonemeal) are ported.
 */
public class BlockMillSapling extends VegetationBlock implements BonemealableBlock {
   public static final MapCodec<BlockMillSapling> CODEC = simpleCodec(p -> {
      throw new UnsupportedOperationException("BlockMillSapling is registered programmatically");
   });
   public static final IntegerProperty STAGE = BlockStateProperties.STAGE;
   protected static final VoxelShape SAPLING_SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);
   private final EnumMillWoodType type;

   public BlockMillSapling(String blockName, EnumMillWoodType type) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.GRASS)
         .strength(0.0F)
         .randomTicks()
         .noCollision()
         .noOcclusion());
      this.type = type;
      this.registerDefaultState(this.stateDefinition.any().setValue(STAGE, 0));
   }

   @Override
   protected MapCodec<BlockMillSapling> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(STAGE);
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SAPLING_SHAPE;
   }

   public EnumMillWoodType getMillType() {
      return this.type;
   }

   public void generateTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
      // 1.12 used Forge TerrainGen.saplingGrowTree (removed) + the WorldGen* generators below.
      // The Mill tree generators (common/world/*) take a java.util.Random and place blocks
      // imperatively via Level#setBlock; bridge RandomSource -> java.util.Random by seed.
      java.util.Random rand = new java.util.Random(random.nextLong());
      MillTreeGenerator generator = switch (this.type) {
         case APPLETREE -> new WorldGenAppleTree(true);
         case OLIVETREE -> new WorldGenOliveTree(true);
         case PISTACHIO -> new WorldGenPistachio(true);
         case CHERRY -> new WorldGenCherry(true);
         case SAKURA -> new WorldGenSakura(true);
      };

      // Clear the sapling, run the generator, restore the sapling if nothing was placed
      // (matches the 1.12 set-air / regenerate / restore-on-failure sequence).
      level.setBlock(pos, Blocks.AIR.defaultBlockState(), 4);
      if (!generator.generate(level, rand, pos)) {
         level.setBlock(pos, state, 4);
      }
   }

   public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
      if (state.getValue(STAGE) == 0) {
         level.setBlock(pos, state.cycle(STAGE), 4);
      } else {
         this.generateTree(level, pos, state, random);
      }
   }

   @Override
   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if (level.getMaxLocalRawBrightness(pos.above()) >= 9 && random.nextInt(7) == 0) {
         this.advanceTree(level, pos, state, random);
      }
   }

   @Override
   public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
      return true;
   }

   @Override
   public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
      return random.nextFloat() < 0.45;
   }

   @Override
   public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
      this.advanceTree(level, pos, state, random);
   }

   public enum EnumMillWoodType {
      APPLETREE(MapColor.WOOD),
      OLIVETREE(MapColor.WOOD),
      PISTACHIO(MapColor.WOOD),
      CHERRY(MapColor.WOOD),
      SAKURA(MapColor.WOOD);

      private final MapColor mapColor;

      EnumMillWoodType(MapColor mapColorIn) {
         this.mapColor = mapColorIn;
      }

      public MapColor getMapColor() {
         return this.mapColor;
      }
   }
}
