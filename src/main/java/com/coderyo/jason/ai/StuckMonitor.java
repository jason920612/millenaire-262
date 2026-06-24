package com.coderyo.jason.ai;

import org.millenaire.common.entity.MillVillager;

/**
 * Range / trajectory based STUCK detector for the villager NewAI navigation (user spec, 2026-06-24):
 *
 * <p>"Stuck" must NOT be decided from a single point ("hasn't left this block"). It must look at a WINDOW of the
 * villager's recent TRAJECTORY: if — while the villager is actively trying to travel — its recent positions stay
 * CONFINED to a small region, or it keeps moving over nearly-the-same path with almost no NET progress (pacing
 * back-and-forth, circling, yaw-spinning in place), that is stuck. A villager genuinely travelling sweeps a large
 * extent with large net displacement and is never flagged.
 *
 * <p>Usage: one monitor per villager. Call {@link #tick(MillVillager)} once per AI step with the live position
 * (only while it is actively pathing — the caller should not feed it while the villager is performing a
 * stationary action, or it would look "confined" by design). Consult {@link #isStuck()}; call {@link #reset()}
 * after a successful re-path / on arrival so the recovery isn't immediately re-flagged. New code under
 * {@code com.coderyo.jason}.
 */
public final class StuckMonitor {

   /** History length in ticks (~6s at 20 tps): confinement sustained this long while pathing = stuck. */
   public static final int WINDOW_TICKS = 120;
   /** Store one position every SAMPLE_PERIOD ticks → {@code WINDOW_TICKS / SAMPLE_PERIOD} samples. */
   public static final int SAMPLE_PERIOD = 4;
   private static final int CAPACITY = WINDOW_TICKS / SAMPLE_PERIOD; // 30 samples
   /** Don't render a verdict until the window has at least this many samples (avoid early false positives). */
   private static final int MIN_SAMPLES = 14;

   /** Max horizontal extent (blocks) of the sampled window under which it counts as CONFINED (stationary / tiny
    * circle / spin all keep the spread this small). */
   public static final double CONFINED_EXTENT = 2.5;
   /** Vertical extent (blocks) above which the villager is CLIMBING/DESCENDING — i.e. doing real vertical work
    * (scaffolding up a tree to chop, mining a shaft) — NOT stuck, even if its horizontal footprint is small. A
    * genuinely stuck villager (pacing / circling / spinning on the flat) keeps BOTH its xz AND its y confined. */
   public static final double CLIMBING_Y_EXTENT = 1.5;
   /** Pacing/looping: NET displacement below this over the window... */
   public static final double LOOP_NET_DISP = 2.0;
   /** ...while the PATH actually traversed is at least this multiple of the net displacement (moved a lot, went
    * nowhere — the signature of back-and-forth pacing / circling). */
   public static final double LOOP_TRAVERSED_RATIO = 3.0;

   private final double[] x = new double[CAPACITY];
   private final double[] y = new double[CAPACITY];
   private final double[] z = new double[CAPACITY];
   private int head;          // index of the next write (oldest sample, once full)
   private int count;         // total samples written (saturates conceptually; valid = min(count, CAPACITY))
   private int sinceSample;

   /** Feed the villager's CURRENT position; samples are decimated to one per {@link #SAMPLE_PERIOD} ticks. */
   public void tick(MillVillager v) {
      if (++sinceSample < SAMPLE_PERIOD) {
         return;
      }
      sinceSample = 0;
      x[head] = v.getX();
      y[head] = v.getY();
      z[head] = v.getZ();
      head = (head + 1) % CAPACITY;
      count++;
   }

   private int validSamples() {
      return Math.min(count, CAPACITY);
   }

   /** Chronological index i (0 = oldest) into the ring buffer. */
   private int chrono(int i, int valid) {
      // when full, oldest is at head; when not full, samples are at [0, count) and oldest is index 0.
      int start = count >= CAPACITY ? head : 0;
      return (start + i) % CAPACITY;
   }

   /**
    * @return true iff the recent trajectory shows the villager is stuck: either CONFINED to a small region, or
    *     PACING/LOOPING (large path traversed but tiny net displacement). False while there are too few samples.
    */
   public boolean isStuck() {
      int valid = validSamples();
      if (valid < MIN_SAMPLES) {
         return false;
      }
      double minX = Double.MAX_VALUE;
      double maxX = -Double.MAX_VALUE;
      double minZ = Double.MAX_VALUE;
      double maxZ = -Double.MAX_VALUE;
      double minY = Double.MAX_VALUE;
      double maxY = -Double.MAX_VALUE;
      double traversed = 0.0;
      double px = 0.0;
      double pz = 0.0;
      for (int i = 0; i < valid; i++) {
         int idx = chrono(i, valid);
         double cx = x[idx];
         double cz = z[idx];
         minX = Math.min(minX, cx);
         maxX = Math.max(maxX, cx);
         minZ = Math.min(minZ, cz);
         maxZ = Math.max(maxZ, cz);
         minY = Math.min(minY, y[idx]);
         maxY = Math.max(maxY, y[idx]);
         if (i > 0) {
            double dx = cx - px;
            double dz = cz - pz;
            traversed += Math.sqrt(dx * dx + dz * dz);
         }
         px = cx;
         pz = cz;
      }
      // CLIMBING/DESCENDING = real vertical work (scaffolding up a tree to chop, sinking a mine shaft), NOT stuck —
      // even though such work keeps the HORIZONTAL footprint small. Only a villager confined in BOTH xz AND y is
      // genuinely going nowhere. (Without this, a tree-chopper scaffolding upward looks "confined" and gets its
      // navigation yanked out from under it mid-work.)
      if (maxY - minY >= CLIMBING_Y_EXTENT) {
         return false;
      }
      double extent = Math.max(maxX - minX, maxZ - minZ);
      if (extent < CONFINED_EXTENT) {
         return true; // stationary shuffle / tiny circle / spin: the whole window fits in a tiny box.
      }
      // pacing / looping: it travelled a long path but ended up near where it started.
      int oldest = chrono(0, valid);
      int newest = chrono(valid - 1, valid);
      double ndx = x[newest] - x[oldest];
      double ndz = z[newest] - z[oldest];
      double net = Math.sqrt(ndx * ndx + ndz * ndz);
      return net < LOOP_NET_DISP && traversed > LOOP_TRAVERSED_RATIO * Math.max(0.5, net);
   }

   /** A short human-readable reason for the current verdict (diagnostics / E2E evidence). */
   public String describe() {
      int valid = validSamples();
      if (valid < MIN_SAMPLES) {
         return "samples=" + valid + " (<" + MIN_SAMPLES + ", no verdict)";
      }
      return "samples=" + valid + " stuck=" + isStuck();
   }

   /** Forget the history (after a successful re-path / on arrival) so recovery isn't instantly re-flagged. */
   public void reset() {
      head = 0;
      count = 0;
      sinceSample = 0;
   }
}
