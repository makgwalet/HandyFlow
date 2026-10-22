package za.co.handyflow.platform.recruiter.dto;

import java.time.Instant;
import java.util.UUID;

/** Approval of jobs and offers, and the company's recruiting settings. */
public final class RecruiterApprovalDtos {

    private RecruiterApprovalDtos() {}

    public record SettingsView(boolean requireJobApproval, boolean requireOfferApproval, int retentionMonths) {}

    public record SettingsRequest(Boolean requireJobApproval, Boolean requireOfferApproval, Integer retentionMonths) {}

    public record DecisionRequest(Boolean approve, String note) {}

    /** Where a job or an offer stands. {@code required} says whether this company needs the approval at all; {@code canDecide} is for whoever is asking. */
    public record ApprovalView(String status, boolean required, UUID submittedBy, String submittedByName, Instant submittedAt,
                               UUID decidedBy, String decidedByName, Instant decidedAt, String note, boolean canDecide) {}

    /** An unsuccessful applicant whose retention period has ended. */
    public record RemovalCandidate(UUID applicantId, String name, String email, Instant lastActivity, Instant dueSince, int applications, boolean hasCv) {}

    public record RemovalRequest(java.util.List<UUID> applicantIds) {}

    public record Refused(UUID applicantId, String reason) {}

    public record RemovalResult(int removed, java.util.List<Refused> refused) {}
}
