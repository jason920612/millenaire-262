package org.millenaire.common.block.mock;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.millenaire.common.entity.TileEntityMockBanner;
import org.millenaire.common.forge.MillRegistry;

public class MockBlockBannerHanging extends WallBannerBlock {
   public static final MapCodec<MockBlockBannerHanging> CODEC = RecordCodecBuilder.mapCodec(
      i -> i.group(DyeColor.CODEC.fieldOf("color").forGetter(MockBlockBannerHanging::getColor), propertiesCodec())
         .apply(i, (color, props) -> new MockBlockBannerHanging(props, 0)));
   public final int bannerType;

   public MockBlockBannerHanging(String blockName, int bannerType) {
      this(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.WOOD)
         .strength(1.0F)
         .noOcclusion(), bannerType);
   }

   private MockBlockBannerHanging(BlockBehaviour.Properties properties, int bannerType) {
      super(DyeColor.WHITE, properties);
      this.bannerType = bannerType;
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new TileEntityMockBanner(pos, state);
   }
}
