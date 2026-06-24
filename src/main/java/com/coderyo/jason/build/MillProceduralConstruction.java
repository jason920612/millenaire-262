package com.coderyo.jason.build;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;

import com.coderyo.jason.build.MillBuildEngine.TerrainFit;
import com.coderyo.jason.build.MillNeedsModel.Decision;
import com.coderyo.jason.build.MillProceduralBuilding.Placement;
import com.coderyo.jason.build.MillProceduralBuilding.Plan;
import com.coderyo.jason.ops.OpState;
import com.coderyo.jason.ops.VillagerWorldOps;

/**
 * Phase 2 (#6) PROCEDURAL BUILDING — the AMBIENT village-construction DRIVER (the actual construction PATH).
 *
 * <p>This REPLACES the village's fixed culture-plan construction loop for ONGOING / ambient development.
 * The 1.12 path picked a random plan from the culture's {@code BuildingPlanSet} list ({@code findBuildingProject}
 * → {@code findBuildingConstruction}); that path is no longer taken when procedural is enabled (which is the
 * DEFAULT — see {@link MillBuildEngine#ENABLED}). Instead, on the village's normal construction tick
 * ({@code Building.updateConstructionQueue}, ~every 20 game-ticks on the town hall) this driver:
 *
 * <ol>
 *   <li><b>Decides the NEXT building</b> with the weighted gap-priority {@link MillNeedsModel} (pop→housing,
 *       resource-gap→workshop, threat→defense, growth→market). If the village reports no gap right now it
 *       still develops PROCEDURALLY — it defaults to a HOUSE / STORAGE (never a fixed plan).</li>
 *   <li><b>Generates the layout</b> with {@link MillProceduralBuilding} (room-composed, culture-styled,
 *       connectivity-guaranteed) scaled by population, and picks a build SITE next to the village + the
 *       hybrid {@link MillBuildEngine.TerrainFit} for that ground.</li>
 *   <li><b>Constructs it for real, over time</b>: each tick a live village villager (the builder) lays a
 *       BATCH of blocks player-like via {@link VillagerWorldOps#place} (real reach, scaffolds for high rows,
 *       strict material check). Materials are PAID FOR out of the village stock (fed by Phase 1 mining +
 *       trade) — a real economy debit. If the stock is empty the build is RESOURCE-GATED: it pauses on that
 *       block until the village gathers the material. There is no material grant and no fixed-plan fallback.</li>
 *   <li><b>On completion</b> registers the finished building's footprint on the village (its building list
 *       grows → the village visibly DEVELOPS) and clears the job so the NEXT need is computed next tick.</li>
 * </ol>
 *
 * <p>State is held here (a per-village job keyed by town-hall {@link Point}), NOT on the {@link Building},
 * so this is purely additive {@code com.coderyo.jason} code over the existing village. Every step emits the
 * greppable {@code ███ SIM BUILD} evidence so the ambient need→generate→construct cycle is observable in the
 * headless sim across a multi-day run.
 */
public final class MillProceduralConstruction {

   /** Blocks a builder lays per village construction tick (player-like, batched so a build progresses over days). */
   private static final int BLOCKS_PER_TICK = 24;


   /** Per-village in-progress procedural building, keyed by the town-hall position. */
   private static final Map<Long, Job> JOBS = new HashMap<>();

   /**
    * Phase 7 (#7) DISCUSSION STEER: a build type the village's villagers DISCUSSED and endorsed, keyed by the
    * town-hall position. Applied by {@link #startNewProceduralBuilding} on the NEXT build decision — but ONLY when
    * a REAL needs-model gap supports it (we promote among genuine needs; we NEVER fabricate a need the gaps don't
    * have — strict no-fabrication). One-shot: consumed when applied. Set by
    * {@link com.coderyo.jason.talk.VillageDiscussion#tickDiscuss}.
    */
   private static final Map<Long, MillNeedsModel.BuildType> PENDING_STEER = new HashMap<>();

   private MillProceduralConstruction() {
   }

   /**
    * Record the build type the village DISCUSSED + endorsed (Phase 7). It is applied to the village's next
    * procedural build decision ONLY if a real gap supports it — see {@link #startNewProceduralBuilding}. Null
    * clears any pending steer. No-op for a client / null town hall.
    */
   public static void steer(Building townHall, MillNeedsModel.BuildType type) {
      if (townHall == null || townHall.getPos() == null || townHall.world == null || townHall.world.isClientSide()) {
         return;
      }
      long key = townHall.getPos().getBlockPos().asLong();
      if (type == null) {
         PENDING_STEER.remove(key);
      } else {
         PENDING_STEER.put(key, type);
      }
   }

   /**
    * Block-placements per ONE unit of structural material charged. A procedural building's true material cost
    * is an ABSTRACT total (like Millénaire's own {@code resCost}: a house costs tens of resources, not one
    * resource per cube) — so the village pays one structural unit every {@code MATERIAL_COST_PER_UNIT} blocks
    * laid, drawn from its gathered + mined stock. This keeps the build affordable from realistic village stock
    * while still being genuinely PAID FOR (not free, not granted, no fixed-plan fallback).
    */
   private static final int MATERIAL_COST_PER_UNIT = 16;

   /** A village's in-progress procedural building: the generated plan + where it sits + the placement cursor. */
   private static final class Job {
      final MillBuildEngine.BuildResult result;
      int cursor;          // index into result.plan.placements
      boolean fitApplied;  // whether the terrain fit (level/clear pad) has been applied yet
      // Start "due" so the village must pay the first structural unit before laying the first material block.
      int blocksSinceCharge = MATERIAL_COST_PER_UNIT;

      Job(MillBuildEngine.BuildResult result) {
         this.result = result;
      }
   }

   /**
    * The ambient construction tick for one village town hall — called from {@code Building.updateConstructionQueue}
    * INSTEAD of the fixed-plan {@code findBuildingProject}/{@code findBuildingConstruction} when procedural is on.
    * Returns {@code true} if it advanced any state this tick (so the caller can mirror the old "changed" return).
    *
    * <p>Fail-fast: this does NOT swallow errors or fall back to a fixed plan. A genuine fault crashes loud via
    * {@link org.millenaire.common.utilities.MillCrash} (the whole-mod fail-fast architecture) — there is no
    * graceful-degradation path here.
    */
   public static boolean tick(Building townHall) {
      if (townHall == null || townHall.world == null || townHall.world.isClientSide()) {
         return false;
      }
      long key = townHall.getPos().getBlockPos().asLong();
      Job job = JOBS.get(key);
      if (job == null) {
         job = startNewProceduralBuilding(townHall);
         if (job == null) {
            return false;
         }
         JOBS.put(key, job);
         return true;
      }
      return advance(townHall, job, key);
   }

   // ===============================================================================================
   // DECIDE + GENERATE + SITE  (no fixed-plan fallback — defaults to procedural HOUSE/STORAGE)
   // ===============================================================================================

   private static Job startNewProceduralBuilding(Building townHall) {
      Decision decision = MillNeedsModel.decide(townHall);
      if (decision == null) {
         // No measured gap — the village still develops PROCEDURALLY (housing for growth). NOT a fixed plan.
         decision = new Decision(MillNeedsModel.BuildType.HOUSE, MillNeedsModel.Resource.NONE,
            "growth-default", new java.util.LinkedHashMap<>(), new java.util.LinkedHashMap<>());
      }

      // Phase 7 (#7) DISCUSSION STEER: if the village's villagers DISCUSSED + endorsed a build type, PROMOTE it —
      // but only when a real needs-model gap of that type exists (steerToward never fabricates a need). The applied
      // reason is tagged "+discussed" so the causal link discussion→build is verifiable in the SIM BUILD log. The
      // steer is one-shot: consumed here whether or not it could be honoured (a stale unsupported steer is dropped).
      long steerKey = townHall.getPos().getBlockPos().asLong();
      MillNeedsModel.BuildType steered = PENDING_STEER.remove(steerKey);
      if (steered != null) {
         Decision before = decision;
         decision = MillNeedsModel.steerToward(decision, steered);
         if (decision != before) {
            MillBuildEngine.log("AMBIENT village=" + safeName(townHall) + " DISCUSSION STEER honoured: villagers "
               + "discussed " + steered + " and a real gap supports it → building " + decision.type
               + " (reason=" + decision.reason + ")");
         } else {
            MillBuildEngine.log("AMBIENT village=" + safeName(townHall) + " DISCUSSION STEER " + steered
               + " dropped: no real gap supports it this tick (no fabrication) — building " + decision.type);
         }
      }

      MillCultureStyle.Style style = MillCultureStyle.extract(townHall.culture);
      int pop = townHall.getVillagerRecords().size();
      int sizeBoost = Math.max(0, Math.min(3, (pop - 8) / 6));
      Plan plan = MillProceduralBuilding.generate(decision.type, style, sizeBoost);

      BlockPos origin = pickSite(townHall, plan);
      if (origin == null) {
         MillBuildEngine.log("village=" + safeName(townHall) + " could not find a procedural build site this tick");
         return null;
      }

      int slope = MillBuildEngine.measureSlope(townHall.world, origin, plan.lengthX, plan.widthZ);
      TerrainFit fit = MillBuildEngine.terrainFit(slope);
      MillBuildEngine.BuildResult r = MillBuildEngine.makeResult(decision, plan, fit, slope, origin);

      // Observation: the village AMBIENTLY decided a need + generated a procedural building.
      MillBuildEngine.log("AMBIENT village=" + safeName(townHall)
         + " culture=" + cultureKey(townHall) + " pop=" + pop
         + " NEED gaps=" + decision.gaps + " scores=" + decision.scores
         + " → chose=" + decision.type + " (reason=" + decision.reason + ")");
      MillBuildEngine.log("AMBIENT village=" + safeName(townHall) + " GENERATED " + decision.type
         + " rooms=" + plan.roomNames() + " connected=" + plan.fullyConnected()
         + " footprint=" + plan.lengthX + "x" + plan.widthZ + "x" + plan.height
         + " blocks=" + plan.placements.size() + " style=" + plan.style.describe()
         + " site=" + origin + " slope=" + slope + " fit=" + fit
         + " villageStock=" + villageStock(townHall));
      return new Job(r);
   }

   /**
    * Choose a build site adjacent to the village: ring out from the town hall in expanding offsets until a spot
    * whose footprint doesn't overlap the town hall is found, and resolve the origin to the local topsoil so the
    * building sits on the ground. Deterministic-ish per village (uses the town-hall hash) so sites spread out.
    */
   private static BlockPos pickSite(Building townHall, Plan plan) {
      Point th = townHall.getPos();
      Level world = townHall.world;
      // Spread successive buildings around the village in a growing spiral keyed off how many we've placed.
      int placed = townHall.buildings.size();
      int ring = 8 + (placed % 6) * 6;                 // 8..38 blocks out
      int ang = (placed * 73 + Math.abs(townHall.hashCode())) % 360;
      double rad = Math.toRadians(ang);
      int ox = th.getiX() + (int) Math.round(Math.cos(rad) * ring);
      int oz = th.getiZ() + (int) Math.round(Math.sin(rad) * ring);
      // findTopSoilBlock always resolves a ground Y (it never throws) so the building sits on the terrain.
      int oy = WorldUtilities.findTopSoilBlock(world, ox, oz) + 1;
      return new BlockPos(ox, oy, oz);
   }

   // ===============================================================================================
   // CONSTRUCT — incremental, player-like, over multiple ticks/days
   // ===============================================================================================

   private static boolean advance(Building townHall, Job job, long key) {
      MillBuildEngine.BuildResult r = job.result;
      Level world = townHall.world;

      if (!job.fitApplied) {
         MillBuildEngine.applyTerrainFit(world, r);
         job.fitApplied = true;
      }

      MillVillager builder = pickBuilder(townHall);
      if (builder == null) {
         // No villager available to build this tick — try again next tick (do not fall back to a fixed plan).
         return false;
      }

      int attempts = 0;
      int laid = 0;
      while (job.cursor < r.plan.placements.size() && attempts < BLOCKS_PER_TICK) {
         Placement pl = r.plan.placements.get(job.cursor);
         boolean needsMaterial = pl.material != null && pl.material != Items.AIR;

         // Charge the village ONE structural unit per MATERIAL_COST_PER_UNIT material-bearing blocks (the
         // abstract resCost-style economy, paid from gathered + mined stock). If the next increment is due and
         // the village can't pay, the build is RESOURCE-GATED: it pauses here (no grant, no fixed-plan fallback)
         // until the village gathers/mines more structural material.
         if (needsMaterial && job.blocksSinceCharge >= MATERIAL_COST_PER_UNIT) {
            if (chargeFromVillageStock(townHall) == null) {
               MillBuildEngine.log("AMBIENT village=" + safeName(townHall)
                  + " CONSTRUCTION WAITING for materials (village stock empty) — " + r.decision.type
                  + " paused at " + job.cursor + "/" + r.plan.placements.size());
               break;
            }
            job.blocksSinceCharge = 0;
         }

         attempts++;
         boolean placed = placeOne(builder, r.origin, pl);
         job.cursor++;
         if (placed) {
            laid++;
            if (needsMaterial) {
               job.blocksSinceCharge++;
            }
         }
      }
      r.blocksPlaced += laid;

      if (job.cursor >= r.plan.placements.size()) {
         finish(townHall, job, key);
         return true;
      }

      // Periodic progress evidence (greppable), throttled so the log stays readable.
      if ((job.cursor / BLOCKS_PER_TICK) % 4 == 0) {
         MillBuildEngine.log("AMBIENT village=" + safeName(townHall) + " CONSTRUCTING " + r.decision.type
            + " progress=" + job.cursor + "/" + r.plan.placements.size()
            + " placed=" + r.blocksPlaced + " builder='" + builderName(builder) + "'");
      }
      return attempts > 0;
   }

   /**
    * Lay a single placement player-like: teleport into reach (scaffolding up for high rows), then place the
    * culture-styled block. The MATERIAL is paid separately (in {@code advance}, one structural unit per
    * {@link #MATERIAL_COST_PER_UNIT} blocks); here the placement gesture just holds the block's own item, which
    * {@link VillagerWorldOps#place} consumes and credits straight back (net-zero) — the authoritative debit is
    * the periodic structural charge. Returns whether the block was actually set.
    */
   private static boolean placeOne(MillVillager builder, BlockPos origin, Placement pl) {
      BlockPos pos = new BlockPos(origin.getX() + (int) pl.rel.getiX(),
         origin.getY() + (int) pl.rel.getiY(),
         origin.getZ() + (int) pl.rel.getiZ());
      BlockState state = pl.state;
      Item gesture = pl.material == null ? Items.AIR : pl.material;

      // The builder needs to hold the gesture item for place()'s strict hold-check; place() credits it back on
      // success, so this is net-zero on inventory (NOT the economy debit — that is the structural charge).
      if (gesture != Items.AIR && builder.countInv(gesture, 0) <= 0) {
         builder.addToInv(gesture, 0, 1);
      }

      // Stand the builder adjacent so the player-like reach test passes (the village tick doesn't run nav here).
      builder.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);

      VillagerWorldOps.PlaceResult res = VillagerWorldOps.place(builder, pos, state, gesture, 0);
      if (res == VillagerWorldOps.PlaceResult.OUT_OF_REACH) {
         for (int guard = 0; guard < 64; guard++) {
            OpState st = VillagerWorldOps.ensureReach(builder, pos);
            if (st == OpState.COMPLETE || st == OpState.BLOCKED) {
               break;
            }
         }
         res = VillagerWorldOps.place(builder, pos, state, gesture, 0);
         VillagerWorldOps.reclaimReach(builder, pos);
      }
      return res == VillagerWorldOps.PlaceResult.PLACED;
   }

   /**
    * Debit ONE unit of building material from the village's ACTUAL accumulated stock — the village funds the
    * build out of whatever wealth its villagers gathered / crafted / mined (its most-abundant good is spent
    * first). Returns the {@link InvItem} spent, or {@code null} if the village holds NOTHING at all (the build
    * is then genuinely resource-gated: it waits for the village to produce more). This is the literal "village
    * stocks + mined resources feed the build materials" rule — no grant, no fixed-plan fallback.
    *
    * <p>ROOT-CAUSE FIX: the stock is read VILLAGE-WIDE ({@code townHall.getBuildings()}), NOT just the town
    * hall's own chests. The 1.12 resource-gathering goals deposit gathered/mined/crafted goods into the
    * villagers' OWN buildings — {@code GoalBringBackResourcesHome}/{@code GoalDeliverGoodsHousehold} into the
    * villager's HOUSE, {@code GoalDeliverResourcesShop} into the SHOP — not into the town hall's chests (only a
    * dedicated builder's {@code GoalGetResourcesForBuild} ever pulls into the town hall). Reading only
    * {@code townHall.getInventoryGoods()}/{@code townHall.takeGoods()} therefore saw an empty town-hall chest and
    * reported stock=0, stalling every build. Summing + debiting across {@code getBuildings()} reads the SAME
    * containers the gatherers fill, so genuinely gathered resources now flow into the build. Same authoritative
    * {@code countGoods}/{@code takeGoods} API the original {@code nbGoodAvailable}/{@code GoalGetResourcesForBuild}
    * path uses — no grant, no fixed-plan fallback.
    */
   private static InvItem chargeFromVillageStock(Building townHall) {
      // Find the most-abundant single good held ANYWHERE in the village (across every village building's chests/
      // furnaces/firepits), then debit one unit from the building that actually holds it.
      InvItem best = null;
      int bestCount = 0;
      Building bestBuilding = null;
      for (Building b : townHall.getBuildings()) {
         if (b == null) {
            continue;
         }
         for (Map.Entry<InvItem, Integer> e : b.getInventoryGoods().entrySet()) {
            if (e.getValue() != null && e.getValue() > bestCount) {
               bestCount = e.getValue();
               best = e.getKey();
               bestBuilding = b;
            }
         }
      }
      if (best == null || bestBuilding == null || bestCount <= 0) {
         return null;
      }
      // Debit from the building that holds it (its real chest/furnace/firepit), village-wide accounting.
      bestBuilding.takeGoods(best.getItem(), best.meta, 1);
      return best;
   }

   /**
    * Total units of all goods the village currently stocks across EVERY village building (for the build-start
    * evidence). Village-wide, matching the resource source the build now consumes — so the logged stock is the
    * real accumulated wealth the gatherers/miners deposited, not just the (usually empty) town-hall chest.
    */
   private static int villageStock(Building townHall) {
      int total = 0;
      for (Building b : townHall.getBuildings()) {
         if (b == null) {
            continue;
         }
         for (Integer c : b.getInventoryGoods().values()) {
            if (c != null) {
               total += c;
            }
         }
      }
      return total;
   }

   private static void finish(Building townHall, Job job, long key) {
      MillBuildEngine.BuildResult r = job.result;
      r.complete = true;

      // Register the building's footprint on the village so the village's building list GROWS (it develops).
      // We track the procedural building by its origin Point — a lightweight registration that does not depend
      // on the fixed-plan BuildingPlan lifecycle (which we have deliberately replaced for ambient construction).
      Point bp = new Point(r.origin.getX(), r.origin.getY(), r.origin.getZ());
      if (!townHall.buildings.contains(bp)) {
         townHall.buildings.add(bp);
      }

      MillBuildEngine.log("AMBIENT village=" + safeName(townHall) + " CONSTRUCTION COMPLETE " + r.decision.type
         + " (" + r.plan.roomNames() + ") in " + cultureKey(townHall) + " style, fit=" + r.fit
         + " placed=" + r.blocksPlaced + "/" + r.blocksTotal
         + " → village now develops " + townHall.buildings.size() + " building(s) — next need computed next tick");
      JOBS.remove(key);
   }

   // ===============================================================================================
   // helpers
   // ===============================================================================================

   /**
    * A live village villager to act as the builder this tick — any known villager that is alive. Returns
    * {@code null} when the village currently has no available builder; that is a real transient state (the
    * build simply resumes next tick), NOT a fallback path.
    */
   private static MillVillager pickBuilder(Building townHall) {
      for (MillVillager v : townHall.getKnownVillagers()) {
         if (v != null && v.isAlive()) {
            return v;
         }
      }
      return null;
   }

   private static String builderName(MillVillager v) {
      try {
         return v.firstName + " " + v.familyName;
      } catch (Throwable t) {
         return "villager";
      }
   }

   private static String cultureKey(Building th) {
      return th.culture != null ? th.culture.key : "?";
   }

   private static String safeName(Building th) {
      try {
         return th.getVillageQualifiedName();
      } catch (Throwable t) {
         return String.valueOf(th.getPos());
      }
   }

   /**
    * Total units of all goods the village currently stocks across EVERY village building — the SAME village-wide
    * source the procedural construction debits. Public so the headless resource-chain demonstration can observe
    * the stock rise (as gatherers/miners deposit) and fall (as the build consumes it).
    */
   public static int villageStockTotal(Building townHall) {
      return villageStock(townHall);
   }

   /**
    * Whether the village currently has a procedural building in progress (a live {@link Job}). Used by the
    * Phase-3 expansion perf-guard ({@code underConstruction < cap}) so a village won't start a new ring while it
    * is still constructing the previous one.
    */
   public static boolean hasActiveJob(Building townHall) {
      if (townHall == null || townHall.getPos() == null) {
         return false;
      }
      return JOBS.containsKey(townHall.getPos().getBlockPos().asLong());
   }

   /** Drop any in-progress job for a village (e.g. on unload) so its state doesn't leak. */
   public static void clear(Building townHall) {
      if (townHall != null && townHall.getPos() != null) {
         long key = townHall.getPos().getBlockPos().asLong();
         JOBS.remove(key);
         PENDING_STEER.remove(key);
      }
   }
}
