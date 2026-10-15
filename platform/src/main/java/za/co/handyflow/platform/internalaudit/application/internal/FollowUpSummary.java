package za.co.handyflow.platform.internalaudit.application.internal;

import java.util.List;

/** Counts for one follow-up engagement, and the plain-text lines used in a follow-up report. */
public final class FollowUpSummary {
    private FollowUpSummary() {}

    public record Line(String title, String severity, String outcome, String note) {}
    public record Counts(int total, int assessed, int implemented, int partial, int notImplemented, int noLongerApplicable, int implementedPct, boolean complete) {}

    public static Counts count(List<String> outcomes) {
        int total = outcomes.size(), a = 0, i = 0, p = 0, n = 0, na = 0;
        for (String o : outcomes) {
            if (o == null) continue;
            a++;
            switch (o) { case "IMPLEMENTED" -> i++; case "PARTIALLY_IMPLEMENTED" -> p++; case "NOT_IMPLEMENTED" -> n++; case "NO_LONGER_APPLICABLE" -> na++; default -> { } }
        }
        int denominator = total - na;   // findings that no longer apply are not counted against the business
        int pct = denominator <= 0 ? 0 : Math.round(i * 100f / denominator);
        return new Counts(total, a, i, p, n, na, pct, total > 0 && a == total);
    }

    private static String label(String outcome) {
        return switch (outcome == null ? "" : outcome) {
            case "IMPLEMENTED" -> "Implemented";
            case "PARTIALLY_IMPLEMENTED" -> "Partially implemented";
            case "NOT_IMPLEMENTED" -> "Not implemented";
            case "NO_LONGER_APPLICABLE" -> "No longer applicable";
            default -> "Not yet assessed";
        };
    }

    /** The follow-up section of a report: each earlier finding, worst first, with what was found. */
    public static String reportText(List<Line> lines) {
        if (lines.isEmpty()) return "No earlier findings were followed up.";
        List<String> order = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
        List<Line> sorted = new java.util.ArrayList<>(lines);
        sorted.sort(java.util.Comparator.comparingInt((Line l) -> {
            int i = order.indexOf(l.severity() == null ? "" : l.severity().toUpperCase());
            return i < 0 ? order.size() : i;
        }).thenComparing(Line::title, String.CASE_INSENSITIVE_ORDER));
        Counts c = count(lines.stream().map(Line::outcome).toList());
        StringBuilder sb = new StringBuilder();
        sb.append(c.implemented()).append(" of ").append(c.total() - c.noLongerApplicable()).append(" applicable findings are implemented (")
          .append(c.implementedPct()).append("%).");
        int n = 1;
        for (Line l : sorted) {
            sb.append("\n\n").append(n++).append(". ").append(l.title()).append(" [").append(l.severity() == null ? "unrated" : l.severity().toUpperCase()).append("] — ").append(label(l.outcome()));
            if (l.note() != null && !l.note().isBlank()) sb.append("\n").append(l.note().trim());
        }
        return sb.toString();
    }
}
