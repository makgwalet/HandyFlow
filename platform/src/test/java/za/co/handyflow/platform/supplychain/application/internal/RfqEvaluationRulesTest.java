package za.co.handyflow.platform.supplychain.application.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.RfqEvaluationRules.*;

/** Covers the reconstructed RFQ scoring rules (see the class comment for the formulas). */
class RfqEvaluationRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private static final UUID L1 = UUID.randomUUID();
    private static final UUID L2 = UUID.randomUUID();
    private static final List<LineReq> REQ = List.of(
            new LineReq(L1, new BigDecimal("10")), new LineReq(L2, new BigDecimal("5")));

    private static List<QuoteLine> full() {
        return List.of(new QuoteLine(L1, new BigDecimal("10"), false), new QuoteLine(L2, new BigDecimal("5"), false));
    }

    private static QuoteIn quote(String name, String landed, int lead, Integer tech, boolean dq, LocalDate valid, List<QuoteLine> lines) {
        return new QuoteIn(UUID.randomUUID(), UUID.randomUUID(), name, new BigDecimal(landed), lead, tech, null, null, false, dq, valid, lines);
    }

    private static Scored row(Result r, QuoteIn q) {
        return r.rows().stream().filter(s -> s.quotationId().equals(q.quotationId())).findFirst().orElseThrow();
    }

    @Test
    void weightsMustBeNonNegativeAndTotal100() {
        assertTrue(RfqEvaluationRules.validWeights(new Weights(50, 15, 15, 10, 10)));
        assertFalse(RfqEvaluationRules.validWeights(new Weights(50, 15, 15, 10, 5)));
        assertFalse(RfqEvaluationRules.validWeights(new Weights(110, -10, 0, 0, 0)));
        assertFalse(RfqEvaluationRules.validWeights(null));
    }

    @Test
    void cheaperAndFasterQuoteIsRankedFirstAndRecommended() {
        QuoteIn cheap = quote("Cheap", "1000", 5, 80, false, null, full());
        QuoteIn dear = quote("Dear", "1250", 10, 80, false, null, full());
        Result r = RfqEvaluationRules.evaluate(REQ, List.of(dear, cheap), new Weights(60, 20, 20, 0, 0), TODAY);
        assertEquals(cheap.quotationId(), r.recommendedQuotationId());
        assertEquals(cheap.quotationId(), r.lowestPriceQuotationId());
        assertEquals(1, (int) row(r, cheap).rank());
        assertEquals(2, (int) row(r, dear).rank());
        assertEquals(0, new BigDecimal("100.00").compareTo(row(r, cheap).priceScore()));
        assertEquals(0, new BigDecimal("80.00").compareTo(row(r, dear).priceScore()));
        assertTrue(row(r, cheap).totalScore().compareTo(row(r, dear).totalScore()) > 0);
    }

    @Test
    void disqualifiedIncompleteAndExpiredQuotesAreNeverRanked() {
        QuoteIn dq = quote("DQ", "500", 1, 100, true, null, full());
        QuoteIn partial = quote("Partial", "600", 1, 100, false, null,
                List.of(new QuoteLine(L1, new BigDecimal("10"), false), new QuoteLine(L2, null, true)));
        QuoteIn lapsed = quote("Lapsed", "700", 1, 100, false, TODAY.minusDays(1), full());
        QuoteIn ok = quote("OK", "900", 7, 60, false, TODAY.plusDays(30), full());
        Result r = RfqEvaluationRules.evaluate(REQ, List.of(dq, partial, lapsed, ok), new Weights(50, 20, 30, 0, 0), TODAY);
        assertEquals(ok.quotationId(), r.recommendedQuotationId());
        assertEquals(ok.quotationId(), r.lowestPriceQuotationId(), "cheaper quotes that are not ranked do not count as lowest");
        for (QuoteIn q : List.of(dq, partial, lapsed)) {
            assertFalse(row(r, q).ranked());
            assertNull(row(r, q).rank());
            assertFalse(row(r, q).flags().isEmpty());
        }
        assertTrue(row(r, lapsed).expired());
        assertFalse(row(r, partial).complete());
        assertEquals(ok.quotationId(), r.rows().get(0).quotationId(), "ranked rows come first");
    }

    @Test
    void shortQuantityIsNotComplete() {
        QuoteIn lowQty = quote("Low", "100", 1, null, false, null,
                List.of(new QuoteLine(L1, new BigDecimal("9"), false), new QuoteLine(L2, new BigDecimal("5"), false)));
        Result r = RfqEvaluationRules.evaluate(REQ, List.of(lowQty), new Weights(100, 0, 0, 0, 0), TODAY);
        assertNull(r.recommendedQuotationId());
        assertFalse(row(r, lowQty).complete());
    }

    @Test
    void criterionWithNoDataIsDroppedAndWeightsRenormalised() {
        // Technical has weight but nobody was scored: total must equal the price/lead blend, not be pulled down.
        QuoteIn only = quote("Only", "1000", 5, null, false, null, full());
        Result r = RfqEvaluationRules.evaluate(REQ, List.of(only), new Weights(50, 25, 25, 0, 0), TODAY);
        Scored s = row(r, only);
        assertNull(s.technicalScore());
        assertEquals(0, new BigDecimal("100.00").compareTo(s.totalScore()));
        assertTrue(s.flags().stream().anyMatch(f -> f.contains("Technical")));
    }

    @Test
    void bbbeeLevelScoresAndExpiredCertificateScoresNothing() {
        QuoteIn l1 = new QuoteIn(UUID.randomUUID(), UUID.randomUUID(), "L1", new BigDecimal("100"), 1, null, null, 1, false, false, null, full());
        QuoteIn l4 = new QuoteIn(UUID.randomUUID(), UUID.randomUUID(), "L4", new BigDecimal("100"), 1, null, null, 4, false, false, null, full());
        QuoteIn old = new QuoteIn(UUID.randomUUID(), UUID.randomUUID(), "Old", new BigDecimal("100"), 1, null, null, 1, true, false, null, full());
        Result r = RfqEvaluationRules.evaluate(REQ, List.of(l1, l4, old), new Weights(0, 0, 0, 0, 100), TODAY);
        assertEquals(0, new BigDecimal("100.00").compareTo(row(r, l1).bbbeeScore()));
        assertEquals(0, new BigDecimal("62.50").compareTo(row(r, l4).bbbeeScore()));
        assertEquals(0, BigDecimal.ZERO.compareTo(row(r, old).bbbeeScore()));
        assertEquals(l1.quotationId(), r.recommendedQuotationId());
    }

    @Test
    void tiesBreakOnLowerLandedCostThenShorterLeadTime() {
        // Identical scores via technical-only weighting; cheaper wins the tie.
        QuoteIn a = quote("A", "900", 9, 70, false, null, full());
        QuoteIn b = quote("B", "800", 9, 70, false, null, full());
        Result r = RfqEvaluationRules.evaluate(REQ, List.of(a, b), new Weights(0, 0, 100, 0, 0), TODAY);
        assertEquals(b.quotationId(), r.recommendedQuotationId());
    }

    @Test
    void noQuotesGivesAnEmptyResult() {
        Result r = RfqEvaluationRules.evaluate(REQ, List.of(), new Weights(50, 15, 15, 10, 10), TODAY);
        assertTrue(r.rows().isEmpty());
        assertNull(r.recommendedQuotationId());
        assertNull(r.lowestPriceQuotationId());
    }
}
