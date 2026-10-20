package za.co.handyflow.platform.earthmoving.domain.model;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Which documents Earthmoving keeps, for what, and who may see them. A machine has photos, manuals, insurance, warranties and other papers; an operator has
 * certificates. Insurance, warranties and operator certificates are sensitive (administrators only) and must carry an expiry date, which feeds the expiry alerts.
 */
public final class DocumentPolicy {
    private DocumentPolicy() {}

    public static final String OWNER_ASSET = "EARTH_ASSET";
    public static final String OWNER_OPERATOR = "EARTH_OPERATOR";
    public static final String OWNER_DISPOSAL = "EARTH_DISPOSAL";
    /** The owners whose documents the expiry alerts watch. */
    public static final Set<String> ALERT_OWNERS = Set.of(OWNER_ASSET, OWNER_OPERATOR);
    public static final Set<String> OWNERS = Set.of(OWNER_ASSET, OWNER_OPERATOR, OWNER_DISPOSAL);

    public static final String PHOTO = "PHOTO", MANUAL = "MANUAL", INSURANCE = "INSURANCE", WARRANTY = "WARRANTY", OTHER = "OTHER";
    public static final String OPERATOR_CERTIFICATE = "OPERATOR_CERTIFICATE", DISPOSAL_EVIDENCE = "DISPOSAL_EVIDENCE";
    private static final Set<String> ASSET_CATEGORIES = Set.of(PHOTO, MANUAL, INSURANCE, WARRANTY, OTHER);
    private static final Set<String> SENSITIVE = Set.of(INSURANCE, WARRANTY, OPERATOR_CERTIFICATE);

    /** The days before expiry that raise an alert, nearest last. */
    public static final int FIRST_WARNING_DAYS = 30;
    public static final int FINAL_WARNING_DAYS = 7;

    public enum Stage { NONE, DUE_30, DUE_7, EXPIRED }

    /** The owner kinds the screens use: ASSET (a machine) and OPERATOR (a person). */
    public static String owner(String kind) {
        String k = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        return switch (k) {
            case "ASSET" -> OWNER_ASSET;
            case "OPERATOR" -> OWNER_OPERATOR;
            case "DISPOSAL" -> OWNER_DISPOSAL;
            default -> throw new IllegalArgumentException("Documents belong to a machine (ASSET), an operator (OPERATOR) or a disposal request (DISPOSAL).");
        };
    }

    public static String category(String ownerType, String category) {
        String c = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        boolean ok = OWNER_ASSET.equals(ownerType) ? ASSET_CATEGORIES.contains(c)
                : OWNER_OPERATOR.equals(ownerType) ? OPERATOR_CERTIFICATE.equals(c)
                : OWNER_DISPOSAL.equals(ownerType) && DISPOSAL_EVIDENCE.equals(c);
        if (!ok) throw new IllegalArgumentException(OWNER_ASSET.equals(ownerType)
                ? "A machine's document is a PHOTO, MANUAL, INSURANCE, WARRANTY or OTHER."
                : OWNER_OPERATOR.equals(ownerType) ? "An operator's document is an OPERATOR_CERTIFICATE." : "A disposal's document is DISPOSAL_EVIDENCE.");
        return c;
    }

    public static boolean sensitive(String category) { return SENSITIVE.contains(category); }

    /** Sensitive documents need administrator authority to see or change; the rest need read or write. */
    public static String authority(String category, boolean write) {
        return sensitive(category) ? EarthmovingRules.ADMIN : write ? EarthmovingRules.WRITE : EarthmovingRules.READ;
    }

    public static LocalDate requireExpiry(String category, LocalDate expiresOn) {
        if (SENSITIVE.contains(category) && expiresOn == null)
            throw new IllegalArgumentException("Give the date this " + category.toLowerCase(Locale.ROOT).replace('_', ' ') + " expires, so you are warned before it does.");
        return expiresOn;
    }

    public static Stage stage(LocalDate expiresOn, LocalDate today) {
        if (expiresOn == null) return Stage.NONE;
        if (expiresOn.isBefore(today)) return Stage.EXPIRED;
        if (!expiresOn.isAfter(today.plusDays(FINAL_WARNING_DAYS))) return Stage.DUE_7;
        if (!expiresOn.isAfter(today.plusDays(FIRST_WARNING_DAYS))) return Stage.DUE_30;
        return Stage.NONE;
    }

    /** An expired insurance or operator certificate means the machine or person should not be working; everything else is a warning. */
    public static String severity(String category, Stage stage) {
        return stage == Stage.EXPIRED && (INSURANCE.equals(category) || OPERATOR_CERTIFICATE.equals(category)) ? "CRITICAL" : "WARNING";
    }

    public static String alertKey(UUID documentId, LocalDate expiresOn, Stage stage) {
        return "DOC_EXPIRY:" + documentId + ":" + expiresOn + ":" + stage.name();
    }

    public static String label(String category) { return category.toLowerCase(Locale.ROOT).replace('_', ' '); }

    public static String describe(Stage stage, LocalDate expiresOn, LocalDate today) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, expiresOn);
        return stage == Stage.EXPIRED ? "expired on " + expiresOn : days == 0 ? "expires today" : "expires in " + days + " day" + (days == 1 ? "" : "s") + " (" + expiresOn + ")";
    }
}
