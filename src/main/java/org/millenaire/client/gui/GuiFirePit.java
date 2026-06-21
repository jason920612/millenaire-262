package org.millenaire.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.entity.TileEntityFirePit;
import org.millenaire.common.ui.firepit.ContainerFirePit;

@Environment(EnvType.CLIENT)
public class GuiFirePit extends AbstractContainerScreen<ContainerFirePit> {
   private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("millenaire", "textures/gui/firepit.png");
   private static final int[][] ARROWS = new int[][]{{77, 22, 23, 31, 8}, {71, 28, 37, 14, 16}, {77, 42, 23, 31, 8}};
   private static final int[] FIRE = new int[]{81, 54};
   private final TileEntityFirePit firePit;

   public GuiFirePit(ContainerFirePit menu, Inventory inventory, Component title, TileEntityFirePit firePit) {
      super(menu, inventory, title, 176, 175);
      this.firePit = firePit;
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
      super.extractBackground(graphics, mouseX, mouseY, partialTicks);
      int x = (this.width - this.imageWidth) / 2;
      int y = (this.height - this.imageHeight) / 2;
      graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
      if (this.firePit.getBurnTime() > 0) {
         int burn = this.getBurnLeftScaled(13);
         graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + FIRE[0], y + FIRE[1] + 12 - burn,
            (float)this.imageWidth, (float)(12 - burn), 14, burn + 1, 256, 256);
      }

      for (int i = 0; i < 3; i++) {
         int[] data = ARROWS[i];
         int arrowX = data[0];
         int arrowY = data[1];
         int arrowLen = data[2];
         int arrowTexY = data[3];
         int arrowHeight = data[4];
         int progress = this.getCookProgressScaled(i, arrowLen);
         graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + arrowX, y + arrowY,
            (float)this.imageWidth, (float)arrowTexY, progress, arrowHeight, 256, 256);
      }
   }

   @Override
   protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      graphics.text(this.font, MillBlocks.FIRE_PIT.getName(), 8, 6, 0xFF404040, false);
      graphics.text(this.font, this.playerInventoryTitle, 8, this.imageHeight - 96 + 2, 0xFF404040, false);
   }

   private int getBurnLeftScaled(int pixels) {
      int time = this.firePit.getTotalBurnTime();
      if (time == 0) {
         time = 200;
      }

      return this.firePit.getBurnTime() * pixels / time;
   }

   private int getCookProgressScaled(int idx, int pixels) {
      int cook = this.firePit.getCookTime(idx);
      return cook != 0 ? cook * pixels / 200 : 0;
   }
}
