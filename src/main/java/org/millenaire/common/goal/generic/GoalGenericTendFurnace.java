package org.millenaire.common.goal.generic;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import org.millenaire.common.annotedparameters.AnnotedParameter;
import org.millenaire.common.annotedparameters.ConfigAnnotations;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.entity.TileEntityFirePit;
import org.millenaire.common.goal.Goal;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.BlockItemUtilities;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;

public class GoalGenericTendFurnace extends GoalGeneric {
   public static final String GOAL_TYPE = "tendfurnace";
   // 26.2: the 1.12 plank "variant" metadata is gone; each wood type is a distinct block, so this uses oak planks (was Blocks.OAK_PLANKS meta 0-5).
   // Lazy: building ItemStacks at class-load (before registries freeze) throws "Components not bound yet".
   private static ItemStack[][] PLANKS;

   private static ItemStack[][] planks() {
      if (PLANKS == null) {
         PLANKS = new ItemStack[][]{
            {new ItemStack(Blocks.OAK_PLANKS, 1)},
            {new ItemStack(Blocks.OAK_PLANKS, 1)},
            {new ItemStack(Blocks.OAK_PLANKS, 1)},
            {new ItemStack(Blocks.OAK_PLANKS, 1)},
            {new ItemStack(Blocks.OAK_PLANKS, 1)},
            {new ItemStack(Blocks.OAK_PLANKS, 1)}
         };
      }
      return PLANKS;
   }
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.INTEGER,
      defaultValue = "4"
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Minimum number of wood to put back in one go."
   )
   public int minimumFuel;

   @Override
   public void applyDefaultSettings() {
      this.lookAtGoal = true;
      this.icon = InvItem.createInvItem(Blocks.FURNACE);
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) throws Exception {
      for (Building dest : this.getBuildings(villager)) {
         if (this.isDestPossible(villager, dest)) {
            int anyWoodAvailable = dest.countGoods(Blocks.OAK_LOG, -1)
               + villager.countInv(Blocks.OAK_LOG, -1)
               + villager.getHouse().countGoods(Blocks.OAK_LOG, -1);

            for (Point p : dest.getResManager().furnaces) {
               FurnaceBlockEntity furnace = p.getFurnace(villager.level());
               if (furnace != null) {
                  if (furnace.getItem(1) == ItemStack.EMPTY && anyWoodAvailable > 4) {
                     return this.packDest(p, dest);
                  }

                  if (furnace.getItem(1).getCount() < 32 && furnace.getItem(1).getItem() == Blocks.OAK_PLANKS.asItem()) {
                     int woodMeta = furnace.getItem(1).getDamageValue();
                     int woodAvailable = this.getWoodCountByMeta(villager, dest, woodMeta);
                     if (woodAvailable >= this.minimumFuel) {
                        return this.packDest(p, dest);
                     }
                  }
               }
            }

            for (Point px : dest.getResManager().firepits) {
               TileEntityFirePit firepit = px.getFirePit(villager.level());
               if (firepit != null) {
                  ItemStack stack = firepit.getItem(3);
                  if (stack.isEmpty() && anyWoodAvailable > 4) {
                     return this.packDest(px, dest);
                  }

                  if (stack.getCount() < 32 && stack.getItem() == Blocks.OAK_PLANKS.asItem()) {
                     int woodMeta = stack.getDamageValue();
                     int woodAvailable = this.getWoodCountByMeta(villager, dest, woodMeta);
                     if (woodAvailable > 4) {
                        return this.packDest(px, dest);
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) {
      Building dest = villager.getGoalBuildingDest();
      BlockEntity tileEntity = villager.level().getBlockEntity(villager.getGoalDestPoint().getBlockPos());
      if (dest != null && tileEntity != null) {
         if (tileEntity instanceof FurnaceBlockEntity) {
            FurnaceBlockEntity furnace = (FurnaceBlockEntity)tileEntity;
            if (furnace.getItem(1) == ItemStack.EMPTY) {
               int mostWoodAvailable = 0;
               int mostWoodAvailableMeta = -1;

               for (int woodMeta = 0; woodMeta < 6; woodMeta++) {
                  int woodAvailable = this.getWoodCountByMeta(villager, dest, woodMeta);
                  if (woodAvailable > mostWoodAvailable) {
                     mostWoodAvailable = woodAvailable;
                     mostWoodAvailableMeta = woodMeta;
                  }
               }

               if (mostWoodAvailableMeta > -1) {
                  return planks()[mostWoodAvailableMeta];
               }
            } else if (furnace.getItem(1).getCount() < 64 && furnace.getItem(1).getItem() == Blocks.OAK_PLANKS.asItem()) {
               int woodMetax = furnace.getItem(1).getDamageValue();
               return planks()[woodMetax];
            }
         } else if (tileEntity instanceof TileEntityFirePit) {
            TileEntityFirePit firepit = (TileEntityFirePit)tileEntity;
            if (firepit.getItem(3) == ItemStack.EMPTY) {
               int mostWoodAvailable = 0;
               int mostWoodAvailableMeta = 0;

               for (int woodMetax = 0; woodMetax < 6; woodMetax++) {
                  int woodAvailable = this.getWoodCountByMeta(villager, dest, woodMetax);
                  if (woodAvailable > mostWoodAvailable) {
                     mostWoodAvailable = woodAvailable;
                     mostWoodAvailableMeta = woodMetax;
                  }
               }

               return planks()[mostWoodAvailableMeta];
            }

            if (firepit.getItem(3).getCount() < 64
               && firepit.getItem(3).getItem() == Blocks.OAK_PLANKS.asItem()) {
               int woodMetaxx = firepit.getItem(3).getDamageValue();
               return planks()[woodMetaxx];
            }
         }
      }

      return null;
   }

   @Override
   public String getTypeLabel() {
      return "tendfurnace";
   }

   private int getWoodCountByMeta(MillVillager villager, Building dest, int woodMeta) {
      BlockState logsToTake = BlockItemUtilities.getLogBlockstateFromPlankMeta(woodMeta);
      return dest.countGoods(logsToTake) + villager.countInv(logsToTake) + villager.getHouse().countGoods(logsToTake);
   }

   @Override
   public boolean isDestPossibleSpecific(MillVillager villager, Building b) {
      return true;
   }

   @Override
   public boolean isPossibleGenericGoal(MillVillager villager) throws Exception {
      return this.getDestination(villager) != null;
   }

   @Override
   public boolean performAction(MillVillager villager) throws Exception {
      Building dest = villager.getGoalBuildingDest();
      BlockEntity tileEntity = villager.level().getBlockEntity(villager.getGoalDestPoint().getBlockPos());
      if (dest != null && tileEntity != null) {
         if (tileEntity instanceof FurnaceBlockEntity) {
            this.performAction_furnace(villager, (FurnaceBlockEntity)tileEntity, dest);
         } else if (tileEntity instanceof TileEntityFirePit) {
            this.performAction_firepit(villager, (TileEntityFirePit)tileEntity, dest);
         }
      }

      return true;
   }

   private void performAction_firepit(MillVillager villager, TileEntityFirePit firepit, Building dest) {
      if (firepit.getItem(3).isEmpty()) {
         int mostWoodAvailable = 0;
         int mostWoodAvailableMeta = -1;

         for (int woodMeta = 0; woodMeta < 6; woodMeta++) {
            int woodAvailable = this.getWoodCountByMeta(villager, dest, woodMeta);
            if (woodAvailable > mostWoodAvailable) {
               mostWoodAvailable = woodAvailable;
               mostWoodAvailableMeta = woodMeta;
            }
         }

         int nbplanks = Math.min(64, mostWoodAvailable * 4);
         firepit.setItem(3, new ItemStack(Blocks.OAK_PLANKS, nbplanks));
         BlockState logsToTake = BlockItemUtilities.getLogBlockstateFromPlankMeta(mostWoodAvailableMeta);
         int nbTaken = dest.takeGoods(logsToTake, nbplanks / 4);
         if (nbTaken < nbplanks / 4) {
            nbTaken += villager.takeFromInv(logsToTake, nbplanks / 4 - nbTaken);
         }

         if (nbTaken < nbplanks / 4) {
            nbTaken += villager.getHouse().takeGoods(logsToTake, nbplanks / 4 - nbTaken);
         }
      } else if (firepit.getItem(3).getCount() < 64
         && firepit.getItem(3).getItem() == Blocks.OAK_PLANKS.asItem()) {
         int woodMetax = firepit.getItem(3).getDamageValue();
         BlockState logsToTakex = BlockItemUtilities.getLogBlockstateFromPlankMeta(woodMetax);
         int woodAvailable = this.getWoodCountByMeta(villager, dest, woodMetax);
         int nbplanksx = Math.min(64 - firepit.getItem(3).getCount(), woodAvailable * 4);
         firepit.setItem(3, new ItemStack(Blocks.OAK_PLANKS, firepit.getItem(3).getCount() + nbplanksx));
         int nbTakenx = dest.takeGoods(logsToTakex, nbplanksx / 4);
         if (nbTakenx < nbplanksx / 4) {
            nbTakenx += villager.takeFromInv(logsToTakex, nbplanksx / 4 - nbTakenx);
         }

         if (nbTakenx < nbplanksx / 4) {
            nbTakenx += villager.getHouse().takeGoods(logsToTakex, nbplanksx / 4 - nbTakenx);
         }
      }
   }

   private void performAction_furnace(MillVillager villager, FurnaceBlockEntity furnace, Building dest) {
      if (furnace.getItem(1) == ItemStack.EMPTY) {
         int mostWoodAvailable = 0;
         int mostWoodAvailableMeta = -1;

         for (int woodMeta = 0; woodMeta < 6; woodMeta++) {
            int woodAvailable = this.getWoodCountByMeta(villager, dest, woodMeta);
            if (woodAvailable > mostWoodAvailable) {
               mostWoodAvailable = woodAvailable;
               mostWoodAvailableMeta = woodMeta;
            }
         }

         int nbplanks = Math.min(64, mostWoodAvailable * 4);
         furnace.setItem(1, new ItemStack(Blocks.OAK_PLANKS, nbplanks));
         BlockState logsToTake = BlockItemUtilities.getLogBlockstateFromPlankMeta(mostWoodAvailableMeta);
         int nbTaken = dest.takeGoods(logsToTake, nbplanks / 4);
         if (nbTaken < nbplanks / 4) {
            nbTaken += villager.takeFromInv(logsToTake, nbplanks / 4 - nbTaken);
         }

         if (nbTaken < nbplanks / 4) {
            nbTaken += villager.getHouse().takeGoods(logsToTake, nbplanks / 4 - nbTaken);
         }
      } else if (furnace.getItem(1).getCount() < 64 && furnace.getItem(1).getItem() == Blocks.OAK_PLANKS.asItem()) {
         int woodMetax = furnace.getItem(1).getDamageValue();
         BlockState logsToTakex = BlockItemUtilities.getLogBlockstateFromPlankMeta(woodMetax);
         int woodAvailable = this.getWoodCountByMeta(villager, dest, woodMetax);
         int nbplanksx = Math.min(64 - furnace.getItem(1).getCount(), woodAvailable * 4);
         furnace.setItem(1, new ItemStack(Blocks.OAK_PLANKS, furnace.getItem(1).getCount() + nbplanksx));
         int nbTakenx = dest.takeGoods(logsToTakex, nbplanksx / 4);
         if (nbTakenx < nbplanksx / 4) {
            nbTakenx += villager.takeFromInv(logsToTakex, nbplanksx / 4 - nbTakenx);
         }

         if (nbTakenx < nbplanksx / 4) {
            nbTakenx += villager.getHouse().takeGoods(logsToTakex, nbplanksx / 4 - nbTakenx);
         }
      }
   }

   @Override
   public boolean validateGoal() {
      return true;
   }
}
