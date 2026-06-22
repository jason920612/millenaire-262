package org.millenaire.common.utilities;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.millenaire.common.convert.ContentFile;
import org.millenaire.common.convert.MillConvert;

/**
 * Coherent file/directory IO unit extracted from the former MillCommonUtilities god-object.
 * Pure relocation: every method and field below was moved verbatim from MillCommonUtilities
 * with identical signatures, logic and results. getReader stays the public entry point and
 * forwards to the unified conversion protocol (MillConvert.contentFileToReader).
 */
public class MillFiles {
   private static File baseDir = null;
   private static File customDir = null;

   public static boolean deleteDir(File dir) {
      if (dir.isDirectory()) {
         String[] children = dir.list();

         for (int i = 0; i < children.length; i++) {
            boolean success = deleteDir(new File(dir, children[i]));
            if (!success) {
               return false;
            }
         }
      }

      return dir.delete();
   }

   public static BufferedWriter getAppendWriter(File file) throws UnsupportedEncodingException, FileNotFoundException {
      FileOutputStream fos = new FileOutputStream(file, true);

      try {
         return new BufferedWriter(new OutputStreamWriter(fos, "UTF8"));
      } catch (UnsupportedEncodingException | RuntimeException var3) {
         try {
            fos.close();
         } catch (IOException var2) {
         }

         throw var3;
      }
   }

   public static File getBuildingsDir(Level world) {
      File saveDir = getWorldSaveDir(world);
      File millenaireDir = new File(saveDir, "millenaire");
      if (!millenaireDir.exists()) {
         millenaireDir.mkdir();
      }

      File buildingsDir = new File(millenaireDir, "buildings");
      if (!buildingsDir.exists()) {
         buildingsDir.mkdir();
      }

      return buildingsDir;
   }

   public static File getExportDir() {
      File exportDir = new File(getMillenaireCustomContentDir(), "exports");
      if (!exportDir.exists()) {
         exportDir.mkdirs();
      }

      return exportDir;
   }

   public static List<String> getFileLines(File file) throws IOException {
      List<String> lines = new ArrayList<>();

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF8"))) {
         for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            lines.add(line);
         }
      }

      return lines;
   }

   public static File getMillenaireContentDir() {
      if (baseDir == null) {
         baseDir = new File(getModsDir(), "millenaire");
      }

      return baseDir;
   }

   public static File getMillenaireCustomContentDir() {
      if (customDir == null) {
         customDir = new File(getModsDir(), "millenaire-custom");
      }

      return customDir;
   }

   public static File getMillenaireHelpDir() {
      return new File(getMillenaireContentDir(), "help");
   }

   public static File getModsDir() {
      return new File(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toFile(), "mods");
   }

   public static BufferedReader getReader(File file) throws IOException {
      // Single canonical implementation of the UTF-8 → Windows-1252 encoding detection now lives in the
      // unified conversion protocol; getReader stays the public entry point and forwards to it.
      return MillConvert.contentFileToReader(new ContentFile(file));
   }

   public static File getWorldSaveDir(Level world) {
      MinecraftServer server = world.getServer();
      return server != null ? server.getWorldPath(LevelResource.ROOT).toFile() : null;
   }

   public static BufferedWriter getWriter(File file) throws UnsupportedEncodingException, FileNotFoundException {
      FileOutputStream fos = new FileOutputStream(file);

      try {
         return new BufferedWriter(new OutputStreamWriter(fos, "UTF8"));
      } catch (UnsupportedEncodingException | RuntimeException var3) {
         try {
            fos.close();
         } catch (IOException var2) {
         }

         throw var3;
      }
   }

   public static class ExtFileFilter implements FilenameFilter {
      String ext = null;

      public ExtFileFilter(String ext) {
         this.ext = ext;
      }

      @Override
      public boolean accept(File file, String name) {
         return !name.toLowerCase().endsWith("." + this.ext) ? false : !name.startsWith(".");
      }
   }

   public static class PrefixExtFileFilter implements FilenameFilter {
      String ext = null;
      String prefix = null;

      public PrefixExtFileFilter(String pref, String ext) {
         this.ext = ext;
         this.prefix = pref;
      }

      @Override
      public boolean accept(File file, String name) {
         if (!name.toLowerCase().endsWith("." + this.ext)) {
            return false;
         } else {
            return !name.toLowerCase().startsWith(this.prefix) ? false : !name.startsWith(".");
         }
      }
   }
}
