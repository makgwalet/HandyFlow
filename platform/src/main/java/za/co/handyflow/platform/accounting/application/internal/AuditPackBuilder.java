package za.co.handyflow.platform.accounting.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Turns the schedules of the financial audit pack into a ZIP of CSV files and a manifest. It knows nothing about the ledger: given the
 * same tables it produces the same files, byte for byte, and the same fingerprint. Only the manifest carries the time of the run.
 */
public final class AuditPackBuilder {
    private AuditPackBuilder() {}

    /** One schedule. Rows must already be in a fixed order. */
    public record Table(String fileName, String description, List<String> header, List<List<String>> rows) {}
    public record FileEntry(String name, String description, int rows, String sha256) {}
    public record Pack(byte[] zip, String manifestJson, String packSha256, List<FileEntry> files) {}

    private static final long FIXED_ZIP_TIME = 0L;
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
            .findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static Pack build(Map<String, Object> parameters, List<Table> tables, List<String> notIncluded, List<String> notes,
                             Instant generatedAt, String generatedBy) {
        Map<String, byte[]> contents = new LinkedHashMap<>();
        List<FileEntry> files = new ArrayList<>();
        for (Table t : tables) {
            byte[] bytes = csv(t).getBytes(StandardCharsets.UTF_8);
            contents.put(t.fileName(), bytes);
            files.add(new FileEntry(t.fileName(), t.description(), t.rows().size(), sha256(bytes)));
        }
        String packSha = packFingerprint(files);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("generatedAt", generatedAt.toString());
        manifest.put("generatedBy", generatedBy);
        manifest.put("parameters", parameters);
        manifest.put("files", files);
        manifest.put("packSha256", packSha);
        manifest.put("notes", notes);
        manifest.put("notIncluded", notIncluded);
        String manifestJson;
        try { manifestJson = JSON.writeValueAsString(manifest); } catch (IOException e) { throw new IllegalStateException(e); }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (var e : contents.entrySet()) put(zip, e.getKey(), e.getValue());
            put(zip, "manifest.json", manifestJson.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) { throw new IllegalStateException(e); }
        return new Pack(out.toByteArray(), manifestJson, packSha, files);
    }

    /** Fingerprint of the content only: the files' names and hashes, in name order. Independent of when the pack was made. */
    public static String packFingerprint(List<FileEntry> files) {
        StringBuilder sb = new StringBuilder();
        files.stream().sorted(Comparator.comparing(FileEntry::name)).forEach(f -> sb.append(f.name()).append(':').append(f.sha256()).append('\n'));
        return sha256(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(FIXED_ZIP_TIME);
        zip.putNextEntry(e); zip.write(bytes); zip.closeEntry();
    }

    static String csv(Table t) {
        StringBuilder sb = new StringBuilder("﻿");
        line(sb, t.header());
        for (List<String> r : t.rows()) line(sb, r);
        return sb.toString();
    }

    private static void line(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) { if (i > 0) sb.append(','); sb.append(cell(cells.get(i))); }
        sb.append("\r\n");
    }

    /**
     * A cell that a spreadsheet would read as a formula (starts with = + - @) is prefixed with an apostrophe so it stays text.
     * Plain numbers, including negative ones, are left alone.
     */
    static String cell(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !isNumber(s)) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private static boolean isNumber(String s) {
        try { new java.math.BigDecimal(s); return true; } catch (NumberFormatException e) { return false; }
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
