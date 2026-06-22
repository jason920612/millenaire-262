package org.millenaire.common.deployer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.commons.io.IOUtils;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.utilities.MillFiles;
import org.millenaire.common.utilities.MillCrash;

public class ContentDeployer {
   private static final String DEV_VERSION_NUMBER = "@VERSION@";

   private static void copyFolder(String modJarPath, String deployLocation, String folder, File destDir) throws IOException {
      if (!destDir.exists() && !destDir.mkdir()) {
         Mill.LOGGER.warn("Failed to create dest dir");
      }

      try (JarFile file = new JarFile(modJarPath)) {
         Enumeration<JarEntry> e = file.entries();

         while (e.hasMoreElements()) {
            JarEntry entry = e.nextElement();
            String jarEntryName = entry.getName();
            if (jarEntryName.startsWith(deployLocation + folder)) {
               File destination = new File(destDir, jarEntryName.substring(deployLocation.length(), jarEntryName.length()));
               if (entry.isDirectory()) {
                  if (!destination.mkdirs()) {
                     Mill.LOGGER.warn("Failed to create dest dirs");
                  }
               } else {
                  try (InputStream stream = file.getInputStream(entry);
                       OutputStream out = new FileOutputStream(destination)) {
                     IOUtils.copy(stream, out);
                  }
               }
            }
         }
      }
   }

   /**
    * Fingerprint of the millenaire/ content packaged in the jar. Computed from each jar entry's
    * name + uncompressed size + CRC-32 (all available from JarEntry metadata, so no stream reads).
    * Written to millenaire/content-hash.txt on deploy and compared on the next launch so that ANY
    * content change (e.g. a corrected blocklist.txt) forces a redeploy, even when the mod version
    * string is unchanged. Upstream 1.12.2 only gated on the version string, which silently served a
    * stale deployed folder whenever content was fixed without a version bump (the byzantines
    * architect_A NPE: a stale blocklist.txt with 1.12 block names was never overwritten).
    */
   private static String computeContentFingerprint(String modJarPath, String deployLocation, String folder) throws IOException {
      try (JarFile file = new JarFile(modJarPath)) {
         java.util.TreeMap<String, String> sorted = new java.util.TreeMap<>();
         Enumeration<JarEntry> e = file.entries();
         while (e.hasMoreElements()) {
            JarEntry entry = e.nextElement();
            String jarEntryName = entry.getName();
            if (jarEntryName.startsWith(deployLocation + folder) && !entry.isDirectory()) {
               // exclude the bookkeeping files we write ourselves so they don't perturb the hash
               if (jarEntryName.endsWith("/version.txt") || jarEntryName.endsWith("/content-hash.txt")) {
                  continue;
               }
               sorted.put(jarEntryName, entry.getSize() + ":" + entry.getCrc());
            }
         }

         StringBuilder sb = new StringBuilder();
         for (java.util.Map.Entry<String, String> ent : sorted.entrySet()) {
            sb.append(ent.getKey()).append('=').append(ent.getValue()).append('\n');
         }

         try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
               hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
         } catch (java.security.NoSuchAlgorithmException sha1Missing) {
            throw new IOException("SHA-1 unavailable for content fingerprint", sha1Missing);
         }
      }
   }

   public static void deployContent(File ourJar) {
      if (!ContentDeployer.class.getResource("ContentDeployer.class").toString().startsWith("jar")) {
         Mill.LOGGER.warn("No need to redeploy Millénaire as we are in a dev environment.");
      } else {
         File modsDir = MillFiles.getModsDir();

         try {
            boolean redeployMillenaire = false;
            File millenaireDir = new File(modsDir, "millenaire");
            String packagedHash = computeContentFingerprint(ourJar.getAbsolutePath(), "todeploy/", "millenaire/");
            if ("8.1.2".equals("@VERSION@")) {
               redeployMillenaire = true;
               Mill.LOGGER.warn("Deploying millenaire/ as we are using a dev version and can't test whether it has changed.");
            } else if (!millenaireDir.exists()) {
               redeployMillenaire = true;
               Mill.LOGGER.warn("Deploying millenaire/ to version 8.1.2 as it can't be found.");
            } else {
               File versionFile = new File(millenaireDir, "version.txt");
               if (!versionFile.exists()) {
                  redeployMillenaire = true;
                  MillFiles.deleteDir(millenaireDir);
                  Mill.LOGGER.warn("Redeploying millenaire/ to version 8.1.2 as it has no version file.");
               } else {
                  BufferedReader reader = MillFiles.getReader(versionFile);
                  String versionString = reader.readLine();
                  if (!versionString.equals("8.1.2")) {
                     redeployMillenaire = true;
                     MillFiles.deleteDir(millenaireDir);
                     Mill.LOGGER.warn("Redeploying millenaire/ to version 8.1.2 as it has version " + versionString + ".");
                  } else {
                     // Version matches: also compare the content fingerprint. A content-only fix (same version)
                     // must still refresh the deployed folder, otherwise a stale blocklist.txt/plan set is served.
                     File hashFile = new File(millenaireDir, "content-hash.txt");
                     String deployedHash = null;
                     if (hashFile.exists()) {
                        try (BufferedReader hashReader = MillFiles.getReader(hashFile)) {
                           deployedHash = hashReader.readLine();
                        }
                     }

                     if (!packagedHash.equals(deployedHash)) {
                        redeployMillenaire = true;
                        MillFiles.deleteDir(millenaireDir);
                        Mill.LOGGER
                           .warn(
                              "Redeploying millenaire/ at version "
                                 + versionString
                                 + ": packaged content fingerprint ("
                                 + packagedHash
                                 + ") differs from deployed ("
                                 + deployedHash
                                 + ")."
                           );
                     } else {
                        Mill.LOGGER
                           .warn("No need to redeploy Millénaire as the millenaire folder is already at version " + versionString + " with matching content.");
                     }
                  }
               }
            }

            if (redeployMillenaire) {
               try {
                  long startTime = System.currentTimeMillis();
                  copyFolder(ourJar.getAbsolutePath(), "todeploy/", "millenaire/", modsDir);
                  Files.write(Paths.get(modsDir.getAbsolutePath() + "/millenaire/version.txt"), "8.1.2".getBytes());
                  Files.write(Paths.get(modsDir.getAbsolutePath() + "/millenaire/content-hash.txt"), packagedHash.getBytes());
                  Mill.LOGGER.warn("Deployed millenaire folder in " + (System.currentTimeMillis() - startTime) + " ms.");
               } catch (IOException deployException) {
                  // FAIL-FAST: a failed millenaire/ deploy leaves the content folder absent/partial, so every
                  // culture/building/language load later finds nothing and NPEs. Crash at the deploy failure.
                  throw MillCrash.fail("Registry", "failed to deploy millenaire/ content: " + deployException);
               }
            }
         } catch (IllegalStateException crash) {
            throw crash; // already a fail-fast crash from the deploy step; propagate unchanged
         } catch (Exception deployCheckException) {
            // FAIL-FAST: the millenaire/ version/redeploy check threw; the content folder is left in an
            // unknown state. Crash instead of silently continuing with possibly-stale or missing content.
            throw MillCrash.fail("Registry", "failed to deploy/check millenaire/ content: " + deployCheckException);
         }

         try {
            File millenaireCustomDir = new File(modsDir, "millenaire-custom");
            if (!millenaireCustomDir.exists()) {
               Mill.LOGGER.warn("Deploying millenaire-custom/ .");

               try {
                  long startTime = System.currentTimeMillis();
                  copyFolder(ourJar.getAbsolutePath(), "todeploy/", "millenaire-custom/", modsDir);
                  Mill.LOGGER.warn("Deployed millenaire-custom folder in " + (System.currentTimeMillis() - startTime) + " ms.");
               } catch (IOException customDeployException) {
                  // FAIL-FAST: a partially-copied millenaire-custom/ silently breaks user submods/overrides.
                  throw MillCrash.fail("Registry", "failed to deploy millenaire-custom/ content: " + customDeployException);
               }
            }
         } catch (IllegalStateException crash) {
            throw crash; // already a fail-fast crash from the custom deploy step; propagate unchanged
         } catch (Exception customCheckException) {
            // FAIL-FAST: the millenaire-custom/ deploy check threw; crash instead of silently continuing.
            throw MillCrash.fail("Registry", "failed to deploy/check millenaire-custom/ content: " + customCheckException);
         }
      }
   }
}
