package org.millenaire.common.advancements;

// 26.2: The old per-player advancement listener bookkeeping (net.minecraft.advancements.
// ICriterionTrigger.Listener + PlayerAdvancements) no longer exists in this form — advancement
// triggering is now datapack driven. Awarding is handled directly by GenericAdvancement.grant(),
// which awards the datapack advancement's criterion by name (PlayerAdvancements.award), so no
// per-player listener bookkeeping is needed. This class is retained only as an inert placeholder so
// GenericAdvancement keeps a stable internal shape. See AlwaysTrueCriterionInstance.
public class PlayerListeners {
   public void grantAndNotify() {
      // No-op: GenericAdvancement.grant() awards the datapack advancement's criterion directly.
   }

   public boolean isEmpty() {
      return true;
   }
}
