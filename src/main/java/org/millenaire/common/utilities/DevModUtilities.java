package org.millenaire.common.utilities;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.pathing.atomicstryker.AStarConfig;
import org.millenaire.common.pathing.atomicstryker.AStarNode;
import org.millenaire.common.pathing.atomicstryker.AStarPathPlannerJPS;
import org.millenaire.common.pathing.atomicstryker.IAStarPathedEntity;

public class DevModUtilities {
   private static HashMap<Player, Integer> autoMoveDirection = new HashMap<>();
   private static HashMap<Player, Integer> autoMoveTarget = new HashMap<>();

   public static void fillInFreeGoods(Player player) {
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.JAPANESE_BLUE_LEGGINGS, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.JAPANESE_BLUE_BOOTS, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.JAPANESE_BLUE_HELMET, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.JAPANESE_BLUE_CHESTPLATE, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.JAPANESE_RED_LEGGINGS, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.JAPANESE_RED_BOOTS, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.JAPANESE_RED_HELMET, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.JAPANESE_RED_CHESTPLATE, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.JAPANESE_GUARD_LEGGINGS, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.JAPANESE_GUARD_BOOTS, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.JAPANESE_GUARD_HELMET, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.JAPANESE_GUARD_CHESTPLATE, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.SUMMONING_WAND, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.AMULET_SKOLL_HATI, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), Items.CLOCK, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.NORMAN_AXE, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.NORMAN_PICKAXE, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.NORMAN_SHOVEL, 1);
      MillCommonUtilities.putItemsInChest(player.getInventory(), Blocks.GOLD_BLOCK, 0, 64);
      MillCommonUtilities.putItemsInChest(player.getInventory(), Blocks.OAK_LOG, 64);
      MillCommonUtilities.putItemsInChest(player.getInventory(), Items.COAL, 64);
      MillCommonUtilities.putItemsInChest(player.getInventory(), Blocks.COBBLESTONE, 128);
      MillCommonUtilities.putItemsInChest(player.getInventory(), Blocks.STONE, 512);
      MillCommonUtilities.putItemsInChest(player.getInventory(), Blocks.SAND, 128);
      MillCommonUtilities.putItemsInChest(player.getInventory(), Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), 64);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.CALVA, 0, 2);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.CHICKEN_CURRY, 2);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.RICE, 0, 64);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.MAIZE, 0, 64);
      MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.TURMERIC, 0, 64);
   }

   public static void runAutoMove(Level world) {
      for (Object o : world.players()) {
         if (o instanceof Player) {
            Player p = (Player)o;
            if (autoMoveDirection.containsKey(p)) {
               if (autoMoveDirection.get(p) == 1) {
                  if (autoMoveTarget.get(p).intValue() < p.getX()) {
                     autoMoveDirection.put(p, -1);
                     autoMoveTarget.put(p, (int)(p.getX() - 100000.0));
                     ServerSender.sendChat(p, ChatFormatting.GREEN, "Auto-move: turning back.");
                  }
               } else if (autoMoveDirection.get(p) == -1 && autoMoveTarget.get(p).intValue() > p.getX()) {
                  autoMoveDirection.put(p, 1);
                  autoMoveTarget.put(p, (int)(p.getX() + 100000.0));
                  ServerSender.sendChat(p, ChatFormatting.GREEN, "Auto-move: turning back again.");
               }

               p.teleportTo(p.getX() + autoMoveDirection.get(p).intValue() * 0.5, p.getY(), p.getZ());
               p.snapTo(p.getX() + autoMoveDirection.get(p).intValue() * 0.5, p.getY(), p.getZ(), p.getYRot(), p.getXRot());
            }
         }
      }
   }

   public static void testGetItemFromBlock() {
      long starttime = System.nanoTime();
      Iterator<Block> iterator = BuiltInRegistries.BLOCK.iterator();

      int count;
      for (count = 0; iterator.hasNext(); count++) {
         Block block = iterator.next();
         block.asItem();
      }

      MillLog.temp(null, "Took " + 1.0 * (System.nanoTime() - starttime) / 1000000.0 + " ms to load " + count + " items from blocks.");
   }

   public static void testPaths(Player player) {
      Point centre = new Point(player);
      MillLog.temp(null, "Attempting test path around: " + player);
      Point start = null;
      Point end = null;
      int toleranceMode = 0;

      for (int i = 0; i < 100 && (start == null || end == null); i++) {
         for (int j = 0; j < 100 && (start == null || end == null); j++) {
            for (int k = 0; k < 100 && (start == null || end == null); k++) {
               for (int l = 0; l < 8 && (start == null || end == null); l++) {
                  Point p = centre.getRelative(i * (1 - (l & 1) * 2), j * (1 - (l & 2)), k * (1 - (l & 4) / 2));
                  Block block = WorldUtilities.getBlock(player.level(), p);
                  if (start == null && block == Blocks.GOLD_BLOCK) {
                     start = p;
                  }

                  if (end == null && block == Blocks.IRON_BLOCK) {
                     end = p.getAbove();
                     toleranceMode = 0;
                  } else if (end == null && block == Blocks.DIAMOND_BLOCK) {
                     end = p.getAbove();
                     toleranceMode = 1;
                  } else if (end == null && block == Blocks.LAPIS_BLOCK) {
                     end = p.getAbove();
                     toleranceMode = 2;
                  }
               }
            }
         }
      }

      if (start != null && end != null) {
         DevModUtilities.DevPathedEntity pathedEntity = new DevModUtilities.DevPathedEntity(player.level(), player);
         AStarConfig jpsConfig;
         if (toleranceMode == 1) {
            jpsConfig = new AStarConfig(true, false, false, true, true, 2, 2);
         } else if (toleranceMode == 2) {
            jpsConfig = new AStarConfig(true, false, false, true, true, 2, 20);
         } else {
            jpsConfig = new AStarConfig(true, false, false, true, true);
         }

         ServerSender.sendChat(player, ChatFormatting.DARK_GREEN, "Calculating path. Tolerance H: " + jpsConfig.toleranceHorizontal);
         AStarPathPlannerJPS jpsPathPlanner = new AStarPathPlannerJPS(player.level(), pathedEntity, true);

         try {
            jpsPathPlanner.getPath(start.getiX(), start.getiY(), start.getiZ(), end.getiX(), end.getiY(), end.getiZ(), jpsConfig);
         } catch (ThreadSafeUtilities.ChunkAccessException var11) {
            MillLog.printException(var11);
         }
      } else {
         ServerSender.sendChat(player, ChatFormatting.DARK_RED, "Could not find start or end: " + start + " - " + end);
      }
   }

   public static void toggleAutoMove(Player player) {
      if (autoMoveDirection.containsKey(player)) {
         autoMoveDirection.remove(player);
         autoMoveTarget.remove(player);
         ServerSender.sendChat(player, ChatFormatting.GREEN, "Auto-move disabled");
      } else {
         autoMoveDirection.put(player, 1);
         autoMoveTarget.put(player, (int)(player.getX() + 100000.0));
         ServerSender.sendChat(player, ChatFormatting.GREEN, "Auto-move enabled");
      }
   }

   public static void validateResourceMap(Map<InvItem, Integer> map) {
      int errors = 0;

      for (InvItem item : map.keySet()) {
         if (item == null) {
            MillLog.printException(new MillLog.MillenaireException("Found a null InvItem in map!"));
            errors++;
         } else if (!map.containsKey(item)) {
            MillLog.printException(new MillLog.MillenaireException("Key: " + item + " not present in map???"));
            errors++;
         } else if (map.get(item) == null) {
            MillLog.printException(new MillLog.MillenaireException("Key: " + item + " has null value in map."));
            errors++;
         }
      }

      if (map.size() > 0) {
         MillLog.error(null, "Validated map. Found " + errors + " amoung " + map.size() + " keys.");
      }
   }

   public static void villagerInteractDev(Player entityplayer, MillVillager villager) {
      if (villager.isChild()) {
         villager.growSize();
         ServerSender.sendChat(entityplayer, ChatFormatting.GREEN, villager.getName() + ": Size: " + villager.getSize() + " gender: " + villager.gender);
         if (entityplayer.getInventory().getSelectedItem() != null && entityplayer.getInventory().getSelectedItem().getItem() == MillItems.SUMMONING_WAND) {
            villager.getRecord().size = 20;
            villager.growSize();
         }
      }

      if (entityplayer.getInventory().getSelectedItem() == ItemStack.EMPTY
         || entityplayer.getInventory().getSelectedItem().getItem() == Items.AIR) {
         ServerSender.sendChat(
            entityplayer,
            ChatFormatting.GREEN,
            villager.getName() + ": Current goal: " + villager.getGoalLabel(villager.goalKey) + " Current pos: " + villager.getPos()
         );
         ServerSender.sendChat(
            entityplayer, ChatFormatting.GREEN, villager.getName() + ": House: " + villager.housePoint + " Town Hall: " + villager.townHallPoint
         );
         ServerSender.sendChat(entityplayer, ChatFormatting.GREEN, villager.getName() + ": ID: " + villager.getVillagerId());
         if (villager.getRecord() != null) {
            ServerSender.sendChat(entityplayer, ChatFormatting.GREEN, villager.getName() + ": Spouse: " + villager.getRecord().spousesName);
         }

         if (villager.getPathDestPoint() != null && villager.pathEntity != null && villager.pathEntity.getNodeCount() > 1) {
            ServerSender.sendChat(
               entityplayer,
               ChatFormatting.GREEN,
               villager.getName()
                  + ": Dest: "
                  + villager.getPathDestPoint()
                  + " distance: "
                  + villager.getPathDestPoint().distanceTo(villager)
                  + " stuck: "
                  + villager.longDistanceStuck
                  + " jump:"
                  + villager.pathEntity.getNextTargetPathPoint()
            );
         } else {
            ServerSender.sendChat(entityplayer, ChatFormatting.GREEN, villager.getName() + ": No dest point.");
         }

         String s = "";
         if (villager.getRecord() != null) {
            for (String tag : villager.getRecord().questTags) {
               s = s + tag + " ";
            }
         }

         if (villager.mw.getProfile(entityplayer).villagersInQuests.containsKey(villager.getVillagerId())) {
            s = s
               + " quest: "
               + villager.mw.getProfile(entityplayer).villagersInQuests.get(villager.getVillagerId()).quest.key
               + "/"
               + villager.mw.getProfile(entityplayer).villagersInQuests.get(villager.getVillagerId()).getCurrentVillager().id;
         }

         if (s != null && s.length() > 0) {
            ServerSender.sendChat(entityplayer, ChatFormatting.GREEN, "Tags: " + s);
         }

         s = "";

         for (InvItem key : villager.inventory.keySet()) {
            if (villager.inventory.get(key) > 0) {
               s = s + key + ":" + villager.inventory.get(key) + " ";
            }
         }

         if (villager.getTarget() != null) {
            s = s + "attacking: " + villager.getTarget() + " ";
         }

         if (s != null && s.length() > 0) {
            ServerSender.sendChat(entityplayer, ChatFormatting.GREEN, "Inv: " + s);
         }
      } else if (entityplayer.getInventory().getSelectedItem() != ItemStack.EMPTY
         && entityplayer.getInventory().getSelectedItem().getItem() == Blocks.SAND.asItem()) {
         if (villager.hiredBy == null) {
            villager.hiredBy = entityplayer.getName().getString();
            ServerSender.sendChat(entityplayer, ChatFormatting.GREEN, "Hired: " + entityplayer.getName().getString());
         } else {
            villager.hiredBy = null;
            ServerSender.sendChat(entityplayer, ChatFormatting.GREEN, "No longer hired");
         }
      } else if (entityplayer.getInventory().getSelectedItem() != ItemStack.EMPTY
         && entityplayer.getInventory().getSelectedItem().getItem() == Blocks.DIRT.asItem()
         && villager.pathEntity != null) {
         int meta = MillCommonUtilities.randomInt(16);

         for (Node p : villager.pathEntity.pointsCopy) {
            if (WorldUtilities.getBlock(villager.level(), p.x, p.y - 1, p.z) != MillBlocks.LOCKED_CHEST) {
               WorldUtilities.setBlockAndMetadata(villager.level(), new Point(p).getBelow(), Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), meta);
            }
         }

         Node px = villager.pathEntity.getCurrentTargetPathPoint();
         if (px != null && WorldUtilities.getBlock(villager.level(), px.x, px.y - 1, px.z) != MillBlocks.LOCKED_CHEST
            )
          {
            WorldUtilities.setBlockAndMetadata(villager.level(), new Point(px).getBelow(), Blocks.GOLD_BLOCK, 0);
         }

         px = villager.pathEntity.getNextTargetPathPoint();
         if (px != null && WorldUtilities.getBlock(villager.level(), px.x, px.y - 1, px.z) != MillBlocks.LOCKED_CHEST
            )
          {
            WorldUtilities.setBlockAndMetadata(villager.level(), new Point(px).getBelow(), Blocks.DIAMOND_BLOCK, 0);
         }

         px = villager.pathEntity.getPreviousTargetPathPoint();
         if (px != null && WorldUtilities.getBlock(villager.level(), px.x, px.y - 1, px.z) != MillBlocks.LOCKED_CHEST
            )
          {
            WorldUtilities.setBlockAndMetadata(villager.level(), new Point(px).getBelow(), Blocks.IRON_BLOCK, 0);
         }
      }

      if (villager.hasChildren()
         && entityplayer.getInventory().getSelectedItem() != ItemStack.EMPTY
         && entityplayer.getInventory().getSelectedItem().getItem() == MillItems.SUMMONING_WAND) {
         MillVillager child = villager.getHouse().createChild(villager, villager.getTownHall(), villager.getRecord().spousesName);
         if (child != null) {
            child.getRecord().size = 20;
            child.growSize();
         }
      }
   }

   private static class DevPathedEntity implements IAStarPathedEntity {
      Level world;
      Player caller;

      DevPathedEntity(Level w, Player p) {
         this.world = w;
         this.caller = p;
      }

      @Override
      public void onFoundPath(List<AStarNode> result) {
         int meta = MillCommonUtilities.randomInt(16);

         for (AStarNode node : result) {
            if (node != result.get(0) && node != result.get(result.size() - 1)) {
               WorldUtilities.setBlockAndMetadata(this.world, new Point(node).getBelow(), Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), meta);
            }
         }
      }

      @Override
      public void onNoPathAvailable() {
         ServerSender.sendChat(this.caller, ChatFormatting.DARK_RED, "No path available.");
      }
   }
}
