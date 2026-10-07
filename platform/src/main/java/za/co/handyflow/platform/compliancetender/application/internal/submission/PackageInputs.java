package za.co.handyflow.platform.compliancetender.application.internal.submission;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A short fingerprint of what a package was built from, one hash for each of the four parts a person can change on the tender.
 * Pure: it hashes strings it is given, so it is tested without a database. Comparing the stored one with today's says which parts
 * changed, which is how a built package is flagged stale.
 */
public record PackageInputs(String details, String requirements, String pricing, String personnel) {

    /** Hash of the given lines, order-independent so reordering rows in a query never looks like a change. */
    public static String hashOf(Collection<String> lines) {
        List<String> sorted = new ArrayList<>(lines);
        sorted.sort(String::compareTo);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String l : sorted) { md.update(l.getBytes(StandardCharsets.UTF_8)); md.update((byte) '\n'); }
            StringBuilder sb = new StringBuilder();
            byte[] d = md.digest();
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", d[i]));   // 12 hex characters is plenty to notice a change
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String encode() {
        return "details=" + details + ";requirements=" + requirements + ";pricing=" + pricing + ";personnel=" + personnel;
    }

    /** Null or unreadable input gives null: a package built before fingerprints existed is "unknown", not "stale". */
    public static PackageInputs parse(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        String d = null, r = null, p = null, k = null;
        for (String part : encoded.split(";")) {
            int eq = part.indexOf('=');
            if (eq < 0) return null;
            String key = part.substring(0, eq), value = part.substring(eq + 1);
            switch (key) {
                case "details" -> d = value;
                case "requirements" -> r = value;
                case "pricing" -> p = value;
                case "personnel" -> k = value;
                default -> { }
            }
        }
        return d == null || r == null || p == null || k == null ? null : new PackageInputs(d, r, p, k);
    }

    /** Plain-words reasons the package no longer matches the tender; empty when it still does or when it cannot be known. */
    public static List<String> changes(PackageInputs built, PackageInputs now) {
        List<String> out = new ArrayList<>();
        if (built == null || now == null) return out;
        if (!built.details.equals(now.details)) out.add("The tender details changed");
        if (!built.requirements.equals(now.requirements)) out.add("The requirements changed");
        if (!built.pricing.equals(now.pricing)) out.add("The pricing changed");
        if (!built.personnel.equals(now.personnel)) out.add("The key personnel changed");
        return out;
    }
}
