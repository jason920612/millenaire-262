package org.millenaire.client.gui.text;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item;
import net.minecraft.resources.Identifier;
import net.minecraft.ChatFormatting;
import org.millenaire.client.book.BookManager;
import org.millenaire.client.book.TextBook;
import org.millenaire.client.book.TextLine;
import org.millenaire.common.buildingplan.BuildingPlanSet;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.VillageType;
import org.millenaire.common.culture.VillagerType;
import org.millenaire.common.item.TradeGood;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillLog;

/**
 * Base class for Mill's custom paginated "book" GUIs.
 *
 * <p>26.2 PORT NOTE: MC 26.2 replaced the immediate-mode {@code GuiScreen}/{@code GuiGraphics}
 * draw model with the render-state extraction pipeline. Screens now implement
 * {@link #extractRenderState(GuiGraphicsExtractor, int, int, float)} (the old {@code drawScreen})
 * and {@link GuiGraphicsExtractor} is the new draw surface (it carries {@code text}/{@code blit}/
 * {@code fill}/{@code fillGradient}/{@code item}). Mill's custom immediate-mode framework is
 * preserved here: the int-coordinate {@code customDraw*} hooks still work because the current
 * extractor is stashed in {@link #gfx} for the duration of a frame, and the legacy helper methods
 * ({@code drawTexturedModalRect}/{@code drawGradientRect}/{@code drawString}/{@code drawHoveringText})
 * are reimplemented on top of it. Buttons are lightweight Mill widgets ({@link MillGuiButton},
 * {@link GuiButtonReference}) drawn manually rather than vanilla {@code Button}s, keeping the
 * {@code .x}/{@code .y}/{@code .id}/{@code .enabled} fields the book layout code mutates directly.
 *
 * <p>26.2: interaction wiring uses the new {@code MouseButtonEvent}/{@code KeyEvent}/
 * {@code CharacterEvent} signatures, routed both to Mill's int-coordinate handlers and to the Mill text
 * fields (which extend {@link EditBox}): a click focuses the field under the cursor, and key/char events
 * are forwarded to the focused field so its EditBox caret/typing logic runs (the fields are drawn via
 * {@code renderMill}, not registered as vanilla widgets).
 */
@Environment(EnvType.CLIENT)
public abstract class GuiText extends Screen {
   private static final Identifier ICONS_TEXTURE = Identifier.fromNamespaceAndPath("millenaire", "textures/gui/icons.png");
   public static final String WHITE = "<white>";
   public static final String YELLOW = "<yellow>";
   public static final String PINK = "<pink>";
   public static final String LIGHTRED = "<lightred>";
   public static final String CYAN = "<cyan>";
   public static final String LIGHTGREEN = "<lightgreen>";
   public static final String BLUE = "<blue>";
   public static final String DARKGREY = "<darkgrey>";
   public static final String LIGHTGREY = "<lightgrey>";
   public static final String ORANGE = "<orange>";
   public static final String PURPLE = "<purple>";
   public static final String DARKRED = "<darkred>";
   public static final String LIGHTBLUE = "<lightblue>";
   public static final String DARKGREEN = "<darkgreen>";
   public static final String DARKBLUE = "<darkblue>";
   public static final String BLACK = "<black>";

   /** Compatibility alias of {@link #minecraft} for the ported 1.12 code. */
   protected final Minecraft mc = Minecraft.getInstance();
   /** Compatibility alias of {@link #font} for the ported 1.12 code. */
   protected Font fontRenderer;
   /** Legacy z-offset; the new pipeline uses strata so this is a no-op holder. */
   public float zLevel = 0.0F;
   /** Item rendering shim mirroring the old {@code RenderItem} surface. */
   protected final ItemRenderShim itemRenderer = new ItemRenderShim();
   protected final ItemRenderShim itemRender = this.itemRenderer;

   /** The current frame's draw surface; valid only during {@link #extractRenderState}. */
   protected GuiGraphicsExtractor gfx;

   private Screen callingScreen = null;
   protected int pageNum = 0;
   protected TextBook textBook = null;
   protected BookManager bookManager = null;
   List<MillGuiTextField> textFields = new ArrayList<>();

   /** Mill's button list. Holds {@link MillGuiButton}/{@link GuiButtonReference} drawn manually. */
   public List<AbstractMillButton> buttonList = new ArrayList<>();

   protected GuiText() {
      super(Component.empty());
      this.fontRenderer = this.mc.font;
   }

   protected void actionPerformed(AbstractMillButton button) {
      if (button instanceof GuiButtonReference) {
         GuiButtonReference refButton = (GuiButtonReference)button;
         GuiTravelBook guiTravelBook = new GuiTravelBook(Minecraft.getInstance().player);
         guiTravelBook.setCallingScreen(this);
         guiTravelBook.jumpToDetails(refButton.culture, refButton.type, refButton.key, false);
         this.mc.setScreenAndShow(guiTravelBook);
      }
   }

   public void buttonPagination() {
      int elementsId = 0;

      try {
         if (this.textBook == null) {
            return;
         }

         int xStart = (this.width - this.getXSize()) / 2;
         int yStart = (this.height - this.getYSize()) / 2;
         this.buttonList.clear();
         this.textFields.clear();
         int vpos = 6;
         if (this.pageNum < this.textBook.nbPages()) {
            for (int cp = 0; cp < this.getTextHeight() && cp < this.textBook.getPage(this.pageNum).getNbLines(); cp++) {
               TextLine line = this.textBook.getPage(this.pageNum).getLine(cp);
               int totalButtonWidth = this.getLineSizeInPx() - 20;
               if (line.buttons != null) {
                  if (line.buttons.length == 1) {
                     if (line.buttons[0] != null) {
                        line.buttons[0].x = xStart + this.getXSize() / 2 - totalButtonWidth / 2;
                        line.buttons[0].setWidth(totalButtonWidth);
                     }
                  } else if (line.buttons.length == 2) {
                     int buttonWidth = totalButtonWidth / 2 - 5;
                     if (line.buttons[0] != null) {
                        line.buttons[0].x = xStart + this.getXSize() / 2 - totalButtonWidth / 2;
                        line.buttons[0].setWidth(buttonWidth);
                     }

                     if (line.buttons[1] != null) {
                        line.buttons[1].x = xStart + this.getXSize() / 2 + 5;
                        line.buttons[1].setWidth(buttonWidth);
                     }
                  } else if (line.buttons.length == 3) {
                     int buttonWidthx = totalButtonWidth / 3 - 10;
                     if (line.buttons[0] != null) {
                        line.buttons[0].x = xStart + this.getXSize() / 2 - totalButtonWidth / 2;
                        line.buttons[0].setWidth(buttonWidthx);
                     }

                     if (line.buttons[1] != null) {
                        line.buttons[1].x = xStart + this.getXSize() / 2 - totalButtonWidth / 2 + buttonWidthx + 10;
                        line.buttons[1].setWidth(buttonWidthx);
                     }

                     if (line.buttons[2] != null) {
                        line.buttons[2].x = xStart + this.getXSize() / 2 - totalButtonWidth / 2 + buttonWidthx * 2 + 20;
                        line.buttons[2].setWidth(buttonWidthx);
                     }
                  }

                  for (int i = 0; i < line.buttons.length; i++) {
                     if (line.buttons[i] != null) {
                        line.buttons[i].y = yStart + vpos;
                        line.buttons[i].setHeight(20);
                        this.buttonList.add(line.buttons[i]);
                     }
                  }
               } else if (line.referenceButton != null) {
                  line.referenceButton.setWidth(20);
                  line.referenceButton.setHeight(20);
                  line.referenceButton.y = yStart + vpos;
                  line.referenceButton.x = xStart + 6 + line.getLineMarginLeft();
                  this.buttonList.add(line.referenceButton);
               } else if (line.textField != null) {
                  MillGuiTextField textField = new MillGuiTextField(
                     this.fontRenderer, xStart + this.getXSize() / 2 + 40, yStart + vpos, 95, 20, line.textField.fieldKey
                  );
                  textField.setValue(line.textField.getText());
                  textField.setMaxLength(line.textField.getMaxStringLength());
                  textField.setTextColor(-1);
                  line.textField = textField;
                  line.textField.setTextColor(-1);
                  line.textField.setBordered(false);
                  this.textFields.add(textField);
               }

               if (line.columns != null) {
                  int lineSize = this.getLineSizeInPx() - line.getTextMarginLeft() - line.getLineMarginLeft() - line.getLineMarginRight();
                  int colSize = (lineSize - (line.columns.length - 1) * 10) / line.columns.length;

                  for (int col = 0; col < line.columns.length; col++) {
                     TextLine column = line.columns[col];
                     int colXStart = col * (colSize + 10) + line.getLineMarginLeft();
                     if (column.referenceButton != null) {
                        column.referenceButton.setWidth(20);
                        column.referenceButton.setHeight(20);
                        column.referenceButton.y = yStart + vpos;
                        column.referenceButton.x = xStart + colXStart + 6 + column.getLineMarginLeft();
                        this.buttonList.add(column.referenceButton);
                     }
                  }
               }

               vpos += line.getLineHeight();
            }
         }
      } catch (Exception var13) {
         MillLog.printException("Exception while doing button pagination in GUI " + this, var13);
      }
   }

   protected void closeGui() {
      if (this.callingScreen != null) {
         this.mc.setScreenAndShow(this.callingScreen);
      } else {
         this.mc.setScreenAndShow(null);
      }
   }

   protected void closeWindow() {
      this.mc.setScreenAndShow(null);
   }

   protected abstract void customDrawBackground(int var1, int var2, float var3);

   protected abstract void customDrawScreen(int var1, int var2, float var3);

   public void decrementPage() {
      if (this.textBook != null) {
         if (this.pageNum > 0) {
            this.pageNum--;
         }

         this.buttonPagination();
      }
   }

   public boolean doesGuiPauseGame() {
      return false;
   }

   @Override
   public boolean isPauseScreen() {
      return this.doesGuiPauseGame();
   }

   // ------------------------------------------------------------------
   // Legacy immediate-mode draw helpers, reimplemented over GuiGraphicsExtractor.
   // ------------------------------------------------------------------

   protected void drawDefaultBackground() {
      if (this.gfx != null) {
         this.gfx.fill(0, 0, this.width, this.height, -1072689136);
      }
   }

   protected void drawTexturedModalRect(int x, int y, int u, int v, int width, int height) {
      if (this.gfx != null && this.boundTexture != null) {
         this.gfx.blit(RenderPipelines.GUI_TEXTURED, this.boundTexture, x, y, (float)u, (float)v, width, height, 256, 256);
      }
   }

   protected void drawGradientRect(int left, int top, int right, int bottom, int startColor, int endColor) {
      if (this.gfx != null) {
         this.gfx.fillGradient(left, top, right, bottom, startColor, endColor);
      }
   }

   protected void drawString(Font font, String text, int x, int y, int color) {
      if (this.gfx != null) {
         this.gfx.text(font, text, x, y, opaque(color), false);
      }
   }

   /**
    * 26.2 {@code GuiGraphicsExtractor.text} skips drawing entirely when the colour's alpha byte is 0
    * (ARGB.alpha(color)==0). The 1.12 Mill text colours (e.g. 1052688 = 0x100F90) carry NO alpha, so all
    * Mill GUI text rendered invisible (empty scrolls/books, vanishing config lines). Force opaque when the
    * caller didn't set an alpha.
    */
   protected static int opaque(int color) {
      return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
   }

   /** Legacy convenience: draw with the default font. */
   protected void drawString(String text, int x, int y, int color) {
      this.drawString(this.fontRenderer, text, x, y, color);
   }

   /** Legacy convenience mirroring 1.12 {@code FontRenderer.getStringWidth}. */
   public int getStringWidth(String text) {
      return this.fontRenderer.width(text);
   }

   /** The texture most recently "bound" via {@link #bindTexture}; used by {@link #drawTexturedModalRect}. */
   private Identifier boundTexture;

   protected void bindTexture(Identifier texture) {
      this.boundTexture = texture;
   }

   protected void drawHoveringText(List<String> lines, int x, int y, Font font) {
      if (this.gfx == null || lines.isEmpty()) {
         return;
      }

      int maxWidth = 0;
      for (String s : lines) {
         int w = font.width(s);
         if (w > maxWidth) {
            maxWidth = w;
         }
      }

      int tx = x + 12;
      int ty = y - 12;
      int boxHeight = 8;
      if (lines.size() > 1) {
         boxHeight += 2 + (lines.size() - 1) * 10;
      }

      if (tx + maxWidth > this.width) {
         tx -= 28 + maxWidth;
      }

      if (ty + boxHeight + 6 > this.height) {
         ty = this.height - boxHeight - 6;
      }

      int bg = -267386864;
      this.gfx.fill(tx - 3, ty - 4, tx + maxWidth + 3, ty + boxHeight + 4, bg);
      this.gfx.fillGradient(tx - 3, ty - 3, tx + maxWidth + 3, ty + boxHeight + 3, 1347420415, 1344798847);

      for (int i = 0; i < lines.size(); i++) {
         this.gfx.text(font, lines.get(i), tx, ty, -1, true);
         if (i == 0) {
            ty += 2;
         }

         ty += 10;
      }
   }

   protected void drawItemStackTooltip(ItemStack stack, int xPos, int yPos, boolean displayItemLegend, String extraLegend) {
      List<String> list = new ArrayList<>();
      if (displayItemLegend) {
         List<Component> tooltip = stack.getTooltipLines(
            Item.TooltipContext.EMPTY,
            this.mc.player,
            this.mc.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL
         );

         for (int k = 0; k < tooltip.size(); k++) {
            if (k == 0) {
               list.add(stack.getRarity().color() + tooltip.get(k).getString());
            } else {
               list.add(ChatFormatting.GRAY + tooltip.get(k).getString());
            }
         }
      }

      if (extraLegend != null) {
         list.addAll(BookManager.splitStringByLength(new FontRendererWrapped(this.fontRenderer), extraLegend, 150));
      }

      if (!list.isEmpty()) {
         this.drawHoveringText(list, xPos, yPos, this.fontRenderer);
      }
   }

   // ------------------------------------------------------------------
   // Main draw — the new pipeline's render entry point (old drawScreen).
   // ------------------------------------------------------------------

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      this.gfx = graphics;
      try {
         this.drawScreen(mouseX, mouseY, partialTick);
      } finally {
         this.gfx = null;
      }
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
   }

   public void drawScreen(int mouseX, int mouseY, float f) {
      try {
         if (this.textBook == null) {
            this.initData();
         }

         ItemStack hoverIcon = null;
         String extraLegend = null;
         boolean displayItemLegend = true;
         GuiButtonReference hoverReferenceButton = null;
         this.drawDefaultBackground();
         this.bindTexture(this.getPNGPath());
         int xStart = (this.width - this.getXSize()) / 2;
         int yStart = (this.height - this.getYSize()) / 2;
         this.drawTexturedModalRect(xStart, yStart, 0, 0, this.getXSize(), this.getYSize());
         this.customDrawBackground(mouseX, mouseY, f);

         if (this.textBook != null) {
            int vpos = 6;
            if (this.pageNum < this.textBook.nbPages()) {
               if (this.textBook.getPage(this.pageNum) == null) {
                  MillLog.printException(new MillLog.MillenaireException("descText.get(pageNum)==null for pageNum: " + this.pageNum + " in GUI: " + this));
               }

               for (int linePos = 0; linePos < this.getTextHeight() && linePos < this.textBook.getPage(this.pageNum).getNbLines(); linePos++) {
                  TextLine line = this.textBook.getPage(this.pageNum).getLine(linePos);
                  int textXstart = xStart + this.getTextXStart() + line.getTextMarginLeft() + line.getLineMarginLeft();
                  this.fontRenderer_drawString(line.style + line.text, textXstart, yStart + vpos + line.getTextMarginTop(), 1052688, line.shadow);

                  if (line.columns != null) {
                     int lineSize = this.getLineSizeInPx() - line.getTextMarginLeft() - line.getLineMarginLeft() - line.getLineMarginRight();
                     int colSize = (lineSize - (line.columns.length - 1) * 10) / line.columns.length;

                     for (int col = 0; col < line.columns.length; col++) {
                        TextLine column = line.columns[col];
                        int colXStart = xStart + this.getTextXStart() + col * (colSize + 10) + line.getLineMarginLeft();
                        textXstart = colXStart + column.getTextMarginLeft();
                        this.fontRenderer_drawString(column.style + column.text, textXstart, yStart + vpos + column.getTextMarginTop(), 1052688, column.shadow);
                     }
                  }

                  vpos += line.getLineHeight();
               }
            }

            this.fontRenderer_drawString(this.pageNum + 1 + "/" + this.getNbPage(), xStart + this.getXSize() / 2 - 10, yStart + this.getYSize() - 10, 1052688, false);
            vpos = 6;
            if (this.pageNum < this.textBook.nbPages()) {
               for (int linePos = 0; linePos < this.getTextHeight() && linePos < this.textBook.getPage(this.pageNum).getNbLines(); linePos++) {
                  TextLine linex = this.textBook.getPage(this.pageNum).getLine(linePos);
                  if (linex.icons != null) {
                     for (int ic = 0; ic < linex.icons.size(); ic++) {
                        ItemStack icon = linex.icons.get(ic);
                        int xPosition = this.getTextXStart() + 18 * ic + linex.getLineMarginLeft();
                        if (icon != null) {
                           this.itemRenderer.renderItemAndEffectIntoGUI(icon, xStart + xPosition, yStart + vpos);
                        }

                        if (xStart + xPosition < mouseX && yStart + vpos < mouseY && xStart + xPosition + 16 > mouseX && yStart + vpos + 16 > mouseY) {
                           hoverIcon = icon;
                           extraLegend = linex.iconExtraLegends.get(ic);
                           displayItemLegend = linex.displayItemLegend();
                        }
                     }
                  }

                  if (linex.columns != null) {
                     int lineSize = this.getLineSizeInPx() - linex.getTextMarginLeft() - linex.getLineMarginLeft() - linex.getLineMarginRight();
                     int colSize = (lineSize - (linex.columns.length - 1) * 10) / linex.columns.length;

                     for (int colx = 0; colx < linex.columns.length; colx++) {
                        TextLine column = linex.columns[colx];
                        int colXStart = this.getTextXStart() + colx * (colSize + 10) + linex.getLineMarginLeft();
                        if (column.icons != null) {
                           for (int ic = 0; ic < column.icons.size(); ic++) {
                              ItemStack iconx = column.icons.get(ic);
                              int iconXpos = colXStart + 18 * ic;
                              if (iconx != null) {
                                 this.itemRenderer.renderItemAndEffectIntoGUI(iconx, xStart + iconXpos, yStart + vpos);
                              }

                              if (xStart + iconXpos < mouseX && yStart + vpos < mouseY && xStart + iconXpos + 16 > mouseX && yStart + vpos + 16 > mouseY) {
                                 hoverIcon = iconx;
                                 extraLegend = column.iconExtraLegends.get(ic);
                                 displayItemLegend = column.displayItemLegend();
                              }
                           }
                        }
                     }
                  }

                  vpos += linex.getLineHeight();
               }
            }

            this.customDrawScreen(mouseX, mouseY, f);
         }

         // Draw buttons + their icons.
         this.bindTexture(ICONS_TEXTURE);
         for (AbstractMillButton button : this.buttonList) {
            button.drawButton(this, mouseX, mouseY);
            if (button instanceof MillGuiButton) {
               MillGuiButton millButton = (MillGuiButton)button;
               if (millButton.itemStackIconLeft != null) {
                  this.itemRenderer.renderItemAndEffectIntoGUI(millButton.itemStackIconLeft, millButton.x + 4, millButton.y + 2);
               }

               if (millButton.itemStackIconRight != null) {
                  this.itemRenderer.renderItemAndEffectIntoGUI(millButton.itemStackIconRight, millButton.x + millButton.width - 4 - 16, millButton.y + 2);
               }

               if (millButton.specialIconLeft != null) {
                  this.drawTexturedModalRect(millButton.x + 4, millButton.y + 2, millButton.specialIconLeft.xpos, millButton.specialIconLeft.ypos, 16, 16);
               }
            } else if (button instanceof GuiButtonReference) {
               GuiButtonReference refButton = (GuiButtonReference)button;
               if (refButton.getIcon() != null) {
                  this.itemRenderer.renderItemAndEffectIntoGUI(refButton.getIcon(), refButton.x + 2, refButton.y + 2);
               }

               if (refButton.x < mouseX && refButton.y < mouseY && refButton.x + refButton.width > mouseX && refButton.y + refButton.height > mouseY) {
                  hoverReferenceButton = refButton;
               }
            }
         }

         for (MillGuiTextField textField : this.textFields) {
            textField.renderMill(this.gfx, this.fontRenderer);
         }

         if (hoverIcon != null) {
            this.drawItemStackTooltip(hoverIcon, mouseX, mouseY, displayItemLegend, extraLegend);
         }

         if (hoverReferenceButton != null) {
            String legend = this instanceof GuiTravelBook ? hoverReferenceButton.getIconName() : hoverReferenceButton.getIconFullLegend();
            this.drawHoveringText(
               BookManager.splitStringByLength(new FontRendererWrapped(this.fontRenderer), legend, 150),
               hoverReferenceButton.x + 15,
               hoverReferenceButton.y,
               this.fontRenderer
            );
         }
      } catch (Exception var23) {
         MillLog.printException("Exception in drawScreen of GUI: " + this, var23);
      }
   }

   private void fontRenderer_drawString(String text, int x, int y, int color, boolean shadow) {
      if (this.gfx != null) {
         this.gfx.text(this.fontRenderer, text, x, y, opaque(color), shadow);
      }
   }

   public Screen getCallingScreen() {
      return this.callingScreen;
   }

   private final int getLineSizeInPx() {
      return this.bookManager.getLineSizeInPx();
   }

   protected int getNbPage() {
      return this.textBook.nbPages();
   }

   public abstract Identifier getPNGPath();

   public final int getTextHeight() {
      return this.bookManager.getTextHeight();
   }

   public final int getTextXStart() {
      return this.bookManager.getTextXStart();
   }

   public final int getXSize() {
      return this.bookManager.getXSize();
   }

   public final int getYSize() {
      return this.bookManager.getYSize();
   }

   protected void handleTextFieldPress(MillGuiTextField textField) {
   }

   /**
    * Legacy key handler (char + LWJGL keycode). Routed from {@link #keyPressed}. Escape (keycode 1)
    * closes the GUI by default. Subclasses may override.
    */
   protected void keyTyped(char c, int i) {
      boolean keyTyped = false;

      for (MillGuiTextField textField : this.textFields) {
         if (textField.isFocused()) {
            keyTyped = true;
            this.handleTextFieldPress(textField);
         }
      }

      if (!keyTyped && i == 1) {
         this.closeGui();
      }
   }

   @Override
   public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
      // A focused Mill text field (an EditBox) gets first refusal so editing keys (backspace, arrows,
      // delete, etc.) reach its caret logic — these fields are drawn via renderMill, not as widgets,
      // so the screen must route input to them explicitly.
      for (MillGuiTextField textField : this.textFields) {
         if (textField.isFocused() && textField.keyPressed(event)) {
            return true;
         }
      }

      // Escape maps to legacy keycode 1; everything else passes through to the legacy handler with code 0.
      if (event.isEscape()) {
         this.keyTyped('\0', 1);
         return true;
      }
      this.keyTyped('\0', 0);
      return super.keyPressed(event);
   }

   @Override
   public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
      // Route typed characters to the focused Mill text field's EditBox (caret insert / max-length).
      for (MillGuiTextField textField : this.textFields) {
         if (textField.isFocused() && textField.charTyped(event)) {
            return true;
         }
      }
      return super.charTyped(event);
   }

   public void incrementPage() {
      if (this.textBook != null) {
         if (this.pageNum < this.getNbPage() - 1) {
            this.pageNum++;
         }

         this.buttonPagination();
      }
   }

   public abstract void initData();

   @Override
   protected void init() {
      super.init();
      this.fontRenderer = this.font;
      this.initData();
      this.buttonPagination();
   }

   /** Legacy alias still called by some subclasses. */
   public void initGui() {
      this.init();
   }

   /**
    * Mill click routing — converts the new event coords to the legacy int handler, and forwards the
    * click to the Mill text fields (EditBox) so a click focuses the field under the cursor and clears
    * focus on the others (the fields are rendered via renderMill, not as registered widgets).
    */
   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      int i = (int)event.x();
      int j = (int)event.y();
      boolean hitField = false;
      for (MillGuiTextField textField : this.textFields) {
         boolean over = textField.isMouseOver(event.x(), event.y());
         textField.setFocused(over);
         if (over) {
            textField.mouseClicked(event, doubleClick);
            hitField = true;
         }
      }

      this.millMouseClicked(i, j, event.button());
      return hitField || super.mouseClicked(event, doubleClick);
   }

   protected void millMouseClicked(int i, int j, int button) {
      int xStart = (this.width - this.getXSize()) / 2;
      int yStart = (this.height - this.getYSize()) / 2;
      int ai = i - xStart;
      int aj = j - yStart;
      if (aj > this.getYSize() - 14 && aj < this.getYSize()) {
         if (ai > 0 && ai < 33) {
            this.decrementPage();
         } else if (ai > this.getXSize() - 33 && ai < this.getXSize()) {
            this.incrementPage();
         }
      }

      for (AbstractMillButton b : new ArrayList<>(this.buttonList)) {
         if (b.enabled && i >= b.x && i < b.x + b.width && j >= b.y && j < b.y + b.height) {
            // Vanilla buttons play UI_BUTTON_CLICK via playDownSound; Mill's custom buttons bypass that,
            // so clicks were silent. Play the same click sound when a Mill button is activated.
            this.mc.getSoundManager().play(
               net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            try {
               this.actionPerformed(b);
            } catch (Exception e) {
               MillLog.printException("Exception in button action", e);
            }
            break;
         }
      }
   }

   @Override
   public void removed() {
      super.removed();
   }

   public void onGuiClosed() {
      this.removed();
   }

   public void setCallingScreen(Screen callingScreen) {
      this.callingScreen = callingScreen;
   }

   // ------------------------------------------------------------------
   // Shims and inner widget classes.
   // ------------------------------------------------------------------

   /** Lightweight item renderer surface mirroring 1.12 {@code RenderItem}. */
   @Environment(EnvType.CLIENT)
   public final class ItemRenderShim {
      public float zLevel = 0.0F;

      public void renderItemAndEffectIntoGUI(ItemStack stack, int x, int y) {
         if (GuiText.this.gfx != null && stack != null) {
            GuiText.this.gfx.item(stack, x, y);
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public static class FontRendererGUIWrapper implements BookManager.IFontRendererWrapper {
      private final GuiText gui;

      public FontRendererGUIWrapper(GuiText gui) {
         this.gui = gui;
      }

      @Override
      public int getStringWidth(String text) {
         return this.gui.fontRenderer.width(text);
      }

      @Override
      public boolean isAvailable() {
         return this.gui.fontRenderer != null;
      }
   }

   @Environment(EnvType.CLIENT)
   public static class FontRendererWrapped implements BookManager.IFontRendererWrapper {
      private final Font fontRenderer;

      public FontRendererWrapped(Font fontRenderer) {
         this.fontRenderer = fontRenderer;
      }

      @Override
      public int getStringWidth(String text) {
         return this.fontRenderer.width(text);
      }

      @Override
      public boolean isAvailable() {
         return this.fontRenderer != null;
      }
   }

   /**
    * Base for Mill's manually-drawn book buttons (replaces vanilla {@code GuiButton}). Keeps the
    * public {@code x}/{@code y}/{@code width}/{@code height}/{@code id}/{@code enabled} fields the
    * book-layout code mutates directly.
    */
   @Environment(EnvType.CLIENT)
   public abstract static class AbstractMillButton {
      // Vanilla widget button sprites (verified in AbstractButton.SPRITES, mc-sources).
      protected static final Identifier BUTTON_SPRITE = Identifier.withDefaultNamespace("widget/button");
      protected static final Identifier BUTTON_DISABLED_SPRITE = Identifier.withDefaultNamespace("widget/button_disabled");
      protected static final Identifier BUTTON_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("widget/button_highlighted");
      public int id;
      public int x;
      public int y;
      public int width;
      public int height;
      public boolean enabled = true;
      public boolean visible = true;
      public String displayString = "";

      protected AbstractMillButton(int id, int x, int y, int width, int height, String label) {
         this.id = id;
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.displayString = label == null ? "" : label;
      }

      public void setWidth(int w) {
         this.width = w;
      }

      public void setHeight(int h) {
         this.height = h;
      }

      public int getWidth() {
         return this.width;
      }

      public int getHeight() {
         return this.height;
      }

      /** Draw the button frame + label using the vanilla nine-slice button sprite. */
      public void drawButton(GuiText gui, int mouseX, int mouseY) {
         if (!this.visible || gui.gfx == null) {
            return;
         }

         boolean hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
         Identifier sprite = !this.enabled ? BUTTON_DISABLED_SPRITE : (hovered ? BUTTON_HIGHLIGHTED_SPRITE : BUTTON_SPRITE);
         gui.gfx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.x, this.y, this.width, this.height);
         // 1.12 vanilla button text colours: disabled #A0A0A0, hovered #FFFFA0, normal #E0E0E0.
         int textColor = !this.enabled ? -6250336 : (hovered ? 16777120 : -2039584);
         gui.gfx.centeredText(gui.fontRenderer, this.displayString, this.x + this.width / 2, this.y + (this.height - 8) / 2, textColor);
      }
   }

   @Environment(EnvType.CLIENT)
   public static class GuiButtonReference extends AbstractMillButton {
      public Culture culture;
      public RefType type;
      public String key;

      public GuiButtonReference(BuildingPlanSet planSet) {
         super(0, 0, 0, 0, 0, "");
         if (planSet == null) {
            MillLog.printException(new Exception("Tried creating a ref button to a null planSet."));
         } else {
            this.culture = planSet.culture;
         }

         this.type = RefType.BUILDING_DETAIL;
         this.key = planSet.key;
      }

      public GuiButtonReference(Culture culture) {
         super(0, 0, 0, 0, 0, "");
         this.culture = culture;
         this.type = RefType.CULTURE;
         this.key = null;
      }

      public GuiButtonReference(Culture culture, RefType type, String key) {
         super(0, 0, 0, 0, 0, "");
         this.culture = culture;
         this.type = type;
         this.key = key;
      }

      public GuiButtonReference(TradeGood tradeGood) {
         super(0, 0, 0, 0, 0, "");
         this.culture = tradeGood.culture;
         this.type = RefType.TRADE_GOOD_DETAIL;
         this.key = tradeGood.key;
      }

      public GuiButtonReference(VillagerType villagerType) {
         super(0, 0, 0, 0, 0, "");
         this.culture = villagerType.culture;
         this.type = RefType.VILLAGER_DETAIL;
         this.key = villagerType.key;
      }

      public GuiButtonReference(VillageType villageType) {
         super(0, 0, 0, 0, 0, "");
         if (villageType.lonebuilding) {
            this.culture = villageType.culture;
            this.type = RefType.BUILDING_DETAIL;
            this.key = villageType.centreBuilding.key;
         } else {
            this.culture = villageType.culture;
            this.type = RefType.VILLAGE_DETAIL;
            this.key = villageType.key;
         }
      }

      @Override
      public void drawButton(GuiText gui, int mouseX, int mouseY) {
         // Reference buttons (1.12 GuiButton) render the vanilla button frame; GuiText overlays the icon.
         if (this.visible && gui.gfx != null) {
            boolean hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            Identifier sprite = !this.enabled ? BUTTON_DISABLED_SPRITE : (hovered ? BUTTON_HIGHLIGHTED_SPRITE : BUTTON_SPRITE);
            gui.gfx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.x, this.y, this.width, this.height);
         }
      }

      public ItemStack getIcon() {
         if (this.type == RefType.BUILDING_DETAIL) {
            return this.culture.getBuildingPlanSet(this.key).getIcon();
         } else if (this.type == RefType.VILLAGER_DETAIL) {
            return this.culture.getVillagerType(this.key).getIcon();
         } else if (this.type == RefType.VILLAGE_DETAIL) {
            return this.culture.getVillageType(this.key).getIcon();
         } else if (this.type == RefType.TRADE_GOOD_DETAIL) {
            return this.culture.getTradeGood(this.key).getIcon();
         } else {
            return this.type == RefType.CULTURE ? this.culture.getIcon() : null;
         }
      }

      public String getIconFullLegend() {
         return LanguageUtilities.string("travelbook.reference_button", this.getIconName());
      }

      public String getIconFullLegendExport() {
         return LanguageUtilities.string("travelbook.reference_button_export", this.getIconNameTranslated());
      }

      public String getIconName() {
         if (this.type == RefType.BUILDING_DETAIL) {
            return this.culture.getBuildingPlanSet(this.key).getNameNative();
         } else if (this.type == RefType.VILLAGER_DETAIL) {
            return this.culture.getVillagerType(this.key).name;
         } else if (this.type == RefType.VILLAGE_DETAIL) {
            return this.culture.getVillageType(this.key).name;
         } else if (this.type == RefType.TRADE_GOOD_DETAIL) {
            return this.culture.getTradeGood(this.key).getName();
         } else {
            return this.type == RefType.CULTURE ? this.culture.getAdjectiveTranslated() : null;
         }
      }

      public String getIconNameTranslated() {
         if (this.type == RefType.BUILDING_DETAIL) {
            return this.culture.getBuildingPlanSet(this.key).getNameNativeAndTranslated();
         } else if (this.type == RefType.VILLAGER_DETAIL) {
            return this.culture.getVillagerType(this.key).getNameNativeAndTranslated();
         } else if (this.type == RefType.VILLAGE_DETAIL) {
            return this.culture.getVillageType(this.key).getNameNativeAndTranslated();
         } else if (this.type == RefType.TRADE_GOOD_DETAIL) {
            return this.culture.getTradeGood(this.key).getName();
         } else {
            return this.type == RefType.CULTURE ? this.culture.getAdjectiveTranslated() : null;
         }
      }

      public static enum RefType {
         BUILDING_DETAIL,
         VILLAGER_DETAIL,
         VILLAGE_DETAIL,
         TRADE_GOOD_DETAIL,
         CULTURE;
      }
   }

   @Environment(EnvType.CLIENT)
   public static class MillGuiButton extends AbstractMillButton {
      public static final int HELPBUTTON = 2000;
      public static final int CHUNKBUTTON = 3000;
      public static final int CONFIGBUTTON = 4000;
      public static final int TRAVELBOOKBUTTON = 5000;
      public ItemStack itemStackIconLeft = null;
      public SpecialIcon specialIconLeft = null;
      public ItemStack itemStackIconRight = null;
      public SpecialIcon specialIconRight = null;

      public MillGuiButton(int buttonId, int x, int y, int widthIn, int heightIn, String label) {
         super(buttonId, x, y, widthIn, heightIn, label);
      }

      /** Legacy 4-arg vanilla form: (id, x, y, label) with the default 200x20 size. */
      public MillGuiButton(int buttonId, int x, int y, String label) {
         super(buttonId, x, y, 200, 20, label);
      }

      public MillGuiButton(String label, int id) {
         super(id, 0, 0, 0, 0, label);
      }

      public MillGuiButton(String label, int id, ItemStack icon) {
         super(id, 0, 0, 0, 0, label);
         this.itemStackIconLeft = icon;
      }

      public MillGuiButton(String label, int id, ItemStack iconLeft, ItemStack iconRight) {
         super(id, 0, 0, 0, 0, label);
         this.itemStackIconLeft = iconLeft;
         this.itemStackIconRight = iconRight;
      }

      public MillGuiButton(String label, int id, SpecialIcon icon) {
         super(id, 0, 0, 0, 0, label);
         this.specialIconLeft = icon;
      }
   }

   /** Mill text field. Extends vanilla {@link EditBox} but keeps the legacy accessor names. */
   @Environment(EnvType.CLIENT)
   public static class MillGuiTextField extends EditBox {
      public final String fieldKey;

      public MillGuiTextField(Font font, int x, int y, int width, int height, String fieldKey) {
         super(font, x, y, width, height, Component.empty());
         this.fieldKey = fieldKey;
      }

      /** Legacy 1.12 constructor with an (unused) widget id. */
      public MillGuiTextField(int id, Font font, int x, int y, int width, int height, String fieldKey) {
         this(font, x, y, width, height, fieldKey);
      }

      public void setMaxStringLength(int len) {
         this.setMaxLength(len);
      }

      public int getMaxStringLength() {
         return 100;
      }

      public String getText() {
         return this.getValue();
      }

      public void setText(String text) {
         this.setValue(text);
      }

      /**
       * Renders the field over the Mill extractor surface. The field is drawn here (not as a registered
       * widget), so this draws the value text plus a blinking caret when focused; the EditBox base still
       * owns the actual text/caret state (driven by the forwarded key/char/mouse events in GuiText).
       */
      public void renderMill(GuiGraphicsExtractor gfx, Font font) {
         if (gfx != null) {
            String value = this.getValue();
            gfx.text(font, value, this.getX(), this.getY(), -1, false);
            // Blinking caret at the end of the text while focused (≈ EditBox's caret, simplified for the
            // single-line Mill fields which always edit at the end).
            if (this.isFocused() && (System.currentTimeMillis() / 500L) % 2L == 0L) {
               gfx.text(font, "_", this.getX() + font.width(value), this.getY(), -1, false);
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum SpecialIcon {
      PLUS(0, 0),
      MINUS(16, 0);

      public int xpos;
      public int ypos;

      private SpecialIcon(int xpos, int ypos) {
         this.xpos = xpos;
         this.ypos = ypos;
      }
   }
}
