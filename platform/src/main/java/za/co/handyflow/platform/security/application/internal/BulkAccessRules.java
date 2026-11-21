package za.co.handyflow.platform.security.application.internal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Pure rules for giving many guards app access in one go. */
final class BulkAccessRules {

    enum Eligibility { PROVISION, SKIP_NOT_ACTIVE, SKIP_ALREADY_ENROLLED }

    /** BCrypt (cost 12) is deliberately slow, so one request is capped; the caller sends further batches. */
    static final int MAX_PER_REQUEST = 100;

    private BulkAccessRules() {}

    static List<UUID> normalizeIds(List<UUID> ids) {
        LinkedHashSet<UUID> out = new LinkedHashSet<>();
        if (ids != null) for (UUID id : ids) if (id != null) out.add(id);
        if (out.size() > MAX_PER_REQUEST) {
            throw new IllegalArgumentException("At most " + MAX_PER_REQUEST + " guards per request");
        }
        return new ArrayList<>(out);
    }

    static Eligibility classify(String guardStatus, boolean alreadyEnrolled, boolean resetExisting) {
        if (!"ACTIVE".equals(guardStatus)) return Eligibility.SKIP_NOT_ACTIVE;
        if (alreadyEnrolled && !resetExisting) return Eligibility.SKIP_ALREADY_ENROLLED;
        return Eligibility.PROVISION;
    }

    /** One CSV field. Quotes when needed, and defuses spreadsheet formulas (=, +, -, @) in names and the like. */
    static String csvField(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    static String csvLine(String... fields) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < fields.length; i++) { if (i > 0) b.append(','); b.append(csvField(fields[i])); }
        return b.append("\r\n").toString();
    }
}
