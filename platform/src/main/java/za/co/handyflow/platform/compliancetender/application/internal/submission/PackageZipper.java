package za.co.handyflow.platform.compliancetender.application.internal.submission;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds the "numbered separate files" output: one ZIP holding every file under a position-numbered, portal-safe name. Pure. */
public final class PackageZipper {

    private PackageZipper() {}

    public record Entry(String name, byte[] content) {}

    /** "03-tax-clearance.pdf" for position 3 of "Tax Clearance (2026).PDF"; the extension is lower-cased, the stem cleaned. */
    public static String numberedName(int position, String fileName) {
        String ext = FileKind.extensionOf(fileName);
        String stem = ext.isEmpty() ? fileName : fileName.substring(0, fileName.length() - ext.length() - 1);
        return String.format(Locale.ROOT, "%02d-%s%s", position, PackageNaming.slug(stem), ext.isEmpty() ? "" : "." + ext);
    }

    public static byte[] zip(List<Entry> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            for (Entry e : entries) {
                zip.putNextEntry(new ZipEntry(e.name()));
                zip.write(e.content());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
