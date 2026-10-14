package za.co.handyflow.platform.accounting.application.internal;

import java.util.UUID;

/**
 * Who may post a journal, and when a journal may be reversed (finance patch F2, 2026-10-14). Pure, so it is tested without
 * Spring.
 * <p>
 * The rule is the one the Journal Entries screen already enforced for new manual journals, now applied to every journal a
 * person makes: the person who made a journal cannot also post it. A reversal is a journal a person makes, so it is now
 * created as a draft that someone else posts, instead of being posted on the spot by whoever clicked Reverse.
 * <p>
 * Journals made by the system from a transaction that was approved where it started (an issued invoice, an approved bill
 * or expense claim, a credit note) carry no maker and are posted by the system; their check happened at the source.
 */
public final class JournalReviewRules {

    private JournalReviewRules() {}

    /** Null when {@code poster} may post a journal made by {@code maker}, otherwise why not. */
    public static String postProblem(UUID maker, UUID poster) {
        if (maker == null) return null;
        if (poster == null) return "The person posting is not known, so a journal made by a person cannot be posted";
        if (maker.equals(poster)) return "You made this journal. A different person must post it";
        return null;
    }

    /** Null when a journal in {@code status} may have a reversal drafted, otherwise why not. */
    public static String reversalProblem(String status, String pendingReversalNumber) {
        if (!"POSTED".equals(status)) return "Only posted journals can be reversed";
        if (pendingReversalNumber != null)
            return "Reversal " + pendingReversalNumber + " is already waiting to be posted";
        return null;
    }

    /** Null when a drafted reversal may be posted against an original in {@code originalStatus}, otherwise why not. */
    public static String postReversalProblem(String originalStatus) {
        if ("POSTED".equals(originalStatus)) return null;
        return "The journal this reverses is " + (originalStatus == null ? "missing" : originalStatus.toLowerCase())
                + ", so this reversal cannot be posted";
    }
}
