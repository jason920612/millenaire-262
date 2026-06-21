package org.millenaire;

import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for headless Mill unit tests. Boots the Minecraft registries ONCE so that Mill classes
 * whose static initialisers touch {@code BuiltInRegistries} (Building, MillVillager, items, blocks, …)
 * can be loaded/mocked in a plain JUnit JVM with no game client. Subclasses run via {@code gradlew test}
 * — fully controllable, no window. References the decompiled 1.12 source for intended behaviour and
 * mc-sources for the 26.2 API.
 */
public abstract class MillHeadlessTest {

   private static boolean bootstrapped = false;

   @BeforeAll
   static void bootstrapMinecraft() {
      if (bootstrapped) {
         return;
      }
      net.minecraft.SharedConstants.tryDetectVersion();
      net.minecraft.server.Bootstrap.bootStrap();
      // Register Mill blocks/items too: some Mill classes' static init (e.g. InvItem.freeGoods) reference
      // mod blocks like MillBlocks.EARTH_DECORATION, which are null until registered. Best-effort — if the
      // registries are frozen in this harness it throws and individual tests that need it skip via assume.
      try {
         org.millenaire.common.block.MillBlocks.register();
         org.millenaire.common.item.MillItems.register();
         org.millenaire.common.block.MillBlocks.registerBlockItems();
      } catch (Throwable t) {
         System.out.println("[MillHeadlessTest] Mill content registration skipped: " + t);
      }
      bootstrapped = true;
   }
}
