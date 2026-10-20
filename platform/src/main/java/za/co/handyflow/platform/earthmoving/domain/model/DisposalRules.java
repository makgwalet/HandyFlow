package za.co.handyflow.platform.earthmoving.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The rules for taking a machine out of the fleet. A machine leaves only through a request that is evidenced, approved by someone other
 * than the person who asked, and completed while nothing is still running on it. Pure, so it can be tested without a database.
 */
public final class DisposalRules {
    private DisposalRules() {}

    public static final String SOLD = "SOLD", SCRAPPED = "SCRAPPED", WRITTEN_OFF = "WRITTEN_OFF", TRANSFERRED = "TRANSFERRED";
    public static final Set<String> REASONS = Set.of(SOLD, SCRAPPED, WRITTEN_OFF, TRANSFERRED);
    /** Reasons where the machine goes to someone, so we must know who. */
    public static final Set<String> NEEDS_COUNTERPARTY = Set.of(SOLD, TRANSFERRED);

    public static final String REQUESTED = "REQUESTED", APPROVED = "APPROVED", REJECTED = "REJECTED", COMPLETED = "COMPLETED", CANCELLED = "CANCELLED";
    public static final Set<String> LIVE = Set.of(REQUESTED, APPROVED);

    public static final int MAX_TEXT = 2000;
    public static final String ALERT_TYPE = "DISPOSAL_APPROVAL";

    public static String reason(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!REASONS.contains(v)) throw new IllegalArgumentException("The reason must be SOLD, SCRAPPED, WRITTEN_OFF or TRANSFERRED.");
        return v;
    }

    public static String details(String value) {
        String v = value == null ? "" : value.trim();
        if (v.length() < 5) throw new IllegalArgumentException("Say why this machine is leaving the fleet.");
        if (v.length() > MAX_TEXT) throw new IllegalArgumentException("Keep the details under " + MAX_TEXT + " characters.");
        return v;
    }

    /** A reason or note that must be written (rejecting, cancelling). */
    public static String note(String value, String what) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) throw new IllegalArgumentException("Give a reason for " + what + ".");
        if (v.length() > MAX_TEXT) throw new IllegalArgumentException("Keep the reason under " + MAX_TEXT + " characters.");
        return v;
    }

    public static String optionalNote(String value) {
        String v = value == null ? "" : value.trim();
        if (v.length() > MAX_TEXT) throw new IllegalArgumentException("Keep the note under " + MAX_TEXT + " characters.");
        return v.isEmpty() ? null : v;
    }

    public static String counterparty(String reason, String value) {
        String v = value == null ? "" : value.trim();
        if (v.length() > 255) throw new IllegalArgumentException("The buyer or recipient name is too long.");
        if (v.isEmpty() && NEEDS_COUNTERPARTY.contains(reason))
            throw new IllegalArgumentException("Say who the machine is " + (SOLD.equals(reason) ? "sold to." : "transferred to."));
        return v.isEmpty() ? null : v;
    }

    public static BigDecimal proceeds(BigDecimal value) {
        if (value != null && value.signum() < 0) throw new IllegalArgumentException("The amount cannot be negative.");
        return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** What stops a machine leaving the fleet right now. Empty means nothing does. */
    public static List<String> blockers(AssetStatus status, boolean openDeployment, long pendingCharges) {
        List<String> out = new ArrayList<>();
        if (status == AssetStatus.RETIRED) out.add("This machine is already retired.");
        if (status == AssetStatus.DEPLOYED || status == AssetStatus.HIRED_OUT || openDeployment)
            out.add("The machine is still out on a deployment or hire. Return it first.");
        if (pendingCharges > 0)
            out.add(pendingCharges + " hire charge" + (pendingCharges == 1 ? " has" : "s have") + " not been invoiced yet. Invoice or void " + (pendingCharges == 1 ? "it" : "them") + " first.");
        return out;
    }

    public static void requireNoBlockers(List<String> blockers) {
        if (!blockers.isEmpty()) throw new IllegalStateException(String.join(" ", blockers));
    }

    public static void requireEvidence(long evidenceCount) {
        if (evidenceCount < 1)
            throw new IllegalStateException("Attach at least one piece of evidence (sale agreement, scrap certificate, transfer papers) first.");
    }

    /** The person who asked cannot be the one who decides. */
    public static void requireDifferentPerson(UUID requestedBy, UUID decidedBy) {
        if (requestedBy != null && requestedBy.equals(decidedBy))
            throw new IllegalStateException("Someone other than the person who asked must approve or reject a disposal.");
    }

    /** What the status line in the history and the audit trail says. */
    public static String statusLine(String reason, String details) {
        return "Disposal (" + reason.toLowerCase(Locale.ROOT).replace('_', ' ') + "): " + details;
    }
}
