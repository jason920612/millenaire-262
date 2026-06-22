package org.millenaire.common.convert;
import org.millenaire.common.utilities.MillFiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.millenaire.MillHeadlessTest;

/**
 * Golden tests for the canonical encoding-detecting content-file reader
 * ({@link MillConvert#contentFileToReader(ContentFile)}), which {@code MillFiles.getReader}
 * now delegates to. Proves the two real-world cases Millénaire content hits:
 * <ul>
 *   <li>modern / translation files are UTF-8 (multibyte: "café", CJK) — must round-trip exactly;</li>
 *   <li>legacy author files are Windows-1252 (single-byte accents like ü=0xFC that are INVALID UTF-8) —
 *       must fall back so "Türkoglu" reads back intact rather than as the U+FFFD replacement char.</li>
 * </ul>
 */
class ContentFileReaderTest extends MillHeadlessTest {

   private static String readAll(File file) throws IOException {
      try (BufferedReader reader = MillConvert.contentFileToReader(new ContentFile(file))) {
         StringBuilder sb = new StringBuilder();
         String line;
         boolean first = true;
         while ((line = reader.readLine()) != null) {
            if (!first) {
               sb.append('\n');
            }
            sb.append(line);
            first = false;
         }
         return sb.toString();
      }
   }

   @Test
   void readsUtf8MultibyteContent() throws IOException {
      // "café" (é = 2-byte UTF-8) plus a CJK char on a second line; round-trips via strict UTF-8.
      String expected = "café\n村民";
      Path tmp = Files.createTempFile("millconvert-utf8", ".txt");
      try {
         Files.write(tmp, expected.getBytes(StandardCharsets.UTF_8));
         assertEquals(expected, readAll(tmp.toFile()));
      } finally {
         Files.deleteIfExists(tmp);
      }
   }

   @Test
   void readsWindows1252FallbackContent() throws IOException {
      // "Türkoglu" written as Windows-1252 bytes: ü = 0xFC, which is INVALID UTF-8, so strict UTF-8 fails
      // and the reader must fall back to Windows-1252 — yielding the real text, not U+FFFD ('�').
      String expected = "Türkoglu";
      byte[] win1252 = expected.getBytes(Charset.forName("windows-1252"));
      Path tmp = Files.createTempFile("millconvert-win1252", ".txt");
      try {
         Files.write(tmp, win1252);
         String actual = readAll(tmp.toFile());
         assertEquals(expected, actual);
         org.junit.jupiter.api.Assertions.assertFalse(actual.contains("�"),
            "fallback must not produce the U+FFFD replacement char");
      } finally {
         Files.deleteIfExists(tmp);
      }
   }
}
