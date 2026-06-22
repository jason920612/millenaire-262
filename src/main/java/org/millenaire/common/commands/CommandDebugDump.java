package org.millenaire.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.millenaire.common.entity.EntityWallDecoration;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.entity.TileEntityFirePit;
import org.millenaire.common.entity.TileEntityMockBanner;
import org.millenaire.common.entity.TileEntityPanel;
import org.millenaire.common.utilities.MillLog;

/**
 * {@code /milldebug dump [radius]} — prints a STRUCTURED, GREPPABLE TEXT report of every
 * Millénaire-related thing around the player (blocks, block-entities, entities) plus the
 * VISUAL/render-relevant state of each, to chat AND the log, so visual issues can be diagnosed
 * by text alone (no screenshots needed).
 *
 * <h2>Why this exists</h2>
 * Visual bugs — a sign not flush on the wall, a floating wall-torch, a mojibake villager name,
 * a villager asleep while fighting, a villager targeting itself — have historically been
 * mis-diagnosed because there was no ground-truth text data. This command emits exactly that:
 * for every record it prints position, registry id, the FULL blockstate (every property=value),
 * an attachment/flush check for wall-mounted blocks, render-shape + TESR resolution for panels,
 * and the full villager state with anomalies flagged inline.
 *
 * <h2>Output format</h2>
 * Every record is ONE line so it greps cleanly out of the log. Records are tagged:
 * <ul>
 *   <li>{@code ███ MILLDUMP HEADER ...} — one per run, the run parameters/counts</li>
 *   <li>{@code ███ MILLDUMP BLOCK ...} — a Mill or Mill-placed wall block + its blockstate</li>
 *   <li>{@code ███ MILLDUMP ENTITY ...} — a MillVillager / wall decoration + its state</li>
 *   <li>{@code ███ MILLDUMP SUMMARY ...} — closing counts</li>
 * </ul>
 * Anomalies are inlined as bracketed tags, e.g. {@code [NOT-ATTACHED]}, {@code [MOJIBAKE]},
 * {@code [SELF-TARGET]}, {@code [SLEEP+COMBAT]}, {@code [FLOATING]}.
 *
 * <h2>Extending</h2>
 * Add a new per-type detail by extending {@link #describeBlockEntity} (block-entity types) or
 * {@link #describeEntity} (entity types). Keep each addition to a single {@code key=value} token
 * appended to the line so the one-line/greppable contract holds.
 *
 * <h2>Guard</h2>
 * Registered under the existing {@code /milldebug} command and gated on permission level 2 (op),
 * so it is not usable in normal play.
 */
public final class CommandDebugDump {
   public static final String TAG = "███ MILLDUMP";
   private static final int DEFAULT_RADIUS = 8;
   private static final int MAX_RADIUS = 64;
   /** The replacement character that signals a decoding failure (mojibake). */
   private static final char REPLACEMENT_CHAR = '�';

   private CommandDebugDump() {
   }

   /**
    * Builds the {@code dump [radius]} sub-tree to be attached under the {@code /milldebug} root by
    * {@link CommandDebugMode}. Returns the literal so the parent can {@code .then(...)} it.
    */
   public static LiteralArgumentBuilder<CommandSourceStack> buildSubcommand() {
      return Commands.literal("dump")
         .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
         .executes(c -> run(c, DEFAULT_RADIUS))
         .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
            .executes(c -> run(c, IntegerArgumentType.getInteger(c, "radius"))));
   }

   /** Optional standalone registration (not required when attached under /milldebug). */
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal("milldump").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
         .executes(c -> run(c, DEFAULT_RADIUS))
         .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
            .executes(c -> run(c, IntegerArgumentType.getInteger(c, "radius")))));
   }

   private static int run(CommandContext<CommandSourceStack> c, int radius) throws CommandSyntaxException {
      CommandSourceStack source = c.getSource();
      Player player = source.getPlayerOrException();
      ServerLevel level = source.getLevel();
      BlockPos center = player.blockPosition();

      // Both the chat feedback and the log get every line. We emit to the log via MillLog.writeText
      // (lands in the log regardless of the DEBUG_MODE switch) and echo to chat via sendSuccess so
      // the operator sees it in-game too.
      DumpSink sink = new DumpSink(source);

      sink.emit(TAG + " HEADER center=" + posStr(center) + " radius=" + radius
         + " level=" + level.dimension().identifier() + " by=" + player.getName().getString());

      int blockCount = dumpBlocks(level, center, radius, sink);
      int entityCount = dumpEntities(level, center, radius, sink);

      sink.emit(TAG + " SUMMARY blocks=" + blockCount + " entities=" + entityCount
         + " (anomalies tagged inline in []) — see log for the full greppable report");
      return 1;
   }

   /**
    * Log-only entry point for automated harnesses (e.g. MillClientSelfTest / MillSelfTest): runs the
    * same dump around {@code center} in {@code level}, emitting every record to the log ONLY (no chat
    * source needed). Never throws out to the caller.
    *
    * @return the number of records (blocks + entities) emitted.
    */
   public static int dumpToLog(ServerLevel level, BlockPos center, int radius) {
      int r = Math.max(1, Math.min(MAX_RADIUS, radius));
      DumpSink sink = new DumpSink(null);
      sink.emit(TAG + " HEADER center=" + posStr(center) + " radius=" + r
         + " level=" + level.dimension().identifier() + " by=AUTOMATED-HARNESS");
      int blocks = 0;
      int entities = 0;
      try {
         blocks = dumpBlocks(level, center, r, sink);
         entities = dumpEntities(level, center, r, sink);
      } catch (Throwable t) {
         sink.emit(TAG + " ABORTED: " + t);
      }
      sink.emit(TAG + " SUMMARY blocks=" + blocks + " entities=" + entities);
      return blocks + entities;
   }

   // ===================================== BLOCKS ================================================

   private static int dumpBlocks(ServerLevel level, BlockPos center, int radius, DumpSink sink) {
      int count = 0;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      int minY = Math.max(level.getMinY(), center.getY() - radius);
      int maxY = Math.min(level.getMaxY(), center.getY() + radius);
      // Sort-free scan; records are emitted in scan order but each carries its absolute position.
      for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
         for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
            for (int y = minY; y <= maxY; y++) {
               m.set(x, y, z);
               BlockState state = level.getBlockState(m);
               if (!isOfInterest(state)) {
                  continue;
               }
               count++;
               try {
                  sink.emit(describeBlock(level, m.immutable(), state));
               } catch (Throwable t) {
                  sink.emit(TAG + " BLOCK @" + posStr(m) + " FAILED-TO-DESCRIBE: " + t);
               }
            }
         }
      }
      return count;
   }

   /** Mill blocks, plus the vanilla wall-mounted ones Mill places (signs / wall torches / banners). */
   private static boolean isOfInterest(BlockState state) {
      Block block = state.getBlock();
      Identifier id = BuiltInRegistries.BLOCK.getKey(block);
      if (id == null) {
         return false;
      }
      if ("millenaire".equals(id.getNamespace())) {
         return true;
      }
      String path = id.getPath();
      // Vanilla wall-mounted / hung blocks that Mill places into buildings, whose flush/attach state
      // is exactly what the developer needs to verify by text.
      return path.contains("wall_sign")
         || path.contains("wall_torch")
         || path.contains("wall_banner")
         || path.equals("sign")
         || path.endsWith("_sign")
         || path.endsWith("_banner")
         || path.contains("wall_hanging_sign")
         || path.contains("torch")
         || path.contains("ladder")
         || path.contains("painting");
   }

   private static String describeBlock(ServerLevel level, BlockPos pos, BlockState state) {
      Block block = state.getBlock();
      Identifier id = BuiltInRegistries.BLOCK.getKey(block);
      StringBuilder sb = new StringBuilder(TAG + " BLOCK @" + posStr(pos) + " id=" + id);
      sb.append(" state=").append(blockStateProps(state));
      sb.append(" renderShape=").append(safe(() -> state.getRenderShape().name()));

      // Wall-mounted attachment / flush check: derive the facing, find the neighbour the block should
      // be attached to (in the OPPOSITE direction of facing for signs/decor; FACING direction it points
      // FROM), and report whether that neighbour is solid. This makes "sign not on wall" provable.
      Direction facing = facingOf(state);
      if (facing != null) {
         // Wall signs/torches/decor attach to the block BEHIND them = relative(facing.getOpposite()).
         BlockPos attachPos = pos.relative(facing.getOpposite());
         BlockState attach = level.getBlockState(attachPos);
         boolean solid = attach.isSolid();
         Identifier attachId = BuiltInRegistries.BLOCK.getKey(attach.getBlock());
         sb.append(" facing=").append(facing)
            .append(" attach@").append(posStr(attachPos)).append("=").append(attachId)
            .append(solid ? "(SOLID)" : "(NOT-SOLID)");
         if (!solid) {
            sb.append(" [NOT-ATTACHED]");
         }
      }

      // Floating-torch check: a freestanding (non-wall) torch should sit on a solid block below it.
      Identifier bid = BuiltInRegistries.BLOCK.getKey(block);
      if (bid != null && bid.getPath().contains("torch") && facing == null) {
         BlockState below = level.getBlockState(pos.below());
         if (!below.isSolid()) {
            sb.append(" support@").append(posStr(pos.below())).append("=")
               .append(BuiltInRegistries.BLOCK.getKey(below.getBlock())).append("(NOT-SOLID) [FLOATING]");
         }
      }

      BlockEntity be = level.getBlockEntity(pos);
      if (be != null) {
         sb.append(" be=").append(be.getClass().getSimpleName());
         describeBlockEntity(be, sb);
      }
      return sb.toString();
   }

   /**
    * Per-block-entity render-relevant detail. EXTENSION POINT: add a new {@code if (be instanceof …)}
    * branch and append one or more {@code key=value} tokens.
    */
   private static void describeBlockEntity(BlockEntity be, StringBuilder sb) {
      if (be instanceof TileEntityPanel panel) {
         // The TESR (TESRPanel) draws the board + icons + text. These fields are what the render-state
         // extractor reads, so reporting them proves whether a blank/broken panel is data or render.
         sb.append(" panelType=").append(panel.panelType)
            .append(" buildingPos=").append(panel.buildingPos)
            .append(" texture=").append(panel.texture == null ? "DEFAULT(null)" : panel.texture)
            .append(" untranslatedLines=").append(panel.untranslatedLines.size())
            .append(" displayLines=").append(panel.displayLines.size());
         if (panel.panelType != 0 && panel.untranslatedLines.isEmpty()) {
            sb.append(" [PANEL-NO-CONTENT]");
         }
      } else if (be instanceof TileEntityMockBanner) {
         sb.append(" bannerBE=present");
      } else if (be instanceof TileEntityFirePit) {
         sb.append(" firePitBE=present");
      }
   }

   // ===================================== ENTITIES =============================================

   private static int dumpEntities(ServerLevel level, BlockPos center, int radius, DumpSink sink) {
      AABB box = new AABB(center).inflate(radius);
      List<Entity> entities = new ArrayList<>(level.getEntitiesOfClass(Entity.class, box, CommandDebugDump::isEntityOfInterest));
      entities.sort(Comparator.comparingDouble(e -> e.distanceToSqr(Vec3.atCenterOf(center))));
      for (Entity e : entities) {
         try {
            sink.emit(describeEntity(e));
         } catch (Throwable t) {
            sink.emit(TAG + " ENTITY @" + posStr(e.blockPosition()) + " FAILED-TO-DESCRIBE: " + t);
         }
      }
      return entities.size();
   }

   private static boolean isEntityOfInterest(Entity e) {
      return e instanceof MillVillager || e instanceof EntityWallDecoration;
   }

   /**
    * Per-entity detail. EXTENSION POINT: add a new {@code if (e instanceof …)} branch for more Mill
    * entity types (animals, targeted mobs). Keep each datum a single {@code key=value} token.
    */
   private static String describeEntity(Entity e) {
      if (e instanceof MillVillager v) {
         return describeVillager(v);
      }
      if (e instanceof EntityWallDecoration d) {
         StringBuilder sb = new StringBuilder(TAG + " ENTITY @" + posStr(d.blockPosition()) + " type=WallDecoration");
         Direction dir = safe(d::getDirection);
         sb.append(" facing=").append(dir);
         sb.append(" hangPos=").append(safe(() -> posStr(d.getPos())));
         return sb.toString();
      }
      return TAG + " ENTITY @" + posStr(e.blockPosition()) + " type=" + e.getType();
   }

   private static String describeVillager(MillVillager v) {
      StringBuilder sb = new StringBuilder(TAG + " ENTITY @" + posStr(v.blockPosition()) + " type=MillVillager");
      sb.append(" uuid=").append(v.getUUID());
      sb.append(" id=").append(safe(v::getVillagerId));

      // RAW names (NOT translated/cleaned) so mojibake/U+FFFD surfaces as text. We render each name
      // with its codepoints so the log is unambiguous regardless of console encoding.
      String first = v.firstName == null ? "" : v.firstName;
      String family = v.familyName == null ? "" : v.familyName;
      sb.append(" firstName='").append(first).append("'").append(codepoints(first));
      sb.append(" familyName='").append(family).append("'").append(codepoints(family));
      if (isMojibake(first) || isMojibake(family)) {
         sb.append(" [MOJIBAKE]");
      }

      sb.append(" culture=").append(safe(() -> v.getCulture() == null ? "null" : v.getCulture().key));
      sb.append(" vtype=").append(safe(() -> v.vtype == null ? "null" : v.vtype.key));
      sb.append(" goalKey=").append(v.goalKey);
      sb.append(" pose=").append(safe(() -> v.getPose().name()));
      sb.append(" swimming=").append(safe(v::isSwimming));
      sb.append(" sprinting=").append(safe(v::isSprinting));
      sb.append(" isRaider=").append(v.isRaider);
      sb.append(" helpsInAttacks=").append(safe(v::helpsInAttacks));
      sb.append(" shouldLieDown=").append(v.shouldLieDown);

      float health = safeFloat(v::getHealth, -1f);
      float maxHealth = safeFloat(v::getMaxHealth, -1f);
      sb.append(" health=").append(health).append("/").append(maxHealth);

      LivingEntity target = safe(v::getTarget);
      sb.append(" target=").append(target == null ? "null" : (target.getType() + "@" + posStr(target.blockPosition())));

      // ---- anomaly flags ----
      if (target == v) {
         sb.append(" [SELF-TARGET]");
      }
      try {
         if (target != null && v.getPose() == net.minecraft.world.entity.Pose.SLEEPING) {
            sb.append(" [SLEEP+COMBAT]");
         }
      } catch (Throwable ignored) {
      }
      if (target != null && v.shouldLieDown) {
         sb.append(" [LIEDOWN+COMBAT]");
      }
      if (health == 0f) {
         sb.append(" [DEAD-BUT-PRESENT]");
      }
      return sb.toString();
   }

   // ===================================== helpers ==============================================

   /** The full blockstate as {@code prop=value,prop=value} (sorted by property name), or {@code {}}. */
   private static String blockStateProps(BlockState state) {
      List<Property<?>> props = new ArrayList<>(state.getProperties());
      if (props.isEmpty()) {
         return "{}";
      }
      props.sort(Comparator.comparing(Property::getName));
      StringBuilder sb = new StringBuilder("{");
      boolean first = true;
      for (Property<?> p : props) {
         if (!first) {
            sb.append(",");
         }
         first = false;
         sb.append(p.getName()).append("=").append(propValue(state, p));
      }
      return sb.append("}").toString();
   }

   private static <T extends Comparable<T>> String propValue(BlockState state, Property<T> p) {
      return p.getName(state.getValue(p));
   }

   /**
    * The attachment facing of a state if it has one, else null. Matches any property named
    * {@code facing} whose value is a {@link Direction} — robust across 26.2's
    * {@code EnumProperty<Direction>} (vanilla FACING) without depending on a concrete property class.
    */
   private static Direction facingOf(BlockState state) {
      for (Property<?> p : state.getProperties()) {
         if (!"facing".equals(p.getName())) {
            continue;
         }
         Object v = state.getValue(p);
         if (v instanceof Direction d) {
            return d;
         }
      }
      return null;
   }

   private static boolean isMojibake(String s) {
      if (s == null) {
         return false;
      }
      return s.indexOf(REPLACEMENT_CHAR) >= 0;
   }

   /** Renders a string's codepoints so non-ASCII content is unambiguous in the log: {@code [U+...]}. */
   private static String codepoints(String s) {
      if (s == null || s.isEmpty()) {
         return "";
      }
      boolean hasNonAscii = false;
      for (int i = 0; i < s.length(); i++) {
         if (s.charAt(i) > 0x7F) {
            hasNonAscii = true;
            break;
         }
      }
      if (!hasNonAscii) {
         return "";
      }
      StringBuilder sb = new StringBuilder("[U+");
      int[] cps = s.codePoints().toArray();
      for (int i = 0; i < cps.length; i++) {
         if (i > 0) {
            sb.append(' ');
         }
         sb.append(String.format("%04X", cps[i]));
      }
      return sb.append("]").toString();
   }

   private static String posStr(BlockPos p) {
      return p.getX() + "/" + p.getY() + "/" + p.getZ();
   }

   private interface ThrowingSupplier<T> {
      T get() throws Exception;
   }

   private static <T> T safe(ThrowingSupplier<T> s) {
      try {
         return s.get();
      } catch (Throwable t) {
         return null;
      }
   }

   private static float safeFloat(ThrowingSupplier<Float> s, float dflt) {
      try {
         Float f = s.get();
         return f == null ? dflt : f;
      } catch (Throwable t) {
         return dflt;
      }
   }

   /** Sends a line to both the log (greppable, survives the DEBUG switch) and the command source chat. */
   private static final class DumpSink {
      private final CommandSourceStack source;

      DumpSink(CommandSourceStack source) {
         this.source = source;
      }

      void emit(String line) {
         MillLog.writeText(line);
         if (source != null) {
            source.sendSuccess(() -> Component.literal(line), false);
         }
      }
   }
}
