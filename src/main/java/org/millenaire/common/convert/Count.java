package org.millenaire.common.convert;

import org.millenaire.common.utilities.MillCrash;

/**
 * A non-negative quantity. Explicit value object so a "count" can never be confused with a raw int
 * (meta, damage, rgb, …) elsewhere in the conversion protocol.
 *
 * <p>M0 of the unified conversion protocol. Fail-fast: a negative count is a programming error, not
 * a recoverable state, so the compact constructor crashes loudly via {@link MillCrash}.</p>
 */
public record Count(int value) {
   public Count {
      if (value < 0) {
         throw MillCrash.fail("Convert", "negative count " + value);
      }
   }
}
