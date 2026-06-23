package org.millenaire.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.world.UserProfile;

/**
 * Crop seeds that plant a Mill crop block on tilled soil. 1.12 implemented Forge's {@code IPlantable}
 * (getPlant/getPlantType) and overrode {@code onItemUse} using {@code canSustainPlant}/{@code
 * onBlockPlacedBy}. Forge's IPlantable and those hooks are gone in 26.2: planting goes through
 * {@code useOn(UseOnContext)} with the crop requirement replicated directly (clicked block is
 * FARMLAND with air above), implemented below.
 */
public class ItemMillSeeds extends ItemMill {
   public final Block crops;
   public final String cropKey;

   public ItemMillSeeds(Block crops, String cropKey) {
      super(cropKey);
      this.crops = crops;
      this.cropKey = cropKey;
   }

   @Override
   public InteractionResult useOn(UseOnContext context) {
      Player player = context.getPlayer();
      Level world = context.getLevel();
      BlockPos pos = context.getClickedPos();
      Direction facing = context.getClickedFace();
      if (player == null) {
         return InteractionResult.FAIL;
      }

      ItemStack itemstack = context.getItemInHand();
      // 1.12 required clicking the top of a block whose soil canSustainPlant(EnumPlantType.Crop) — i.e.
      // tilled farmland — with air above. Forge's IPlantable/canSustainPlant is gone in 26.2, so replicate
      // the crop requirement directly: the clicked block must be farmland and the space above must be air.
      if (facing == Direction.UP
         && world.getBlockState(pos).is(Blocks.FARMLAND)
         && world.getBlockState(pos.above()).isAir()) {
         UserProfile profile = Mill.getMillWorld(world).getProfile(player);
         if (!profile.isTagSet("cropplanting_" + this.cropKey) && !MillConfigValues.DEV) {
            if (!world.isClientSide()) {
               ServerSender.sendTranslatedSentence(player, 'f', "ui.cropplantingknowledge");
            }

            return InteractionResult.FAIL;
         } else {
            world.setBlockAndUpdate(pos.above(), this.crops.defaultBlockState());
            // 1.12 also called crops.onBlockPlacedBy + the vanilla PLACED_BLOCK criterion; CropBlock has
            // no placement-orientation state so setPlacedBy is a no-op here, but we grant Mill's
            // MASTER_FARMER advancement as before (the vanilla PLACED_BLOCK criterion fires from normal
            // block placement, which this isn't, so it is intentionally omitted).
            org.millenaire.common.advancements.MillAdvancements.MASTER_FARMER.grant(player);
            itemstack.shrink(1);
            return InteractionResult.SUCCESS;
         }
      } else {
         return InteractionResult.FAIL;
      }
   }
}
