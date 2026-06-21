package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import org.millenaire.client.network.ClientSender;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.ui.ContainerPuja;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.village.Building;

/**
 * Mill puja/sacrifice shrine screen.
 *
 * <p>26.2 PORT NOTE: ported from {@code GuiContainer} to {@link AbstractContainerScreen} of the Mill
 * {@link ContainerPuja} menu; drawing via {@link GuiGraphicsExtractor}. Slot/item/tooltip rendering is
 * handled by the base class. The enchantment-target buttons (drawn from the puja texture) and the
 * progress bars are preserved. The per-slot hover tint (1.12's 50% white overlay) is reproduced in
 * {@link #extractSlot}; custom slot tooltips use the new tooltip API.
 */
@Environment(EnvType.CLIENT)
public class GuiPujas extends AbstractContainerScreen<ContainerPuja> {
   private static final Identifier TEXTURE_PUJAS = Identifier.fromNamespaceAndPath("millenaire", "textures/gui/pujas.png");
   private static final Identifier TEXTURE_SACRIFICES = Identifier.fromNamespaceAndPath("millenaire", "textures/gui/mayansacrifices.png");
   private final Building temple;
   private final Player player;

   public GuiPujas(ContainerPuja menu, Inventory inventory, Component title, Player player, Building temple) {
      super(menu, inventory, title, 176, 188);
      this.temple = temple;
      this.player = player;
      if (MillConfigValues.LogPujas >= 3) {
         MillLog.debug(this, "Opening shrine GUI");
      }
   }

   private Identifier texture() {
      return this.temple.pujas != null && this.temple.pujas.type == 1 ? TEXTURE_SACRIFICES : TEXTURE_PUJAS;
   }

   private void blit(GuiGraphicsExtractor graphics, int x, int y, int u, int v, int w, int h) {
      graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture(), x, y, (float)u, (float)v, w, h, 256, 256);
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
      super.extractBackground(graphics, mouseX, mouseY, partialTicks);
      int x = (this.width - this.imageWidth) / 2;
      int y = (this.height - this.imageHeight) / 2;
      this.blit(graphics, x, y, 0, 0, this.imageWidth, this.imageHeight);
      if (this.temple.pujas != null) {
         int linePos = 0;
         int colPos = 0;

         for (int cp = 0; cp < this.temple.pujas.getTargets().size(); cp++) {
            boolean active = this.temple.pujas.currentTarget == this.temple.pujas.getTargets().get(cp);
            this.blit(
               graphics,
               x + this.getTargetXStart() + colPos * this.getButtonWidth(),
               y + this.getTargetYStart() + this.getButtonHeight() * linePos,
               active ? this.temple.pujas.getTargets().get(cp).startXact : this.temple.pujas.getTargets().get(cp).startX,
               active ? this.temple.pujas.getTargets().get(cp).startYact : this.temple.pujas.getTargets().get(cp).startY,
               this.getButtonWidth(),
               this.getButtonHeight()
            );

            if (++colPos >= this.getNbPerLines()) {
               colPos = 0;
               linePos++;
            }
         }

         int progress = this.temple.pujas.getPujaProgressScaled(13);
         this.blit(graphics, x + 27, y + 39 + 13 - progress, 176, 13 - progress, 15, progress);
         progress = this.temple.pujas.getOfferingProgressScaled(16);
         this.blit(graphics, x + 84, y + 63 + 16 - progress, 176, 47 - progress, 19, progress);
      }
   }

   @Override
   protected void extractSlot(GuiGraphicsExtractor graphics, net.minecraft.world.inventory.Slot slot, int mouseX, int mouseY) {
      super.extractSlot(graphics, slot, mouseX, mouseY);
      // 1.12 drew a 50% white hover tint over the hovered slot (slot coords are local to the
      // leftPos/topPos-translated pose, the same space extractSlot draws in).
      if (slot == this.hoveredSlot && slot.isActive()) {
         graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, -2130706433);
      }
   }

   @Override
   protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      if (this.temple.pujas != null && this.temple.pujas.type == 1) {
         graphics.text(this.font, LanguageUtilities.string("sacrifices.offering"), 8, 6, 0xFF404040, false);
         graphics.text(this.font, LanguageUtilities.string("sacrifices.panditfee"), 8, 75, 0xFF404040, false);
      } else {
         graphics.text(this.font, LanguageUtilities.string("pujas.offering"), 8, 6, 0xFF404040, false);
         graphics.text(this.font, LanguageUtilities.string("pujas.panditfee"), 8, 75, 0xFF404040, false);
      }

      graphics.text(this.font, I18n.get("container.inventory"), 8, this.imageHeight - 94 + 2, 0xFF404040, false);
   }

   @Override
   protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      if (this.hoveredSlot != null && this.menu.getCarried().isEmpty() && !this.hoveredSlot.hasItem()) {
         List<Component> list = new ArrayList<>();
         if (this.hoveredSlot instanceof ContainerPuja.OfferingSlot) {
            list.add(Component.literal("§6" + LanguageUtilities.string("pujas.offeringslot")));
            list.add(Component.literal("§7" + LanguageUtilities.string("pujas.offeringslot2")));
         } else if (this.hoveredSlot instanceof ContainerPuja.MoneySlot) {
            list.add(Component.literal("§6" + LanguageUtilities.string("pujas.moneyslot")));
         } else if (this.hoveredSlot instanceof ContainerPuja.ToolSlot) {
            list.add(Component.literal("§6" + LanguageUtilities.string("pujas.toolslot")));
         }

         if (!list.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(this.font, list, mouseX, mouseY);
            return;
         }
      }

      super.extractTooltip(graphics, mouseX, mouseY);

      // Enchantment-target hover labels.
      int startx = (this.width - this.imageWidth) / 2;
      int starty = (this.height - this.imageHeight) / 2;
      if (this.temple.pujas != null) {
         int linePos = 0;
         int colPos = 0;

         for (int cp = 0; cp < this.temple.pujas.getTargets().size(); cp++) {
            if (mouseX > startx + this.getTargetXStart() + colPos * this.getButtonWidth()
               && mouseX < startx + this.getTargetXStart() + (colPos + 1) * this.getButtonWidth()
               && mouseY > starty + this.getTargetYStart() + this.getButtonHeight() * linePos
               && mouseY < starty + this.getTargetYStart() + this.getButtonHeight() * (linePos + 1)) {
               String s = LanguageUtilities.string(this.temple.pujas.getTargets().get(cp).mouseOver);
               graphics.setComponentTooltipForNextFrame(this.font, List.of(Component.literal(s)), mouseX, mouseY);
            }

            if (++colPos >= this.getNbPerLines()) {
               colPos = 0;
               linePos++;
            }
         }
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      boolean handled = super.mouseClicked(event, doubleClick);
      int x = (int)event.x();
      int y = (int)event.y();
      int startx = (this.width - this.imageWidth) / 2;
      int starty = (this.height - this.imageHeight) / 2;
      if (this.temple.pujas != null) {
         int linePos = 0;
         int colPos = 0;

         for (int cp = 0; cp < this.temple.pujas.getTargets().size(); cp++) {
            if (x > startx + this.getTargetXStart() + colPos * this.getButtonWidth()
               && x < startx + this.getTargetXStart() + (colPos + 1) * this.getButtonWidth()
               && y > starty + this.getTargetYStart() + this.getButtonHeight() * linePos
               && y < starty + this.getTargetYStart() + this.getButtonHeight() * (linePos + 1)) {
               ClientSender.pujasChangeEnchantment(this.player, this.temple, cp);
            }

            if (++colPos >= this.getNbPerLines()) {
               colPos = 0;
               linePos++;
            }
         }
      }

      return handled;
   }

   private int getButtonHeight() {
      if (this.temple.pujas == null) {
         return 0;
      } else if (this.temple.pujas.type == 0) {
         return 17;
      } else {
         return this.temple.pujas.type == 1 ? 20 : 0;
      }
   }

   private int getButtonWidth() {
      if (this.temple.pujas == null) {
         return 0;
      } else if (this.temple.pujas.type == 0) {
         return 46;
      } else {
         return this.temple.pujas.type == 1 ? 20 : 0;
      }
   }

   private int getNbPerLines() {
      if (this.temple.pujas == null) {
         return 1;
      } else if (this.temple.pujas.type == 0) {
         return 1;
      } else {
         return this.temple.pujas.type == 1 ? 3 : 1;
      }
   }

   private int getTargetXStart() {
      if (this.temple.pujas == null) {
         return 0;
      } else if (this.temple.pujas.type == 0) {
         return 118;
      } else {
         return this.temple.pujas.type == 1 ? 110 : 0;
      }
   }

   private int getTargetYStart() {
      if (this.temple.pujas == null) {
         return 0;
      } else if (this.temple.pujas.type == 0) {
         return 22;
      } else {
         return this.temple.pujas.type == 1 ? 22 : 0;
      }
   }
}
