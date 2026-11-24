package za.co.handyflow.platform.accounting.domain.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What a journal request must satisfy before anything is saved (audit finding 1.8): a known entry type, every line on an
 * active account of this company, and amounts in whole cents. Also when a system-made draft may be thrown away. Pure.
 */
public final class JournalInputRules {

    /** The values the acc_journal_entries entry_type check constraint accepts. */
    public static final Set<String> ENTRY_TYPES =
            Set.of("MANUAL", "INVOICE", "PAYMENT", "BANK", "DEPRECIATION", "ADJUSTMENT", "VAT");

    private JournalInputRules() {}

    public record Line(UUID accountId, BigDecimal debit, BigDecimal credit) {}

    /** The first thing wrong with the request, in words for the person, or null when it is acceptable. */
    public static String problem(String entryType, Set<UUID> usableAccountIds, List<Line> lines) {
        if (entryType != null && !entryType.isBlank() && !ENTRY_TYPES.contains(entryType))
            return "\"" + entryType + "\" is not a journal type. Use one of: " + String.join(", ", ENTRY_TYPES.stream().sorted().toList());
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            int n = i + 1;
            if (l.accountId() == null) return "Line " + n + " has no account";
            if (!usableAccountIds.contains(l.accountId()))
                return "Line " + n + " is on an account that does not exist, is not active, or is not in your chart of accounts";
            if (tooFine(l.debit()) || tooFine(l.credit()))
                return "Line " + n + " has an amount with more than two decimal places";
        }
        return null;
    }

    private static boolean tooFine(BigDecimal v) {
        return v != null && v.signum() != 0 && v.stripTrailingZeros().scale() > 2;
    }

    /**
     * A draft the system made for a business event (it carries a source key, for example an issued invoice waiting to
     * post) must be posted, not discarded: discarding it would leave the document out of the ledger without a trace.
     */
    public static String discardProblem(String sourceKey, String entryNumber) {
        if (sourceKey == null || sourceKey.isBlank()) return null;
        return "Journal " + entryNumber + " was made by the system for a business event and cannot be discarded. "
                + "Post it, or fix what stopped it posting.";
    }
}
