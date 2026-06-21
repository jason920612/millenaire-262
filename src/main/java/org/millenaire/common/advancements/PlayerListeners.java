package org.millenaire.common.advancements;

// 26.2: The old per-player advancement listener bookkeeping (net.minecraft.advancements.
// ICriterionTrigger.Listener + PlayerAdvancements) no longer exists in this form — advancement
// triggering is now codec/datapack driven. This class is retained only as an inert placeholder
// so GenericAdvancement keeps a stable internal shape; the real listener wiring is a TODO that
// belongs to the SimpleCriterionTrigger reimplementation. See AlwaysTrueCriterionInstance.
public class PlayerListeners {
   public void grantAndNotify() {
      // TODO: re-implement via SimpleCriterionTrigger.trigger(ServerPlayer, ...) once the
      // Millénaire advancement triggers are ported to the 26.2 codec model.
   }

   public boolean isEmpty() {
      return true;
   }
}
