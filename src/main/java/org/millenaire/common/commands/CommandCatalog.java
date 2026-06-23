package org.millenaire.common.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import com.coderyo.jason.catalog.MillCatalog;
import org.millenaire.common.utilities.MillLog;

/**
 * {@code /milldebug catalog} — runs the COMPREHENSIVE static catalog + dynamic scenario inventory
 * ({@link MillCatalog}) around the player and prints the greppable {@code ███ CATALOG} /
 * {@code ███ SCENARIO} / {@code ███ COVERAGE SUMMARY} report to both the log and the operator's chat.
 *
 * <p>This is the on-demand sibling of the automated harness phase: the same engine, but invokable in a
 * live world. It iterates the live registries (so it stays complete as content changes) and does all of
 * its spawning/placement in a scratch area near the player, cleaning up after itself.
 *
 * <p>Attached under the existing {@code /milldebug} root and op-gated (LEVEL_ADMINS).
 */
public final class CommandCatalog {
   private CommandCatalog() {
   }

   /** Builds the {@code catalog} sub-tree for {@link CommandDebugMode} to {@code .then(...)}. */
   public static LiteralArgumentBuilder<CommandSourceStack> buildSubcommand() {
      return Commands.literal("catalog")
         .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
         .executes(CommandCatalog::run);
   }

   private static int run(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CommandSourceStack source = c.getSource();
      Player player = source.getPlayerOrException();
      ServerLevel level = source.getLevel();
      // Scratch area well above the player so placement/spawning can't disturb the build.
      BlockPos scratch = player.blockPosition().above(40);

      MillCatalog.Sink sink = line -> {
         MillLog.writeText(line);
         source.sendSuccess(() -> Component.literal(line), false);
      };
      MillCatalog.Result r = MillCatalog.run(level, scratch, sink);
      source.sendSuccess(() -> Component.literal("███ CATALOG done: blocks=" + r.blocks + " items=" + r.items
         + " entities=" + r.entities + " scenarios=" + r.scenarios + " (see log for the full greppable report)"), false);
      return 1;
   }
}
