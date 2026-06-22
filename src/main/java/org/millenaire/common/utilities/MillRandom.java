package org.millenaire.common.utilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.world.entity.player.Player;

/**
 * Shared RNG state for Millénaire, extracted from MillCommonUtilities (Phase A2).
 *
 * The single static {@link #random} instance and its seeding live here together so the
 * RNG stream is identical to before the extraction. Anything that previously read
 * {@code MillCommonUtilities.random} directly now reads {@code MillRandom.random}.
 *
 * The weighted-choice helper still references {@link MillCommonUtilities.WeightedChoice},
 * which remains a data contract in MillCommonUtilities.
 */
public class MillRandom {
   public static Random random = new Random();

   public static Random getRandom() {
      if (random == null) {
         random = new Random();
      }

      return random;
   }

   public static void initRandom(int seed) {
      random = new Random(seed);
   }

   public static boolean chanceOn(int i) {
      return getRandom().nextInt(i) == 0;
   }

   public static int randomInt(int max) {
      return getRandom().nextInt(max);
   }

   public static long randomLong() {
      return getRandom().nextLong();
   }

   public static boolean probability(double probability) {
      return getRandom().nextDouble() < probability;
   }

   public static MillCommonUtilities.WeightedChoice getWeightedChoice(List<? extends MillCommonUtilities.WeightedChoice> choices, Player player) {
      int weightTotal = 0;
      List<Integer> weights = new ArrayList<>();

      for (MillCommonUtilities.WeightedChoice choice : choices) {
         weightTotal += choice.getChoiceWeight(player);
         weights.add(choice.getChoiceWeight(player));
      }

      if (weightTotal < 1) {
         return null;
      } else {
         int random = randomInt(weightTotal);
         int count = 0;

         for (int i = 0; i < choices.size(); i++) {
            count += weights.get(i);
            if (random < count) {
               return choices.get(i);
            }
         }

         return null;
      }
   }
}
