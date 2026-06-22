package org.millenaire.common.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.VillageInventory;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.village.Building;

/**
 * Mill prayer/sacrifice shrine, backing inventory for {@link ContainerPuja}.
 *
 * <p>26.2 PORT: the enchantment-application subsystem (cost / apply / level-up) is reimplemented over
 * the data-driven enchantment API. 1.12's object-based {@code Enchantment}/{@code EnchantmentHelper}
 * calls become a {@code Holder<Enchantment>} resolved from the temple level's registry access (see
 * {@link #targetHolder()}) + {@code EnchantmentHelper.getItemEnchantmentLevel} / {@code Enchantment
 * .getMaxLevel} / {@code getMinCost} / {@code canEnchant} / {@code Enchantment.areCompatible} /
 * {@code EnchantmentHelper.updateEnchantments}; {@link PrayerTarget#enchantment} is a
 * {@code ResourceKey<Enchantment>}. Item-stack NBT persistence uses the level's {@code HolderLookup
 * .Provider} via {@code ItemStack.parse}/{@code save} in {@link #readFromNBT}/{@link #writeToNBT}.
 */
public class PujaSacrifice implements Container {
   public static final int TOOL = 1;
   public static final int ARMOUR = 2;
   public static final int HELMET = 3;
   public static final int BOOTS = 4;
   public static final int SWORD_AXE = 5;
   public static final int SWORD = 6;
   public static final int BOW = 7;
   public static final int UNBREAKABLE = 8;
   public static PujaSacrifice.PrayerTarget[] PUJA_TARGETS = new PujaSacrifice.PrayerTarget[]{
      new PujaSacrifice.PrayerTarget(Enchantments.EFFICIENCY, "pujas.god0", 0, 188, 46, 188, 1),
      new PujaSacrifice.PrayerTarget(Enchantments.UNBREAKING, "pujas.god1", 0, 205, 46, 205, 1),
      new PujaSacrifice.PrayerTarget(Enchantments.FORTUNE, "pujas.god2", 0, 222, 46, 222, 1),
      new PujaSacrifice.PrayerTarget(Enchantments.SILK_TOUCH, "pujas.god3", 0, 239, 46, 239, 1)
   };
   public static PujaSacrifice.PrayerTarget[] MAYAN_TARGETS = new PujaSacrifice.PrayerTarget[]{
      new PujaSacrifice.PrayerTarget(Enchantments.PROTECTION, "mayan.god0", 0, 188, 120, 188, 2),
      new PujaSacrifice.PrayerTarget(Enchantments.FIRE_PROTECTION, "mayan.god1", 20, 188, 140, 188, 2),
      new PujaSacrifice.PrayerTarget(Enchantments.BLAST_PROTECTION, "mayan.god2", 40, 188, 160, 188, 2),
      new PujaSacrifice.PrayerTarget(Enchantments.PROJECTILE_PROTECTION, "mayan.god3", 60, 188, 180, 188, 2),
      new PujaSacrifice.PrayerTarget(Enchantments.THORNS, "mayan.god4", 80, 188, 200, 188, 2),
      new PujaSacrifice.PrayerTarget(Enchantments.RESPIRATION, "mayan.god5", 100, 188, 120, 188, 3),
      new PujaSacrifice.PrayerTarget(Enchantments.AQUA_AFFINITY, "mayan.god6", 0, 208, 120, 208, 3),
      new PujaSacrifice.PrayerTarget(Enchantments.FEATHER_FALLING, "mayan.god7", 20, 208, 140, 208, 4),
      new PujaSacrifice.PrayerTarget(Enchantments.SHARPNESS, "mayan.god8", 40, 208, 160, 208, 5),
      new PujaSacrifice.PrayerTarget(Enchantments.SMITE, "mayan.god9", 0, 188, 120, 188, 5),
      new PujaSacrifice.PrayerTarget(Enchantments.BANE_OF_ARTHROPODS, "mayan.god10", 80, 188, 200, 188, 5),
      new PujaSacrifice.PrayerTarget(Enchantments.KNOCKBACK, "mayan.god11", 60, 208, 180, 208, 6),
      new PujaSacrifice.PrayerTarget(Enchantments.FIRE_ASPECT, "mayan.god12", 20, 188, 140, 188, 6),
      new PujaSacrifice.PrayerTarget(Enchantments.LOOTING, "mayan.god13", 80, 208, 200, 208, 6),
      new PujaSacrifice.PrayerTarget(Enchantments.POWER, "mayan.god14", 40, 208, 160, 208, 7),
      new PujaSacrifice.PrayerTarget(Enchantments.PUNCH, "mayan.god15", 60, 208, 180, 208, 7),
      new PujaSacrifice.PrayerTarget(Enchantments.FLAME, "mayan.god16", 20, 188, 140, 188, 7),
      new PujaSacrifice.PrayerTarget(Enchantments.INFINITY, "mayan.god17", 80, 208, 200, 208, 7),
      new PujaSacrifice.PrayerTarget(Enchantments.UNBREAKING, "mayan.god18", 100, 208, 220, 208, 8)
   };
   public static int PUJA_DURATION = 30;
   public static final short PUJA = 0;
   public static final short MAYAN = 1;
   private ItemStack[] items;
   public PujaSacrifice.PrayerTarget currentTarget = null;
   public int offeringProgress = 0;
   public int offeringNeeded = 1;
   public short pujaProgress = 0;
   public Building temple = null;
   public MillVillager priest = null;
   public short type = 0;

   public static boolean validForItem(int type, Item item) {
      // 1.12 keyed on the removed item subclasses ItemPickaxe/ItemSpade/ItemAxe/ItemSword/ItemArmor/
      // ItemBow. On 26.2 tools/armour are plain Items distinguished by data components: a digger's
      // DataComponents.TOOL rules reference a BlockTags.MINEABLE_WITH_* tag (so we can tell pickaxe vs
      // shovel vs axe vs hoe), a sword has DataComponents.WEAPON + a TOOL whose rules are NOT a
      // mineable tag, armour exposes DataComponents.EQUIPPABLE with the EquipmentSlot (HEAD/FEET/…),
      // and bows are still BowItem. This reproduces the original per-type classification exactly.
      ItemStack stack = new ItemStack(item);
      boolean pickaxe = isDigger(stack, net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE);
      boolean shovel = item instanceof ShovelItem || isDigger(stack, net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL);
      boolean axe = item instanceof AxeItem || isDigger(stack, net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE);
      boolean sword = isSword(stack);
      boolean bow = item instanceof BowItem;
      switch (type) {
         case 1:
            return shovel || axe || pickaxe;
         case 2:
            return isArmour(stack);
         case 3:
            return isArmourSlot(stack, net.minecraft.world.entity.EquipmentSlot.HEAD);
         case 4:
            return isArmourSlot(stack, net.minecraft.world.entity.EquipmentSlot.FEET);
         case 5:
            return sword || axe;
         case 6:
            return sword;
         case 7:
            return bow;
         case 8:
            return sword || isArmour(stack) || bow;
         default:
            return false;
      }
   }

   /** True if the item is a digger tool whose TOOL component mines the given vanilla mineable tag. */
   private static boolean isDigger(ItemStack stack, net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> mineable) {
      net.minecraft.world.item.component.Tool tool = stack.get(net.minecraft.core.component.DataComponents.TOOL);
      if (tool == null) {
         return false;
      }
      for (net.minecraft.world.item.component.Tool.Rule rule : tool.rules()) {
         if (rule.speed().isPresent() && rule.blocks().unwrapKey().filter(mineable::equals).isPresent()) {
            return true;
         }
      }
      return false;
   }

   /** True for a melee sword: carries the WEAPON component but is not one of the mineable diggers. */
   private static boolean isSword(ItemStack stack) {
      if (!stack.has(net.minecraft.core.component.DataComponents.WEAPON)) {
         return false;
      }
      return !isDigger(stack, net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
         && !isDigger(stack, net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE)
         && !isDigger(stack, net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)
         && !isDigger(stack, net.minecraft.tags.BlockTags.MINEABLE_WITH_HOE);
   }

   private static boolean isArmour(ItemStack stack) {
      net.minecraft.world.item.equipment.Equippable eq = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
      return eq != null && eq.slot().isArmor();
   }

   private static boolean isArmourSlot(ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot) {
      net.minecraft.world.item.equipment.Equippable eq = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
      return eq != null && eq.slot() == slot;
   }

   public PujaSacrifice(Building temple, CompoundTag tag) {
      this.temple = temple;
      if (temple.containsTags("sacrifices")) {
         this.type = 1;
      }

      this.readFromNBT(tag);
   }

   public PujaSacrifice(Building temple, short type) {
      this.temple = temple;
      this.items = new ItemStack[this.getContainerSize()];

      for (int i = 0; i < this.items.length; i++) {
         this.items[i] = ItemStack.EMPTY;
      }

      this.type = type;
   }

   /** Resolves the target enchantment's {@link Holder} from the temple level's registry access. */
   private net.minecraft.core.Holder<Enchantment> targetHolder() {
      if (this.currentTarget == null || this.temple == null || this.temple.world == null) {
         return null;
      }
      return this.temple.world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(this.currentTarget.enchantment);
   }

   public void calculateOfferingsNeeded() {
      this.offeringNeeded = 0;
      // Faithful 26.2 port of the 1.12 cost formula. 1.12 used object-based Enchantment + EnchantmentHelper;
      // 26.2 uses Holder<Enchantment> resolved from registry access + the DataComponents.ENCHANTMENTS component.
      if (this.items[4] != ItemStack.EMPTY && this.currentTarget != null) {
         ItemStack tool = this.items[4];
         net.minecraft.core.Holder<Enchantment> holder = this.targetHolder();
         if (holder == null) {
            return;
         }
         int currentLevel = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(holder, tool);
         if (currentLevel < holder.value().getMaxLevel() && holder.value().canEnchant(tool)) {
            net.minecraft.world.item.enchantment.ItemEnchantments existing =
               net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(tool);
            int nbother = existing.keySet().size();
            // Abort if any existing enchantment is incompatible with the target (1.12 isCompatibleWith).
            for (net.minecraft.core.Holder<Enchantment> other : existing.keySet()) {
               if (!other.equals(holder) && !net.minecraft.world.item.enchantment.Enchantment.areCompatible(holder, other)) {
                  return;
               }
            }
            // 1.12 excluded the target itself from the "other enchantments" multiplier when already present.
            if (currentLevel > 0) {
               nbother--;
            }
            int cost = 50 + holder.value().getMinCost(currentLevel + 1) * 10;
            cost *= nbother / 2 + 1;
            this.offeringNeeded = cost;
         }
      }
   }

   public boolean canPray() {
      return this.offeringNeeded <= this.offeringProgress ? false : this.items[0] != ItemStack.EMPTY;
   }

   public void changeEnchantment(int i) {
      if (this.currentTarget != this.getTargets().get(i)) {
         this.currentTarget = this.getTargets().get(i);
         this.offeringProgress = 0;
         this.calculateOfferingsNeeded();
      }
   }

   @Override
   public void clearContent() {
      if (this.items != null) {
         for (int i = 0; i < this.items.length; i++) {
            this.items[i] = ItemStack.EMPTY;
         }
      }
   }

   private void completeOffering() {
      // Apply / level-up the target enchantment on the tool. 1.12 used ItemStack.addEnchantment /
      // NBT lvl-mutation; 26.2 mutates the DataComponents.ENCHANTMENTS component via
      // EnchantmentHelper.updateEnchantments + ItemEnchantments.Mutable.set (current level + 1).
      net.minecraft.core.Holder<Enchantment> holder = this.targetHolder();
      if (holder != null && this.items[4] != ItemStack.EMPTY) {
         ItemStack tool = this.items[4];
         int currentLevel = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(holder, tool);
         net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(tool, m -> m.set(holder, currentLevel + 1));
      }

      this.offeringProgress = 0;
      this.calculateOfferingsNeeded();
      this.temple.getTownHall().requestSave("Puja/sacrifice offering complete");
   }

   @Override
   public ItemStack removeItem(int slot, int nb) {
      if (this.items[slot] != ItemStack.EMPTY) {
         if (this.items[slot].getCount() <= nb) {
            ItemStack itemstack = this.items[slot];
            this.items[slot] = ItemStack.EMPTY;
            return itemstack;
         } else {
            ItemStack itemstack1 = this.items[slot].split(nb);
            if (this.items[slot].getCount() == 0) {
               this.items[slot] = ItemStack.EMPTY;
            }

            return itemstack1;
         }
      } else {
         return ItemStack.EMPTY;
      }
   }

   private void endPuja() {
      ItemStack offer = this.items[0];
      if (offer != ItemStack.EMPTY) {
         int offerValue = this.getOfferingValue(offer);
         this.offeringProgress += offerValue;
         offer.setCount(offer.getCount() - 1);
         if (offer.getCount() == 0) {
            this.items[0] = ItemStack.EMPTY;
         }

         if (this.offeringProgress >= this.offeringNeeded) {
            this.completeOffering();
         }
      }
   }

   public int getInventoryStackLimit() {
      return 64;
   }

   public String getName() {
      return LanguageUtilities.string("pujas.invanme");
   }

   public int getOfferingProgressScaled(int scale) {
      return this.offeringNeeded == 0 ? 0 : this.offeringProgress * scale / this.offeringNeeded;
   }

   public int getOfferingValue(ItemStack is) {
      if (this.type == 0) {
         return this.getOfferingValuePuja(is);
      } else {
         return this.type == 1 ? this.getOfferingValueMayan(is) : 0;
      }
   }

   public int getOfferingValueMayan(ItemStack is) {
      if (is.getItem() == Items.SKELETON_SKULL) {
         return 4096;
      } else if (is.getItem() == Items.GHAST_TEAR) {
         return 384;
      } else if (is.getItem() == Items.BLAZE_ROD) {
         return 64;
      } else if (is.getItem() == MillItems.CACAUHAA) {
         return 64;
      } else if (is.getItem() == Items.CHICKEN) {
         return 1;
      } else if (is.getItem() == Items.BEEF) {
         return 1;
      } else if (is.getItem() == Items.PORKCHOP) {
         return 1;
      } else if (is.getItem() == Items.COD) {
         return 1;
      } else if (is.getItem() == Items.LEATHER) {
         return 1;
      } else if (is.getItem() == Items.INK_SAC) {
         return 1;
      } else if (is.getItem() == Items.SLIME_BALL) {
         return 1;
      } else if (is.getItem() == Items.ROTTEN_FLESH) {
         return 2;
      } else if (is.getItem() == Items.BONE) {
         return 2;
      } else if (is.getItem() == Items.MAGMA_CREAM) {
         return 4;
      } else if (is.getItem() == Items.GUNPOWDER) {
         return 4;
      } else if (is.getItem() == Items.SPIDER_EYE) {
         return 4;
      } else {
         return is.getItem() == Items.ENDER_PEARL ? 6 : 0;
      }
   }

   public int getOfferingValuePuja(ItemStack is) {
      if (is.getItem() == Items.DIAMOND) {
         return 384;
      } else if (is.getItem() == Items.MILK_BUCKET) {
         return 128;
      } else if (is.getItem() == Items.GOLDEN_APPLE) {
         return 96;
      } else if (is.getItem() == Items.GOLD_INGOT) {
         return 64;
      } else if (is.getItem() == MillItems.RICE) {
         return 8;
      } else if (is.getItem() == MillItems.RASGULLA) {
         return 64;
      } else if (is.getItem() == Blocks.POPPY.asItem() || is.getItem() == Blocks.DANDELION.asItem()) {
         return 16;
      } else if (is.getItem() == Blocks.SHORT_GRASS.asItem() || is.getItem() == Items.APPLE) {
         return 8;
      } else if (is.getItem() == Items.WOOL.white()) {
         return 8;
      } else {
         return is.getItem() == Items.MELON ? 4 : 0;
      }
   }

   public int getPujaProgressScaled(int scale) {
      return this.pujaProgress * scale / PUJA_DURATION;
   }

   @Override
   public int getContainerSize() {
      return 5;
   }

   @Override
   public ItemStack getItem(int par1) {
      return this.items[par1];
   }

   public List<PujaSacrifice.PrayerTarget> getTargets() {
      if (this.items[4] == ItemStack.EMPTY) {
         return new ArrayList<>();
      } else if (this.type == 0) {
         List<PujaSacrifice.PrayerTarget> targets = new ArrayList<>();

         for (PujaSacrifice.PrayerTarget t : PUJA_TARGETS) {
            if (t.validForItem(this.items[4].getItem())) {
               targets.add(t);
            }
         }

         return targets;
      } else if (this.type == 1) {
         List<PujaSacrifice.PrayerTarget> targets = new ArrayList<>();

         for (PujaSacrifice.PrayerTarget tx : MAYAN_TARGETS) {
            if (tx.validForItem(this.items[4].getItem())) {
               targets.add(tx);
            }
         }

         return targets;
      } else {
         return new ArrayList<>();
      }
   }

   @Override
   public boolean isEmpty() {
      return false;
   }

   @Override
   public boolean canPlaceItem(int i, ItemStack itemstack) {
      return true;
   }

   @Override
   public boolean stillValid(Player player) {
      return false;
   }

   @Override
   public void setChanged() {
   }

   public boolean performPuja(MillVillager priest) {
      this.priest = priest;
      if (this.pujaProgress == 0) {
         boolean success = this.startPuja();
         if (success) {
            this.pujaProgress = 1;
         }

         return success;
      } else if (this.pujaProgress >= PUJA_DURATION) {
         this.endPuja();
         this.pujaProgress = 0;
         return this.canPray();
      } else {
         this.pujaProgress++;
         return this.canPray();
      }
   }

   public void readFromNBT(CompoundTag par1NBTTagCompound) {
      // 1.12 read each slot from an "Items" NBT list (new ItemStack(NBTTagCompound)); 26.2 ItemStacks
      // serialise through ItemStack.CODEC with a RegistryOps backed by the temple level's registry
      // access (HolderLookup.Provider). The enchantment target is restored from its registry Identifier.
      this.items = new ItemStack[this.getContainerSize()];
      for (int i = 0; i < this.items.length; i++) {
         this.items[i] = ItemStack.EMPTY;
      }

      net.minecraft.core.HolderLookup.Provider provider = this.temple != null && this.temple.world != null ? this.temple.world.registryAccess() : null;
      if (provider != null) {
         net.minecraft.nbt.ListTag list = par1NBTTagCompound.getListOrEmpty("Items");
         net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, provider);
         for (int i = 0; i < list.size(); i++) {
            net.minecraft.nbt.CompoundTag slotTag = list.getCompoundOrEmpty(i);
            int slot = slotTag.getByteOr("Slot", (byte)0) & 255;
            if (slot >= 0 && slot < this.items.length) {
               this.items[slot] = ItemStack.CODEC.parse(ops, slotTag.getCompoundOrEmpty("Stack")).result().orElse(ItemStack.EMPTY);
            }
         }
      }

      String target = par1NBTTagCompound.getStringOr("enchantmentTarget", "");
      if (!target.isEmpty()) {
         net.minecraft.resources.Identifier targetId = net.minecraft.resources.Identifier.tryParse(target);
         for (PujaSacrifice.PrayerTarget t : this.getTargets()) {
            if (t.enchantment.identifier().equals(targetId)) {
               this.currentTarget = t;
            }
         }
      }

      this.offeringProgress = par1NBTTagCompound.getShortOr("offeringProgress", (short)0);
      this.pujaProgress = par1NBTTagCompound.getShortOr("pujaProgress", (short)0);
      this.calculateOfferingsNeeded();
   }

   @Override
   public ItemStack removeItemNoUpdate(int par1) {
      if (this.items[par1] != ItemStack.EMPTY) {
         ItemStack itemstack = this.items[par1];
         this.items[par1] = ItemStack.EMPTY;
         this.setChanged();
         return itemstack;
      } else {
         return ItemStack.EMPTY;
      }
   }

   @Override
   public void setItem(int par1, ItemStack par2ItemStack) {
      this.items[par1] = par2ItemStack;
      if (par2ItemStack != ItemStack.EMPTY && par2ItemStack.getCount() > this.getInventoryStackLimit()) {
         par2ItemStack.setCount(this.getInventoryStackLimit());
      }

      this.setChanged();
   }

   private boolean startPuja() {
      int money = VillageInventory.countMoney(this);
      if (money == 0) {
         return false;
      } else if (this.offeringNeeded != 0 && this.offeringProgress < this.offeringNeeded) {
         if (this.items[0] == ItemStack.EMPTY) {
            return false;
         } else {
            money -= 8;
            int denier = money % 64;
            int denier_argent = (money - denier) / 64 % 64;
            int denier_or = (money - denier - denier_argent * 64) / 4096;
            if (denier == 0) {
               this.items[1] = ItemStack.EMPTY;
            } else {
               this.items[1] = new ItemStack(MillItems.DENIER, denier);
            }

            if (denier_argent == 0) {
               this.items[2] = ItemStack.EMPTY;
            } else {
               this.items[2] = new ItemStack(MillItems.DENIER_ARGENT, denier_argent);
            }

            if (denier_or == 0) {
               this.items[3] = ItemStack.EMPTY;
            } else {
               this.items[3] = new ItemStack(MillItems.DENIER_OR, denier_or);
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public void writeToNBT(CompoundTag par1NBTTagCompound) {
      // Mirror of readFromNBT: items saved to an "Items" list via ItemStack.CODEC + a RegistryOps from
      // the temple level's registry access; the enchantment target saved as its registry Identifier.
      net.minecraft.core.HolderLookup.Provider provider = this.temple != null && this.temple.world != null ? this.temple.world.registryAccess() : null;
      if (provider != null && this.items != null) {
         net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, provider);
         net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
         for (int i = 0; i < this.items.length; i++) {
            if (this.items[i] != null && this.items[i] != ItemStack.EMPTY && !this.items[i].isEmpty()) {
               net.minecraft.nbt.CompoundTag slotTag = new net.minecraft.nbt.CompoundTag();
               slotTag.putByte("Slot", (byte)i);
               ItemStack.CODEC.encodeStart(ops, this.items[i]).result().ifPresent(t -> slotTag.put("Stack", t));
               list.add(slotTag);
            }
         }
         par1NBTTagCompound.put("Items", list);
      }

      if (this.currentTarget != null) {
         par1NBTTagCompound.putString("enchantmentTarget", this.currentTarget.enchantment.identifier().toString());
      }

      par1NBTTagCompound.putShort("offeringProgress", (short)this.offeringProgress);
      par1NBTTagCompound.putShort("pujaProgress", this.pujaProgress);
   }

   public static class PrayerTarget {
      public final ResourceKey<Enchantment> enchantment;
      public final String mouseOver;
      public final int startX;
      public final int startY;
      public final int startXact;
      public final int startYact;
      public final int toolType;

      public PrayerTarget(ResourceKey<Enchantment> enchantment, String mouseOver, int startX, int startY, int startXact, int startYact, int toolType) {
         this.enchantment = enchantment;
         this.mouseOver = mouseOver;
         this.startX = startX;
         this.startY = startY;
         this.startXact = startXact;
         this.startYact = startYact;
         this.toolType = toolType;
      }

      public boolean validForItem(Item item) {
         return PujaSacrifice.validForItem(this.toolType, item);
      }
   }
}
