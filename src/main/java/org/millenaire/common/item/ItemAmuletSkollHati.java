package org.millenaire.common.item;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.utilities.MillLog;

/**
 * Skoll/Hati amulet — toggles day/night when right-clicked. 1.12 overrode
 * {@code onItemRightClick(World, EntityPlayer, EnumHand)} returning {@code ActionResult<ItemStack>};
 * on 26.2 this is {@code use(Level, Player, InteractionHand)} returning {@link InteractionResult}.
 */
public class ItemAmuletSkollHati extends ItemMill {
   public ItemAmuletSkollHati(String itemName) {
      super(itemName);
   }

   @Override
   public InteractionResult use(Level worldIn, Player playerIn, InteractionHand handIn) {
      if (MillConfigValues.LogOther >= 3) {
         MillLog.debug(this, "Using skoll amulet.");
      }

      if (worldIn.isClientSide() || !(worldIn instanceof ServerLevel serverLevel)) {
         return InteractionResult.SUCCESS;
      } else {
         // 1.12: time = world.getWorldTime()+24000; if currently late day/dusk jump to dawn, else jump to night.
         // 26.2: the day-time clock moved to ServerClockManager. Resolve the overworld clock holder and set
         // its total ticks the same way (toggle between dawn and night relative to the next day).
         Optional<? extends Holder<WorldClock>> clock = worldIn.registryAccess().get(WorldClocks.OVERWORLD);
         if (clock.isPresent()) {
            ServerClockManager clockManager = serverLevel.getServer().clockManager();
            Holder<WorldClock> overworld = clock.get();
            long time = clockManager.getTotalTicks(overworld) + 24000L;
            if (time % 24000L > 11000L && time % 24000L < 23500L) {
               clockManager.setTotalTicks(overworld, time - time % 24000L - 500L);
            } else {
               clockManager.setTotalTicks(overworld, time - time % 24000L + 13000L);
            }
         }

         return InteractionResult.SUCCESS;
      }
   }
}
