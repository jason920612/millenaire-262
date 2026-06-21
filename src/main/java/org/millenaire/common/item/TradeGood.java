package org.millenaire.common.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.village.Building;

public class TradeGood {
   public static final String HIDDEN = "hidden";
   public static final String FOREIGNTRADE = "foreigntrade";
   public Culture culture;
   public InvItem item;
   public String name;
   private final int sellingPrice;
   private final int buyingPrice;
   public int reservedQuantity;
   public int targetQuantity;
   public int foreignMerchantPrice;
   public String requiredTag;
   public boolean autoGenerate = false;
   public int minReputation;
   public String travelBookCategory = null;
   public final String key;
   public boolean travelBookDisplay = true;

   public TradeGood(String key, Culture culture, InvItem iv) {
      this.item = iv;
      this.name = this.item.getName();
      this.sellingPrice = 0;
      this.buyingPrice = 1;
      this.requiredTag = null;
      this.culture = culture;
      this.key = key;
   }

   public TradeGood(
      String key,
      Culture culture,
      String name,
      InvItem item,
      int sellingPrice,
      int buyingPrice,
      int reservedQuantity,
      int targetQuantity,
      int foreignMerchantPrice,
      boolean autoGenerate,
      String tag,
      int minReputation,
      String desc
   ) {
      this.culture = culture;
      this.key = key;
      this.name = name;
      this.item = item;
      this.sellingPrice = sellingPrice;
      this.buyingPrice = buyingPrice;
      this.requiredTag = tag;
      this.autoGenerate = autoGenerate;
      this.reservedQuantity = reservedQuantity;
      this.targetQuantity = targetQuantity;
      this.foreignMerchantPrice = foreignMerchantPrice;
      this.minReputation = minReputation;
      this.travelBookCategory = desc;
      this.travelBookDisplay = this.travelBookCategory != null && !this.travelBookCategory.equals("hidden");
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (!(obj instanceof TradeGood)) {
         return false;
      } else {
         TradeGood g = (TradeGood)obj;
         // Compare the goods' items (consistent with hashCode = item.hashCode()). The original
         // "g.item.equals(obj)" compared an InvItem to a TradeGood — always false — so equality
         // degenerated to identity. 1.12 got away with it (same instance everywhere); on 26.2 the client
         // rebuilds goods from the shop packet, so the price map's good and the hovered good are different
         // instances -> shopBuys.get(name).get(good) returned null -> getBuyingPrice unboxed null ->
         // trade-GUI tooltip render crash (NPE).
         return this.item.equals(g.item);
      }
   }

   public int getBasicBuyingPrice(Building shop) {
      if (shop == null) {
         return this.buyingPrice;
      } else {
         return shop.getTownHall().villageType.buyingPrices.containsKey(this.item)
            ? shop.getTownHall().villageType.buyingPrices.get(this.item)
            : this.buyingPrice;
      }
   }

   public int getBasicSellingPrice(Building shop) {
      if (shop == null) {
         return this.sellingPrice;
      } else {
         return shop.getTownHall().villageType.sellingPrices.containsKey(this.item)
            ? shop.getTownHall().villageType.sellingPrices.get(this.item)
            : this.sellingPrice;
      }
   }

   public int getCalculatedBuyingPrice(Building shop, Player player) {
      return shop == null ? this.buyingPrice : shop.getBuyingPrice(this, player);
   }

   public int getCalculatedSellingPrice(Building shop, Player player) {
      return shop == null ? this.sellingPrice : shop.getSellingPrice(this, player);
   }

   public int getCalculatedSellingPrice(MillVillager merchant) {
      if (merchant == null) {
         return this.foreignMerchantPrice;
      } else {
         return merchant.merchantSells.containsKey(this) ? merchant.merchantSells.get(this) : this.foreignMerchantPrice;
      }
   }

   public ItemStack getIcon() {
      return this.item.getItemStack();
   }

   public String getName() {
      return this.item != null && this.item.block == Blocks.OAK_LOG && this.item.meta == -1
         ? LanguageUtilities.string("travelbook.anywood")
         : new ItemStack(this.item.getItem(), 1).getDisplayName().getString();
   }

   @Override
   public int hashCode() {
      return this.item.hashCode();
   }

   @Override
   public String toString() {
      return "Goods@" + this.culture.key + ":" + this.key + "/" + this.item.getItemStack().getItem().getDescriptionId();
   }

   public void validateGood() {
      if (this.buyingPrice != 0 && this.sellingPrice != 0 && this.sellingPrice <= this.buyingPrice) {
         MillLog.minor(this, "Selling price of " + this.sellingPrice + " should be superior to buying price (" + this.buyingPrice + ").");
      }
   }
}
