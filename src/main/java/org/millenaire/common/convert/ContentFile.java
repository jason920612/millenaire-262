package org.millenaire.common.convert;

import java.io.File;

/**
 * A Millénaire content file (culture name lists, sentences, dialogues, plans, …). Wrapping the raw
 * {@link File} gives {@link MillConvert#contentFileToReader(ContentFile)} an explicit type to attach
 * the UTF-8 / Windows-1252 encoding detection to.
 *
 * <p>M0 of the unified conversion protocol.</p>
 */
public record ContentFile(File file) {
}
