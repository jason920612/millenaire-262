package org.millenaire.common.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.millenaire.client.book.TextBook;
import org.millenaire.client.gui.DisplayActions;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;

/**
 * Parchment/scroll item — opens a text panel, or (for the village scroll, t==4) the village book GUI.
 * 1.12 used {@code onItemRightClick}→ now {@code use(Level,Player,InteractionHand)} returning
 * {@link InteractionResult}; the village-position payload moved from stack NBT
 * ({@code getTagCompound}) to the {@link CustomData} component; the custom display name moved from
 * {@code getItemStackDisplayName} to {@code getName(ItemStack)}.
 *
 * <p>NOTE: 1.12 set {@code maxStackSize=1} and {@code setCreativeTab(null)} (to hide obsolete
 * parchments) post-construction; both are now construction-time Properties and are dropped here (the
 * stack-size cap and creative-tab hiding are TODO — pass through a richer ItemMill ctor when MillItems
 * is rewritten).
 */
public class ItemParchment extends ItemMill {
   private static final String NBT_VILLAGE_POS = "village_pos_";
   public static final int NORMAN_VILLAGERS = 1;
   public static final int NORMAN_BUILDINGS = 2;
   public static final int NORMAN_ITEMS = 3;
   public static final int VILLAGE_BOOK = 4;
   public static final int INDIAN_VILLAGERS = 5;
   public static final int INDIAN_BUILDINGS = 6;
   public static final int INDIAN_ITEMS = 7;
   public static final int MAYAN_VILLAGERS = 9;
   public static final int MAYAN_BUILDINGS = 10;
   public static final int MAYAN_ITEMS = 11;
   public static final int JAPANESE_VILLAGERS = 16;
   public static final int JAPANESE_BUILDINGS = 17;
   public static final int JAPANESE_ITEMS = 18;
   public static final int SADHU = 15;
   private final int[] textsId;

   public static ItemStack createParchmentForVillage(Building townHall) {
      ItemStack parchment = new ItemStack(MillItems.PARCHMENT_VILLAGE_SCROLL);
      CompoundTag compound = new CompoundTag();
      townHall.getPos().write(compound, NBT_VILLAGE_POS);
      parchment.set(DataComponents.CUSTOM_DATA, CustomData.of(compound));
      return parchment;
   }

   public ItemParchment(String itemName, int t, boolean obsolete) {
      this(itemName, new int[]{t}, obsolete);
   }

   public ItemParchment(String itemName, int[] tIds, boolean obsolete) {
      // 1.12 set maxStackSize=1; that is now Properties.stacksTo(1) at construction.
      super(itemName, new net.minecraft.world.item.Item.Properties().stacksTo(1));
      this.textsId = tIds;
      // NOTE: 1.12 hid obsolete parchments from the creative tab via setCreativeTab(null). Creative-tab
      // membership is now decided by ItemGroupEvents in the mod initializer (MillRegistry), so an
      // obsolete parchment is simply not added to the tab there rather than flagged on the item.
   }

   private static CompoundTag tag(ItemStack stack) {
      return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
   }

   private void displayVillageBook(Player player, ItemStack is) {
      if (!player.level().isClientSide()) {
         Point p = Point.read(tag(is), NBT_VILLAGE_POS);
         Building townHall = Mill.getMillWorld(player.level()).getBuilding(p);
         if (townHall == null) {
            ServerSender.sendTranslatedSentence(player, '6', "panels.invalidid");
         } else {
            // 1.12 only opened the book if the target chunk was already loaded (func_177410_o), else
            // "too far". 26.2 Level.getChunk NEVER returns null — it force-generates the chunk synchronously
            // (server hitch + the "too far" branch became dead code). Use the non-loading hasChunk so a far
            // village correctly reports "too far" instead of force-loading it.
            if (!player.level().hasChunk(p.getChunkX(), p.getChunkZ())) {
               ServerSender.sendTranslatedSentence(player, '6', "panels.toofar");
            } else if (!townHall.isActive) {
               ServerSender.sendTranslatedSentence(player, '6', "panels.toofar");
            } else {
               ServerSender.displayVillageBookGUI(player, p);
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   @Override
   public Component getName(ItemStack stack) {
      Component base = super.getName(stack);
      if (this.textsId[0] == 4 && stack.has(DataComponents.CUSTOM_DATA)) {
         Point p = Point.read(tag(stack), NBT_VILLAGE_POS);
         if (p != null) {
            Building townHall = Mill.getMillWorld(Minecraft.getInstance().level).getBuilding(p);
            if (townHall != null) {
               return Component.literal(base.getString() + ": " + townHall.getVillageQualifiedName());
            }
         }
      }

      return base;
   }

   @Override
   public InteractionResult use(Level world, Player entityplayer, InteractionHand handIn) {
      ItemStack itemstack = entityplayer.getItemInHand(handIn);
      if (this.textsId[0] == 4) {
         if (!world.isClientSide()) {
            this.displayVillageBook(entityplayer, itemstack);
         }

         return InteractionResult.SUCCESS;
      } else {
         if (world.isClientSide()) {
            if (this.textsId.length == 1) {
               List<List<String>> parchment = LanguageUtilities.getParchment(this.textsId[0]);
               if (parchment != null) {
                  TextBook book = TextBook.convertStringsToBook(parchment);
                  DisplayActions.displayParchmentPanelGUI(entityplayer, book, null, 0, true);
               } else {
                  Mill.proxy.localTranslatedSentence(entityplayer, '6', "panels.notextfound", "" + this.textsId[0]);
               }
            } else {
               List<List<String>> combinedText = new ArrayList<>();

               for (int i = 0; i < this.textsId.length; i++) {
                  List<List<String>> parchment = LanguageUtilities.getParchment(this.textsId[i]);
                  if (parchment != null) {
                     combinedText.addAll(parchment);
                  }
               }

               TextBook book = TextBook.convertStringsToBook(combinedText);
               DisplayActions.displayParchmentPanelGUI(entityplayer, book, null, 0, true);
            }
         }

         return InteractionResult.SUCCESS;
      }
   }
}
