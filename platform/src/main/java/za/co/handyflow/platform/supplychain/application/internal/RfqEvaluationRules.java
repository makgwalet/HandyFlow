package za.co.handyflow.platform.supplychain.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure RFQ comparison maths (no I/O). RECONSTRUCTED from its call sites; review the formulas below
 * before relying on the ranking.
 *
 * <p>A quotation is <b>ranked</b> only when it is not disqualified, covers every RFQ line in full
 * (no no-bids, quantity offered at least the quantity requested) and its validity has not lapsed.
 * Each criterion scores 0-100:
 * <ul>
 *   <li>price: lowest ranked landed cost / this landed cost</li>
 *   <li>lead time: shortest ranked lead time / this lead time (zero days scores 100)</li>
 *   <li>technical: the buyer's 0-100 score</li>
 *   <li>performance: the supplier scorecard, 0-100</li>
 *   <li>B-BBEE: level 1 = 100 down to level 8 = 12.5 (level 9 or none = 0); an expired certificate scores nothing</li>
 * </ul>
 * A criterion with no data (technical not scored, no scorecard history, no certificate) is dropped and the
 * remaining weights re-normalised, so a supplier is never marked down for something that has not happened.
 * Rank: total score descending, then lower landed cost, then shorter lead time.
 */
public final class RfqEvaluationRules {

    private RfqEvaluationRules() {}

    public record Weights(int price, int leadTime, int technical, int performance, int bbbee) {}

    public record LineReq(UUID rfqLineId, BigDecimal quantity) {}

    public record QuoteLine(UUID rfqLineId, BigDecimal qtyOffered, boolean noBid) {}

    public record QuoteIn(UUID quotationId, UUID supplierId, String supplierName, BigDecimal landedCost, int leadTimeDays,
                          Integer technicalScore, Integer performanceScore, Integer bbbeeLevel, boolean bbbeeExpired,
                          boolean disqualified, LocalDate validUntil, List<QuoteLine> lines) {}

    public record Scored(UUID quotationId, boolean complete, boolean expired, boolean ranked, Integer rank,
                         BigDecimal priceScore, BigDecimal leadTimeScore, BigDecimal technicalScore,
                         BigDecimal performanceScore, BigDecimal bbbeeScore, BigDecimal totalScore, List<String> flags) {}

    public record Result(List<Scored> rows, UUID recommendedQuotationId, UUID lowestPriceQuotationId) {}

    /** Every weight is zero or more and they total exactly 100. */
    public static boolean validWeights(Weights w) {
        if (w == null) return false;
        int[] a = {w.price(), w.leadTime(), w.technical(), w.performance(), w.bbbee()};
        int sum = 0;
        for (int v : a) {
            if (v < 0) return false;
            sum += v;
        }
        return sum == 100;
    }

    public static Result evaluate(List<LineReq> required, List<QuoteIn> quotes, Weights w, LocalDate today) {
        // Pass 1: eligibility.
        record Elig(QuoteIn q, boolean complete, boolean expired, boolean ranked, List<String> flags) {}
        List<Elig> elig = new ArrayList<>();
        for (QuoteIn q : quotes) {
            List<String> flags = new ArrayList<>();
            boolean complete = covers(required, q.lines());
            boolean expired = q.validUntil() != null && q.validUntil().isBefore(today);
            if (q.disqualified()) flags.add("Disqualified");
            if (!complete) flags.add("Does not quote every line in full");
            if (expired) flags.add("Quotation validity has lapsed");
            if (q.bbbeeExpired()) flags.add("B-BBEE certificate has expired");
            elig.add(new Elig(q, complete, expired, !q.disqualified() && complete && !expired, flags));
        }

        // Benchmarks come from the ranked quotations only.
        BigDecimal minLanded = null;
        Integer minLead = null;
        for (Elig e : elig) {
            if (!e.ranked()) continue;
            if (e.q().landedCost() != null && e.q().landedCost().signum() > 0
                    && (minLanded == null || e.q().landedCost().compareTo(minLanded) < 0)) minLanded = e.q().landedCost();
            if (minLead == null || e.q().leadTimeDays() < minLead) minLead = e.q().leadTimeDays();
        }

        // Pass 2: score the ranked ones.
        Map<UUID, Scored> scored = new HashMap<>();
        List<Elig> rankedList = new ArrayList<>();
        for (Elig e : elig) {
            QuoteIn q = e.q();
            if (!e.ranked()) {
                scored.put(q.quotationId(), new Scored(q.quotationId(), e.complete(), e.expired(), false, null,
                        null, null, null, null, null, null, e.flags()));
                continue;
            }
            rankedList.add(e);
            BigDecimal price = ratio(minLanded, q.landedCost());
            BigDecimal lead = q.leadTimeDays() <= 0 ? bd(100) : ratio(minLead == null ? null : BigDecimal.valueOf(minLead),
                    BigDecimal.valueOf(q.leadTimeDays()));
            BigDecimal tech = q.technicalScore() == null ? null : clamp(BigDecimal.valueOf(q.technicalScore()));
            BigDecimal perf = q.performanceScore() == null ? null : clamp(BigDecimal.valueOf(q.performanceScore()));
            BigDecimal bbbee = null;
            if (q.bbbeeLevel() != null && !q.bbbeeExpired()) {
                int lvl = q.bbbeeLevel();
                bbbee = lvl >= 1 && lvl <= 8 ? bd(100).multiply(BigDecimal.valueOf(9 - lvl)).divide(bd(8), 2, RoundingMode.HALF_UP) : bd(0);
            } else if (q.bbbeeExpired()) {
                bbbee = bd(0);
            }
            if (w.technical() > 0 && tech == null) e.flags().add("Technical score not captured - criterion left out");
            if (w.performance() > 0 && perf == null) e.flags().add("No supplier performance history - criterion left out");
            if (w.bbbee() > 0 && bbbee == null) e.flags().add("No B-BBEE level on file - criterion left out");

            BigDecimal weighted = part(price, w.price()).add(part(lead, w.leadTime())).add(part(tech, w.technical()))
                    .add(part(perf, w.performance())).add(part(bbbee, w.bbbee()));
            int used = (price != null ? w.price() : 0) + (lead != null ? w.leadTime() : 0)
                    + (tech != null ? w.technical() : 0) + (perf != null ? w.performance() : 0) + (bbbee != null ? w.bbbee() : 0);
            BigDecimal total = used == 0 ? bd(0)
                    : weighted.multiply(bd(100)).divide(BigDecimal.valueOf(used), 2, RoundingMode.HALF_UP);
            scored.put(q.quotationId(), new Scored(q.quotationId(), true, false, true, null,
                    price, lead, tech, perf, bbbee, total, e.flags()));
        }

        // Rank.
        rankedList.sort(Comparator
                .comparing((Elig e) -> scored.get(e.q().quotationId()).totalScore()).reversed()
                .thenComparing(e -> e.q().landedCost())
                .thenComparingInt(e -> e.q().leadTimeDays()));
        UUID recommended = null;
        UUID lowest = null;
        int pos = 1;
        for (Elig e : rankedList) {
            Scored s = scored.get(e.q().quotationId());
            scored.put(s.quotationId(), new Scored(s.quotationId(), s.complete(), s.expired(), true, pos++,
                    s.priceScore(), s.leadTimeScore(), s.technicalScore(), s.performanceScore(), s.bbbeeScore(),
                    s.totalScore(), s.flags()));
            if (recommended == null) recommended = s.quotationId();
        }

        BigDecimal lowestCost = null;
        for (Elig e : rankedList) {
            BigDecimal c = e.q().landedCost();
            if (c != null && (lowestCost == null || c.compareTo(lowestCost) < 0)) { lowestCost = c; lowest = e.q().quotationId(); }
        }

        // Keep the caller's order, ranked first.
        List<Scored> rows = new ArrayList<>();
        for (Elig e : rankedList) rows.add(scored.get(e.q().quotationId()));
        for (Elig e : elig) if (!e.ranked()) rows.add(scored.get(e.q().quotationId()));
        return new Result(rows, recommended, lowest);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static boolean covers(List<LineReq> required, List<QuoteLine> offered) {
        if (required.isEmpty()) return false;
        Map<UUID, QuoteLine> byLine = new HashMap<>();
        for (QuoteLine l : offered) byLine.put(l.rfqLineId(), l);
        for (LineReq r : required) {
            QuoteLine l = byLine.get(r.rfqLineId());
            if (l == null || l.noBid() || l.qtyOffered() == null || l.qtyOffered().compareTo(r.quantity()) < 0) return false;
        }
        return true;
    }

    private static BigDecimal ratio(BigDecimal best, BigDecimal mine) {
        if (best == null || mine == null || mine.signum() <= 0) return null;
        return clamp(best.multiply(bd(100)).divide(mine, 2, RoundingMode.HALF_UP));
    }

    private static BigDecimal part(BigDecimal score, int weight) {
        return score == null ? BigDecimal.ZERO : score.multiply(BigDecimal.valueOf(weight)).divide(bd(100), 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal clamp(BigDecimal v) {
        if (v.signum() < 0) return bd(0);
        return v.compareTo(bd(100)) > 0 ? bd(100) : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(int v) { return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP); }
}
