package org.millenaire.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * Holder that registers all Millénaire commands with the Brigadier dispatcher.
 *
 * <p>In 1.12 each command implemented Forge's {@code ICommand} and was registered through
 * {@code FMLServerStartingEvent.registerServerCommand}. In 26.2 commands are Brigadier
 * {@link com.mojang.brigadier.builder.LiteralArgumentBuilder LiteralArgumentBuilder}s registered against a
 * {@link CommandDispatcher}. Each Millénaire command class now exposes a static
 * {@code register(CommandDispatcher<CommandSourceStack>)} method; this holder simply calls them all.</p>
 *
 * <p>The integrator wires {@link #registerAll(CommandDispatcher)} into Fabric's
 * {@code CommandRegistrationCallback.EVENT}.</p>
 */
public class MillCommands {
   public static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
      CommandDebugResendProfiles.register(dispatcher);
      CommandDebugResetVillagers.register(dispatcher);
      CommandRenameVillage.register(dispatcher);
      CommandListActiveVillages.register(dispatcher);
      CommandTeleportToVillage.register(dispatcher);
      CommandGiveReputation.register(dispatcher);
      CommandSpawnVillage.register(dispatcher, false);
      CommandSpawnVillage.register(dispatcher, true);
      CommandImportCulture.register(dispatcher);
      CommandSwitchVillageControl.register(dispatcher);
      CommandDebugMode.register(dispatcher);
   }
}
