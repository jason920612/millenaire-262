package org.millenaire.common.advancements;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import org.millenaire.common.network.ServerSender;

// 26.2: Was `implements ICriterionTrigger<AlwaysTrueCriterionInstance>` — that interface plus
// PlayerAdvancements.Listener / CriteriaTriggers registration was removed in favour of the
// codec-based SimpleCriterionTrigger + datapack JSON model. Millénaire's triggers were all
// "always true once the code path runs" (code-triggered, not condition-evaluated), so the clean
// 26.2 equivalent is to award the matching datapack advancement's criteria directly from grant().
// grant() does exactly that here (in addition to the chat message + stat tracking) — awarding
// `millenaire:<key>` if the advancement is present in the loaded datapack, and no-opping gracefully
// when it is not (the advancement JSON migration is tracked separately; see the package note).
public class GenericAdvancement {
   private final String key;
   private final Identifier triggerRL;

   public GenericAdvancement(String key) {
      this.key = key;
      this.triggerRL = Identifier.fromNamespaceAndPath("millenaire", key);
   }

   public AlwaysTrueCriterionInstance deserializeInstance() {
      return new AlwaysTrueCriterionInstance(this.getId());
   }

   public Identifier getId() {
      return this.triggerRL;
   }

   public String getKey() {
      return this.key;
   }

   public void grant(Player player) {
      if (player instanceof ServerPlayer serverPlayer) {
         awardDatapackAdvancement(serverPlayer);
         ServerSender.sendAdvancementEarned(serverPlayer, this.key);
      }

      MillAdvancements.addToStats(player, this.key);
   }

   /**
    * Awards every still-unearned criterion of the {@code millenaire:<key>} advancement, mirroring the
    * 1.12 "code-triggered, always-true" behaviour. No-ops if the advancement is absent from the loaded
    * datapack (the advancement JSON migration is tracked separately).
    */
   private void awardDatapackAdvancement(ServerPlayer player) {
      AdvancementHolder holder = player.level().getServer().getAdvancements().get(this.triggerRL);
      if (holder == null) {
         return;
      }

      AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
      if (!progress.isDone()) {
         for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
         }
      }
   }
}
