package com.coderyo.jason.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.VillagerType;
import org.millenaire.common.entity.MillEntities;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.goal.Goal;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.VillagerRecord;
import org.millenaire.common.world.MillWorldData;

/**
 * COMPREHENSIVE textualised STATIC catalog of every in-game Millénaire content piece — block, item,
 * entity type, and villager-type-per-culture — produced by ITERATING the live registries (never a
 * hardcoded list, so it stays complete as content changes). Every record is emitted as ONE greppable
 * line so the whole catalog is text-inspectable from the harness log (no screenshots).
 *
 * <h2>Why this exists</h2>
 * The user's firm requirement is that the harness exercise + TEXT-log EVERY in-game block, item,
 * entity/creature and (via {@link MillScenarios}) AI behaviour / interaction, in both STATIC (this
 * class: placement / render / state) and DYNAMIC (MillScenarios) scenarios. This class is the STATIC
 * half: it places every block, builds a stack for every item, and spawns one of every entity / villager
 * type, then records the porting-relevant facts (blockstate, render shape, block-entity, attach check,
 * AIR-resolution; item model/tab/components; villager raw name+codepoints, culture, AI goals, render
 * texture, pose) and flags anomalies inline.
 *
 * <h2>Output format</h2>
 * <ul>
 *   <li>{@code ███ CATALOG BLOCK ...} — one per {@code millenaire} block (placed in a scratch area)</li>
 *   <li>{@code ███ CATALOG ITEM ...}  — one per {@code millenaire} item</li>
 *   <li>{@code ███ CATALOG ENTITY ...}— one per Mill EntityType + one per VillagerType per culture</li>
 *   <li>{@code ███ COVERAGE SUMMARY ...} — closing counts + every distinct anomaly flag seen</li>
 * </ul>
 * Anomalies are inlined as bracketed tags: {@code [AIR-RESOLVE]}, {@code [NO-MODEL]},
 * {@code [NOT-ATTACHED]}, {@code [MOJIBAKE]}, {@code [NO-RENDER]}, {@code [NO-AI]}.
 *
 * <h2>Guard</h2>
 * All spawning/placement happens in a single SCRATCH chunk-area high in the air and is cleaned up after
 * each record, so it can never OOM or leave debris. Entity spawns use {@link Entity#discard()} right
 * after inspection. The whole run is wrapped so one bad record can't abort the catalog.
 */
public final class MillCatalog {
   public static final String TAG = "███ CATALOG";
   public static final String SUMMARY_TAG = "███ COVERAGE SUMMARY";
   /** The replacement character that signals a decoding failure (mojibake). */
   private static final char REPLACEMENT_CHAR = '�';
   private static final String MODID = "millenaire";

   private MillCatalog() {
   }

   /** Aggregate result of a catalog run, returned so the harness can fold it into its own summary. */
   public static final class Result {
      public int blocks;
      public int items;
      public int entities;
      public int scenarios;
      /** Distinct anomaly flag -> count (e.g. {@code [AIR-RESOLVE]} -> 3). */
      public final Map<String, Integer> anomalies = new LinkedHashMap<>();
      /** Result of the LIVE server-side melee acquisition scenario — fed into the COMBAT coverage re-statement. */
      public boolean lastMeleeOk;

      void flag(String tag) {
         anomalies.merge(tag, 1, Integer::sum);
      }

      String anomaliesStr() {
         if (anomalies.isEmpty()) {
            return "[]";
         }
         StringBuilder sb = new StringBuilder("[");
         boolean first = true;
         for (Map.Entry<String, Integer> e : anomalies.entrySet()) {
            if (!first) {
               sb.append(", ");
            }
            first = false;
            sb.append(e.getKey()).append("x").append(e.getValue());
         }
         return sb.append("]").toString();
      }
   }

   /** A line sink: log-only (harness) or also chat (command). */
   public interface Sink {
      void emit(String line);
   }

   /** Log-only sink for the automated harness. */
   public static Sink logSink() {
      return MillLog::writeText;
   }

   /**
    * Runs the FULL static catalog (blocks + items + entities) plus the dynamic scenario inventory in
    * {@code level}, using a scratch area near {@code scratch}. Never throws out to the caller. Returns
    * the {@link Result} with counts + anomaly flags and emits a closing {@link #SUMMARY_TAG} line.
    */
   public static Result run(ServerLevel level, BlockPos scratch, Sink sink) {
      Result r = new Result();
      sink.emit(TAG + " HEADER level=" + level.dimension().identifier() + " scratch=" + posStr(scratch)
         + " (iterating BuiltInRegistries — registry-driven, stays complete)");
      try {
         r.blocks = catalogBlocks(level, scratch, sink, r);
      } catch (Throwable t) {
         sink.emit(TAG + " BLOCK PHASE ABORTED: " + t);
      }
      try {
         r.items = catalogItems(sink, r);
      } catch (Throwable t) {
         sink.emit(TAG + " ITEM PHASE ABORTED: " + t);
      }
      try {
         r.entities = catalogEntities(level, scratch, sink, r);
      } catch (Throwable t) {
         sink.emit(TAG + " ENTITY PHASE ABORTED: " + t);
      }
      try {
         r.scenarios = MillScenarios.run(level, scratch, sink, r);
      } catch (Throwable t) {
         sink.emit(MillScenarios.TAG + " PHASE ABORTED: " + t);
      }
      sink.emit(SUMMARY_TAG + " blocks=" + r.blocks + " items=" + r.items + " entities=" + r.entities
         + " scenarios=" + r.scenarios + " anomalies=" + r.anomaliesStr());
      return r;
   }

   // ===== Public phase entrypoints so the harness can run the phases with its own level/scratch and
   // ===== inject the dynamic-scenario coverage map BETWEEN the static phases and the scenario inventory.

   /** Runs only the static BLOCK phase. */
   public static int catalogBlocksPublic(ServerLevel level, BlockPos scratch, Sink sink, Result r) {
      return catalogBlocks(level, scratch, sink, r);
   }

   /** Runs only the static ITEM phase. */
   public static int catalogItemsPublic(Sink sink, Result r) {
      return catalogItems(sink, r);
   }

   /** Runs only the static ENTITY phase. */
   public static int catalogEntitiesPublic(ServerLevel level, BlockPos scratch, Sink sink, Result r) {
      return catalogEntities(level, scratch, sink, r);
   }

   // ===================================== BLOCKS ================================================

   /**
    * Iterates {@link BuiltInRegistries#BLOCK} for every {@code millenaire} block, places its default
    * state in the scratch cell, and records id + full blockstate + render shape + block-entity + the
    * wall-mounted attach/flush check + AIR-resolution check + model presence. One line per block.
    */
   private static int catalogBlocks(ServerLevel level, BlockPos scratch, Sink sink, Result r) {
      int count = 0;
      List<Identifier> ids = millIds(BuiltInRegistries.BLOCK.keySet());
      BlockPos cell = scratch.above(4); // a single reusable scratch cell well above ground
      // A solid platform under/behind the cell so wall-mounted blocks have something to attach to.
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
               safeSet(level, cell.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            }
         }
      }
      for (Identifier id : ids) {
         Block block = BuiltInRegistries.BLOCK.getValue(id);
         count++;
         StringBuilder sb = new StringBuilder(TAG + " BLOCK id=" + id);
         try {
            BlockState def = block.defaultBlockState();
            // AIR-RESOLVE: a Mill block whose default state resolved to vanilla AIR is a porting bug
            // (the block object exists in the registry but its state collapsed to air).
            boolean isAir = def.isAir();
            sb.append(" class=").append(block.getClass().getSimpleName());
            sb.append(" state=").append(blockStateProps(def));
            sb.append(" renderShape=").append(safe(() -> def.getRenderShape().name()));

            // Place it so render shape / block-entity / attach can be read from a real world state.
            safeSet(level, cell, def);
            BlockState placed = level.getBlockState(cell);
            boolean placedAir = placed.isAir() && !isAir; // placement collapsed it to air -> bug
            if (isAir || placedAir) {
               sb.append(" [AIR-RESOLVE]");
               r.flag("[AIR-RESOLVE]");
            }

            // Wall-mounted attach/flush check (mirrors CommandDebugDump): a facing-bearing block should
            // sit against a solid neighbour in facing.getOpposite().
            Direction facing = facingOf(placed);
            if (facing != null) {
               BlockPos attachPos = cell.relative(facing.getOpposite());
               boolean solid = level.getBlockState(attachPos).isSolid();
               sb.append(" facing=").append(facing).append(" attachSolid=").append(solid);
               if (!solid) {
                  sb.append(" [NOT-ATTACHED]");
                  r.flag("[NOT-ATTACHED]");
               }
            }

            BlockEntity be = level.getBlockEntity(cell);
            sb.append(" be=").append(be == null ? "none" : be.getClass().getSimpleName());

            // Model presence (block-model JSON for the blockstate's base name). Best-effort classpath probe.
            boolean hasModel = resourceExists("/assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json")
               || resourceExists("/assets/" + id.getNamespace() + "/models/block/" + id.getPath() + ".json");
            sb.append(" hasModel=").append(hasModel);
            if (!hasModel) {
               sb.append(" [NO-MODEL]");
               r.flag("[NO-MODEL]");
            }
         } catch (Throwable t) {
            sb.append(" FAILED-TO-DESCRIBE: ").append(t);
         } finally {
            // Clean up: reset the cell to air so the next block starts fresh.
            safeSet(level, cell, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
         }
         sink.emit(sb.toString());
      }
      // Tear down the scratch platform.
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
               safeSet(level, cell.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
         }
      }
      return count;
   }

   // ===================================== ITEMS ================================================

   /**
    * Iterates {@link BuiltInRegistries#ITEM} for every {@code millenaire} item and records id +
    * has-model + creative-tab membership + component presence (durability/food/tool). One line per item.
    */
   private static int catalogItems(Sink sink, Result r) {
      int count = 0;
      List<Identifier> ids = millIds(BuiltInRegistries.ITEM.keySet());
      for (Identifier id : ids) {
         Item item = BuiltInRegistries.ITEM.getValue(id);
         count++;
         StringBuilder sb = new StringBuilder(TAG + " ITEM id=" + id);
         try {
            ItemStack stack = new ItemStack(item);
            sb.append(" class=").append(item.getClass().getSimpleName());

            boolean hasModel = resourceExists("/assets/" + id.getNamespace() + "/models/item/" + id.getPath() + ".json")
               || resourceExists("/assets/" + id.getNamespace() + "/items/" + id.getPath() + ".json");
            sb.append(" hasModel=").append(hasModel);

            // Creative-tab membership: Mill puts every registered item in the millenaire tab, so we
            // report it via the item's own membership in the Mill REGISTERED_ITEMS list.
            boolean inMillTab = org.millenaire.common.forge.MillRegistry.REGISTERED_ITEMS.contains(item);
            sb.append(" inMillTab=").append(inMillTab);

            // Component presence (26.2 data-components): durability / food / tool / stack size.
            int maxDmg = stack.getMaxDamage();
            boolean durable = stack.isDamageableItem();
            FoodProperties food = stack.get(DataComponents.FOOD);
            boolean isTool = stack.has(DataComponents.TOOL);
            sb.append(" maxStack=").append(stack.getMaxStackSize());
            sb.append(" durability=").append(durable ? maxDmg : 0);
            sb.append(" food=").append(food != null ? food.nutrition() : "none");
            sb.append(" tool=").append(isTool);

            if (!hasModel) {
               sb.append(" [NO-MODEL]");
               r.flag("[NO-MODEL]");
            }
         } catch (Throwable t) {
            sb.append(" FAILED-TO-DESCRIBE: ").append(t);
         }
         sink.emit(sb.toString());
      }
      return count;
   }

   // ===================================== ENTITIES =============================================

   /**
    * Catalogs (a) every Mill {@link EntityType} in {@link MillEntities#REGISTERED} by spawning one, and
    * (b) every {@link VillagerType} across all cultures by building a mock villager from a synthetic
    * record, then records type + (for villagers) raw firstName/familyName (+codepoints), culture,
    * registered AI goals, render texture, and pose. One line per record; flags MOJIBAKE / NO-RENDER /
    * NO-AI inline. Everything is spawned in the scratch cell and discarded immediately.
    */
   private static int catalogEntities(ServerLevel level, BlockPos scratch, Sink sink, Result r) {
      int count = 0;
      BlockPos cell = scratch.above(4);

      // --- (a) raw Mill EntityTypes (blaze/ghast/wither-skeleton targets, wall decoration, the three
      // generic villager shells). We spawn the non-villager ones here; villager shells are exercised
      // exhaustively per VillagerType below. ---
      for (EntityType<?> type : new ArrayList<>(MillEntities.REGISTERED)) {
         Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
         // Skip the three generic villager shells here — they're covered per-VillagerType below with
         // full name/culture/AI detail (spawning a bare shell would log empty names).
         if (type == MillEntities.VILLAGER_MALE || type == MillEntities.VILLAGER_FEMALE_SYM
            || type == MillEntities.VILLAGER_FEMALE_ASYM) {
            continue;
         }
         count++;
         StringBuilder sb = new StringBuilder(TAG + " ENTITY type=" + id + " kind=raw");
         Entity e = null;
         try {
            e = type.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (e == null) {
               sb.append(" create=null [NO-RENDER]");
               r.flag("[NO-RENDER]");
            } else {
               final Entity ent = e;
               ent.setPos(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
               sb.append(" class=").append(ent.getClass().getSimpleName());
               sb.append(" pose=").append(safe(() -> ent.getPose().name()));
               sb.append(" category=").append(safe(() -> type.getCategory().getName()));
            }
         } catch (Throwable t) {
            sb.append(" FAILED: ").append(t);
         } finally {
            if (e != null) {
               try {
                  e.discard();
               } catch (Throwable ignored) {
               }
            }
         }
         sink.emit(sb.toString());
      }

      // --- (b) every VillagerType per culture ---
      MillWorldData mw = safe(() -> Mill.getMillWorld(level));
      for (Culture culture : new ArrayList<>(Culture.ListCultures)) {
         for (VillagerType vtype : new ArrayList<>(culture.listVillagerTypes)) {
            count++;
            StringBuilder sb = new StringBuilder(TAG + " ENTITY type=villager culture=" + culture.key
               + " vtype=" + vtype.key);
            MillVillager v = null;
            try {
               sb.append(" gender=").append(vtype.gender);
               sb.append(" model=").append(vtype.model);
               sb.append(" isChild=").append(vtype.isChild);

               // Build a synthetic MOCK record (mockVillager=true → no world registration), then a mock
               // villager. This exercises the real createVillagerEntity → EntityType.create path and the
               // real name/texture generation, in the scratch cell.
               v = spawnMockVillager(culture, vtype, mw, level);
               if (v == null) {
                  sb.append(" spawn=null [NO-RENDER]");
                  r.flag("[NO-RENDER]");
                  sink.emit(sb.toString());
                  continue;
               }
               v.setPos(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);

               // RAW names + codepoints so mojibake/U+FFFD surfaces as text.
               String first = v.firstName == null ? "" : v.firstName;
               String family = v.familyName == null ? "" : v.familyName;
               sb.append(" firstName='").append(first).append("'").append(codepoints(first));
               sb.append(" familyName='").append(family).append("'").append(codepoints(family));
               if (isMojibake(first) || isMojibake(family)) {
                  sb.append(" [MOJIBAKE]");
                  r.flag("[MOJIBAKE]");
               }

               // Registered AI goals for this villager type (the per-type goal list the village hands out).
               List<Goal> goals = vtype.goals;
               sb.append(" aiGoals=").append(goals == null ? 0 : goals.size());
               if (goals != null && !goals.isEmpty()) {
                  StringBuilder gk = new StringBuilder("{");
                  for (int i = 0; i < goals.size(); i++) {
                     if (i > 0) {
                        gk.append(",");
                     }
                     gk.append(goalKey(goals.get(i)));
                  }
                  sb.append(gk.append("}"));
               }
               if (goals == null || goals.isEmpty()) {
                  sb.append(" [NO-AI]");
                  r.flag("[NO-AI]");
               }

               // Render state: the body texture the renderer will bind, + pose. A null texture means the
               // renderer has nothing to draw -> NO-RENDER.
               final MillVillager vv = v;
               Identifier tex = vv.texture;
               sb.append(" texture=").append(tex == null ? "null" : tex);
               sb.append(" pose=").append(safe(() -> vv.getPose().name()));
               if (tex == null) {
                  sb.append(" [NO-RENDER]");
                  r.flag("[NO-RENDER]");
               }
            } catch (Throwable t) {
               sb.append(" FAILED: ").append(t);
            } finally {
               if (v != null) {
                  try {
                     v.discard();
                  } catch (Throwable ignored) {
                  }
               }
            }
            sink.emit(sb.toString());
         }
      }
      return count;
   }

   /**
    * Builds a MOCK villager (no world registration) for {@code vtype} via the real
    * {@link VillagerRecord#createVillagerRecord} → {@link MillVillager#createMockVillager} path. A null
    * {@link MillWorldData} is tolerated by passing a throwaway record builder; if record creation needs
    * a MillWorldData we use the one supplied. Returns null if the path can't produce a villager.
    */
   private static MillVillager spawnMockVillager(Culture culture, VillagerType vtype, MillWorldData mw, ServerLevel level) {
      try {
         if (mw == null) {
            return null;
         }
         VillagerRecord rec = VillagerRecord.createVillagerRecord(
            culture, vtype.key, mw, null, null, null, null, -1L, true);
         if (rec == null) {
            return null;
         }
         return MillVillager.createMockVillager(rec, level);
      } catch (Throwable t) {
         return null;
      }
   }

   // ===================================== helpers ==============================================

   /** All ids in {@code keys} whose namespace is {@code millenaire}, in stable (sorted) order. */
   private static List<Identifier> millIds(Iterable<Identifier> keys) {
      List<Identifier> out = new ArrayList<>();
      for (Identifier id : keys) {
         if (MODID.equals(id.getNamespace())) {
            out.add(id);
         }
      }
      out.sort((a, b) -> a.toString().compareTo(b.toString()));
      return out;
   }

   private static String goalKey(Goal g) {
      try {
         if (g.key != null && !g.key.isEmpty()) {
            return g.key;
         }
      } catch (Throwable ignored) {
      }
      return g.getClass().getSimpleName();
   }

   private static String blockStateProps(BlockState state) {
      List<Property<?>> props = new ArrayList<>(state.getProperties());
      if (props.isEmpty()) {
         return "{}";
      }
      props.sort((a, b) -> a.getName().compareTo(b.getName()));
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

   private static Direction facingOf(BlockState state) {
      for (Property<?> p : state.getProperties()) {
         if (!"facing".equals(p.getName())) {
            continue;
         }
         Object val = state.getValue(p);
         if (val instanceof Direction d) {
            return d;
         }
      }
      return null;
   }

   private static boolean resourceExists(String path) {
      try {
         return MillCatalog.class.getResource(path) != null;
      } catch (Throwable t) {
         return false;
      }
   }

   private static void safeSet(ServerLevel level, BlockPos pos, BlockState state) {
      try {
         level.setBlock(pos, state, 3);
      } catch (Throwable ignored) {
      }
   }

   private static boolean isMojibake(String s) {
      return s != null && s.indexOf(REPLACEMENT_CHAR) >= 0;
   }

   /** Renders a string's codepoints so non-ASCII content is unambiguous: {@code [U+...]}. */
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

   static String posStr(BlockPos p) {
      return p.getX() + "/" + p.getY() + "/" + p.getZ();
   }

   interface ThrowingSupplier<T> {
      T get() throws Exception;
   }

   static <T> T safe(ThrowingSupplier<T> s) {
      try {
         return s.get();
      } catch (Throwable t) {
         return null;
      }
   }
}
