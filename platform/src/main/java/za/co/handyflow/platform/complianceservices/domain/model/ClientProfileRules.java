package za.co.handyflow.platform.complianceservices.domain.model;

import java.util.List;
import java.util.Set;

/** The allowed values and shape rules for a compliance client's profile. Pure, so it is easy to test. */
public final class ClientProfileRules {

    private ClientProfileRules() {}

    public static final Set<String> ENTITY_TYPES = Set.of("PTY_LTD", "LTD", "NPC", "CC", "SOLE_PROPRIETOR",
            "PARTNERSHIP", "TRUST", "NGO", "COOPERATIVE", "STATE_ENTITY", "OTHER");
    public static final Set<String> TURNOVER_BANDS = Set.of("UNDER_1M", "1M_TO_10M", "10M_TO_50M", "50M_TO_250M", "OVER_250M");
    public static final Set<String> RISK_RATINGS = Set.of("LOW", "MEDIUM", "HIGH");
    public static final List<String> ONBOARDING_STATUSES = List.of("NOT_STARTED", "IN_PROGRESS", "COMPLETE", "ON_HOLD");
    public static final Set<String> SOURCES = Set.of("CLIENT_SUPPLIED", "STAFF_ENTERED", "DOCUMENT", "REGISTER_CHECK");

    /** The profile facts whose origin is recorded. */
    public static final Set<String> TRACKED_FIELDS = Set.of("legalName", "registrationNumber", "entityType", "taxNumber",
            "vatNumber", "registeredAddress", "industry", "employeeCount", "turnoverBand");

    public static String requireOneOf(String field, String value, Set<String> allowed) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim().toUpperCase();
        if (!allowed.contains(v)) throw new IllegalArgumentException(field + " must be one of " + allowed.stream().sorted().toList());
        return v;
    }

    /** Company registration numbers: digits, with the usual slashes, kept as typed but without stray spaces. */
    public static String registrationNumber(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().replaceAll("\\s+", "");
        if (!v.matches("[0-9A-Za-z/\\-]{5,30}")) throw new IllegalArgumentException("registrationNumber looks wrong: use letters, digits, / and - only");
        return v.toUpperCase();
    }

    /** South African tax and VAT numbers are 10 digits. VAT numbers start with 4. */
    public static String taxNumber(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.replaceAll("[\\s-]", "");
        if (!v.matches("\\d{10}")) throw new IllegalArgumentException("taxNumber must be 10 digits");
        return v;
    }

    public static String vatNumber(String raw) {
        String v = taxNumber(raw);
        if (v != null && !v.startsWith("4")) throw new IllegalArgumentException("vatNumber must be 10 digits starting with 4");
        return v;
    }

    public static Integer employeeCount(Integer n) {
        if (n != null && (n < 0 || n > 10_000_000)) throw new IllegalArgumentException("employeeCount is out of range");
        return n;
    }

    public static String clean(String v, int max) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
