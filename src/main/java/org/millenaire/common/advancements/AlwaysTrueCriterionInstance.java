package org.millenaire.common.advancements;

import net.minecraft.resources.Identifier;

// 26.2: The 1.12 advancement-trigger system (ICriterionTrigger / AbstractCriterionInstance /
// PlayerAdvancements.Listener / CriteriaTriggers) was replaced by a codec-driven
// SimpleCriterionTrigger + datapack JSON model. Faithfully reproducing Millénaire's custom
// triggers requires registered Codec-based triggers plus advancement JSON in the datapack
// (TODO: architectural reimplementation). For now this is a lightweight value object that
// preserves the old public surface so the rest of the mod compiles and the visible
// behaviour (ServerSender advancement-earned message + stat tracking) keeps working.
public class AlwaysTrueCriterionInstance {
   private final Identifier id;

   public AlwaysTrueCriterionInstance(Identifier rl) {
      this.id = rl;
   }

   public Identifier getId() {
      return this.id;
   }

   public boolean test() {
      return true;
   }
}
