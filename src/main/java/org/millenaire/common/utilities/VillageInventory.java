package org.millenaire.common.utilities;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import org.millenaire.common.advancements.MillAdvancements;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.TileEntityFirePit;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.MillItems;

/**
 * Village-economy inventory cluster: the money (denier/purse) accounting plus the chest/furnace/fire-pit
 * item put/count/take operations and the NBT inventory read/write helpers. Extracted verbatim (Phase A3)
 * out of the {@code MillCommonUtilities} and {@code WorldUtilities} god-objects so the village-economy
 * inventory forms one coherent unit. Pure relocation — behaviour-identical.
 */
public class VillageInventory {
   public static void changeMoney(Container chest, int toChange, Player player) {
      boolean hasPurse = false;

      for (int i = 0; i < chest.getContainerSize() && !hasPurse; i++) {
         ItemStack stack = chest.getItem(i);
         if (stack != null && stack.getItem() == MillItems.PURSE) {
            hasPurse = true;
         }
      }

      if (hasPurse) {
         int current_denier = getItemsFromChest(chest, MillItems.DENIER, 0, Integer.MAX_VALUE);
         int current_DENIER_ARGENT = getItemsFromChest(chest, MillItems.DENIER_ARGENT, 0, Integer.MAX_VALUE);
         int current_DENIER_OR = getItemsFromChest(chest, MillItems.DENIER_OR, 0, Integer.MAX_VALUE);
         int finalChange = current_DENIER_OR * 64 * 64 + current_DENIER_ARGENT * 64 + current_denier + toChange;

         for (int ix = 0; ix < chest.getContainerSize() && finalChange != 0; ix++) {
            ItemStack stack = chest.getItem(ix);
            if (stack != null && stack.getItem() == MillItems.PURSE) {
               int content = MillItems.PURSE.totalDeniers(stack) + finalChange;
               if (content >= 0) {
                  MillItems.PURSE.setDeniers(stack, player, content);
                  finalChange = 0;
               } else {
                  MillItems.PURSE.setDeniers(stack, player, 0);
                  finalChange = content;
               }
            }
         }
      } else {
         int total = toChange + countMoney(chest);
         int denier = total % 64;
         int DENIER_ARGENT = (total - denier) / 64 % 64;
         int DENIER_OR = (total - denier - DENIER_ARGENT * 64) / 4096;
         if (player != null && DENIER_OR > 0) {
            MillAdvancements.CRESUS.grant(player);
         }

         int current_denier = countChestItems(chest, MillItems.DENIER, 0);
         int current_DENIER_ARGENT = countChestItems(chest, MillItems.DENIER_ARGENT, 0);
         int current_DENIER_OR = countChestItems(chest, MillItems.DENIER_OR, 0);
         if (MillConfigValues.LogWifeAI >= 1) {
            MillLog.major(
               null,
               "Putting: "
                  + denier
                  + "/"
                  + DENIER_ARGENT
                  + "/"
                  + DENIER_OR
                  + " replacing "
                  + current_denier
                  + "/"
                  + current_DENIER_ARGENT
                  + "/"
                  + current_DENIER_OR
            );
         }

         if (denier < current_denier) {
            getItemsFromChest(chest, MillItems.DENIER, 0, current_denier - denier);
         } else if (denier > current_denier) {
            putItemsInChest(chest, MillItems.DENIER, 0, denier - current_denier);
         }

         if (DENIER_ARGENT < current_DENIER_ARGENT) {
            getItemsFromChest(chest, MillItems.DENIER_ARGENT, 0, current_DENIER_ARGENT - DENIER_ARGENT);
         } else if (DENIER_ARGENT > current_DENIER_ARGENT) {
            putItemsInChest(chest, MillItems.DENIER_ARGENT, 0, DENIER_ARGENT - current_DENIER_ARGENT);
         }

         if (DENIER_OR < current_DENIER_OR) {
            getItemsFromChest(chest, MillItems.DENIER_OR, 0, current_DENIER_OR - DENIER_OR);
         } else if (DENIER_OR > current_DENIER_OR) {
            putItemsInChest(chest, MillItems.DENIER_OR, 0, DENIER_OR - current_DENIER_OR);
         }
      }
   }

   public static int countChestItems(Container chest, Block block, int meta) {
      return countChestItems(chest, block.asItem(), meta);
   }

   public static int countChestItems(Container chest, BlockState blockState) {
      return countChestItems(chest, blockState.getBlock(), 0);
   }

   public static int countChestItems(Container chest, Item item, int meta) {
      if (chest == null) {
         return 0;
      } else {
         int maxSlot = chest.getContainerSize();
         if (chest instanceof Inventory) {
            maxSlot -= 5;
         }

         int nb = 0;

         for (int i = 0; i < maxSlot; i++) {
            ItemStack stack = chest.getItem(i);
            if (stack != null && stack.getItem() == item) {
               nb += stack.getCount();
            }

            if (item == Blocks.OAK_LOG.asItem()
               && meta == -1
               && stack != null
               && stack.getItem() == Blocks.ACACIA_LOG.asItem()) {
               nb += stack.getCount();
            }
         }

         return nb;
      }
   }

   public static int countFurnaceItems(Container furnace, Item item, int meta) {
      if (furnace == null) {
         return 0;
      } else {
         int nb = 0;
         ItemStack stack = furnace.getItem(2);
         if (stack != null && stack.getItem() == item) {
            nb += stack.getCount();
         }

         if (item == Blocks.OAK_LOG.asItem()
            && meta == -1
            && stack != null
            && stack.getItem() == Blocks.ACACIA_LOG.asItem()) {
            nb += stack.getCount();
         }

         return nb;
      }
   }

   public static int countMoney(Container chest) {
      int deniers = 0;

      for (int i = 0; i < chest.getContainerSize(); i++) {
         ItemStack stack = chest.getItem(i);
         if (stack != null) {
            if (stack.getItem() == MillItems.PURSE) {
               deniers += MillItems.PURSE.totalDeniers(stack);
            } else if (stack.getItem() == MillItems.DENIER) {
               deniers += stack.getCount();
            } else if (stack.getItem() == MillItems.DENIER_ARGENT) {
               deniers += stack.getCount() * 64;
            } else if (stack.getItem() == MillItems.DENIER_OR) {
               deniers += stack.getCount() * 64 * 64;
            }
         }
      }

      return deniers;
   }

   public static int getItemsFromChest(Container chest, Block block, int meta, int toTake) {
      return getItemsFromChest(chest, block.asItem(), meta, toTake);
   }

   public static int getItemsFromChest(Container chest, BlockState blockState, int toTake) {
      return getItemsFromChest(chest, blockState.getBlock(), 0, toTake);
   }

   public static int getItemsFromChest(Container chest, Item item, int meta, int toTake) {
      if (chest == null) {
         return 0;
      } else {
         int nb = 0;
         int maxSlot = chest.getContainerSize() - 1;
         if (chest instanceof Inventory) {
            maxSlot -= 4;
         }

         for (int i = maxSlot; i >= 0 && nb < toTake; i--) {
            ItemStack stack = chest.getItem(i);
            if (stack != null && stack.getItem() == item) {
               if (stack.getCount() <= toTake - nb) {
                  nb += stack.getCount();
                  chest.setItem(i, ItemStack.EMPTY);
               } else {
                  chest.removeItem(i, toTake - nb);
                  nb = toTake;
               }
            }

            if (item == Blocks.OAK_LOG.asItem()
               && meta == -1
               && stack != null
               && stack.getItem() == Blocks.ACACIA_LOG.asItem()) {
               if (stack.getCount() <= toTake - nb) {
                  nb += stack.getCount();
                  chest.setItem(i, ItemStack.EMPTY);
               } else {
                  chest.removeItem(i, toTake - nb);
                  nb = toTake;
               }
            }
         }

         return nb;
      }
   }

   public static int getItemsFromFirePit(TileEntityFirePit firepit, Item item, int toTake) {
      if (firepit == null) {
         return 0;
      } else {
         int taken = 0;

         for (int stackNb = 0; stackNb < 3; stackNb++) {
            ItemStack stack = firepit.getItem(4 + stackNb);
            if (taken < toTake && stack != null && stack.getItem() == item) {
               taken += firepit.removeItem(4 + stackNb, toTake).getCount();
            }
         }

         return taken;
      }
   }

   public static int getItemsFromFurnace(FurnaceBlockEntity furnace, Item item, int toTake) {
      if (furnace == null) {
         return 0;
      } else {
         int nb = 0;
         ItemStack stack = furnace.getItem(2);
         if (stack != null && stack.getItem() == item) {
            if (stack.getCount() <= toTake - nb) {
               nb += stack.getCount();
               furnace.setItem(2, ItemStack.EMPTY);
            } else {
               furnace.removeItem(2, toTake - nb);
               nb = toTake;
            }
         }

         return nb;
      }
   }

   public static int putItemsInChest(Container chest, Block block, int toPut) {
      return putItemsInChest(chest, block.asItem(), 0, toPut);
   }

   public static int putItemsInChest(Container chest, Block block, int meta, int toPut) {
      return putItemsInChest(chest, block.asItem(), meta, toPut);
   }

   public static int putItemsInChest(Container chest, Item item, int toPut) {
      return putItemsInChest(chest, item, 0, toPut);
   }

   public static int putItemsInChest(Container chest, Item item, int meta, int toPut) {
      if (chest == null) {
         return 0;
      } else {
         int nb = 0;
         int maxSlot = chest.getContainerSize();
         if (chest instanceof Inventory) {
            maxSlot -= 5;
         }

         for (int i = 0; i < maxSlot && nb < toPut; i++) {
            ItemStack stack = chest.getItem(i);
            if (stack != ItemStack.EMPTY && stack.getItem() == item) {
               if (stack.getMaxStackSize() - stack.getCount() >= toPut - nb) {
                  stack.setCount(stack.getCount() + toPut - nb);
                  nb = toPut;
               } else {
                  nb += stack.getMaxStackSize() - stack.getCount();
                  stack.setCount(stack.getMaxStackSize());
               }

               chest.setItem(i, stack);
            }
         }

         for (int ix = 0; ix < maxSlot && nb < toPut; ix++) {
            ItemStack stack = chest.getItem(ix);
            if (stack == ItemStack.EMPTY) {
               stack = new ItemStack(item, 1);
               if (stack.getItem() instanceof InvItem.IItemInitialEnchantmens && !Mill.serverWorlds.isEmpty()) {
                  // Enchantments are dynamic-registry Holders now; resolve via the server world's RegistryAccess.
                  ((InvItem.IItemInitialEnchantmens)stack.getItem())
                     .applyEnchantments(stack, Mill.serverWorlds.get(0).world.registryAccess());
               }

               if (toPut - nb <= stack.getMaxStackSize()) {
                  stack.setCount(toPut - nb);
                  nb = toPut;
               } else {
                  stack.setCount(stack.getMaxStackSize());
                  nb += stack.getCount();
               }

               chest.setItem(ix, stack);
            }
         }

         return nb;
      }
   }

   public static void readInventory(ListTag nbttaglist, Map<InvItem, Integer> inventory) {
      for (int i = 0; i < nbttaglist.size(); i++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(i);
         String itemName = nbttagcompound1.getStringOr("item", "");
         String itemMod = nbttagcompound1.getStringOr("itemmod", "");
         int itemMeta = nbttagcompound1.getIntOr("meta", 0);
         Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemMod + ":" + itemName));
         inventory.put(InvItem.createInvItem(item, itemMeta), nbttagcompound1.getIntOr("amount", 0));
      }
   }

   public static ListTag writeInventory(Map<InvItem, Integer> inventory) {
      ListTag nbttaglist = new ListTag();

      for (InvItem key : inventory.keySet()) {
         if (key.getItem() != null) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(key.getItem());
            CompoundTag nbttagcompound1 = new CompoundTag();
            nbttagcompound1.putString("item", itemId.getPath());
            nbttagcompound1.putString("itemmod", itemId.getNamespace());
            nbttagcompound1.putInt("meta", key.meta);
            nbttagcompound1.putInt("amount", inventory.get(key));
            nbttaglist.add(nbttagcompound1);
         } else {
            MillLog.error(null, "Key with null item when saving inventory: " + key);
         }
      }

      return nbttaglist;
   }
}
