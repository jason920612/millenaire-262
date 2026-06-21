package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import org.millenaire.client.gui.text.GuiText;
import org.millenaire.client.gui.text.GuiTravelBook;
import org.millenaire.client.network.ClientSender;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.ui.ContainerTrade;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.UserProfile;

/**
 * Mill trade screen.
 *
 * <p>26.2 PORT NOTE: 1.12 extended {@code GuiContainer}; on 26.2 we extend
 * {@link AbstractContainerScreen} of the Mill {@link ContainerTrade} menu and draw via
 * {@link GuiGraphicsExtractor} ({@code extractBackground}/{@code extractLabels}/{@code extractTooltip}).
 * The base class now renders the slots, carried item and standard tooltips, so the old reflection
 * helpers ({@code drawSlotInventory}/{@code drawItemStackInventory}) and the manual GL slot loop are
 * gone. Mill's selling/buying-row scrolling, the donation toggle, the problem overlays and the
 * price tooltip are preserved. The per-slot "problem" darken overlay and the help "?" button are
 * reimplemented on 26.2 ({@link #extractSlot} and a vanilla {@link Button} added in {@link #init}).
 */
@Environment(EnvType.CLIENT)
public class GuiTrade extends AbstractContainerScreen<ContainerTrade> {
   private Building building;
   private MillVillager merchant;
   private final Player player;
   private final UserProfile profile;
   private int sellingRow = 0;
   private int buyingRow = 0;
   private final ContainerTrade container;
   private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("millenaire", "textures/gui/trade.png");

   public GuiTrade(ContainerTrade menu, Inventory inventory, Component title, Player player, Building building) {
      super(menu, inventory, title, 248, 222);
      this.container = menu;
      this.building = building;
      this.player = player;
      this.profile = building.mw.getProfile(player);
      this.updateRows(false, 0, 0);
      this.updateRows(true, 0, 0);
   }

   public GuiTrade(ContainerTrade menu, Inventory inventory, Component title, Player player, MillVillager merchant) {
      super(menu, inventory, title, 248, 222);
      this.container = menu;
      this.merchant = merchant;
      this.player = player;
      this.profile = merchant.mw.getProfile(player);
      this.updateRows(false, 0, 0);
      this.updateRows(true, 0, 0);
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
      super.extractBackground(graphics, mouseX, mouseY, partialTicks);
      int x = (this.width - this.imageWidth) / 2;
      int y = (this.height - this.imageHeight) / 2;
      blit(graphics, x, y, 0, 0, this.imageWidth, this.imageHeight);
      if (this.sellingRow == 0) {
         blit(graphics, x + 216, y + 68, 5, 5, 11, 7);
      }

      if (this.buyingRow == 0) {
         blit(graphics, x + 216, y + 122, 5, 5, 11, 7);
      }

      if (this.sellingRow >= this.container.nbRowSelling - 2) {
         blit(graphics, x + 230, y + 68, 5, 5, 11, 7);
      }

      if (this.buyingRow >= this.container.nbRowBuying - 2) {
         blit(graphics, x + 230, y + 122, 5, 5, 11, 7);
      }

      if (!this.profile.donationActivated) {
         blit(graphics, x + 8, y + 122, 0, 238, 16, 16);
         blit(graphics, x + 8 + 16, y + 122, 16, 222, 16, 16);
      } else {
         blit(graphics, x + 8, y + 122, 0, 222, 16, 16);
         blit(graphics, x + 8 + 16, y + 122, 16, 238, 16, 16);
      }
   }

   private static void blit(GuiGraphicsExtractor graphics, int x, int y, int u, int v, int w, int h) {
      graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, (float)u, (float)v, w, h, 256, 256);
   }

   @Override
   protected void init() {
      super.init();
      // 1.12 added the "?" help button (MillGuiButton) at the top-right when trading with a building.
      if (this.building != null) {
         this.addRenderableWidget(
            Button.builder(Component.literal("?"), b -> this.openHelp()).bounds(this.leftPos + this.imageWidth - 21, this.topPos + 5, 15, 20).build()
         );
      }
   }

   @Override
   protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
      super.extractSlot(graphics, slot, mouseX, mouseY);
      // 1.12 darkened any trade slot whose good has a "problem" (50% black overlay; slot coords are
      // local to the leftPos/topPos-translated pose, matching extractSlot's coordinate space).
      String problem = null;
      if (slot instanceof ContainerTrade.TradeSlot) {
         problem = ((ContainerTrade.TradeSlot)slot).isProblem();
      } else if (slot instanceof ContainerTrade.MerchantSlot) {
         problem = ((ContainerTrade.MerchantSlot)slot).isProblem();
      }

      if (problem != null) {
         graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, Integer.MIN_VALUE);
      }
   }

   @Override
   protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      if (this.building != null) {
         graphics.text(this.font, this.building.getNativeBuildingName(), 8, 6, 0xFF404040, false);
         graphics.text(this.font, LanguageUtilities.string("ui.wesell") + ":", 8, 22, 0xFF404040, false);
         graphics.text(this.font, LanguageUtilities.string("ui.webuy") + ":", 8, 76, 0xFF404040, false);
      } else {
         graphics.text(this.font, this.merchant.getVillagerName() + ": " + this.merchant.getNativeOccupationName(), 8, 6, 0xFF404040, false);
         graphics.text(this.font, LanguageUtilities.string("ui.isell") + ":", 8, 22, 0xFF404040, false);
      }

      graphics.text(this.font, LanguageUtilities.string("ui.inventory"), 44, this.imageHeight - 96 + 2, 0xFF404040, false);
   }

   @Override
   protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      // Custom price/problem tooltips for trade slots.
      if (this.hoveredSlot != null && this.menu.getCarried().isEmpty() && this.hoveredSlot.hasItem()) {
         ItemStack itemstack = this.hoveredSlot.getItem();
         if (this.hoveredSlot instanceof ContainerTrade.TradeSlot) {
            ContainerTrade.TradeSlot tslot = (ContainerTrade.TradeSlot)this.hoveredSlot;
            int price = tslot.sellingSlot
               ? tslot.good.getCalculatedSellingPrice(this.building, this.player)
               : tslot.good.getCalculatedBuyingPrice(this.building, this.player);
            int priceColour = MillCommonUtilities.getPriceColourMC(price);
            String priceText = MillCommonUtilities.getShortPrice(price);
            List<Component> list = baseTooltip(itemstack);
            if (!tslot.sellingSlot && this.profile.donationActivated) {
               list.add(Component.literal("§6" + LanguageUtilities.string("ui.donatinggoods")));
               list.add(Component.literal(LanguageUtilities.string("ui.repgain", "" + price * 4)));
            } else {
               list.add(Component.literal("§" + Integer.toHexString(priceColour) + priceText));
               list.add(Component.literal(LanguageUtilities.string("ui.repgain", "" + price)));
            }

            String problem = tslot.isProblem();
            if (problem != null) {
               list.add(Component.literal("§4" + problem));
            }

            graphics.setComponentTooltipForNextFrame(this.font, list, mouseX, mouseY);
            return;
         } else if (this.hoveredSlot instanceof ContainerTrade.MerchantSlot) {
            ContainerTrade.MerchantSlot tslot = (ContainerTrade.MerchantSlot)this.hoveredSlot;
            int price = tslot.good.getCalculatedSellingPrice(this.merchant);
            int priceColour = MillCommonUtilities.getPriceColourMC(price);
            String priceText = MillCommonUtilities.getShortPrice(price);
            List<Component> list = baseTooltip(itemstack);
            list.add(Component.literal("§" + Integer.toHexString(priceColour) + priceText));
            String problem = tslot.isProblem();
            if (problem != null) {
               list.add(Component.literal("§4" + problem));
            }

            graphics.setComponentTooltipForNextFrame(this.font, list, mouseX, mouseY);
            return;
         }
      }

      super.extractTooltip(graphics, mouseX, mouseY);

      // Donation-toggle hover labels.
      int startx = (this.width - this.imageWidth) / 2;
      int starty = (this.height - this.imageHeight) / 2;
      int dx = mouseX - startx;
      int dy = mouseY - starty;
      if (dy >= 122 && dy <= 138) {
         if (dx >= 8 && dx <= 24) {
            graphics.setComponentTooltipForNextFrame(this.font, List.of(Component.literal(LanguageUtilities.string("ui.trade_buying"))), mouseX, mouseY);
         } else if (dx >= 24 && dx <= 40) {
            graphics.setComponentTooltipForNextFrame(this.font, List.of(Component.literal(LanguageUtilities.string("ui.trade_donation"))), mouseX, mouseY);
         }
      }
   }

   private List<Component> baseTooltip(ItemStack stack) {
      List<Component> list = new ArrayList<>(this.getTooltipFromContainerItem(stack));
      return list;
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      int x = (int)event.x();
      int y = (int)event.y();
      if (event.button() == 0) {
         int startx = (this.width - this.imageWidth) / 2;
         int starty = (this.height - this.imageHeight) / 2;
         int dx = x - startx;
         int dy = y - starty;
         if (dy >= 68 && dy <= 74) {
            if (dx >= 216 && dx <= 226) {
               if (this.sellingRow > 0) {
                  this.sellingRow--;
                  this.updateRows(true, 1, this.sellingRow);
               }
            } else if (dx >= 230 && dx <= 240 && this.sellingRow < this.container.nbRowSelling - 2) {
               this.sellingRow++;
               this.updateRows(true, -1, this.sellingRow);
            }
         } else if (dy >= 122 && dy <= 127) {
            if (dx >= 216 && dx <= 226) {
               if (this.buyingRow > 0) {
                  this.buyingRow--;
                  this.updateRows(false, 1, this.buyingRow);
               }
            } else if (dx >= 230 && dx <= 240 && this.buyingRow < this.container.nbRowBuying - 2) {
               this.buyingRow++;
               this.updateRows(false, -1, this.buyingRow);
            }
         }

         if (dy >= 122 && dy <= 138) {
            if (dx >= 8 && dx <= 24) {
               if (this.profile.donationActivated) {
                  this.profile.donationActivated = false;
                  ClientSender.playerToggleDonation(this.player, this.profile.donationActivated);
               }
            } else if (dx >= 24 && dx <= 40 && !this.profile.donationActivated) {
               this.profile.donationActivated = true;
               ClientSender.playerToggleDonation(this.player, this.profile.donationActivated);
            }
         }
      }

      // 26.2 PORT NOTE: ContainerTrade is built CLIENT-ONLY and is NOT the player's authoritative
      // containerMenu, so vanilla's slotClicked path operates on the real inventory menu and never on
      // ContainerTrade -- a trade-slot click would hit the inventory menu with an out-of-range slot id
      // and desync. We intercept trade-slot clicks here and send the Mill trade packet directly,
      // returning WITHOUT calling super so no vanilla inventory click is generated. Player inventory
      // slots still fall through to super and work normally.
      if (this.hoveredSlot instanceof ContainerTrade.TradeSlot || this.hoveredSlot instanceof ContainerTrade.MerchantSlot) {
         if (event.button() == 0 || event.button() == 1) {
            this.container.sendTradeRequest(this.hoveredSlot, event.button(), Minecraft.getInstance().hasShiftDown(), this.player);
            return true;
         }
      }

      return super.mouseClicked(event, doubleClick);
   }

   private void updateRows(boolean selling, int change, int row) {
      // 1.12 scrolled by mutating each Slot's display x/y; on 26.2 Slot.x/y are final, so the menu
      // rebuilds the scrollable slots in place at the window-relative on-screen position and hides
      // off-window rows via Slot.isActive() (see ContainerTrade.scrollTo). The "change" delta is no
      // longer needed because positions are recomputed absolutely from the top row.
      this.container.scrollTo(selling, row);
   }

   /** Opens the culture travel-book (the old "?" help button action). */
   public void openHelp() {
      Culture culture = this.building != null ? this.building.culture : this.merchant.getCulture();
      GuiTravelBook guiTravelBook = new GuiTravelBook(Minecraft.getInstance().player);
      guiTravelBook.setCallingScreen(this);
      guiTravelBook.jumpToDetails(culture, GuiText.GuiButtonReference.RefType.CULTURE, null, false);
      Minecraft.getInstance().setScreenAndShow(guiTravelBook);
   }
}
