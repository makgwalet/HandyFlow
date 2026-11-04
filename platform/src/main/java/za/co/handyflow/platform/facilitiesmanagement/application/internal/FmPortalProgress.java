package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalProgressResponse.Step;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns a work order into the steps a client sees: Reported, Accepted, Booked, In progress, Done. Built only from the
 * client-facing status (see {@link FmPortalStatus}) so internal states such as PENDING_VERIFICATION never show.
 * Photo rules live here too: a client sees general and "before" photos, and "after" photos only once the job is done.
 */
public final class FmPortalProgress {

    public static final Set<String> PHOTO_KINDS = Set.of("PHOTO", "BEFORE_PHOTO", "AFTER_PHOTO");

    private FmPortalProgress() {}

    /** May a client see a file of this kind and type on a job in this internal status? */
    public static boolean photoVisible(String evidenceType, String contentType, String internalStatus) {
        if (evidenceType == null || !PHOTO_KINDS.contains(evidenceType)) return false;
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) return false;
        return !"AFTER_PHOTO".equals(evidenceType) || "COMPLETED".equals(internalStatus);
    }

    public static List<Step> steps(String internalStatus, Instant createdAt, Instant approvedAt, LocalDate scheduledDate,
                                   Instant completedAt) {
        String status = FmPortalStatus.present(internalStatus);
        List<Step> out = new ArrayList<>();
        out.add(new Step("REPORTED", "Reported", "DONE", createdAt, null));
        if ("DECLINED".equals(status)) {
            out.add(new Step("DECLINED", "Declined", "CURRENT", null, null));
            return out;
        }
        if ("CANCELLED".equals(status)) {
            out.add(new Step("CANCELLED", "Cancelled", "CURRENT", null, null));
            return out;
        }
        int stage = switch (status == null ? "" : status) {
            case "SUBMITTED" -> 1;
            case "OPEN", "ASSIGNED", "ON_HOLD" -> 2;
            case "IN_PROGRESS" -> 3;
            case "COMPLETED" -> 4;
            default -> 0;
        };
        Instant booked = scheduledDate == null ? null : scheduledDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        out.add(step("ACCEPTED", "Accepted by the team", 1, stage, approvedAt, null));
        out.add(step("BOOKED", "Booked", 2, stage, booked, "ON_HOLD".equals(status) ? "On hold for now" : null));
        out.add(step("IN_PROGRESS", "Work under way", 3, stage, null, null));
        out.add(step("DONE", "Done", 4, stage, completedAt, null));
        return out;
    }

    private static Step step(String key, String label, int index, int stage, Instant at, String note) {
        String state = index < stage || (index == stage && stage == 4) ? "DONE" : index == stage ? "CURRENT" : "PENDING";
        return new Step(key, label, state, "DONE".equals(state) || "CURRENT".equals(state) ? at : null, note);
    }
}
