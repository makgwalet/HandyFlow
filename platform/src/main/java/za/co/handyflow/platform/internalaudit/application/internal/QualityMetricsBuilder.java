package za.co.handyflow.platform.internalaudit.application.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Works out the audit quality figures from plain inputs so the arithmetic can be tested without a database. */
public final class QualityMetricsBuilder {
    private QualityMetricsBuilder() {}

    public record ReviewIn(UUID engagementId, String status, String conclusion, Instant requestedAt, Instant completedAt, List<String> failedCodes) {}
    public record NoteIn(String status) {}
    public record FindingIn(boolean reopened) {}
    public record ConclusionRow(String conclusion, int count) {}
    public record FailedCheck(String code, String question, int count) {}
    public record Metrics(int reviewsCompleted, int reviewsLive, int satisfactoryPct, List<ConclusionRow> byConclusion, Double averageDaysToComplete,
                          List<FailedCheck> mostFailedChecks, int issuedWithoutCompletedReview, List<UUID> issuedWithoutCompletedReviewEngagements,
                          int reviewNotesRaised, int reviewNotesCleared, int reviewNotesClearedPct, int findingsReopened, int findingsTotal, int findingsReopenedPct) {}

    private static int pct(int a, int b) { return b == 0 ? 0 : Math.round(a * 100f / b); }

    /** @param engagementsWithIssuedReport engagements whose report has been issued. */
    public static Metrics build(List<ReviewIn> reviews, Set<UUID> engagementsWithIssuedReport, List<NoteIn> notes, List<FindingIn> findings) {
        int completed = 0, live = 0, satisfactory = 0;
        Map<String, Integer> byConclusion = new LinkedHashMap<>();
        List.of("SATISFACTORY", "NEEDS_IMPROVEMENT", "UNSATISFACTORY").forEach(c -> byConclusion.put(c, 0));
        Map<String, Integer> failed = new HashMap<>();
        long days = 0;
        Set<UUID> reviewed = new HashSet<>();
        for (ReviewIn r : reviews) {
            if ("COMPLETED".equals(r.status())) {
                completed++;
                reviewed.add(r.engagementId());
                if (r.conclusion() != null) byConclusion.merge(r.conclusion(), 1, Integer::sum);
                if ("SATISFACTORY".equals(r.conclusion())) satisfactory++;
                if (r.requestedAt() != null && r.completedAt() != null) days += Duration.between(r.requestedAt(), r.completedAt()).toHours();
                for (String code : r.failedCodes()) failed.merge(code, 1, Integer::sum);
            } else if ("REQUESTED".equals(r.status()) || "IN_PROGRESS".equals(r.status())) live++;
        }
        Double avg = completed == 0 ? null : Math.round(days / 24.0 / completed * 10) / 10.0;
        List<FailedCheck> top = failed.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .map(e -> new FailedCheck(e.getKey(), QualityChecklist.find(e.getKey()).map(QualityChecklist.Item::question).orElse(e.getKey()), e.getValue())).toList();
        List<UUID> unreviewed = engagementsWithIssuedReport.stream().filter(e -> !reviewed.contains(e)).sorted().toList();
        int cleared = (int) notes.stream().filter(n -> "CLEARED".equals(n.status())).count();
        int reopened = (int) findings.stream().filter(FindingIn::reopened).count();
        return new Metrics(completed, live, pct(satisfactory, completed), byConclusion.entrySet().stream().map(e -> new ConclusionRow(e.getKey(), e.getValue())).toList(), avg,
                top, unreviewed.size(), unreviewed, notes.size(), cleared, pct(cleared, notes.size()), reopened, findings.size(), pct(reopened, findings.size()));
    }
}
