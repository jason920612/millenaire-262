package org.millenaire.common.block.mock;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.millenaire.common.entity.TileEntityMockBanner;
import org.millenaire.common.forge.MillRegistry;

public class MockBlockBannerStanding extends BannerBlock {
   public static final MapCodec<MockBlockBannerStanding> CODEC = RecordCodecBuilder.mapCodec(
      i -> i.group(DyeColor.CODEC.fieldOf("color").forGetter(MockBlockBannerStanding::getColor), propertiesCodec())
         .apply(i, (color, props) -> new MockBlockBannerStanding(props, 0)));
   public final int bannerType;

   public MockBlockBannerStanding(String blockName, int bannerType) {
      this(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.WOOD)
         .strength(1.0F)
         .noOcclusion(), bannerType);
   }

   private MockBlockBannerStanding(BlockBehaviour.Properties properties, int bannerType) {
      super(DyeColor.WHITE, properties);
      this.bannerType = bannerType;
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new TileEntityMockBanner(pos, state);
   }
}
