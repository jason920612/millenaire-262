package org.millenaire.common.utilities;

/**
 * Fail-fast: Millénaire crashes loudly on any unhandled assumption rather than
 * degrading; see FAIL-FAST-PLAN.
 *
 * <p>Every swallowed exception, fallback default, null-tolerance, unloaded-chunk
 * guard or missing-resource default is a latent bug that hides corruption. Instead
 * of degrading gracefully, callers route the assumption through this helper so the
 * game crashes immediately with a loud, greppable marker ({@code ███ MILL}).</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 *   throw MillCrash.fail("AI", "no path");
 *   var townHall = MillCrash.need(maybeNull, "Entity", "townHall");
 *   MillCrash.check(loaded, "World", "chunk not loaded");
 * }</pre>
 */
public final class MillCrash {

   private static final String MARKER = "███ MILL ";

   private MillCrash() {
   }

   /**
    * Builds (does NOT throw) an {@link IllegalStateException} describing a fatal
    * unhandled assumption. Callers always write {@code throw MillCrash.fail(...)};
    * returning the exception (instead of throwing it here) lets the compiler see
    * the {@code throw} so flow analysis treats the call site as terminating.
    *
    * @param subsystem short subsystem tag (e.g. "AI", "World", "NBT")
    * @param detail    what assumption was violated
    * @return the exception to throw
    */
   public static RuntimeException fail(String subsystem, String detail) {
      String message = MARKER + subsystem + ": " + detail;
      IllegalStateException ex = new IllegalStateException(message);
      MillLog.error(MARKER + subsystem, detail);
      return ex;
   }

   /**
    * Returns {@code value} if non-null; otherwise crashes loudly.
    *
    * @param value     the value that must not be null
    * @param subsystem short subsystem tag
    * @param what      name of the thing that was expected to be present
    * @return {@code value}, guaranteed non-null
    */
   public static <T> T need(T value, String subsystem, String what) {
      if (value == null) {
         throw fail(subsystem, what + " was null");
      }
      return value;
   }

   /**
    * Crashes loudly unless {@code condition} holds.
    *
    * @param condition assertion that must be true
    * @param subsystem short subsystem tag
    * @param detail    what assumption was violated when false
    */
   public static void check(boolean condition, String subsystem, String detail) {
      if (!condition) {
         throw fail(subsystem, detail);
      }
   }
}
