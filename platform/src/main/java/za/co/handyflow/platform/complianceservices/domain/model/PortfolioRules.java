package za.co.handyflow.platform.complianceservices.domain.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Early warning across the portfolio: which clients show signs of trouble, and why. Pure, so every threshold can be tested.
 * It names the signs and ranks clients to look at first. It gives no score and never says a client is compliant or non-compliant;
 * a client with no signs is only a client with nothing recorded that matches these signs.
 */
public final class PortfolioRules {

    private PortfolioRules() {}

    public static final int OVERDUE_MANY = 3;
    public static final int REJECTED_REPEAT = 3;
    public static final int REJECTED_WINDOW_DAYS = 90;
    public static final int WAITING_DAYS = 14;
    public static final int STALLED_DAYS = 30;
    public static final int EXPIRY_DAYS = 30;
    public static final int AGREEMENT_SOON_DAYS = 30;

    public static final String HIGH = "HIGH";
    public static final String MEDIUM = "MEDIUM";
    public static final String LOW = "LOW";

    /** What is recorded for one client. Filled in by the service; the rules only read it. */
    public static final class Facts {
        public final UUID clientId;
        public final String name;
        public String riskRating;
        public int overdueDeadlines;
        public int oldestOverdueDays;
        public int expiredDocuments;
        public int expiringDocuments;
        public int rejectedRecently;
        public int documentsWaiting;
        public int seriousCases;
        public int overdueCases;
        public int stalledCases;
        public int overdueImpacts;
        public int openImpacts;
        /** NONE (never had one), CURRENT, ENDING_SOON, PAST_END (still marked active after its end date), LAPSED (had one, none now). */
        public String agreement = "NONE";
        public int agreementDays;

        public Facts(UUID clientId, String name) { this.clientId = clientId; this.name = name; }
    }

    public record Signal(String code, String severity, String label, String detail, String tab) {}

    public record Entry(UUID clientId, String clientName, String riskRating, int high, int medium, int low, List<Signal> signals) {}

    private static String plural(int n, String one, String many) { return n + " " + (n == 1 ? one : many); }

    /** The signs recorded for a client, most serious first. Empty when none match. */
    public static List<Signal> signals(Facts f) {
        List<Signal> out = new ArrayList<>();
        if (f.overdueDeadlines > 0) {
            out.add(new Signal("OVERDUE_DEADLINES", f.overdueDeadlines >= OVERDUE_MANY ? HIGH : MEDIUM, "Overdue deadlines",
                    plural(f.overdueDeadlines, "deadline is", "deadlines are") + " overdue, the oldest by " + plural(f.oldestOverdueDays, "day", "days"), "deadlines"));
        }
        if (f.expiredDocuments > 0) {
            out.add(new Signal("EXPIRED_DOCUMENTS", HIGH, "Expired documents", plural(f.expiredDocuments, "verified document has", "verified documents have") + " expired", "documents"));
        }
        if (f.expiringDocuments > 0) {
            out.add(new Signal("EXPIRING_DOCUMENTS", MEDIUM, "Documents expiring", plural(f.expiringDocuments, "document expires", "documents expire") + " within " + EXPIRY_DAYS + " days", "documents"));
        }
        if (f.rejectedRecently >= REJECTED_REPEAT) {
            out.add(new Signal("REPEATED_REJECTIONS", MEDIUM, "Documents keep being rejected", f.rejectedRecently + " documents were rejected in the last " + REJECTED_WINDOW_DAYS + " days", "documents"));
        }
        if (f.documentsWaiting > 0) {
            out.add(new Signal("DOCUMENTS_WAITING", MEDIUM, "Documents waiting for review", plural(f.documentsWaiting, "document has", "documents have") + " waited more than " + WAITING_DAYS + " days", "documents"));
        }
        if (f.seriousCases > 0) {
            out.add(new Signal("SERIOUS_CASES", HIGH, "Serious open cases", plural(f.seriousCases, "high-severity case is", "high-severity cases are") + " still open", "cases"));
        }
        if (f.overdueCases > 0) {
            out.add(new Signal("CASES_OVERDUE", MEDIUM, "Cases past their due date", plural(f.overdueCases, "open case is", "open cases are") + " past its due date", "cases"));
        }
        if (f.stalledCases > 0) {
            out.add(new Signal("CASES_STALLED", MEDIUM, "Remediation not finished", plural(f.stalledCases, "case has", "cases have") + " been open for more than " + STALLED_DAYS + " days", "cases"));
        }
        if (f.overdueImpacts > 0) {
            out.add(new Signal("REGULATORY_OVERDUE", HIGH, "Regulatory changes not dealt with", plural(f.overdueImpacts, "change is", "changes are") + " past the date it takes effect", "regulatory"));
        } else if (f.openImpacts > 0) {
            out.add(new Signal("REGULATORY_OPEN", LOW, "Regulatory changes to deal with", plural(f.openImpacts, "change is", "changes are") + " still to be dealt with", "regulatory"));
        }
        switch (f.agreement) {
            case "PAST_END" -> out.add(new Signal("AGREEMENT_PAST_END", HIGH, "Agreement past its end date",
                    "The agreement ended " + plural(f.agreementDays, "day", "days") + " ago but is still marked active", "agreements"));
            case "LAPSED" -> out.add(new Signal("AGREEMENT_LAPSED", MEDIUM, "No current agreement", "Earlier agreements have ended and none is active", "agreements"));
            case "ENDING_SOON" -> out.add(new Signal("AGREEMENT_ENDING", MEDIUM, "Agreement ending", "The agreement ends in " + plural(f.agreementDays, "day", "days") + " and does not renew by itself", "agreements"));
            default -> { }
        }
        if ("HIGH".equals(f.riskRating)) {
            out.add(new Signal("HIGH_RATING", LOW, "Rated high risk", "The client's risk rating is high", "profile"));
        }
        out.sort(Comparator.comparingInt(s -> rank(s.severity())));
        return out;
    }

    static int rank(String severity) { return HIGH.equals(severity) ? 0 : MEDIUM.equals(severity) ? 1 : 2; }

    public static Entry entry(Facts f) {
        List<Signal> s = signals(f);
        int high = 0, medium = 0, low = 0;
        for (Signal x : s) { if (HIGH.equals(x.severity())) high++; else if (MEDIUM.equals(x.severity())) medium++; else low++; }
        return new Entry(f.clientId, f.name, f.riskRating, high, medium, low, s);
    }

    /** Clients with at least one sign, the ones with the most serious signs first. */
    public static List<Entry> intervention(List<Facts> all) {
        List<Entry> out = new ArrayList<>();
        for (Facts f : all) {
            Entry e = entry(f);
            if (!e.signals().isEmpty()) out.add(e);
        }
        out.sort(Comparator.comparingInt((Entry e) -> -e.high()).thenComparingInt(e -> -e.medium()).thenComparingInt(e -> -e.low()).thenComparing(Entry::clientName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** What the agreements recorded for a client say. Dates are compared by day. */
    public static String[] agreementState(List<String> statuses, java.time.LocalDate earliestActiveEnd, java.time.LocalDate today) {
        boolean anyActive = statuses.contains("ACTIVE");
        boolean anyOpen = statuses.contains("ACTIVE") || statuses.contains("SUSPENDED") || statuses.contains("DRAFT");
        if (anyActive && earliestActiveEnd != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, earliestActiveEnd);
            if (days < 0) return new String[]{"PAST_END", String.valueOf(-days)};
            if (days <= AGREEMENT_SOON_DAYS) return new String[]{"ENDING_SOON", String.valueOf(days)};
        }
        if (anyActive) return new String[]{"CURRENT", "0"};
        if (!anyOpen && statuses.contains("ENDED")) return new String[]{"LAPSED", "0"};
        return new String[]{"NONE", "0"};
    }
}
