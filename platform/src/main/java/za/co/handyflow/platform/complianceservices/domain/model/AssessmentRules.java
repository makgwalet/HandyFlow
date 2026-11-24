package za.co.handyflow.platform.complianceservices.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How an obligation is judged in an assessment. Evidence is checked by the shared evaluator, but a document on file
 * is never enough: someone has to say the control is actually in place. Pure, so it is easy to test.
 */
public final class AssessmentRules {

    private AssessmentRules() {}

    public static final Set<String> CONTROLS = Set.of("UNKNOWN", "YES", "PARTIAL", "NO");
    public static final Set<String> WORKFLOWS = Set.of("NONE", "REMEDIATION", "AUTHORITY");
    public static final int MIN_NOTE = 15;

    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String NOT_ASSESSED = "NOT_ASSESSED";
    public static final String COMPLIANT = "COMPLIANT";
    public static final String PARTIALLY_COMPLIANT = "PARTIALLY_COMPLIANT";
    public static final String NON_COMPLIANT = "NON_COMPLIANT";
    public static final String EVIDENCE_MISSING = "EVIDENCE_MISSING";
    public static final String EXPIRED = "EXPIRED";
    public static final String UNDER_REVIEW = "UNDER_REVIEW";
    public static final String REMEDIATION_IN_PROGRESS = "REMEDIATION_IN_PROGRESS";
    public static final String AWAITING_AUTHORITY = "AWAITING_AUTHORITY";

    /** One evidence check, as the shared evaluator reported it. Result is MET, MISSING, EXPIRED or PENDING. */
    public record EvidenceCheck(String documentType, boolean mandatory, String result) {}

    /** What the required evidence says overall. Optional evidence never counts against an obligation. */
    public static String evidenceStatus(List<EvidenceCheck> checks) {
        List<EvidenceCheck> required = checks.stream().filter(EvidenceCheck::mandatory).toList();
        if (required.isEmpty()) return "NOT_REQUIRED";
        for (String bad : List.of("MISSING", "EXPIRED", "PENDING")) {
            if (required.stream().anyMatch(c -> bad.equals(c.result()))) return bad;
        }
        return "MET";
    }

    /** The status before any workflow note is applied. This is the one that is scored. */
    public static String status(String applicability, String evidenceStatus, String control) {
        if (ApplicabilityRules.NOT_APPLICABLE.equals(applicability)) return NOT_APPLICABLE;
        if (!ApplicabilityRules.APPLICABLE.equals(applicability)) return NOT_ASSESSED;
        if ("NO".equals(control)) return NON_COMPLIANT;
        switch (evidenceStatus) {
            case "MISSING": return EVIDENCE_MISSING;
            case "EXPIRED": return EXPIRED;
            case "PENDING": return UNDER_REVIEW;
            default: break;
        }
        return switch (control) {
            case "YES" -> COMPLIANT;
            case "PARTIAL" -> PARTIALLY_COMPLIANT;
            default -> NOT_ASSESSED;
        };
    }

    /** What to show: a problem that is being worked on says so, without changing the score. */
    public static String display(String status, String workflow) {
        boolean problem = Set.of(NON_COMPLIANT, PARTIALLY_COMPLIANT, EVIDENCE_MISSING, EXPIRED, UNDER_REVIEW).contains(status);
        if (!problem) return status;
        return switch (workflow) {
            case "REMEDIATION" -> REMEDIATION_IN_PROGRESS;
            case "AUTHORITY" -> AWAITING_AUTHORITY;
            default -> status;
        };
    }

    public static String control(String raw) {
        String c = raw == null || raw.isBlank() ? "UNKNOWN" : raw.trim().toUpperCase();
        if (!CONTROLS.contains(c)) throw new IllegalArgumentException("control must be one of " + CONTROLS.stream().sorted().toList());
        return c;
    }

    public static String workflow(String raw, String note) {
        String w = raw == null || raw.isBlank() ? "NONE" : raw.trim().toUpperCase();
        if (!WORKFLOWS.contains(w)) throw new IllegalArgumentException("workflow must be one of " + WORKFLOWS.stream().sorted().toList());
        if (!w.equals("NONE") && (note == null || note.trim().length() < MIN_NOTE)) {
            throw new IllegalArgumentException("Say what is being done, in at least " + MIN_NOTE + " characters");
        }
        return w;
    }

    // ---- weights and score ----

    public record Weights(int low, int medium, int high, int critical) {
        public Weights {
            for (int w : new int[]{low, medium, high, critical}) if (w < 1 || w > 100) throw new IllegalArgumentException("Each weight must be 1 to 100");
            if (!(low <= medium && medium <= high && high <= critical)) throw new IllegalArgumentException("A riskier obligation cannot weigh less than a safer one");
        }
        public static Weights defaults() { return new Weights(1, 2, 4, 8); }
        public int of(String risk) {
            if (risk == null) return medium;
            return switch (risk) { case "LOW" -> low; case "HIGH" -> high; case "CRITICAL" -> critical; default -> medium; };
        }
    }

    private static final Set<String> UNSCORED = Set.of(NOT_APPLICABLE, NOT_ASSESSED);

    public static boolean scored(String status) { return !UNSCORED.contains(status); }

    public static BigDecimal points(String status) {
        return switch (status) {
            case COMPLIANT -> BigDecimal.ONE;
            case PARTIALLY_COMPLIANT -> new BigDecimal("0.5");
            default -> BigDecimal.ZERO;
        };
    }

    public record Line(String code, String risk, String status) {}

    /** Percent is null when nothing could be scored. Not-assessed items are counted, never treated as failures or passes. */
    public record Score(BigDecimal percent, int scored, int unscored) {}

    public static Score score(List<Line> lines, Weights w) {
        BigDecimal got = BigDecimal.ZERO, max = BigDecimal.ZERO;
        int scored = 0, unscored = 0;
        for (Line l : lines) {
            if (NOT_APPLICABLE.equals(l.status())) continue;
            if (!scored(l.status())) { unscored++; continue; }
            BigDecimal weight = new BigDecimal(w.of(l.risk()));
            got = got.add(weight.multiply(points(l.status())));
            max = max.add(weight);
            scored++;
        }
        BigDecimal pct = max.signum() == 0 ? null : got.multiply(new BigDecimal("100")).divide(max, 1, RoundingMode.HALF_UP);
        return new Score(pct, scored, unscored);
    }

    // ---- completion and comparison ----

    public record DraftItem(String code, String applicability, String control, String note) {}

    /** Obligations that apply but have not been judged and carry no note. */
    public static List<String> completionBlockers(List<DraftItem> items) {
        List<String> out = new ArrayList<>();
        for (DraftItem i : items) {
            boolean applies = ApplicabilityRules.APPLICABLE.equals(i.applicability());
            boolean judged = !"UNKNOWN".equals(i.control());
            boolean explained = i.note() != null && i.note().trim().length() >= MIN_NOTE;
            if (applies && !judged && !explained) out.add(i.code());
        }
        return out;
    }

    public record Snapshot(String status, String reason) {}

    public record Change(String code, String from, String to, String reason) {}

    /** What moved since the last completed assessment, with the reason behind each move. */
    public static List<Change> changes(Map<String, Snapshot> previous, Map<String, Snapshot> current) {
        List<Change> out = new ArrayList<>();
        for (var e : current.entrySet()) {
            Snapshot before = previous.get(e.getKey());
            Snapshot now = e.getValue();
            if (before == null) out.add(new Change(e.getKey(), null, now.status(), now.reason()));
            else if (!before.status().equals(now.status())) out.add(new Change(e.getKey(), before.status(), now.status(), now.reason()));
        }
        for (var e : previous.entrySet()) {
            if (!current.containsKey(e.getKey())) out.add(new Change(e.getKey(), e.getValue().status(), null, "No longer assigned to this client"));
        }
        out.sort(java.util.Comparator.comparing(Change::code));
        return out;
    }
}
