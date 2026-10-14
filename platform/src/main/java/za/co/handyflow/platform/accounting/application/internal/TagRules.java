package za.co.handyflow.platform.accounting.application.internal;

import java.util.*;

/**
 * Pure rules for tagging journal lines with a cost centre automatically (finance F28). Every module's postings go through the
 * same journal creation, so a rule here reaches fuel, payroll, bills, expenses and invoices without changing those modules.
 * A rule needs at least one condition: an account code, words that appear in the line or journal text, or a journal type.
 * The first matching rule (lowest priority number) wins. Only income and expense lines are tagged.
 */
public final class TagRules {
    private TagRules() {}

    public record Rule(UUID id, String name, int priority, String accountCode, String textContains, String entryType, UUID costCentreId) {}
    public record Line(String accountCode, String accountType, String lineDescription, String entryDescription, String reference, String entryType, UUID costCentreId) {}

    public static final int MAX_RULES = 200;
    public static final Set<String> ENTRY_TYPES = Set.of("MANUAL", "INVOICE", "PAYMENT", "BANK", "DEPRECIATION", "ADJUSTMENT", "VAT");

    public static String saveProblem(String name, String accountCode, String textContains, String entryType, UUID costCentreId, int priority) {
        if (name == null || name.isBlank()) return "Name the rule.";
        if (name.trim().length() > 120) return "The name is too long (120 characters at most).";
        boolean none = blank(accountCode) && blank(textContains) && blank(entryType);
        if (none) return "Give the rule at least one condition: an account, some words, or a journal type.";
        if (!blank(textContains) && textContains.trim().length() < 3) return "Words to look for need at least 3 characters, or the rule would match too much.";
        if (!blank(textContains) && textContains.trim().length() > 80) return "Words to look for can be 80 characters at most.";
        if (!blank(entryType) && !ENTRY_TYPES.contains(entryType)) return "Unknown journal type.";
        if (costCentreId == null) return "Choose the cost centre to tag with.";
        if (priority < 1 || priority > 9999) return "Priority is a number from 1 to 9999; lower numbers are tried first.";
        return null;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    public static boolean matches(Rule r, Line l) {
        if (!blank(r.accountCode()) && !r.accountCode().trim().equals(l.accountCode())) return false;
        if (!blank(r.entryType()) && !r.entryType().equals(l.entryType())) return false;
        if (!blank(r.textContains())) {
            String hay = (nz(l.lineDescription()) + " " + nz(l.entryDescription()) + " " + nz(l.reference())).toLowerCase(Locale.ROOT);
            if (!hay.contains(r.textContains().trim().toLowerCase(Locale.ROOT))) return false;
        }
        return true;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    public static boolean taggable(Line l) {
        return l.costCentreId() == null && ("INCOME".equals(l.accountType()) || "EXPENSE".equals(l.accountType()));
    }

    /** The first matching rule by priority, or null. A line that already has a cost centre, or is not income or expense, is left alone. */
    public static Rule pick(List<Rule> rules, Line l) {
        if (!taggable(l)) return null;
        return rules.stream().sorted(Comparator.comparingInt(Rule::priority).thenComparing(Rule::name))
                .filter(r -> matches(r, l)).findFirst().orElse(null);
    }
}
