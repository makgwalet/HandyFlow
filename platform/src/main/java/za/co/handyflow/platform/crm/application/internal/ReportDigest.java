package za.co.handyflow.platform.crm.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import za.co.handyflow.platform.crm.domain.model.LeadStage;

/** The words of a scheduled report. Pure, no database: the service gathers the numbers, this writes them up. */
final class ReportDigest {
    record Message(String title, String body) {}
    record Lead(LeadStage stage, BigDecimal value, LocalDate closeDate) {}
    record SourceRow(String source, long leads, long won, long lost, BigDecimal winRate) {}
    record FunnelRow(LeadStage stage, long reached) {}

    static final int MAX_SOURCES = 10;
    private static final List<LeadStage> OPEN = List.of(LeadStage.NEW, LeadStage.CONTACTED, LeadStage.QUALIFIED);

    private ReportDigest() {}

    static String money(BigDecimal v) {
        BigDecimal n = v == null ? BigDecimal.ZERO : v.setScale(0, RoundingMode.HALF_UP);
        return "R " + String.format(Locale.ROOT, "%,d", n.longValue()).replace(',', ' ');
    }

    private static String label(LeadStage s) { return s.name().charAt(0) + s.name().substring(1).toLowerCase(Locale.ROOT); }
    private static String plural(long n, String one, String many) { return n + " " + (n == 1 ? one : many); }

    static Message pipeline(List<Lead> leads, LocalDate today) {
        Map<LeadStage, long[]> count = new EnumMap<>(LeadStage.class);
        Map<LeadStage, BigDecimal> value = new EnumMap<>(LeadStage.class);
        for (LeadStage s : LeadStage.values()) { count.put(s, new long[1]); value.put(s, BigDecimal.ZERO); }
        long overdue = 0, open = 0;
        BigDecimal openValue = BigDecimal.ZERO;
        for (Lead l : leads) {
            LeadStage s = l.stage() == null ? LeadStage.NEW : l.stage();
            count.get(s)[0]++;
            if (l.value() != null) value.put(s, value.get(s).add(l.value()));
            if (OPEN.contains(s)) {
                open++;
                if (l.value() != null) openValue = openValue.add(l.value());
                if (l.closeDate() != null && l.closeDate().isBefore(today)) overdue++;
            }
        }
        StringBuilder sb = new StringBuilder("Open deals: ").append(open).append(" worth ").append(money(openValue)).append('.');
        for (LeadStage s : OPEN) sb.append("\n- ").append(label(s)).append(": ").append(count.get(s)[0]).append(" (").append(money(value.get(s))).append(')');
        if (overdue > 0) sb.append("\n").append(plural(overdue, "open deal is", "open deals are")).append(" past the expected close date.");
        sb.append("\nWon so far: ").append(count.get(LeadStage.WON)[0]).append(" (").append(money(value.get(LeadStage.WON))).append(").");
        sb.append("\nLost so far: ").append(count.get(LeadStage.LOST)[0]).append(" (").append(money(value.get(LeadStage.LOST))).append(").");
        return new Message("Pipeline summary: " + plural(open, "open deal", "open deals"), sb.toString());
    }

    static Message sources(List<SourceRow> rows) {
        if (rows.isEmpty()) return new Message("Lead sources: no leads yet", "There are no leads to report on yet.");
        StringBuilder sb = new StringBuilder("Where your leads come from:");
        int shown = Math.min(rows.size(), MAX_SOURCES);
        for (int i = 0; i < shown; i++) {
            SourceRow r = rows.get(i);
            sb.append("\n- ").append(r.source() == null || r.source().isBlank() ? "Not recorded" : r.source()).append(": ").append(plural(r.leads(), "lead", "leads"))
                    .append(", ").append(r.won()).append(" won, ").append(r.lost()).append(" lost");
            if (r.winRate() != null) sb.append(" (").append(r.winRate().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toPlainString()).append("% win rate)");
        }
        if (rows.size() > shown) sb.append("\n...and ").append(rows.size() - shown).append(" more sources.");
        long total = rows.stream().mapToLong(SourceRow::leads).sum();
        return new Message("Lead sources: " + plural(total, "lead", "leads"), sb.toString());
    }

    static Message funnel(List<FunnelRow> rows, long totalLeads, long won, BigDecimal conversion) {
        StringBuilder sb = new StringBuilder("How many leads reached each stage:");
        for (FunnelRow r : rows) sb.append("\n- ").append(label(r.stage())).append(": ").append(r.reached());
        sb.append("\n").append(totalLeads == 0 ? "No leads yet."
                : won + " of " + totalLeads + " leads were won" + (conversion == null ? "." : " (" + conversion.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%)."));
        return new Message("Sales funnel: " + plural(totalLeads, "lead", "leads"), sb.toString());
    }
}
