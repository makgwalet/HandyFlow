package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;

import java.time.Duration;
import java.time.Instant;

/**
 * Derives the SLA state of a work order. One definition, used by the API, the monitor and the report so
 * the numbers can never disagree.
 * <ul>
 *   <li><b>Response</b> is met when the work order is first assigned (dispatched) on or before the response due time.</li>
 *   <li><b>Resolution</b> is met when the technician completes the work on or before the resolution due time.
 *       Time spent awaiting verification does not count against it, and a paused clock reads PAUSED.</li>
 * </ul>
 * States: NONE (no SLA, or cancelled/rejected), ON_TRACK, AT_RISK, PAUSED (resolution only), MET, BREACHED.
 * AT_RISK means the due time is within the larger of 30 minutes or 20% of the whole target window.
 */
public final class FmSlaStatus {

    private FmSlaStatus() {}

    public static String response(FmWorkOrder w, Instant now) {
        if (w.getResponseDueAt() == null) return "NONE";
        if (w.getRespondedAt() != null) {
            return !w.getRespondedAt().isAfter(w.getResponseDueAt()) ? "MET" : "BREACHED";
        }
        if (isWithdrawn(w)) return "NONE";
        return open(w.getCreatedAt(), w.getResponseDueAt(), now);
    }

    public static String resolution(FmWorkOrder w, Instant now) {
        if (w.getResolutionDueAt() == null) return "NONE";
        if (w.getCompletedAt() != null) {
            return !w.getCompletedAt().isAfter(w.getResolutionDueAt()) ? "MET" : "BREACHED";
        }
        if (isWithdrawn(w)) return "NONE";
        if (w.getSlaPausedAt() != null) return "PAUSED";
        return open(w.getCreatedAt(), w.getResolutionDueAt(), now);
    }

    /** The moment a still-open target tips from ON_TRACK to AT_RISK. */
    public static Instant warnAt(Instant start, Instant due) {
        long totalMinutes = Math.max(0, Duration.between(start, due).toMinutes());
        long windowMinutes = Math.max(30, totalMinutes / 5);
        return due.minus(Duration.ofMinutes(windowMinutes));
    }

    private static String open(Instant start, Instant due, Instant now) {
        if (now.isAfter(due)) return "BREACHED";
        return !now.isBefore(warnAt(start, due)) ? "AT_RISK" : "ON_TRACK";
    }

    private static boolean isWithdrawn(FmWorkOrder w) {
        return "CANCELLED".equals(w.getStatus()) || "REJECTED".equals(w.getStatus());
    }
}