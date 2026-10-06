package za.co.handyflow.platform.compliancetender.application.internal.submission;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * What exactly went into a package: one entry per file in package order, plus a hash over the entries.
 * The hash covers section, name, size and content hash of each file in order and nothing else (no
 * timestamps), so the same content in the same order always gives the same package hash, and any change
 * to any file, its name or its position changes it.
 */
public record PackageManifest(List<Entry> entries, long totalBytes, String packageHash) {

    public record Entry(int position, String sectionKey, String fileName, long sizeBytes, String sha256, Integer pages) {}

    public static PackageManifest of(List<PackageFile> orderedFiles) {
        java.util.ArrayList<Entry> entries = new java.util.ArrayList<>();
        StringBuilder canonical = new StringBuilder();
        long total = 0;
        int position = 1;
        for (PackageFile file : orderedFiles) {
            entries.add(new Entry(position, file.sectionKey(), file.fileName(), file.sizeBytes(), file.sha256(), file.pages()));
            canonical.append(position).append('|').append(file.sectionKey()).append('|').append(file.fileName())
                    .append('|').append(file.sizeBytes()).append('|').append(file.sha256()).append('\n');
            total += file.sizeBytes();
            position++;
        }
        return new PackageManifest(List.copyOf(entries), total, sha256Hex(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    }

    public static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in this JVM.", e);
        }
    }
}
