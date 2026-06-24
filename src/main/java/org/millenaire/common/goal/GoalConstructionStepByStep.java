package org.millenaire.common.goal;

import com.coderyo.jason.ops.VillagerWorldOps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import org.millenaire.common.buildingplan.BuildingBlock;
import org.millenaire.common.buildingplan.BuildingPlan;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.pathing.atomicstryker.AStarConfig;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.ConstructionIP;

@DocumentedElement.Documentation("Build a building")
public class GoalConstructionStepByStep extends Goal {
   public GoalConstructionStepByStep() {
      this.tags.add("tag_construction");
      this.icon = InvItem.createInvItem(Items.IRON_SHOVEL);
   }

   @Override
   public int actionDuration(MillVillager villager) {
      ConstructionIP cip = villager.getCurrentConstruction();
      if (cip == null) {
         return 0;
      } else {
         BuildingBlock bblock = cip.getCurrentBlock();
         if (bblock == null) {
            return 0;
         } else {
            int toolEfficiency = (int)villager.getBestShovel().getDestroySpeed(new ItemStack(villager.getBestShovel(), 1), Blocks.DIRT.defaultBlockState());
            int duration = 14;
            byte var6;
            if (toolEfficiency > 8) {
               var6 = 7;
            } else if (toolEfficiency == 8) {
               var6 = 8;
            } else if (toolEfficiency >= 6) {
               var6 = 10;
            } else if (toolEfficiency >= 4) {
               var6 = 12;
            } else if (toolEfficiency >= 2) {
               var6 = 14;
            } else {
               var6 = 16;
            }

            return bblock.block != Blocks.AIR
                  && bblock.block != Blocks.DIRT
                  && bblock.block != Blocks.GRASS_BLOCK
                  && bblock.block != Blocks.SAND
               ? var6
               : (int)(var6 / 4.0F);
         }
      }
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      ConstructionIP cip = villager.getCurrentConstruction();
      if (cip == null) {
         return null;
      } else {
         BuildingBlock bblock = cip.getCurrentBlock();
         return bblock == null ? null : this.packDest(bblock.p);
      }
   }

   private ConstructionIP getDoableConstructionIP(MillVillager villager) {
      for (ConstructionIP cip : villager.getTownHall().getConstructionsInProgress()) {
         boolean possible = true;
         if (cip.getBuilder() != null && cip.getBuilder() != villager || cip.getBuildingLocation() == null || cip.getBblocks() == null) {
            possible = false;
         }

         if (possible) {
            if (villager.getTownHall().getBuildingPlanForConstruction(cip) == null) {
               return null;
            }

            for (MillVillager v : villager.getTownHall().getKnownVillagers()) {
               if ((Goal.getResourcesForBuild.key.equals(v.goalKey) || Goal.construction.key.equals(v.goalKey)) && v.constructionJobId == cip.getId()) {
                  possible = false;
               }
            }

            for (InvItem key : villager.getTownHall().getBuildingPlanForConstruction(cip).resCost.keySet()) {
               if (villager.countInv(key) < villager.getTownHall().getBuildingPlanForConstruction(cip).resCost.get(key)) {
                  possible = false;
               }
            }
         }

         if (possible) {
            return cip;
         }
      }

      return null;
   }

   @Override
   public ItemStack[] getHeldItemsOffHandTravelling(MillVillager villager) {
      ConstructionIP cip = villager.getCurrentConstruction();
      if (cip == null) {
         return null;
      } else {
         BuildingBlock bblock = cip.getCurrentBlock();
         if (bblock != null && bblock.block != Blocks.AIR && bblock.block.asItem() != Items.AIR) {
            // 26.2: getStateFromMeta/getItemDropped/damageDropped are gone (drops are loot-table driven);
            // the held "carry" item is just the block's own item.
            Item item = bblock.block.asItem();
            return new ItemStack[]{new ItemStack(item, 1)};
         } else {
            return null;
         }
      }
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) {
      return villager.getBestShovelStack();
   }

   @Override
   public AStarConfig getPathingConfig(MillVillager villager) {
      ConstructionIP cip = villager.getCurrentConstruction();
      if (cip != null && cip.getBuildingLocation() != null && cip.getBuildingLocation().containsPlanTag("scaffoldings")) {
         return JPS_CONFIG_BUILDING_SCAFFOLDINGS;
      } else {
         return !villager.canVillagerClearLeaves() ? JPS_CONFIG_BUILDING_NO_LEAVES : JPS_CONFIG_BUILDING;
      }
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) {
      return this.getDoableConstructionIP(villager) != null;
   }

   @Override
   public boolean isStillValidSpecific(MillVillager villager) throws Exception {
      ConstructionIP cip = villager.getCurrentConstruction();
      return cip != null;
   }

   @Override
   public boolean lookAtGoal() {
      return true;
   }

   @Override
   public void onAccept(MillVillager villager) {
      ConstructionIP cip = this.getDoableConstructionIP(villager);
      if (cip != null) {
         cip.setBuilder(villager);
         villager.constructionJobId = cip.getId();
      }
   }

   /**
    * Lay one {@link BuildingBlock}, player-like, returning whether the block was set (same contract as the 1.12
    * {@code bblock.build(...)} it replaces, so the surrounding step/bookkeeping is unchanged).
    *
    * <p>O7 player-like placement: for a simple single-cube wall/floor/roof block ({@link BuildingBlock#isSimpleNormalBlock()})
    * the builder uses the real, reach-gated op — {@link VillagerWorldOps#ensureReach} builds a temporary scaffold
    * column (tracked on, and later reclaimed against, the BUILDING anchor) so high rows come into reach, then
    * {@link VillagerWorldOps#place(MillVillager, BlockPos, BlockState, Item, int)} STRICTLY consumes the matching
    * building material and sets the EXACT rotation-correct {@link BuildingBlock#getPlacementState()} (never dropped to
    * the block's default state) with a real swing + place sound. Every other point (doors, beds, banners, spawners,
    * ground-clear, AIR — anything with multi-block/block-entity/world side effects) still goes through the faithful
    * {@link BuildingBlock#build(Level, Building, boolean, boolean)} dispatch, untouched.
    *
    * <p>The player-like path DEGRADES GRACEFULLY: if the target is momentarily out of reach (the column is still
    * climbing) or no material is in hand THIS tick, it falls back to the guaranteed 1.12 {@code build(...)} so a
    * building can never stall — the physical act becomes player-like without weakening 1.12's "the block always gets
    * laid" contract. STATELESS: all transient column state lives on the {@code TaskPointStore} point, not the goal.
    */
   private boolean placeBuildingBlock(MillVillager villager, ConstructionIP cip, BuildingBlock bblock) {
      if (!bblock.isSimpleNormalBlock()) {
         return bblock.build(villager.level(), villager.getTownHall(), false, false);
      }

      BlockPos target = bblock.p.getBlockPos();
      BlockPos anchor = constructionAnchor(cip, target);

      // Lay the planned block via the AI-invokable place ACTION: it climbs a temporary scaffold column (tracked on the
      // BUILDING anchor so high rows come into reach), STRICTLY consumes the matching building material, and sets the
      // EXACT rotation-correct placement state with a real swing + place sound — the same facade the other player-like
      // ops use, so construction goes through one uniform place seam.
      com.coderyo.jason.ops.OpState result = com.coderyo.jason.ops.VillagerActions.placeBlock(
         villager, target, bblock.getPlacementState(), bblock.getMaterialItem(), bblock.getMeta(), anchor
      );

      if (result == com.coderyo.jason.ops.OpState.COMPLETE) {
         // The reachable block is laid; tear down any temporary climb column so towers never leave scaffold behind.
         VillagerWorldOps.reclaimReach(villager, anchor);
         return true;
      }

      // Out of reach this tick (column still climbing) or no material in hand: fall back to the guaranteed 1.12
      // placement so the building never stalls. Reclaim any partial column first (the fallback teleport-places).
      VillagerWorldOps.reclaimReach(villager, anchor);
      return bblock.build(villager.level(), villager.getTownHall(), false, false);
   }

   /** The stable per-building scaffold anchor: the construction's building-location pos, else the target itself. */
   private static BlockPos constructionAnchor(ConstructionIP cip, BlockPos target) {
      if (cip.getBuildingLocation() != null && cip.getBuildingLocation().pos != null) {
         return cip.getBuildingLocation().pos.getBlockPos();
      }
      return target;
   }

   @Override
   public boolean performAction(MillVillager villager) throws MillLog.MillenaireException {
      ConstructionIP cip = villager.getCurrentConstruction();
      if (cip == null) {
         return true;
      } else {
         BuildingBlock bblock = cip.getCurrentBlock();
         if (bblock == null) {
            return true;
         } else {
            if (MillConfigValues.LogWifeAI >= 2) {
               MillLog.minor(
                  villager,
                  "Setting block at "
                     + bblock.p
                     + " type: "
                     + bblock.block
                     + " replacing: "
                     + villager.getBlock(bblock.p)
                     + " distance: "
                     + bblock.p.distanceTo(villager)
               );
            }

            if (bblock.p.horizontalDistanceTo(villager) < 1.0 && bblock.p.getiY() > villager.getY() && bblock.p.getiY() < villager.getY() + 2.0) {
               boolean jumped = false;
               Level world = villager.level();
               if (!WorldUtilities.isBlockFullCube(world, villager.getPos().getiX() + 1, villager.getPos().getiY() + 1, villager.getPos().getiZ())
                  && !WorldUtilities.isBlockFullCube(world, villager.getPos().getiX() + 1, villager.getPos().getiY() + 2, villager.getPos().getiZ())) {
                  villager.setPos(villager.getPos().getiX() + 1, villager.getPos().getiY() + 1, villager.getPos().getiZ());
                  jumped = true;
               }

               if (!jumped
                  && !WorldUtilities.isBlockFullCube(world, villager.getPos().getiX() - 1, villager.getPos().getiY() + 1, villager.getPos().getiZ())
                  && !WorldUtilities.isBlockFullCube(world, villager.getPos().getiX() - 1, villager.getPos().getiY() + 2, villager.getPos().getiZ())) {
                  villager.setPos(villager.getPos().getiX() - 1, villager.getPos().getiY() + 1, villager.getPos().getiZ());
                  jumped = true;
               }

               if (!jumped
                  && !WorldUtilities.isBlockFullCube(world, villager.getPos().getiX(), villager.getPos().getiY(), villager.getPos().getiZ() + 1)
                  && !WorldUtilities.isBlockFullCube(world, villager.getPos().getiX(), villager.getPos().getiY() + 2, villager.getPos().getiZ() + 1)) {
                  villager.setPos(villager.getPos().getiX(), villager.getPos().getiY() + 1, villager.getPos().getiZ() + 1);
                  jumped = true;
               }

               if (!jumped
                  && !WorldUtilities.isBlockFullCube(world, villager.getPos().getiX(), villager.getPos().getiY() + 1, villager.getPos().getiZ() - 1)
                  && !WorldUtilities.isBlockFullCube(world, villager.getPos().getiX(), villager.getPos().getiY() + 2, villager.getPos().getiZ() - 1)) {
                  villager.setPos(villager.getPos().getiX(), villager.getPos().getiY() + 1, villager.getPos().getiZ() - 1);
                  jumped = true;
               }

               if (!jumped && MillConfigValues.LogWifeAI >= 1) {
                  MillLog.major(villager, "Tried jumping in construction but couldn't");
               }
            }

            boolean blockSet = this.placeBuildingBlock(villager, cip, bblock);

            while (!blockSet && cip.areBlocksLeft()) {
               cip.incrementBblockPos();
               BuildingBlock bb = cip.getCurrentBlock();
               if (bb != null && !bb.alreadyDone(villager.level())) {
                  blockSet = this.placeBuildingBlock(villager, cip, bb);
               }
            }

            villager.swing(InteractionHand.MAIN_HAND);
            villager.actionStart = 0L;
            boolean foundNextBlock = false;

            while (!foundNextBlock && cip.areBlocksLeft()) {
               cip.incrementBblockPos();
               BuildingBlock bb = cip.getCurrentBlock();
               if (bb != null && !bb.alreadyDone(villager.level())) {
                  villager.setGoalDestPoint(bb.p);
                  foundNextBlock = true;
               }
            }

            if (!cip.areBlocksLeft()) {
               if (MillConfigValues.LogBuildingPlan >= 1) {
                  MillLog.major(this, "Villager " + villager + " laid last block in " + cip.getBuildingLocation().planKey + " at " + bblock.p);
               }

               cip.clearBblocks();
               BuildingPlan plan = villager.getTownHall().getBuildingPlanForConstruction(cip);

               for (InvItem key : plan.resCost.keySet()) {
                  villager.takeFromInv(key.getItem(), key.meta, plan.resCost.get(key));
               }

               if (cip.getBuildingLocation() != null) {
                  if (cip.getBuildingLocation().level == 0) {
                     villager.getTownHall().initialiseConstruction(cip, cip.getBuildingLocation().chestPos);
                  } else {
                     Building building = cip.getBuildingLocation().getBuilding(villager.level());
                     if (building != null) {
                        plan.updateBuildingForPlan(building);
                     }
                  }
               }
            }

            if (!foundNextBlock) {
               villager.setGoalDestPoint(null);
            }

            if (MillConfigValues.LogWifeAI >= 2 && villager.extraLog) {
               MillLog.minor(villager, "Reseting actionStart after " + (villager.level().getOverworldClockTime() - villager.actionStart));
            }

            return !cip.areBlocksLeft();
         }
      }
   }

   @Override
   public int priority(MillVillager villager) {
      return 1500;
   }

   @Override
   public int range(MillVillager villager) {
      return 5;
   }

   @Override
   public boolean stopMovingWhileWorking() {
      return false;
   }

   @Override
   public boolean stuckAction(MillVillager villager) throws MillLog.MillenaireException {
      if (villager.getGoalDestPoint().horizontalDistanceTo(villager) < 30.0) {
         if (MillConfigValues.LogWifeAI >= 2) {
            MillLog.major(villager, "Putting block at a distance: " + villager.getGoalDestPoint().distanceTo(villager));
         }

         this.performAction(villager);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public long stuckDelay(MillVillager villager) {
      return 100L;
   }

   @Override
   public boolean unreachableDestination(MillVillager villager) throws Exception {
      this.performAction(villager);
      return true;
   }
}
