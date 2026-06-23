package org.millenaire.common.advancements;

import net.minecraft.resources.Identifier;

// 26.2: The 1.12 advancement-trigger system (ICriterionTrigger / AbstractCriterionInstance /
// PlayerAdvancements.Listener / CriteriaTriggers) was replaced by a datapack JSON model. Millénaire's
// custom triggers were all "always true once the code path runs", so the datapack advancements
// (data/millenaire/advancement/*.json) use the built-in `minecraft:impossible` trigger and are awarded
// by name from GenericAdvancement.grant() (PlayerAdvancements.award). No custom Codec-based trigger is
// therefore needed, and this class remains a lightweight inert value object preserving the old public
// surface (alongside the ServerSender advancement-earned message + stat tracking that grant() also does).
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
