package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * What a quality review requires. Pure, so the rules can be tested without a database. A review checks work; it never scores a client or
 * says a client is compliant.
 */
public final class QualityRules {

    private QualityRules() {}

    public static final Set<String> SUBJECT_TYPES = Set.of("ASSESSMENT", "CASE", "QUOTE", "OTHER");
    public static final Set<String> SAMPLEABLE = Set.of("ASSESSMENT", "CASE");
    public static final Set<String> RESULTS = Set.of("PENDING", "PASS", "FAIL", "NA");
    public static final int MIN_NOTE = 10;
    public static final int MIN_OUTCOME_NOTE = 15;
    public static final int MAX_ITEMS = 40;
    public static final int MAX_SAMPLE_DAYS = 366;
    public static final int STALE_DAYS = 7;

    public record Line(int position, boolean critical, String result) {}

    public static String subjectType(String raw) {
        String t = raw == null ? "" : raw.trim().toUpperCase();
        if (!SUBJECT_TYPES.contains(t)) throw new IllegalArgumentException("Say what is being reviewed: an assessment, a case, a quote or something else");
        return t;
    }

    public static String text(String raw, String what, int max, boolean required) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) {
            if (required) throw new IllegalArgumentException(what + " is required");
            return null;
        }
        if (t.length() > max) throw new IllegalArgumentException(what + " is too long (at most " + max + " characters)");
        return t;
    }

    public static String result(String raw) {
        String r = raw == null ? "" : raw.trim().toUpperCase();
        if (!RESULTS.contains(r)) throw new IllegalArgumentException("A check is marked pass, fail, not applicable or pending");
        return r;
    }

    /** A failed check, or one that does not apply, says why. */
    public static String itemNote(String result, String raw) {
        String t = raw == null ? "" : raw.trim();
        boolean needed = "FAIL".equals(result) || "NA".equals(result);
        if (needed && t.length() < MIN_NOTE) {
            throw new IllegalArgumentException(("FAIL".equals(result) ? "Say what is wrong" : "Say why this check does not apply") + " (at least " + MIN_NOTE + " characters)");
        }
        if (t.isEmpty()) return null;
        return t.length() > 2000 ? t.substring(0, 2000) : t;
    }

    /** The reason a review cannot be completed with this outcome, or null. */
    public static String completionProblem(String outcome, List<Line> lines) {
        if (!"APPROVED".equals(outcome) && !"REWORK_REQUIRED".equals(outcome)) return "The outcome is approved, or rework required";
        boolean pending = lines.stream().anyMatch(l -> "PENDING".equals(l.result()));
        boolean failed = lines.stream().anyMatch(l -> "FAIL".equals(l.result()));
        boolean criticalFailed = lines.stream().anyMatch(l -> l.critical() && "FAIL".equals(l.result()));
        if ("APPROVED".equals(outcome)) {
            if (pending) return "Mark every check before approving";
            if (criticalFailed) return "A critical check failed, so the work cannot be approved. Send it back for rework";
            return null;
        }
        return failed ? null : "Mark at least one check as failed to send the work back, so the person doing the rework knows what to fix";
    }

    public static String outcomeNote(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() < MIN_OUTCOME_NOTE) throw new IllegalArgumentException("The review needs a summary of at least " + MIN_OUTCOME_NOTE + " characters");
        return t.length() > 2000 ? t.substring(0, 2000) : t;
    }

    /** The person who prepared the work reviews it only with a recorded reason. Returns the cleaned reason, or null when it is not needed. */
    public static String exception(UUID preparedBy, UUID reviewer, String reason) {
        if (reviewer == null) throw new IllegalArgumentException("A reviewer is required");
        if (preparedBy == null || !preparedBy.equals(reviewer)) return null;
        String r = reason == null ? "" : reason.trim();
        if (r.length() < MIN_OUTCOME_NOTE) {
            throw new IllegalArgumentException("You prepared this work, so someone else should review it. If no one else can, give a reason of at least " + MIN_OUTCOME_NOTE + " characters");
        }
        return r.length() > 1000 ? r.substring(0, 1000) : r;
    }

    /** How many to review from a pool: the rate rounded up, and at least one when there is anything to review. */
    public static int sampleSize(int pool, int ratePercent) {
        if (pool <= 0 || ratePercent <= 0) return 0;
        return Math.min(pool, Math.max(1, (int) Math.ceil(pool * Math.min(ratePercent, 100) / 100.0)));
    }

    /** A random sample, in a stable order for a given random source so it can be tested. */
    public static <T> List<T> pick(List<T> pool, int ratePercent, Random random) {
        List<T> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, random);
        return new ArrayList<>(copy.subList(0, sampleSize(copy.size(), ratePercent)));
    }

    public static void checkSample(LocalDate from, LocalDate to, int ratePercent) {
        if (from == null || to == null) throw new IllegalArgumentException("Choose the period to sample from");
        if (to.isBefore(from)) throw new IllegalArgumentException("The end of the period is before its start");
        if (ChronoUnit.DAYS.between(from, to) > MAX_SAMPLE_DAYS) throw new IllegalArgumentException("Sample from at most a year at a time");
        if (ratePercent < 1 || ratePercent > 100) throw new IllegalArgumentException("The sample rate is between 1 and 100 percent");
    }

    public static List<String> actions(String status, boolean hasNextRound) {
        return switch (status) {
            case "IN_PROGRESS" -> List.of("RECORD", "COMPLETE");
            case "REWORK_REQUIRED" -> hasNextRound ? List.of() : List.of("NEXT_ROUND");
            default -> List.of();
        };
    }

    // ---- the work queue ----

    /** A review that has been open a week is medium; a review sent back with no next round is medium; anything else is low. */
    public static WorkQueueRules.Item queueItem(LocalDate today, UUID clientId, String clientName, String label, String status, java.time.Instant since) {
        long age = since == null ? 0 : ChronoUnit.DAYS.between(since.atZone(java.time.ZoneOffset.UTC).toLocalDate(), today);
        boolean rework = "REWORK_REQUIRED".equals(status);
        String detail = rework ? "Sent back for rework, no new round yet" : age <= 0 ? "Review started today" : "Review open for " + age + (age == 1 ? " day" : " days");
        return new WorkQueueRules.Item(rework ? "QUALITY_REWORK" : "QUALITY_REVIEW", rework || age > STALE_DAYS ? WorkQueueRules.MEDIUM : WorkQueueRules.LOW, clientId, clientName, label, detail, null, "quality", false);
    }
}
