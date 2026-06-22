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
import org.millenaire.common.utilities.MillCommonUtilities;
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

   public static void deployContent(File ourJar) {
      if (!ContentDeployer.class.getResource("ContentDeployer.class").toString().startsWith("jar")) {
         Mill.LOGGER.warn("No need to redeploy Millénaire as we are in a dev environment.");
      } else {
         File modsDir = MillCommonUtilities.getModsDir();

         try {
            boolean redeployMillenaire = false;
            File millenaireDir = new File(modsDir, "millenaire");
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
                  MillCommonUtilities.deleteDir(millenaireDir);
                  Mill.LOGGER.warn("Redeploying millenaire/ to version 8.1.2 as it has no version file.");
               } else {
                  BufferedReader reader = MillCommonUtilities.getReader(versionFile);
                  String versionString = reader.readLine();
                  if (!versionString.equals("8.1.2")) {
                     redeployMillenaire = true;
                     MillCommonUtilities.deleteDir(millenaireDir);
                     Mill.LOGGER.warn("Redeploying millenaire/ to version 8.1.2 as it has version " + versionString + ".");
                  } else {
                     Mill.LOGGER.warn("No need to redeploy Millénaire as the millenaire folder is already at vesion " + versionString + ".");
                  }
               }
            }

            if (redeployMillenaire) {
               try {
                  long startTime = System.currentTimeMillis();
                  copyFolder(ourJar.getAbsolutePath(), "todeploy/", "millenaire/", modsDir);
                  Files.write(Paths.get(modsDir.getAbsolutePath() + "/millenaire/version.txt"), "8.1.2".getBytes());
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
