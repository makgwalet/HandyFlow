package za.co.handyflow.platform.clinic.application.internal;

import za.co.handyflow.platform.clinic.application.internal.ClaimLedgerRules.Totals;

import java.math.BigDecimal;
import java.util.List;

/**
 * The four figures at the top of the Claims screen, for every claim of a status (patch 0178), so they stay right when the list is paged.
 * Same meaning as the screen had: outstanding is the scheme portion for a claim not yet answered (draft, submitted) and what the scheme
 * still owes for an accepted or partly paid one; paid is everything the scheme has paid on any of the claims.
 */
final class ClaimSummaryRules {

    private ClaimSummaryRules() {}

    record Fact(String status, BigDecimal schemePortion, Totals totals) {}
    record Summary(long total, BigDecimal outstanding, BigDecimal paid, long rejected) {}

    static Summary summarise(List<Fact> facts) {
        BigDecimal outstanding = BigDecimal.ZERO, paid = BigDecimal.ZERO;
        long rejected = 0;
        for (Fact f : facts) {
            Totals t = f.totals() == null ? Totals.NONE : f.totals();
            BigDecimal portion = f.schemePortion() == null ? BigDecimal.ZERO : f.schemePortion();
            switch (f.status() == null ? "" : f.status()) {
                case "DRAFT", "SUBMITTED" -> outstanding = outstanding.add(portion);
                case "ACCEPTED", "PARTIAL" -> outstanding = outstanding.add(ClaimLedgerRules.outstanding(portion, t));
                case "REJECTED" -> rejected++;
                default -> { }
            }
            paid = paid.add(t.paid());
        }
        return new Summary(facts.size(), outstanding, paid, rejected);
    }
}
