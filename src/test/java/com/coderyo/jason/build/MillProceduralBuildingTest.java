package com.coderyo.jason.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.coderyo.jason.build.MillNeedsModel.BuildType;
import com.coderyo.jason.build.MillNeedsModel.Decision;
import com.coderyo.jason.build.MillNeedsModel.Resource;
import com.coderyo.jason.build.MillNeedsModel.VillageState;
import com.coderyo.jason.build.MillProceduralBuilding.Plan;
import com.coderyo.jason.build.MillBuildEngine.TerrainFit;

/**
 * Java twin of the sim-validated {@code task-ops-sim/buildsim.py}: the WEIGHTED gap-priority needs model + the
 * room-composition + terrain-fit logic are run on the SAME three scenarios with the SAME assertions — an
 * overcrowded village builds a HOUSE, a threatened one a TOWER, a food-short one a WORKSHOP tuned to food; rooms
 * compose into a connected footprint (doors == rooms-1); the culture style is applied; the hybrid terrain fit is
 * chosen. All pure — no Minecraft {@code Level} needed (the needs model + generator are world-free).
 */
class MillProceduralBuildingTest extends org.millenaire.MillHeadlessTest {

   // 1) overcrowded byzantine village on a gentle slope → HOUSE
   @Test
   void overcrowdedVillageBuildsHouse() {
      VillageState v = new VillageState(18, 12, 30, 30, 60, 0, 0, true);
      Decision d = MillNeedsModel.decide(v);
      assertNotNull(d, "should have a gap");
      assertEquals(BuildType.HOUSE, d.type, "overcrowded village should build a house");
      assertEquals("housing", d.reason);

      Plan p = MillProceduralBuilding.generate(d.type, MillCultureStyle.forKey("byzantines"), 0);
      assertTrue(p.fullyConnected(), "house rooms must be fully connected (doors == rooms-1)");
      assertEquals("byzantines", p.style.culture);
      assertEquals("stone_bricks", blockPath(p.style.wall), "byzantine wall style not applied");

      assertEquals(TerrainFit.ADAPT, MillBuildEngine.terrainFit(1), "gentle slope should adapt");
   }

   // 2) japanese village, housed, under threat → defense gap dominates → TOWER, steep slope → LEVEL_PAD
   @Test
   void threatenedVillageBuildsTower() {
      VillageState v = new VillageState(10, 14, 18, 25, 50, 9, 1, true);
      Decision d = MillNeedsModel.decide(v);
      assertNotNull(d);
      assertEquals(BuildType.TOWER, d.type, "threatened village should build a tower");
      assertEquals("defense", d.reason);

      Plan p = MillProceduralBuilding.generate(d.type, MillCultureStyle.forKey("japanese"), 0);
      assertTrue(p.fullyConnected());
      assertEquals(MillCultureStyle.RoofShape.HIP, p.style.roofShape, "japanese roof shape not applied");

      assertEquals(TerrainFit.LEVEL_PAD, MillBuildEngine.terrainFit(7), "steep slope should level a pad");
   }

   // 3) mayan village short on food → workshop tuned to food → WORKSHOP, mid slope → PARTIAL_LEVEL
   @Test
   void foodShortVillageBuildsFoodWorkshop() {
      VillageState v = new VillageState(10, 14, 30, 30, 5, 0, 5, true);
      Decision d = MillNeedsModel.decide(v);
      assertNotNull(d);
      assertEquals(BuildType.WORKSHOP, d.type, "food-short village should build a workshop");
      assertEquals(Resource.FOOD, d.resource, "workshop must be tuned to the missing resource (food)");
      assertEquals("workshop:food", d.reason);

      Plan p = MillProceduralBuilding.generate(d.type, MillCultureStyle.forKey("mayan"), 0);
      assertTrue(p.fullyConnected());

      assertEquals(TerrainFit.PARTIAL_LEVEL, MillBuildEngine.terrainFit(4), "mid slope should be hybrid partial-level");
   }

   @Test
   void noGapsBuildsNothing() {
      // well-housed, well-stocked, safe, has a market → no gaps.
      VillageState v = new VillageState(8, 20, 50, 50, 80, 0, 10, true);
      assertEquals(null, MillNeedsModel.decide(v), "a satisfied village builds nothing");
   }

   @Test
   void biggerVillagesBuildMoreRooms() {
      // sizeBoost extends the footprint with extra rooms.
      Plan small = MillProceduralBuilding.generate(BuildType.HOUSE, MillCultureStyle.forKey("norman"), 0);
      Plan big = MillProceduralBuilding.generate(BuildType.HOUSE, MillCultureStyle.forKey("norman"), 2);
      assertTrue(big.rooms.size() > small.rooms.size(), "size boost should add rooms");
      assertTrue(big.fullyConnected(), "bigger building still fully connected");
      assertTrue(big.placements.size() > small.placements.size(), "bigger building has more blocks");
   }

   @Test
   void everyBuildingTypeComposesConnectedRooms() {
      for (BuildType t : BuildType.values()) {
         Plan p = MillProceduralBuilding.generate(t, MillCultureStyle.forKey("norman"), 0);
         assertTrue(p.fullyConnected(), t + " rooms must be connected");
         assertTrue(p.placements.size() > 0, t + " must produce placements");
      }
   }

   private static String blockPath(net.minecraft.world.level.block.state.BlockState s) {
      return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
   }
}
