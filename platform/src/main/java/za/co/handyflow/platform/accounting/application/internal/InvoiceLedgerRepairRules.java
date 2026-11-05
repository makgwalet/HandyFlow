package za.co.handyflow.platform.accounting.application.internal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * INV-01 repair rules. An invoice's missing issuance journal is dated its original issue date while that period is open
 * (which includes a period an authorised person has reopened). Where the original period is locked it is posted in the
 * current open period and classified as a prior-period correction. Where today is locked too, it cannot be posted. Pure.
 */
public final class InvoiceLedgerRepairRules {

    public static final String ORIGINAL_DATE = "ORIGINAL_DATE", PRIOR_PERIOD_CORRECTION = "PRIOR_PERIOD_CORRECTION", UNPOSTABLE = "UNPOSTABLE";
    public static final int MIN_REASON = 10;

    private InvoiceLedgerRepairRules() {}

    private static boolean closed(LocalDate date, LocalDate lockedThrough) { return lockedThrough != null && !date.isAfter(lockedThrough); }

    public static String category(LocalDate issuedOn, LocalDate today, LocalDate lockedThrough) {
        if (closed(today, lockedThrough)) return UNPOSTABLE;
        if (issuedOn != null && !closed(issuedOn, lockedThrough)) return ORIGINAL_DATE;
        return PRIOR_PERIOD_CORRECTION;   // original period locked, or no recorded issue date: post now, say so
    }

    /** The date the journal is posted on, or null when it cannot be posted. */
    public static LocalDate entryDate(String category, LocalDate issuedOn, LocalDate today) {
        return switch (category) {
            case ORIGINAL_DATE -> issuedOn;
            case PRIOR_PERIOD_CORRECTION -> today;
            default -> null;
        };
    }

    /** Prior-period corrections are classified ADJUSTMENT; an invoice posted on its own date is an INVOICE entry. */
    public static String entryType(String category) { return PRIOR_PERIOD_CORRECTION.equals(category) ? "ADJUSTMENT" : "INVOICE"; }

    public static String reasonProblem(String reason) {
        if (reason == null || reason.trim().length() < MIN_REASON) return "Give the reason for the repair (at least " + MIN_REASON + " characters).";
        return reason.trim().length() > 500 ? "The reason can be at most 500 characters." : null;
    }

    /** Identifies exactly which invoices and amounts were previewed, in a stable order. */
    public static String previewHash(List<UUID> ids, List<BigDecimal> totals) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) sb.append(ids.get(i)).append('=').append(totals.get(i).stripTrailingZeros().toPlainString()).append(';');
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
