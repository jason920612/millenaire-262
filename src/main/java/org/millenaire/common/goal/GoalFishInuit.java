package org.millenaire.common.goal;

import net.minecraft.world.level.block.Blocks;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.utilities.MillRandom;

/**
 * Inuit fishing — O4 player-like refactor.
 *
 * <p>Like {@link GoalFish}, the catch itself is now the REAL {@code BuiltInLootTables.FISHING} loot the villager
 * casts for and picks up (full vanilla bobber animation). This subclass adds back ONLY the 1.12 culture-specific
 * bonus on a successful catch: a 1/4 chance of a {@code BONE_BLOCK} (1.12 {@code GoalFishInuit.addFishResults}). The
 * old fixed {@code 1× COD} is dropped because the real FISHING loot already yields fish — net intent preserved
 * (Inuit fishing brings in fish plus the occasional bone block).
 */
@DocumentedElement.Documentation("Extension of basic fishing goal that also brings in bone block.")
public class GoalFishInuit extends GoalFish {
   @Override
   protected void addFishResults(MillVillager villager) {
      if (MillRandom.chanceOn(4)) {
         villager.addToInv(Blocks.BONE_BLOCK, 1);
      }
   }
}
