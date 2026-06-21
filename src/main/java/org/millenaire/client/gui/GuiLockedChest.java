package org.millenaire.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.millenaire.common.ui.ContainerLockedChest;

/**
 * Screen for the Mill locked chest. 1.12 extended {@code GuiChest}; on 26.2 we extend
 * {@link AbstractContainerScreen} of the custom {@link ContainerLockedChest} menu.
 *
 * <p>26.2: the open flow is wired through {@code ClientGuiHandler.getClientGuiElement(1, …)}, which uses
 * the {@link org.millenaire.common.entity.TileEntityLockedChest} block entity directly as the container
 * inventory (the 1.12 BlockLockedChest.getInventory machinery is gone) and shows this screen.
 */
@Environment(EnvType.CLIENT)
public class GuiLockedChest extends AbstractContainerScreen<ContainerLockedChest> {
   private static final Identifier CONTAINER_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
   boolean locked = true;
   private final int containerRows;

   public GuiLockedChest(ContainerLockedChest menu, Inventory inventory, Component title, boolean locked) {
      super(menu, inventory, title, 176, 114 + (menu.getLowerChestInventory().getContainerSize() / 9) * 18);
      this.locked = locked;
      this.containerRows = menu.getLowerChestInventory().getContainerSize() / 9;
      this.inventoryLabelY = this.imageHeight - 94;
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      super.extractBackground(graphics, mouseX, mouseY, a);
      int xo = (this.width - this.imageWidth) / 2;
      int yo = (this.height - this.imageHeight) / 2;
      graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, xo, yo, 0.0F, 0.0F, this.imageWidth, this.containerRows * 18 + 17, 256, 256);
      graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, xo, yo + this.containerRows * 18 + 17, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
   }

   @Override
   protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      // 1.12 GuiChest drew the chest's name in the top-left; the default 26.2 label colour (0x404040) has a
      // zero alpha byte, so GuiGraphicsExtractor.text skips it and the title was invisible. Draw it opaque,
      // matching the other Mill container screens, plus the player-inventory label.
      graphics.text(this.font, this.title, 8, 6, 0xFF404040, false);
      graphics.text(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 0xFF404040, false);
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (!this.locked) {
         return super.keyPressed(event);
      }
      // Locked: only allow escape / the inventory key to close the screen.
      if (event.key() == 256 || this.minecraft.options.keyInventory.matches(event)) {
         this.onClose();
         return true;
      }
      return true;
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      return this.locked || super.mouseClicked(event, doubleClick);
   }
}
