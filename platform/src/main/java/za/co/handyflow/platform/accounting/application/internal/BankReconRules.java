package za.co.handyflow.platform.accounting.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bank reconciliation rules (finance patch F12, 2026-10-14). Pure, so they are tested without Spring.
 * <p>
 * A bank account is reconciled when the ledger agrees with the statement once the items that are only on one side are
 * taken out: {@code (book balance - items only in the books) = (statement balance - items only on the statement)}. What is
 * left over is the unexplained difference, for example an opening balance that was never entered.
 */
public final class BankReconRules {

    private static final BigDecimal CENT = new BigDecimal("0.005");

    private BankReconRules() {}

    public static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * What is left unexplained. {@code statementOnlyNet}: statement lines with no journal yet, money in positive, out
     * negative. {@code bookOnlyNet}: ledger bank lines not on the statement yet (debit positive, credit negative).
     */
    public static BigDecimal difference(BigDecimal statementBalance, BigDecimal bookBalance,
                                        BigDecimal statementOnlyNet, BigDecimal bookOnlyNet) {
        BigDecimal books = money(bookBalance).subtract(money(bookOnlyNet));
        BigDecimal bank = money(statementBalance).subtract(money(statementOnlyNet));
        return money(books.subtract(bank));
    }

    /** True when the difference is nothing (to the cent). */
    public static boolean balanced(BigDecimal difference) {
        return money(difference).abs().compareTo(CENT) < 0;
    }

    /**
     * Null when this reconciliation may be signed off, otherwise why not. Every statement line up to the date must be
     * matched, the difference must be nothing, and the signer must not have made the journals being signed off when
     * another person could.
     *
     * @param journalMakers people who made journals or matches in this reconciliation
     * @param otherSigners  other active people who may sign off
     */
    public static String signOffProblem(long unmatchedStatementLines, BigDecimal difference, UUID signer,
                                        Set<UUID> journalMakers, long otherSigners) {
        if (unmatchedStatementLines > 0)
            return unmatchedStatementLines + (unmatchedStatementLines == 1 ? " statement line is" : " statement lines are")
                    + " not matched yet. Match or categorise them first";
        if (!balanced(difference))
            return "The ledger and the statement are R " + money(difference).abs().toPlainString()
                    + (money(difference).signum() > 0 ? " apart (the ledger is higher)" : " apart (the statement is higher)")
                    + " after the unmatched items. Find the difference first";
        if (signer != null && journalMakers != null && journalMakers.contains(signer) && otherSigners > 0)
            return "You made some of the journals in this reconciliation, so someone else must sign it off"
                    + (otherSigners == 1 ? " (one other person can)" : " (" + otherSigners + " other people can)");
        return null;
    }

    /** True when the sign-off is allowed only because nobody else can sign; log it. */
    public static boolean soleSigner(UUID signer, Set<UUID> journalMakers, long otherSigners) {
        return signer != null && journalMakers != null && journalMakers.contains(signer) && otherSigners <= 0;
    }

    /** Null when {@code periodEnd} may be reconciled and signed off, otherwise why not. */
    public static String periodProblem(LocalDate periodEnd, LocalDate today, LocalDate signedThrough) {
        if (periodEnd == null) return "Choose the date the statement runs to";
        if (periodEnd.isAfter(today)) return "The statement date cannot be in the future";
        if (signedThrough != null && !periodEnd.isAfter(signedThrough))
            return "This account is already signed off through " + signedThrough + ". Choose a later date, or reopen that sign-off";
        return null;
    }

    /** Null when a statement line dated {@code lineDate} may be added, otherwise why not (the period is signed off). */
    public static String signedOffLineProblem(LocalDate lineDate, LocalDate signedThrough) {
        if (lineDate == null || signedThrough == null || lineDate.isAfter(signedThrough)) return null;
        return "This bank account is signed off through " + signedThrough + ", so a line dated " + lineDate
                + " cannot be added. Reopen the sign-off first";
    }

    // ── Matching rules ────────────────────────────────────────────────────────

    public record Rule(UUID id, String name, UUID bankAccountId, String direction, String text, String field,
                       BigDecimal min, BigDecimal max, int priority, boolean active, UUID otherAccountId) {}

    /** A statement line as the rules see it. {@code type} is CREDIT (money in) or DEBIT (money out); amount is positive. */
    public record Line(UUID bankAccountId, String type, String description, String reference, BigDecimal amount) {}

    /** Null when a rule can be saved, otherwise why not. */
    public static String ruleProblem(String name, String direction, String text, BigDecimal min, BigDecimal max, UUID otherAccountId) {
        if (name == null || name.trim().isEmpty()) return "Give the rule a name";
        if (otherAccountId == null) return "Choose the account these lines are booked to";
        if (direction != null && !Set.of("IN", "OUT", "ANY").contains(direction)) return "Direction must be money in, money out or either";
        boolean hasText = text != null && !text.trim().isEmpty();
        if (!hasText && min == null && max == null) return "A rule needs some text to look for, or an amount range, or it would match every line";
        if (min != null && min.signum() < 0) return "The smallest amount cannot be below zero";
        if (min != null && max != null && min.compareTo(max) > 0) return "The smallest amount is more than the largest";
        return null;
    }

    /** True when {@code rule} fits {@code line}: account, direction, text (any case, spaces squeezed) and amount range. */
    public static boolean matches(Rule rule, Line line) {
        if (rule == null || line == null || !rule.active()) return false;
        if (rule.bankAccountId() != null && !rule.bankAccountId().equals(line.bankAccountId())) return false;
        String dir = rule.direction() == null ? "ANY" : rule.direction();
        if ("IN".equals(dir) && !"CREDIT".equals(line.type())) return false;
        if ("OUT".equals(dir) && !"DEBIT".equals(line.type())) return false;
        BigDecimal amt = money(line.amount());
        if (rule.min() != null && amt.compareTo(money(rule.min())) < 0) return false;
        if (rule.max() != null && amt.compareTo(money(rule.max())) > 0) return false;
        String needle = squeeze(rule.text());
        if (needle.isEmpty()) return true;
        String field = rule.field() == null ? "EITHER" : rule.field();
        boolean inDescription = squeeze(line.description()).contains(needle);
        boolean inReference = squeeze(line.reference()).contains(needle);
        return switch (field) {
            case "DESCRIPTION" -> inDescription;
            case "REFERENCE" -> inReference;
            default -> inDescription || inReference;
        };
    }

    /** The rule that applies: lowest priority number first, then the more specific (longer text), then name. Null when none. */
    public static Rule firstMatch(List<Rule> rules, Line line) {
        return rules.stream().filter(r -> matches(r, line))
                .min(Comparator.comparingInt(Rule::priority)
                        .thenComparing((Rule r) -> -squeeze(r.text()).length())
                        .thenComparing(r -> r.name() == null ? "" : r.name().toLowerCase()))
                .orElse(null);
    }

    private static String squeeze(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
