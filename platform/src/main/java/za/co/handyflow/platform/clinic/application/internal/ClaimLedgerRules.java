package za.co.handyflow.platform.clinic.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The money rules for a scheme claim (CLINIC-DEC-001 to 003, 006). What the scheme still owes is the claim's scheme portion
 * less what it paid, less write-offs, less credit notes. Nothing here rewrites the claim's own amounts.
 */
final class ClaimLedgerRules {

    private ClaimLedgerRules() {}

    record Totals(BigDecimal paid, BigDecimal writtenOff, BigDecimal credited) {
        static final Totals NONE = new Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        boolean untouched() { return paid.signum() == 0 && writtenOff.signum() == 0 && credited.signum() == 0; }
    }

    static BigDecimal outstanding(BigDecimal schemePortion, Totals t) {
        BigDecimal due = schemePortion == null ? BigDecimal.ZERO : schemePortion;
        BigDecimal left = due.subtract(t.paid()).subtract(t.writtenOff()).subtract(t.credited());
        return left.signum() < 0 ? BigDecimal.ZERO.setScale(2) : left.setScale(2, RoundingMode.HALF_UP);
    }

    /** The status a claim has once the ledger is as given; the current status when the ledger changes nothing. */
    static String statusAfter(String current, BigDecimal schemePortion, Totals t) {
        if ("VOIDED".equals(current) || "DRAFT".equals(current) || "SUBMITTED".equals(current) || t.untouched()) return current;
        boolean adjusted = t.writtenOff().signum() > 0 || t.credited().signum() > 0;
        if (outstanding(schemePortion, t).signum() == 0) return adjusted ? "CLOSED" : "PAID";
        return t.paid().signum() > 0 ? "PARTIAL" : current;
    }

    private static final Set<String> RECEIVES_PAYMENT = Set.of("ACCEPTED", "PARTIAL");
    private static final Set<String> CAN_WRITE_OFF = Set.of("ACCEPTED", "PARTIAL");
    private static final Set<String> CAN_CREDIT = Set.of("ACCEPTED", "PARTIAL", "REJECTED");
    private static final Set<String> CAN_VOID = Set.of("DRAFT", "SUBMITTED", "ACCEPTED", "REJECTED");

    static boolean receivesPayment(String status) { return RECEIVES_PAYMENT.contains(status); }

    /** CLINIC-DEC-001: Mark paid receives whatever the scheme still owes, no more and no less. */
    static BigDecimal markPaid(String status, BigDecimal outstanding) {
        requireStatus(status, RECEIVES_PAYMENT, "receive a payment");
        if (outstanding.signum() <= 0) throw new IllegalStateException("The scheme owes nothing on this claim");
        return outstanding;
    }

    /** A partial payment: more than zero, less than what is owed (use Mark paid for the rest). */
    static BigDecimal partial(String status, BigDecimal outstanding, BigDecimal supplied) {
        requireStatus(status, RECEIVES_PAYMENT, "receive a payment");
        if (outstanding.signum() <= 0) throw new IllegalStateException("The scheme owes nothing on this claim");
        BigDecimal a = amount(supplied, "Enter the amount the scheme paid");
        if (a.compareTo(outstanding) >= 0) throw new IllegalArgumentException(
                "A partial payment must be less than the R " + outstanding + " still owed; use Mark paid for the full balance");
        return a;
    }

    /** CLINIC-DEC-001: a write-off is its own transaction, never more than is still owed. */
    static BigDecimal writeOff(String status, BigDecimal outstanding, BigDecimal supplied) {
        requireStatus(status, CAN_WRITE_OFF, "be written off");
        return adjustment(outstanding, supplied, "written off");
    }

    /** CLINIC-DEC-003: a credit note reduces what is owed, including on a rejected claim. */
    static BigDecimal creditNote(String status, BigDecimal outstanding, BigDecimal supplied) {
        requireStatus(status, CAN_CREDIT, "be credited");
        return adjustment(outstanding, supplied, "credited");
    }

    /** CLINIC-DEC-002: a claim can be voided only before any money has moved on it. */
    static void requireVoidable(String status, Totals t) {
        requireStatus(status, CAN_VOID, "be voided");
        if (!t.untouched()) throw new IllegalStateException(
                "This claim has a payment, write-off or credit note, so it cannot be voided. Issue a credit note or a correction instead");
    }

    private static BigDecimal adjustment(BigDecimal outstanding, BigDecimal supplied, String verb) {
        if (outstanding.signum() <= 0) throw new IllegalStateException("The scheme owes nothing on this claim");
        BigDecimal a = amount(supplied, "Enter the amount");
        if (a.compareTo(outstanding) > 0) throw new IllegalArgumentException(
                "R " + a + " is more than the R " + outstanding + " still owed, so it cannot be " + verb);
        return a;
    }

    private static BigDecimal amount(BigDecimal supplied, String missing) {
        if (supplied == null) throw new IllegalArgumentException(missing);
        BigDecimal a = supplied.setScale(2, RoundingMode.HALF_UP);
        if (a.signum() <= 0) throw new IllegalArgumentException("The amount must be more than zero");
        return a;
    }

    private static void requireStatus(String status, Set<String> allowed, String what) {
        if (!allowed.contains(status)) throw new IllegalStateException(
                "A claim that is " + status + " cannot " + what + " (allowed from: " + String.join(", ", allowed.stream().sorted().toList()) + ")");
    }

    /** Reason for a write-off, credit note, void or allocation override: 10 to 500 characters once trimmed. */
    static String reason(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() < 10) throw new IllegalArgumentException("Give a reason of at least 10 characters");
        if (t.length() > 500) throw new IllegalArgumentException("Keep the reason under 500 characters");
        return t;
    }

    // ── CLINIC-DEC-006: allocating one scheme payment across claims ──────────────────────────────────────────────────

    record Open(UUID claimId, Instant billedAt, BigDecimal outstanding) {}
    record Share(UUID claimId, BigDecimal amount) {}

    /**
     * Oldest claim first, each paid in full before the next gets anything. There is no proportional spread: if the money
     * does not cover the last claim it is part-paid, and money beyond everything owed is refused.
     */
    static List<Share> oldestFirst(BigDecimal received, List<Open> open) {
        BigDecimal left = amount(received, "Enter the amount received");
        List<Open> ordered = new ArrayList<>(open);
        ordered.sort(Comparator.comparing(Open::billedAt).thenComparing(o -> o.claimId().toString()));
        List<Share> out = new ArrayList<>();
        for (Open o : ordered) {
            if (left.signum() == 0) break;
            if (o.outstanding().signum() <= 0) continue;
            BigDecimal give = left.min(o.outstanding()).setScale(2, RoundingMode.HALF_UP);
            out.add(new Share(o.claimId(), give));
            left = left.subtract(give);
        }
        if (left.signum() > 0) throw new IllegalArgumentException(
                "R " + left + " of the payment is more than the scheme owes on the open claims. Nothing was recorded");
        return out;
    }

    /** A person's own split: needs a reason, must add up to the amount received, and may not exceed any claim's balance. */
    static List<Share> manual(BigDecimal received, List<Open> open, List<Share> requested, String reason) {
        reason(reason);
        BigDecimal total = amount(received, "Enter the amount received");
        if (requested == null || requested.isEmpty()) throw new IllegalArgumentException("Choose the claims to pay");
        Map<UUID, BigDecimal> owed = new HashMap<>();
        for (Open o : open) owed.put(o.claimId(), o.outstanding());
        Set<UUID> seen = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        List<Share> out = new ArrayList<>();
        for (Share s : requested) {
            if (!seen.add(s.claimId())) throw new IllegalArgumentException("A claim is listed twice");
            BigDecimal limit = owed.get(s.claimId());
            if (limit == null) throw new IllegalArgumentException("A chosen claim is not open for this scheme payment");
            BigDecimal a = amount(s.amount(), "Enter an amount for each chosen claim");
            if (a.compareTo(limit) > 0) throw new IllegalArgumentException("R " + a + " is more than the R " + limit + " owed on a chosen claim");
            sum = sum.add(a);
            out.add(new Share(s.claimId(), a));
        }
        if (sum.compareTo(total) != 0) throw new IllegalArgumentException(
                "The chosen amounts add up to R " + sum + " but the payment received is R " + total);
        return out;
    }
}
