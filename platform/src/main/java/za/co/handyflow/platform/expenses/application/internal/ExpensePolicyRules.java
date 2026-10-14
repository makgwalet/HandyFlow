package za.co.handyflow.platform.expenses.application.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure rules for expense policies, duplicate-claim detection and reimbursement batches (finance F18). No framework, no database.
 * A policy belongs to a category, or to "*" for every category without its own policy.
 */
public final class ExpensePolicyRules {

    private ExpensePolicyRules() {}

    public static final String ANY = "*";
    public static final int DUPLICATE_WINDOW_DAYS = 3;
    public static final int MAX_BATCH_CLAIMS = 200;

    /** limitAction is BLOCK (the claim cannot be submitted) or WARN (it goes to the approver with a flag). */
    public record Policy(String category, BigDecimal maxPerClaim, String limitAction, BigDecimal receiptAbove, Integer maxAgeDays) {}

    /** severity is BLOCK or WARN. */
    public record Finding(String code, String severity, String text) {}

    public static Policy pick(List<Policy> policies, String category) {
        Policy any = null;
        for (Policy p : policies) {
            if (p.category().equals(category)) return p;
            if (ANY.equals(p.category())) any = p;
        }
        return any;
    }

    public static List<Finding> check(Policy p, BigDecimal amount, LocalDate claimDate, LocalDate today, boolean hasReceipt) {
        List<Finding> out = new ArrayList<>();
        if (p == null || amount == null) return out;
        if (p.maxPerClaim() != null && amount.compareTo(p.maxPerClaim()) > 0) {
            String sev = "BLOCK".equals(p.limitAction()) ? "BLOCK" : "WARN";
            out.add(new Finding("LIMIT", sev, "R " + money(amount) + " is above the limit of R " + money(p.maxPerClaim()) + " for this category."));
        }
        if (p.receiptAbove() != null && amount.compareTo(p.receiptAbove()) > 0 && !hasReceipt) {
            out.add(new Finding("RECEIPT", "BLOCK", p.receiptAbove().signum() == 0
                    ? "A receipt is required for every claim in this category."
                    : "A receipt is required for claims above R " + money(p.receiptAbove()) + "."));
        }
        if (p.maxAgeDays() != null && claimDate != null && claimDate.isBefore(today.minusDays(p.maxAgeDays()))) {
            out.add(new Finding("OLD", "WARN", "The expense is more than " + p.maxAgeDays() + " days old."));
        }
        return out;
    }

    public static boolean blocked(List<Finding> findings) {
        return findings.stream().anyMatch(f -> "BLOCK".equals(f.severity()));
    }

    public static String blockText(List<Finding> findings) {
        return String.join(" ", findings.stream().filter(f -> "BLOCK".equals(f.severity())).map(Finding::text).toList());
    }

    /** Null when the policy may be saved. */
    public static String policyProblem(String category, BigDecimal maxPerClaim, String limitAction, BigDecimal receiptAbove, Integer maxAgeDays,
                                       List<String> categories) {
        if (category == null || !(ANY.equals(category) || categories.contains(category))) return "Choose a category, or 'every category'.";
        if (maxPerClaim != null && maxPerClaim.signum() <= 0) return "The limit must be more than zero.";
        if (maxPerClaim != null && !("BLOCK".equals(limitAction) || "WARN".equals(limitAction))) return "Say whether going over the limit blocks the claim or only warns.";
        if (receiptAbove != null && receiptAbove.signum() < 0) return "The receipt threshold cannot be negative.";
        if (maxAgeDays != null && maxAgeDays < 1) return "The age limit must be at least 1 day.";
        if (maxPerClaim == null && receiptAbove == null && maxAgeDays == null) return "Set at least one rule.";
        return null;
    }

    // ── duplicates ────────────────────────────────────────────────────────

    /** "SAME" (same amount, date and category), "NEAR" (same amount within a few days), or null. The caller has matched the person. */
    public static String duplicateKind(BigDecimal amount, LocalDate date, String category,
                                       BigDecimal otherAmount, LocalDate otherDate, String otherCategory) {
        if (amount == null || otherAmount == null || date == null || otherDate == null) return null;
        if (amount.compareTo(otherAmount) != 0) return null;
        long days = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(date, otherDate));
        if (days == 0 && category != null && category.equals(otherCategory)) return "SAME";
        return days <= DUPLICATE_WINDOW_DAYS ? "NEAR" : null;
    }

    public static Finding duplicateFinding(String kind, String otherNumber) {
        return "SAME".equals(kind)
                ? new Finding("DUPLICATE", "WARN", "Looks like a duplicate of " + otherNumber + " (same person, date, category and amount).")
                : new Finding("NEAR_DUPLICATE", "WARN", "Similar to " + otherNumber + " (same person and amount, within " + DUPLICATE_WINDOW_DAYS + " days).");
    }

    // ── reimbursement batches ─────────────────────────────────────────────

    public record BatchClaim(UUID id, String number, String status, BigDecimal amount) {}

    /** Null when these claims can be put in a batch. */
    public static String batchProblem(List<BatchClaim> claims, java.util.Set<UUID> alreadyBatched, int requested) {
        if (requested == 0) return "Choose at least one claim.";
        if (requested > MAX_BATCH_CLAIMS) return "A batch holds at most " + MAX_BATCH_CLAIMS + " claims.";
        if (claims.size() != requested) return "Some of the chosen claims were not found.";
        for (BatchClaim c : claims) {
            if (!"APPROVED".equals(c.status())) return c.number() + " is " + c.status().toLowerCase() + "; only approved claims can be paid.";
            if (alreadyBatched.contains(c.id())) return c.number() + " is already in an unpaid batch.";
        }
        return null;
    }

    public static BigDecimal total(List<BatchClaim> claims) {
        return claims.stream().map(BatchClaim::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static String payProblem(String batchStatus, boolean bankAccountNeeded, UUID bankAccountId) {
        if (!"DRAFT".equals(batchStatus)) return "This batch is " + batchStatus.toLowerCase() + "; only an unpaid batch can be paid.";
        if (bankAccountNeeded && bankAccountId == null) return "Choose the bank account the batch is paid from.";
        return null;
    }

    private static String money(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
