package org.millenaire.common.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import org.millenaire.client.network.ClientSender;
import org.millenaire.common.advancements.MillAdvancements;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.TradeGood;
import org.millenaire.common.ui.MillMenus;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.VillageInventory;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.UserProfile;

public class ContainerTrade extends AbstractContainerMenu {
   public static final int DONATION_REP_MULTIPLIER = 4;
   private Building building;
   private MillVillager merchant;
   public int nbRowSelling = 0;
   public int nbRowBuying = 0;
   /** Index of the top visible grid row for the selling/merchant list (2 rows shown at a time). */
   public int sellingTopRow = 0;
   /** Index of the top visible grid row for the buying list (2 rows shown at a time). */
   public int buyingTopRow = 0;

   public ContainerTrade(int containerId, Player player, Building building) {
      super(MillMenus.TRADE, containerId);
      this.building = building;
      Set<TradeGood> sellingGoods = building.getSellingGoods(player);
      int slotnb = 0;
      if (sellingGoods != null) {
         for (TradeGood g : sellingGoods) {
            int slotrow = slotnb / 13;
            ContainerTrade.TradeSlot slot = new ContainerTrade.TradeSlot(building, player, true, g, 8 + 18 * (slotnb - 13 * slotrow), 32 + slotrow * 18);
            slot.gridRow = slotrow;
            slot.bind(this);
            this.addSlot(slot);
            slotnb++;
         }
      }

      this.nbRowSelling = slotnb / 13 + 1;
      Set<TradeGood> buyingGoods = building.getBuyingGoods(player);
      slotnb = 0;
      if (buyingGoods != null) {
         for (TradeGood g : buyingGoods) {
            int slotrow = slotnb / 13;
            ContainerTrade.TradeSlot slot = new ContainerTrade.TradeSlot(building, player, false, g, 8 + 18 * (slotnb - 13 * slotrow), 86 + slotrow * 18);
            slot.gridRow = slotrow;
            slot.bind(this);
            this.addSlot(slot);
            slotnb++;
         }
      }

      this.nbRowBuying = slotnb / 13 + 1;

      for (int l = 0; l < 3; l++) {
         for (int k1 = 0; k1 < 9; k1++) {
            this.addSlot(new Slot(player.getInventory(), k1 + l * 9 + 9, 8 + k1 * 18 + 36, 103 + l * 18 + 37));
         }
      }

      for (int i1 = 0; i1 < 9; i1++) {
         this.addSlot(new Slot(player.getInventory(), i1, 8 + i1 * 18 + 36, 198));
      }

      if (!building.world.isClientSide()) {
         UserProfile profile = building.mw.getProfile(player);
         this.unlockTradableGoods(profile);
      }
   }

   public ContainerTrade(int containerId, Player player, MillVillager merchant) {
      super(MillMenus.TRADE, containerId);
      this.merchant = merchant;
      int slotnb = 0;
      Set<TradeGood> sellingGoods = merchant.merchantSells.keySet();
      if (sellingGoods != null) {
         for (TradeGood g : sellingGoods) {
            int slotrow = slotnb / 13;
            ContainerTrade.MerchantSlot slot = new ContainerTrade.MerchantSlot(merchant, player, g, 8 + 18 * (slotnb - 13 * slotrow), 32 + slotrow * 18);
            slot.gridRow = slotrow;
            slot.bind(this);
            this.addSlot(slot);
            slotnb++;
         }
      }

      this.nbRowSelling = slotnb / 13 + 1;

      for (int l = 0; l < 3; l++) {
         for (int k1 = 0; k1 < 9; k1++) {
            this.addSlot(new Slot(player.getInventory(), k1 + l * 9 + 9, 8 + k1 * 18 + 36, 103 + l * 18 + 37));
         }
      }

      for (int i1 = 0; i1 < 9; i1++) {
         this.addSlot(new Slot(player.getInventory(), i1, 8 + i1 * 18 + 36, 198));
      }

      if (!merchant.level().isClientSide()) {
         UserProfile profile = merchant.mw.getProfile(player);
         this.unlockTradableGoods(profile);
      }
   }

   @Override
   public boolean stillValid(@NonNull Player entityplayer) {
      return true;
   }

   /**
    * Scrolls the selling/buying (or merchant) list to the given top grid row, reproducing the 1.12
    * behaviour where the two visible rows always render at the same on-screen y (32/50 selling,
    * 86/104 buying) while off-window rows are hidden.
    *
    * <p>1.12 mutated the live {@code Slot.yDisplayPosition}/{@code xDisplayPosition} fields. On 26.2
    * {@code Slot.x}/{@code y} are {@code final}, so we rebuild each scrollable slot object in place
    * (keeping its container index) at the window-relative on-screen position; {@link Slot#isActive()}
    * (overridden on the Mill slot classes) hides rows outside the [top, top+1] window from both
    * rendering and hit-testing, so hover, highlight and click all stay aligned with the rendered y.
    */
   public void scrollTo(boolean selling, int topRow) {
      if (selling) {
         this.sellingTopRow = topRow;
      } else {
         this.buyingTopRow = topRow;
      }

      for (int i = 0; i < this.slots.size(); i++) {
         Slot slot = this.slots.get(i);
         if (slot instanceof ContainerTrade.TradeSlot tslot && tslot.sellingSlot == selling) {
            this.slots.set(i, rebindIndex(tslot.relocate(topRow), i));
         } else if (slot instanceof ContainerTrade.MerchantSlot mslot && selling) {
            this.slots.set(i, rebindIndex(mslot.relocate(topRow), i));
         }
      }
   }

   private static Slot rebindIndex(Slot slot, int index) {
      slot.index = index;
      return slot;
   }

   public Building getBuilding() {
      return this.building;
   }

   public MillVillager getMerchant() {
      return this.merchant;
   }

   @Override
   public void clicked(int slotId, int dragType, @NonNull ContainerInput clickTypeIn, @NonNull Player player) {
      // 26.2 PORT NOTE: this container is built CLIENT-ONLY (PACKET_OPENGUI 104 flow) and is NOT the
      // player's authoritative containerMenu, so vanilla's click path never reaches here for trade
      // slots. GuiTrade intercepts trade-slot clicks in the screen and calls
      // {@link #sendTradeRequest} directly (see GuiTrade.mouseClicked). This override is kept only so
      // any stray dispatch on a trade slot is a no-op rather than a vanilla inventory desync.
      if (slotId >= 0 && slotId < this.slots.size()) {
         Slot slot = this.slots.get(slotId);
         if (slot instanceof ContainerTrade.TradeSlot || slot instanceof ContainerTrade.MerchantSlot) {
            return;
         }
      }

      super.clicked(slotId, dragType, clickTypeIn, player);
   }

   /**
    * Translate a screen-side click on a trade slot into a server trade request (packet 200 / sub-id
    * 62), reproducing the 1.12 quantity rules: shift (quick-move) = 64; right-click on a selling/
    * buying shop slot = 8; right-click on a merchant slot = 64; plain left-click = 1. Called from
    * {@link org.millenaire.client.gui.GuiTrade#mouseClicked} for {@link TradeSlot}/{@link MerchantSlot}.
    *
    * @param button 0 = left mouse button, 1 = right mouse button
    * @param shift  whether shift is held (quick-move)
    */
   public void sendTradeRequest(Slot slot, int button, boolean shift, Player player) {
      if (slot instanceof ContainerTrade.TradeSlot tslot) {
         int nbItems = 1;
         if (shift) {
            nbItems = 64;
         } else if (button == 1) {
            nbItems = 8;
         }

         ClientSender.sendTrade(player, this.building, this.merchant, tslot.good, tslot.sellingSlot, false, nbItems);
      } else if (slot instanceof ContainerTrade.MerchantSlot mslot) {
         int nbItems = shift || button == 1 ? 64 : 1;
         ClientSender.sendTrade(player, this.building, this.merchant, mslot.good, true, true, nbItems);
      }
   }

   /**
    * Server-side execution of a trade, refactored out of the 1.12 {@code clicked} body unchanged.
    * {@code nbItems} is the resolved quantity (QUICK_MOVE=64, right-click=8, else 1) computed on the
    * client. {@code serverPlayer} must be the authoritative server player so its inventory auto-syncs.
    */
   public void executeTrade(TradeGood good, boolean sellingSlot, boolean merchantTrade, int nbItems, Player serverPlayer) {
      if (merchantTrade) {
         if (this.merchant == null) {
            return;
         }

         ContainerTrade.MerchantSlot tslotx = this.findMerchantSlot(good);
         if (tslotx != null && tslotx.isProblem() == null) {
            int playerMoney = VillageInventory.countMoney(serverPlayer.getInventory());
            if (playerMoney < good.getCalculatedSellingPrice(this.merchant) * nbItems) {
               nbItems = Mth.floor(playerMoney / good.getCalculatedSellingPrice(this.merchant));
            }

            if (this.merchant.getHouse().countGoods(good.item) < nbItems) {
               nbItems = this.merchant.getHouse().countGoods(good.item);
            }

            nbItems = VillageInventory.putItemsInChest(serverPlayer.getInventory(), good.item.getItem(), good.item.meta, nbItems);
            VillageInventory.changeMoney(serverPlayer.getInventory(), -good.getCalculatedSellingPrice(this.merchant) * nbItems, serverPlayer);
            this.merchant.getHouse().takeGoods(good.item, nbItems);
            Mill.getMillWorld(serverPlayer.level()).getProfile(serverPlayer).adjustLanguage(this.merchant.getCulture().key, nbItems);
         }

         this.broadcastChanges();
         this.merchant.getHouse().invalidateInventoryCache();
         if (!this.merchant.getHouse().world.isClientSide()) {
            this.merchant.getHouse().sendChestPackets(serverPlayer);
         }

         if (!this.merchant.level().isClientSide()) {
            UserProfile profilex = this.merchant.mw.getProfile(serverPlayer);
            this.unlockTradableGoods(profilex);
         }

         return;
      }

      if (this.building == null) {
         return;
      }

      ContainerTrade.TradeSlot tslot = this.findTradeSlot(good, sellingSlot);
      UserProfile profile = this.building.mw.getProfile(serverPlayer);
      if (tslot != null && tslot.isProblem() == null) {
         int playerMoney = VillageInventory.countMoney(serverPlayer.getInventory());
         if (sellingSlot) {
            if (playerMoney < good.getCalculatedSellingPrice(this.building, serverPlayer) * nbItems) {
               nbItems = Mth.floor(playerMoney / good.getCalculatedSellingPrice(this.building, serverPlayer));
            }

            if (!good.autoGenerate && this.building.countGoods(good.item.getItem(), good.item.meta) < nbItems) {
               nbItems = this.building.countGoods(good.item.getItem(), good.item.meta);
            }

            nbItems = VillageInventory.putItemsInChest(serverPlayer.getInventory(), good.item.getItem(), good.item.meta, nbItems);
            VillageInventory.changeMoney(serverPlayer.getInventory(), -good.getCalculatedSellingPrice(this.building, serverPlayer) * nbItems, serverPlayer);
            if (!good.autoGenerate) {
               this.building.takeGoods(good.item.getItem(), good.item.meta, nbItems);
            }

            if (this.building.getTownHall().controlledBy != null && !this.building.getTownHall().controlledBy.equals(serverPlayer.getUUID())) {
               Player owner = this.building.world.getPlayerByUUID(this.building.getTownHall().controlledBy);
               if (owner != null) {
                  MillAdvancements.MP_NEIGHBOURTRADE.grant(owner);
               }
            }

            this.building.adjustReputation(serverPlayer, good.getCalculatedSellingPrice(this.building, serverPlayer) * nbItems);
            this.building.getTownHall().adjustLanguage(serverPlayer, nbItems);
         } else {
            if (VillageInventory.countChestItems(serverPlayer.getInventory(), good.item.getItem(), good.item.meta) < nbItems) {
               nbItems = VillageInventory.countChestItems(serverPlayer.getInventory(), good.item.getItem(), good.item.meta);
            }

            nbItems = this.building.storeGoods(good.item.getItem(), good.item.meta, nbItems);
            VillageInventory.getItemsFromChest(serverPlayer.getInventory(), good.item.getItem(), good.item.meta, nbItems);
            if (!profile.donationActivated) {
               VillageInventory.changeMoney(serverPlayer.getInventory(), good.getCalculatedBuyingPrice(this.building, serverPlayer) * nbItems, serverPlayer);
            }

            if (this.building.getTownHall().controlledBy != null && !this.building.getTownHall().controlledBy.equals(serverPlayer.getUUID())) {
               Player owner = this.building.world.getPlayerByUUID(this.building.getTownHall().controlledBy);
               if (owner != null) {
                  MillAdvancements.MP_NEIGHBOURTRADE.grant(owner);
               }
            }

            int repMultiplier = 1;
            if (profile.donationActivated) {
               repMultiplier = 4;
            }

            this.building.adjustReputation(serverPlayer, good.getCalculatedBuyingPrice(this.building, serverPlayer) * nbItems * repMultiplier);
            this.building.getTownHall().adjustLanguage(serverPlayer, nbItems);
         }
      }

      this.broadcastChanges();
      this.building.invalidateInventoryCache();
      if (!this.building.world.isClientSide()) {
         this.building.sendChestPackets(serverPlayer);
      }

      if (!this.building.world.isClientSide()) {
         this.unlockTradableGoods(profile);
      }
   }

   /** Server-side: resolve the {@link TradeGood} for the given unique key from this container's slots. */
   // Match the clicked good by item+meta, not good.key: the client rebuilds shop goods from the shop
   // packet with the placeholder key "generated" (StreamReadWrite.readNullableGoods), so the key can't
   // be matched against the server's real-keyed goods. item+meta survives the packet and is unique.
   public TradeGood findGoodByItem(net.minecraft.world.item.Item item, int meta, boolean sellingSlot, boolean merchantTrade) {
      for (Slot slot : this.slots) {
         if (merchantTrade) {
            if (slot instanceof ContainerTrade.MerchantSlot mslot
               && mslot.good.item.getItem() == item && mslot.good.item.meta == meta) {
               return mslot.good;
            }
         } else if (slot instanceof ContainerTrade.TradeSlot tslot && tslot.sellingSlot == sellingSlot
            && tslot.good.item.getItem() == item && tslot.good.item.meta == meta) {
            return tslot.good;
         }
      }

      return null;
   }

   private ContainerTrade.TradeSlot findTradeSlot(TradeGood good, boolean sellingSlot) {
      for (Slot slot : this.slots) {
         if (slot instanceof ContainerTrade.TradeSlot tslot && tslot.sellingSlot == sellingSlot && tslot.good.key.equals(good.key)) {
            return tslot;
         }
      }

      return null;
   }

   private ContainerTrade.MerchantSlot findMerchantSlot(TradeGood good) {
      for (Slot slot : this.slots) {
         if (slot instanceof ContainerTrade.MerchantSlot mslot && mslot.good.key.equals(good.key)) {
            return mslot;
         }
      }

      return null;
   }

   @Override
   @NonNull
   public ItemStack quickMoveStack(@NonNull Player player, int index) {
      // Trade slots are virtual (display-only); the actual trade happens in clicked().
      return ItemStack.EMPTY;
   }

   private void unlockTradableGoods(UserProfile profile) {
      List<TradeGood> unlockedGoods = new ArrayList<>();

      for (Slot slot : this.slots) {
         if (slot instanceof ContainerTrade.TradeSlot) {
            ContainerTrade.TradeSlot tradeSlot = (ContainerTrade.TradeSlot)slot;
            if (tradeSlot.isProblem() == null) {
               unlockedGoods.add(tradeSlot.good);
            }
         } else if (slot instanceof ContainerTrade.MerchantSlot) {
            ContainerTrade.MerchantSlot merchantSlot = (ContainerTrade.MerchantSlot)slot;
            if (merchantSlot.isProblem() == null) {
               unlockedGoods.add(merchantSlot.good);
            }
         }
      }

      if (!unlockedGoods.isEmpty()) {
         if (this.building != null) {
            profile.unlockTradeGoods(this.building.culture, unlockedGoods);
         } else if (this.merchant != null) {
            profile.unlockTradeGoods(this.merchant.getCulture(), unlockedGoods);
         }
      }
   }

   public static class MerchantSlot extends Slot {
      public MillVillager merchant;
      public Player player;
      public final TradeGood good;
      /** Logical grid row of this slot in the merchant list; used for window scrolling. */
      public int gridRow = 0;
      /** Owning container, for resolving the current scroll window. */
      private ContainerTrade container;

      public MerchantSlot(MillVillager merchant, Player player, TradeGood good, int xpos, int ypos) {
         super(player.getInventory(), -1, xpos, ypos);
         this.merchant = merchant;
         this.good = good;
         this.player = player;
      }

      void bind(ContainerTrade container) {
         this.container = container;
      }

      /** Rebuilds this slot at the on-screen position for the current scroll window (32 = top row). */
      ContainerTrade.MerchantSlot relocate(int topRow) {
         ContainerTrade.MerchantSlot copy = new ContainerTrade.MerchantSlot(this.merchant, this.player, this.good, this.x, 32 + 18 * (this.gridRow - topRow));
         copy.gridRow = this.gridRow;
         copy.bind(this.container);
         return copy;
      }

      @Override
      public boolean isActive() {
         return this.container == null || this.gridRow >= this.container.sellingTopRow && this.gridRow <= this.container.sellingTopRow + 1;
      }

      @Override
      public ItemStack remove(int i) {
         return ItemStack.EMPTY;
      }

      @Override
      public boolean hasItem() {
         return !this.getItem().isEmpty();
      }

      @Override
      public int getMaxStackSize() {
         return 0;
      }

      @Override
      public ItemStack getItem() {
         return new ItemStack(this.good.item.getItem(), Math.max(Math.min(this.merchant.getHouse().countGoods(this.good.item), 99), 1));
      }

      @Override
      public boolean mayPlace(ItemStack itemstack) {
         return true;
      }

      public String isProblem() {
         if (this.merchant.getHouse().countGoods(this.good.item) < 1) {
            return LanguageUtilities.string("ui.outofstock");
         } else {
            int playerMoney = VillageInventory.countMoney(this.player.getInventory());
            if (this.merchant.getCulture().getTradeGood(this.good.item) != null) {
               if (playerMoney < this.good.getCalculatedSellingPrice(this.merchant)) {
                  return LanguageUtilities.string("ui.missingdeniers").replace("<0>", "" + (this.good.getCalculatedSellingPrice(this.merchant) - playerMoney));
               }
            } else {
               MillLog.error(null, "Unknown trade good: " + this.good);
            }

            return null;
         }
      }

      @Override
      public void setChanged() {
      }

      @Override
      public void set(ItemStack itemstack) {
      }

      @Override
      public String toString() {
         return this.good.getName();
      }
   }

   public static class TradeSlot extends Slot {
      public final Building building;
      public final Player player;
      public final TradeGood good;
      public final boolean sellingSlot;
      /** Logical grid row of this slot in the selling/buying list; used for window scrolling. */
      public int gridRow = 0;
      /** Owning container, for resolving the current scroll window. */
      private ContainerTrade container;

      public TradeSlot(Building building, Player player, boolean sellingSlot, TradeGood good, int xpos, int ypos) {
         super(player.getInventory(), -1, xpos, ypos);
         this.building = building;
         if (good.item.item == Items.AIR) {
            MillLog.error(good, "Trying to add air to the trade UI.");
         }

         this.good = good;
         this.player = player;
         this.sellingSlot = sellingSlot;
      }

      void bind(ContainerTrade container) {
         this.container = container;
      }

      private int topRow() {
         if (this.container == null) {
            return 0;
         }
         return this.sellingSlot ? this.container.sellingTopRow : this.container.buyingTopRow;
      }

      /** Rebuilds this slot at the on-screen position for the current scroll window (32/86 = top row). */
      ContainerTrade.TradeSlot relocate(int topRow) {
         int baseY = this.sellingSlot ? 32 : 86;
         ContainerTrade.TradeSlot copy = new ContainerTrade.TradeSlot(
            this.building, this.player, this.sellingSlot, this.good, this.x, baseY + 18 * (this.gridRow - topRow)
         );
         copy.gridRow = this.gridRow;
         copy.bind(this.container);
         return copy;
      }

      @Override
      public boolean isActive() {
         int top = this.topRow();
         return this.gridRow >= top && this.gridRow <= top + 1;
      }

      @Override
      public ItemStack remove(int i) {
         return ItemStack.EMPTY;
      }

      @Override
      public boolean hasItem() {
         return !this.getItem().isEmpty();
      }

      @Override
      public int getMaxStackSize() {
         return 0;
      }

      @Override
      public ItemStack getItem() {
         return this.sellingSlot
            ? new ItemStack(
               this.good.item.getItem(),
               Math.max(Math.min(this.building.countGoods(this.good.item.getItem(), this.good.item.meta), 99), 1)
            )
            : new ItemStack(
               this.good.item.getItem(),
               Math.max(Math.min(VillageInventory.countChestItems(this.player.getInventory(), this.good.item.getItem(), this.good.item.meta), 99), 1)
            );
      }

      @Override
      public boolean mayPlace(ItemStack itemstack) {
         return true;
      }

      public String isProblem() {
         if (this.sellingSlot) {
            if (this.building.countGoods(this.good.item.getItem(), this.good.item.meta) < 1
               && this.good.requiredTag != null
               && !this.building.containsTags(this.good.requiredTag)) {
               return LanguageUtilities.string("ui.missingequipment") + ": " + this.good.requiredTag;
            }

            if (this.building.countGoods(this.good.item.getItem(), this.good.item.meta) < 1 && !this.good.autoGenerate) {
               return LanguageUtilities.string("ui.outofstock");
            }

            if (this.building.getTownHall().getReputation(this.player) < this.good.minReputation) {
               return LanguageUtilities.string("ui.reputationneeded", this.building.culture.getReputationLevelLabel(this.good.minReputation));
            }

            int playerMoney = VillageInventory.countMoney(this.player.getInventory());
            if (playerMoney < this.good.getCalculatedSellingPrice(this.building, this.player)) {
               return LanguageUtilities.string("ui.missingdeniers")
                  .replace("<0>", "" + (this.good.getCalculatedSellingPrice(this.building, this.player) - playerMoney));
            }
         } else if (VillageInventory.countChestItems(this.player.getInventory(), this.good.item.getItem(), this.good.item.meta) == 0) {
            return LanguageUtilities.string("ui.noneininventory");
         }

         return null;
      }

      @Override
      public void setChanged() {
      }

      @Override
      public void set(ItemStack itemstack) {
      }

      @Override
      public String toString() {
         return this.good.name + (this.sellingSlot ? LanguageUtilities.string("ui.selling") : LanguageUtilities.string("ui.buying"));
      }
   }
}
