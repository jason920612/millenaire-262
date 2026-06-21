package org.millenaire.common.item;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillLog;

public final class InvItem implements Comparable<InvItem> {
   private static Map<Integer, InvItem> CACHE = new HashMap<>();
   public static final int ANYENCHANTED = 1;
   public static final int ENCHANTEDSWORD = 2;
   public static final List<InvItem> freeGoods = new ArrayList<>();
   public static final HashMap<String, InvItem> INVITEMS_BY_NAME = new HashMap<>();
   public final Item item;
   public final Block block;
   public final ItemStack staticStack;
   public final ItemStack[] staticStackArray;
   public final int meta;
   public final int special;
   private String key = null;

   private static int computeHash(Item item, int meta, int special) {
      return item == null ? (meta << 8) + (special << 12) : item.hashCode() + (meta << 8) + (special << 12);
   }

   public static InvItem createInvItem(Block block) {
      return createInvItem(block, 0);
   }

   public static InvItem createInvItem(Block block, int meta) {
      Item item = block.asItem();
      int hash = computeHash(item, meta, 0);
      if (CACHE.containsKey(hash)) {
         if (CACHE.get(hash).item == item) {
            return CACHE.get(hash);
         }

         MillLog.error(null, "Collision between InvItem hash? " + CACHE.get(hash) + " has same hash as " + item + ":" + meta + ": " + hash);
      }

      InvItem ii = new InvItem(block, meta);
      CACHE.put(hash, ii);
      return ii;
   }

   public static InvItem createInvItem(BlockState bs) {
      return createInvItem(bs.getBlock(), 0);
   }

   public static InvItem createInvItem(int special) {
      int hash = computeHash(null, 0, special);
      if (CACHE.containsKey(hash)) {
         if (CACHE.get(hash).special == special) {
            return CACHE.get(hash);
         }

         MillLog.error(null, "Collision between InvItem hash? " + CACHE.get(hash) + " has same hash as special: " + special + ": " + hash);
      }

      InvItem ii = new InvItem(special);
      CACHE.put(hash, ii);
      return ii;
   }

   public static InvItem createInvItem(Item item) {
      return createInvItem(item, 0);
   }

   public static InvItem createInvItem(Item item, int meta) {
      int hash = computeHash(item, meta, 0);
      if (CACHE.containsKey(hash)) {
         if (CACHE.get(hash).item == item) {
            return CACHE.get(hash);
         }

         MillLog.error(null, "Collision between InvItem hash? " + CACHE.get(hash) + " has same hash as " + item + ":" + meta + ": " + hash);
      }

      InvItem ii = new InvItem(item, meta);
      CACHE.put(hash, ii);
      return ii;
   }

   public static InvItem createInvItem(ItemStack is) {
      return createInvItem(is.getItem(), 0);
   }

   private static void loadInvItemList(File file) {
      try {
         BufferedReader reader = MillCommonUtilities.getReader(file);

         String line;
         while ((line = reader.readLine()) != null) {
            try {
               if (line.trim().length() > 0 && !line.startsWith("//")) {
                  String[] temp = line.trim().split(";");
                  if (temp.length > 2) {
                     // itemlist.txt carries 1.12 camelCase registry ids (e.g. millenaire:normanAxe), but
                     // 26.2 registry ids (and our items) are all lowercase, and Identifier rejects [A-Z].
                     // Lowercase the id so the legacy content resolves to the registered item/block.
                     String registryId = temp[1].toLowerCase(java.util.Locale.ROOT);
                     Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(registryId));
                     if (item != null) {
                        INVITEMS_BY_NAME.put(temp[0], createInvItem(item, Integer.parseInt(temp[2])));
                     } else {
                        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(registryId));
                        if (block == null) {
                           MillLog.error(null, "Could not load good: " + temp[1]);
                        } else if (block.asItem() == null) {
                           MillLog.error(null, "Tried to create good from block with no item: " + line);
                        } else {
                           INVITEMS_BY_NAME.put(temp[0], createInvItem(block, Integer.parseInt(temp[2])));
                        }
                     }
                  }
               }
            } catch (Exception var6) {
               MillLog.printException("Exception while reading line: " + line, var6);
            }
         }
      } catch (IOException var7) {
         MillLog.printException(var7);
      }
   }

   public static void loadItemList() {
      for (File loadDir : Mill.loadingDirs) {
         File mainList = new File(loadDir, "itemlist.txt");
         if (mainList.exists()) {
            loadInvItemList(mainList);
         }
      }

      INVITEMS_BY_NAME.put("anyenchanted", createInvItem(1));
      INVITEMS_BY_NAME.put("enchantedsword", createInvItem(2));

      for (String key : INVITEMS_BY_NAME.keySet()) {
         INVITEMS_BY_NAME.get(key).setKey(key);
      }
   }

   private InvItem(Block block, int meta) {
      this.block = block;
      this.item = block.asItem();
      this.meta = meta;
      this.staticStack = this.item != null ? new ItemStack(this.item, 1) : ItemStack.EMPTY;
      this.staticStackArray = new ItemStack[]{this.staticStack};
      this.special = 0;
      this.checkValidity();
   }

   private InvItem(int special) {
      this.special = special;
      this.staticStack = null;
      this.staticStackArray = new ItemStack[]{this.staticStack};
      this.item = null;
      this.block = null;
      this.meta = 0;
      this.checkValidity();
   }

   private InvItem(Item item, int meta) {
      this.item = item;
      if (Block.byItem(item) != Blocks.AIR) {
         this.block = Block.byItem(item);
      } else {
         this.block = null;
      }

      this.meta = meta;
      this.staticStack = item != null ? new ItemStack(item, 1) : ItemStack.EMPTY;
      this.staticStackArray = new ItemStack[]{this.staticStack};
      this.special = 0;
      this.checkValidity();
   }

   private InvItem(ItemStack is) {
      this.item = is.getItem();
      if (Block.byItem(this.item) != Blocks.AIR) {
         this.block = Block.byItem(this.item);
      } else {
         this.block = null;
      }

      if (0 > 0) {
         this.meta = 0;
      } else {
         this.meta = 0;
      }

      this.staticStack = this.item != null ? new ItemStack(this.item, 1) : ItemStack.EMPTY;
      this.staticStackArray = new ItemStack[]{this.staticStack};
      this.special = 0;
      this.checkValidity();
   }

   private InvItem(String s) {
      this.special = 0;
      if (s.split("/").length > 2) {
         int id = Integer.parseInt(s.split("/")[0]);
         if (BuiltInRegistries.ITEM.byId(id) == null) {
            MillLog.printException("Tried creating InvItem with null id from string: " + s, new Exception());
            this.item = null;
         } else {
            this.item = BuiltInRegistries.ITEM.byId(id);
         }

         if (BuiltInRegistries.BLOCK.byId(id) == null) {
            this.block = null;
         } else {
            this.block = BuiltInRegistries.BLOCK.byId(id);
         }

         this.meta = Integer.parseInt(s.split("/")[1]);
         this.staticStack = this.item != null ? new ItemStack(this.item, 1) : ItemStack.EMPTY;
      } else {
         this.staticStack = null;
         this.item = null;
         this.block = null;
         this.meta = 0;
      }

      this.staticStackArray = new ItemStack[]{this.staticStack};
      this.checkValidity();
   }

   private void checkValidity() {
      if (this.block == Blocks.AIR) {
         MillLog.error(this, "Attempted to create an InvItem for air blocks.");
      }

      if (this.item == null && this.block == null && this.special == 0) {
         MillLog.error(this, "Attempted to create an empty InvItem.");
      }
   }

   public int compareTo(InvItem ii) {
      if (this.special > 0 || ii.special > 0) {
         return this.special - ii.special;
      } else {
         return this.item != null && ii.item != null
            ? this.item.getDescriptionId().compareTo(ii.item.getDescriptionId()) + this.meta - ii.meta
            : this.special - ii.special;
      }
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (!(obj instanceof InvItem)) {
         return false;
      } else {
         InvItem other = (InvItem)obj;
         return other.item == this.item && other.meta == this.meta && other.special == this.special;
      }
   }

   public Block getBlock() {
      return this.block;
   }

   public Item getItem() {
      return this.item;
   }

   public ItemStack getItemStack() {
      return this.staticStack == null ? null : this.staticStack;
   }

   public String getKey() {
      return this.key;
   }

   public String getName() {
      if (this.special == 1) {
         return LanguageUtilities.string("ui.anyenchanted");
      } else if (this.special == 2) {
         return LanguageUtilities.string("ui.enchantedsword");
      } else if (this.meta == -1 && this.block == Blocks.OAK_LOG) {
         return LanguageUtilities.string("ui.woodforplanks");
      } else if (this.meta == 0 && this.block == Blocks.OAK_LOG) {
         return LanguageUtilities.string("ui.woodoak");
      } else if (this.meta == 1 && this.block == Blocks.OAK_LOG) {
         return LanguageUtilities.string("ui.woodpine");
      } else if (this.meta == 2 && this.block == Blocks.OAK_LOG) {
         return LanguageUtilities.string("ui.woodbirch");
      } else if (this.meta == 3 && this.block == Blocks.OAK_LOG) {
         return LanguageUtilities.string("ui.woodjungle");
      } else if (this.meta == 0 && this.block == Blocks.ACACIA_LOG) {
         return LanguageUtilities.string("ui.woodacacia");
      } else if (this.meta == 1 && this.block == Blocks.ACACIA_LOG) {
         return LanguageUtilities.string("ui.wooddarkoak");
      } else if (this.meta == -1) {
         return new ItemStack(this.item).getHoverName().getString();
      } else if (this.item != null) {
         return new ItemStack(this.item).getHoverName().getString();
      } else {
         MillLog.printException(new MillLog.MillenaireException("Trying to get the name of an invalid InvItem."));
         return "id:" + this.item + ";meta:" + this.meta;
      }
   }

   public String getTranslationKey() {
      return "_item:" + BuiltInRegistries.ITEM.getId(this.item) + ":" + this.meta;
   }

   @Override
   public int hashCode() {
      return computeHash(this.item, this.meta, this.special);
   }

   public boolean matches(InvItem ii) {
      return ii.item == this.item && (ii.meta == this.meta || ii.meta == -1 || this.meta == -1);
   }

   public void setKey(String key) {
      this.key = key;
   }

   @Override
   public String toString() {
      return this.getName() + "/" + this.meta;
   }

   static {
      freeGoods.add(createInvItem(Blocks.DIRT, 0));
      freeGoods.add(createInvItem(MillBlocks.EARTH_DECORATION, 0));
      freeGoods.add(createInvItem(Blocks.WATER, 0));
      freeGoods.add(createInvItem(Blocks.OAK_SAPLING, 0));
      freeGoods.add(createInvItem(Blocks.DANDELION, 0));
      freeGoods.add(createInvItem(Blocks.POPPY, 0));
      freeGoods.add(createInvItem(Blocks.SHORT_GRASS, 0));
      freeGoods.add(createInvItem(Blocks.CLAY, 0));
      freeGoods.add(createInvItem(Blocks.BREWING_STAND, 0));
      freeGoods.add(createInvItem(Blocks.OAK_LEAVES, -1));
      freeGoods.add(createInvItem(Blocks.OAK_SAPLING, -1));
      freeGoods.add(createInvItem(Blocks.CAKE, 0));
      freeGoods.add(createInvItem(MillBlocks.PATHDIRT, -1));
      freeGoods.add(createInvItem(MillBlocks.PATHDIRT_SLAB, -1));
      freeGoods.add(createInvItem(MillBlocks.PATHGRAVEL, -1));
      freeGoods.add(createInvItem(MillBlocks.PATHGRAVEL_SLAB, -1));
      freeGoods.add(createInvItem(MillBlocks.PATHSLABS, -1));
      freeGoods.add(createInvItem(MillBlocks.PATHSLABS_SLAB, -1));
      freeGoods.add(createInvItem(MillBlocks.PATHSANDSTONE, -1));
      freeGoods.add(createInvItem(MillBlocks.PATHSANDSTONE_SLAB, -1));
      freeGoods.add(createInvItem(MillBlocks.PATHGRAVELSLABS, -1));
      freeGoods.add(createInvItem(MillBlocks.PATHGRAVELSLABS_SLAB, -1));
      freeGoods.add(createInvItem(MillBlocks.PATHOCHRESLABS, -1));
      freeGoods.add(createInvItem(MillBlocks.PATHOCHRESLABS_SLAB, -1));
   }

   public interface IItemInitialEnchantmens {
      /**
       * Applies the item's intrinsic enchantments to the stack. In 26.2 enchantments are
       * data-component {@code Holder<Enchantment>} resolved from the dynamic (datapack) registry,
       * so a {@link net.minecraft.core.RegistryAccess} must be threaded in to resolve them.
       */
      void applyEnchantments(ItemStack stack, net.minecraft.core.RegistryAccess registryAccess);
   }
}
