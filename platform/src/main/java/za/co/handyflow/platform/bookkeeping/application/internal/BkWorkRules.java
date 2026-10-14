package za.co.handyflow.platform.bookkeeping.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pure rules for Bookkeeping's bank rules, client queries and handover to the accountant (finance F19). No framework.
 * <p>
 * The matching rules deliberately mirror Accounting's {@code BankReconRules} (modules may not reach into each other's
 * internals); moving both onto one shared class is a follow-up.
 */
public final class BkWorkRules {

    private BkWorkRules() {}

    public static final int MAX_BULK = 200;
    public static final Set<String> QUERY_KINDS = Set.of("MISSING_DOCUMENT", "QUESTION");
    public static final int MIN_TEXT = 5;

    // ── matching rules ────────────────────────────────────────────────────

    public record Rule(UUID id, String name, UUID bankAccountId, String direction, String text, String field,
                       BigDecimal min, BigDecimal max, int priority, boolean active, UUID contraAccountId) {}

    /** type is CREDIT (money in) or DEBIT (money out); amount is positive. */
    public record Line(UUID bankAccountId, String type, String description, String reference, BigDecimal amount) {}

    public static String ruleProblem(String name, String direction, String text, BigDecimal min, BigDecimal max, UUID contraAccountId) {
        if (name == null || name.trim().isEmpty()) return "Give the rule a name.";
        if (contraAccountId == null) return "Choose the account these lines are booked to.";
        if (direction != null && !Set.of("IN", "OUT", "ANY").contains(direction)) return "Direction must be money in, money out or either.";
        boolean hasText = text != null && !text.trim().isEmpty();
        if (!hasText && min == null && max == null) return "A rule needs some text to look for, or an amount range, or it would match every line.";
        if (min != null && min.signum() < 0) return "The smallest amount cannot be below zero.";
        if (min != null && max != null && min.compareTo(max) > 0) return "The smallest amount is more than the largest.";
        return null;
    }

    public static boolean matches(Rule rule, Line line) {
        if (rule == null || line == null || !rule.active()) return false;
        if (rule.bankAccountId() != null && !rule.bankAccountId().equals(line.bankAccountId())) return false;
        String dir = rule.direction() == null ? "ANY" : rule.direction();
        if ("IN".equals(dir) && !"CREDIT".equals(line.type())) return false;
        if ("OUT".equals(dir) && !"DEBIT".equals(line.type())) return false;
        BigDecimal amt = cents(line.amount());
        if (rule.min() != null && amt.compareTo(cents(rule.min())) < 0) return false;
        if (rule.max() != null && amt.compareTo(cents(rule.max())) > 0) return false;
        String needle = squeeze(rule.text());
        if (needle.isEmpty()) return true;
        boolean inDescription = squeeze(line.description()).contains(needle);
        boolean inReference = squeeze(line.reference()).contains(needle);
        String field = rule.field() == null ? "EITHER" : rule.field();
        return switch (field) {
            case "DESCRIPTION" -> inDescription;
            case "REFERENCE" -> inReference;
            default -> inDescription || inReference;
        };
    }

    /** Lowest priority number first, then the longer (more specific) text, then name. Null when none match. */
    public static Rule firstMatch(List<Rule> rules, Line line) {
        return rules.stream().filter(r -> matches(r, line))
                .min(Comparator.comparingInt(Rule::priority)
                        .thenComparing((Rule r) -> -squeeze(r.text()).length())
                        .thenComparing(r -> r.name() == null ? "" : r.name().toLowerCase()))
                .orElse(null);
    }

    /** Null when this many lines may be categorised in one go. */
    public static String bulkProblem(int count) {
        if (count == 0) return "Choose at least one line.";
        if (count > MAX_BULK) return "Categorise at most " + MAX_BULK + " lines at a time.";
        return null;
    }

    // ── client queries ────────────────────────────────────────────────────

    public static String queryProblem(String kind, String text) {
        if (kind == null || !QUERY_KINDS.contains(kind)) return "Choose whether you are asking for a document or asking a question.";
        if (text == null || text.trim().length() < MIN_TEXT) return "Say what you need (at least " + MIN_TEXT + " characters).";
        return null;
    }

    public static String answerProblem(String status, String answer, String documentRef) {
        if (!"OPEN".equals(status)) return "Only an open query can be answered.";
        boolean hasAnswer = answer != null && answer.trim().length() >= 2;
        boolean hasDoc = documentRef != null && !documentRef.isBlank();
        if (!hasAnswer && !hasDoc) return "Record the client's answer or where the document is.";
        return null;
    }

    public static String closeProblem(String status) {
        return "CLOSED".equals(status) ? "This query is already closed." : null;
    }

    // ── handover to the accountant ────────────────────────────────────────

    /** Null when the period may be handed over; otherwise what stands in the way. */
    public static String handoverProblem(LocalDate periodEnd, LocalDate today, long unreconciled, long drafts, boolean openHandover) {
        if (periodEnd == null) return "Choose the date the handover runs to.";
        if (periodEnd.isAfter(today)) return "The handover date cannot be in the future.";
        if (openHandover) return "A handover is already with the accountant. Wait for it to be accepted or returned.";
        if (unreconciled > 0) return unreconciled + (unreconciled == 1 ? " bank line is" : " bank lines are") + " not categorised yet.";
        if (drafts > 0) return drafts + (drafts == 1 ? " journal is" : " journals are") + " still a draft. Post or discard them.";
        return null;
    }

    /** Things worth knowing but not blocking. */
    public static List<String> handoverWarnings(long openQueries) {
        return openQueries > 0 ? List.of(openQueries + (openQueries == 1 ? " client query is" : " client queries are") + " still open.") : List.of();
    }

    /** Null when {@code responder} may accept or return a handover. */
    public static String respondProblem(String status, String action, String note, UUID sender, UUID responder, long otherResponders) {
        if (!"SENT".equals(status)) return "This handover has already been " + ("ACCEPTED".equals(status) ? "accepted" : "returned") + ".";
        if (!"ACCEPT".equals(action) && !"RETURN".equals(action)) return "Choose whether to accept or return the handover.";
        if ("RETURN".equals(action) && (note == null || note.trim().length() < 10))
            return "Say what needs fixing (at least 10 characters) so the bookkeeper can act on it.";
        if (responder != null && responder.equals(sender) && otherResponders > 0)
            return "You sent this handover, so someone else must accept or return it.";
        return null;
    }

    public static boolean soleResponder(UUID sender, UUID responder, long otherResponders) {
        return responder != null && responder.equals(sender) && otherResponders <= 0;
    }

    public static BigDecimal cents(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private static String squeeze(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
