package org.millenaire.client.gui;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.millenaire.common.buildingplan.BuildingPlanSet;
import org.millenaire.common.culture.VillageType;
import org.millenaire.common.culture.VillagerType;
import org.millenaire.common.item.TradeGood;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillLog;

/**
 * "Content unlocked" toast shown when the player discovers new Mill content.
 *
 * <p>26.2 PORT NOTE: the 1.12 {@code IToast.draw(GuiToast, long)} immediate-mode draw became the
 * render-state model: {@link #update(ToastManager, long)} computes visibility and
 * {@link #extractRenderState(GuiGraphicsExtractor, Font, long)} draws (blit/text/item over the
 * extractor). The toast background uses the vanilla toast atlas.
 */
@Environment(EnvType.CLIENT)
public class UnlockingToast implements Toast {
   // 26.2: the old textures/gui/toasts.png atlas (drawn via u/v offsets) was split into GUI SPRITES; the toast
   // background is now the "toast/advancement" sprite drawn with blitSprite (see vanilla AdvancementToast).
   // Using the old atlas blit produced a missing-texture (magenta) toast background.
   private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("toast/advancement");
   private final BuildingPlanSet planSet;
   private final VillageType villageType;
   private final VillagerType villagerType;
   private final TradeGood tradeGood;
   private final List<TradeGood> tradeGoods;
   private final int nbUnlocked;
   private final int nbTotal;
   private Toast.Visibility visibility = Toast.Visibility.SHOW;

   public UnlockingToast(BuildingPlanSet planSet, int nbUnlocked, int nbTotal) {
      this.planSet = planSet;
      this.villageType = null;
      this.villagerType = null;
      this.tradeGood = null;
      this.tradeGoods = null;
      this.nbUnlocked = nbUnlocked;
      this.nbTotal = nbTotal;
   }

   public UnlockingToast(List<TradeGood> tradeGoods, int nbUnlocked, int nbTotal) {
      this.tradeGoods = tradeGoods;
      this.villageType = null;
      this.villagerType = null;
      this.tradeGood = null;
      this.planSet = null;
      this.nbUnlocked = nbUnlocked;
      this.nbTotal = nbTotal;
   }

   public UnlockingToast(TradeGood tradeGood, int nbUnlocked, int nbTotal) {
      this.tradeGood = tradeGood;
      this.villageType = null;
      this.villagerType = null;
      this.planSet = null;
      this.tradeGoods = null;
      this.nbUnlocked = nbUnlocked;
      this.nbTotal = nbTotal;
   }

   public UnlockingToast(VillagerType villagerType, int nbUnlocked, int nbTotal) {
      this.villagerType = villagerType;
      this.villageType = null;
      this.planSet = null;
      this.tradeGood = null;
      this.tradeGoods = null;
      this.nbUnlocked = nbUnlocked;
      this.nbTotal = nbTotal;
   }

   public UnlockingToast(VillageType villageType, int nbUnlocked, int nbTotal) {
      this.villageType = villageType;
      this.planSet = null;
      this.villagerType = null;
      this.tradeGood = null;
      this.tradeGoods = null;
      this.nbUnlocked = nbUnlocked;
      this.nbTotal = nbTotal;
   }

   @Override
   public Toast.Visibility getWantedVisibility() {
      return this.visibility;
   }

   @Override
   public void update(ToastManager manager, long fullyVisibleForMs) {
      this.visibility = fullyVisibleForMs >= this.getDisplayDuration() ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
      String title = null;
      String text = null;
      ItemStack icon = null;

      try {
         if (this.planSet != null) {
            title = this.planSet.getNameNative();
            text = LanguageUtilities.string("travelbook.unlockedbuilding", this.planSet.culture.getAdjectiveTranslated(), "" + this.nbUnlocked, "" + this.nbTotal);
            icon = this.planSet.getIcon();
         } else if (this.villageType != null) {
            title = this.villageType.name;
            text = LanguageUtilities.string("travelbook.unlockedvillage", this.villageType.culture.getAdjectiveTranslated(), "" + this.nbUnlocked, "" + this.nbTotal);
            icon = this.villageType.getIcon();
         } else if (this.villagerType != null) {
            title = this.villagerType.name;
            text = LanguageUtilities.string("travelbook.unlockedvillager", this.villagerType.culture.getAdjectiveTranslated(), "" + this.nbUnlocked, "" + this.nbTotal);
            icon = this.villagerType.getIcon();
         } else if (this.tradeGood != null) {
            title = this.tradeGood.getName();
            text = LanguageUtilities.string("travelbook.unlockedtradegood", this.tradeGood.culture.getAdjectiveTranslated(), "" + this.nbUnlocked, "" + this.nbTotal);
            icon = this.tradeGood.getIcon();
         } else if (this.tradeGoods != null) {
            int pos = (int)(fullyVisibleForMs / 1000L);
            if (pos >= 0 && pos < this.tradeGoods.size()) {
               TradeGood tg = this.tradeGoods.get(pos);
               title = tg.getName();
               text = LanguageUtilities.string("travelbook.unlockedtradegood", tg.culture.getAdjectiveTranslated(), "" + this.nbUnlocked, "" + this.nbTotal);
               icon = tg.getIcon();
            }
         }

         graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, this.width(), this.height());
         if (title != null) {
            graphics.text(font, title, 30, 7, -11534256, false);
         }
         if (text != null) {
            graphics.text(font, text, 30, 18, -16777216, false);
         }
         if (icon != null) {
            graphics.item(icon, 8, 8);
         }
      } catch (Exception var9) {
         MillLog.printException(this.toString(), var9);
      }
   }

   private long getDisplayDuration() {
      return this.tradeGoods != null ? 1000L * this.tradeGoods.size() : 2000L;
   }

   @Override
   public String toString() {
      if (this.planSet != null) {
         return "Toast:" + this.planSet;
      } else if (this.villageType != null) {
         return "Toast:" + this.villageType;
      } else if (this.villagerType != null) {
         return "Toast:" + this.villagerType;
      } else if (this.tradeGood != null) {
         return "Toast:" + this.tradeGood;
      } else {
         return this.tradeGoods != null ? "Toast:" + this.tradeGoods : "Toast:no data";
      }
   }
}
