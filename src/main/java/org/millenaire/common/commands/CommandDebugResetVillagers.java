package org.millenaire.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.VillagerRecord;
import org.millenaire.common.world.MillWorldData;

public class CommandDebugResetVillagers {
   public static final String NAME = "millDebugResetVillagers";

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal(NAME).executes(c -> execute(c.getSource())));
   }

   private static int execute(CommandSourceStack source) throws CommandSyntaxException {
      ServerPlayer senderPlayer = source.getPlayerOrException();
      MillWorldData worldData = Mill.getMillWorld(source.getLevel());
      Building village = worldData.getClosestVillage(new Point(senderPlayer));
      if (village == null || village.getPos().distanceTo(senderPlayer) > 50.0) {
         source.sendFailure(Component.literal("No village within 50 blocks."));
         return 0;
      }

      int despawnedVillagers = 0;
      int respawnedVillagers = 0;

      for (VillagerRecord vr : village.getAllVillagerRecords()) {
         List<MillVillager> matchingVillagers = new ArrayList<>();

         for (MillVillager villager : worldData.getAllKnownVillagers()) {
            if (villager.getVillagerId() == vr.getVillagerId()) {
               matchingVillagers.add(villager);
            }
         }

         for (int i = matchingVillagers.size() - 1; i >= 0; i--) {
            if (matchingVillagers.get(i).isRemoved()) {
               matchingVillagers.get(i).despawnVillagerSilent();
               despawnedVillagers++;
               matchingVillagers.remove(i);
            }
         }

         if (matchingVillagers.size() == 0) {
            village.respawnVillager(vr, vr.getHouse().location.sleepingPos);
            respawnedVillagers++;
         }
      }

      ServerSender.sendChat(
         senderPlayer,
         ChatFormatting.DARK_GREEN,
         "Repeared the villager list of "
            + village.getVillageQualifiedName()
            + ". Despawned "
            + despawnedVillagers
            + " dead villager(s) and respawned "
            + respawnedVillagers
            + " villagers."
      );

      return 1;
   }
}
