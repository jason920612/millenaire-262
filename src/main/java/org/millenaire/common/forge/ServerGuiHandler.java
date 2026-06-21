package org.millenaire.common.forge;

/**
 * In 1.12 this was the Forge {@code IGuiHandler} that built server-side {@code Container}s by GUI id.
 *
 * <p>26.2: Mill does NOT use the vanilla {@code player.openMenu(MenuProvider)} flow. It keeps its own
 * open-GUI protocol: {@code ServerSender.display*GUI} writes a {@code PACKET_OPENGUI(104)} packet
 * carrying one of the int GUI ids below (plus the per-GUI payload), and the client's
 * {@code ClientReceiver.readGUIPacket} builds the Screen/Menu via {@code ClientGuiHandler}. This class
 * therefore only holds the int GUI id constants that both ends share (the building/trade/quest/puja/
 * fire-pit/etc. menus all open through that path), so nothing needs re-wiring to MenuProviders here.
 */
public class ServerGuiHandler {
   public static final int GUI_MILL_CHEST = 1;
   public static final int GUI_TRADE = 2;
   public static final int GUI_QUEST = 3;
   public static final int GUI_VILLAGECHIEF = 4;
   public static final int GUI_VILLAGEBOOK = 5;
   public static final int GUI_PUJAS = 6;
   public static final int GUI_PANEL = 7;
   public static final int GUI_MERCHANT = 8;
   public static final int GUI_NEGATIONWAND = 9;
   public static final int GUI_NEWBUILDING = 10;
   public static final int GUI_CONTROLLEDPROJECTPANEL = 11;
   public static final int GUI_HIRE = 12;
   public static final int GUI_NEWVILLAGE = 13;
   public static final int GUI_CONTROLLEDMILITARYPANEL = 14;
   public static final int GUI_IMPORTTABLE = 15;
   public static final int GUI_FIRE_PIT = 16;

}
