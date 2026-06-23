package org.millenaire.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.utilities.MillLog;

/**
 * {@code /milldebug on|off|status} - flips the master {@link MillConfigValues#DEBUG_MODE} switch at runtime.
 *
 * <p>When DEBUG_MODE is on, every {@code [MILLDEBUG]} diagnostic log point across the mod fires
 * (village generation, villager lifecycle, render binding, block placement, packet flow, etc.).
 * When off, all those points are cheap boolean no-ops. This lets the developer toggle the flood of
 * diagnostic logging without restarting / editing the config file.</p>
 */
public class CommandDebugMode {
   public static final String NAME = "milldebug";

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal(NAME)
            .then(Commands.literal("on").executes(c -> set(c, true)))
            .then(Commands.literal("off").executes(c -> set(c, false)))
            .then(Commands.literal("status").executes(CommandDebugMode::status))
            // /milldebug dump [radius] — structured text inventory of every Mill block/BE/entity +
            // its visual/render state around the player (op-gated inside buildSubcommand()).
            .then(CommandDebugDump.buildSubcommand())
            // /milldebug catalog — COMPREHENSIVE registry-iterating static catalog (every block/item/
            // entity/villager-type) + dynamic scenario inventory, emitting ███ CATALOG / ███ SCENARIO /
            // ███ COVERAGE SUMMARY lines (op-gated inside buildSubcommand()).
            .then(CommandCatalog.buildSubcommand())
            .executes(CommandDebugMode::status)
      );
   }

   private static int set(CommandContext<CommandSourceStack> c, boolean on) {
      MillConfigValues.DEBUG_MODE = on;
      String msg = "[MILLDEBUG] master debug mode is now " + (on ? "ON" : "OFF");
      // Log it both ways: writeText so it lands in the log regardless of the switch, and feedback to the player.
      MillLog.writeText(msg);
      c.getSource().sendSuccess(() -> Component.literal(msg), true);
      return 1;
   }

   private static int status(CommandContext<CommandSourceStack> c) {
      String msg = "[MILLDEBUG] master debug mode is currently " + (MillConfigValues.DEBUG_MODE ? "ON" : "OFF");
      c.getSource().sendSuccess(() -> Component.literal(msg), false);
      return 1;
   }
}
