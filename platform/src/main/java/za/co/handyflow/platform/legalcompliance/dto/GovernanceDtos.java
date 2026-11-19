package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Governance committees: members, meetings, minutes, decisions and actions. */
public final class GovernanceDtos {

    private GovernanceDtos() {}

    public record CommitteeRequest(@NotBlank @Size(max = 255) String name, @NotNull CommitteeType committeeType, String charter, int meetingFrequencyMonths, int quorumMin, int charterReviewMonths) {}

    public record MemberRequest(@NotBlank @Size(max = 255) String memberName, UUID userId, @NotNull MemberRole memberRole, @NotNull LocalDate appointedOn) {}

    public record EndMemberRequest(@NotNull LocalDate endedOn) {}

    public record CharterReviewRequest(@NotNull LocalDate reviewedOn) {}

    public record MeetingRequest(@NotNull LocalDate meetingDate, @NotBlank @Size(max = 255) String title, String agenda) {}

    public record HoldRequest(@NotNull List<UUID> attendeeIds) {}

    public record MinutesRequest(@NotBlank String minutes) {}

    public record ResolutionRequest(@NotNull ResolutionKind kind, @NotBlank String description, @Size(max = 255) String ownerName, LocalDate dueDate) {}

    public record CompleteRequest(@NotNull LocalDate completedOn, String note) {}

    public record MemberResponse(UUID id, String memberName, UUID userId, MemberRole memberRole, LocalDate appointedOn, LocalDate endedOn, boolean active) {}

    public record ResolutionResponse(UUID id, UUID meetingId, UUID committeeId, ResolutionKind kind, String description, String ownerName, LocalDate dueDate, ResolutionStatus status,
                                     LocalDate completedOn, String note, boolean overdue, String committeeName, LocalDate meetingDate) {}

    public record MeetingResponse(UUID id, UUID committeeId, LocalDate meetingDate, String title, String agenda, MeetingStatus status, List<String> attendees, Integer presentCount, Boolean quorumMet,
                                  String cancelReason, String minutes, MinutesStatus minutesStatus, String minutesByName, String minutesApprovedByName, LocalDate minutesApprovedOn, boolean canRecordResolutions) {}

    public record MeetingDetail(MeetingResponse meeting, List<ResolutionResponse> resolutions) {}

    public record CommitteeResponse(UUID id, String reference, String name, CommitteeType committeeType, String charter, int meetingFrequencyMonths, int quorumMin, int charterReviewMonths,
                                    LocalDate lastCharterReview, CommitteeStatus status, String dissolvedReason, int activeMembers, int qualifyingMembers, boolean hasChair, LocalDate lastHeld,
                                    LocalDate nextScheduled, boolean meetingOverdue, boolean charterReviewOverdue, int openActions, int overdueActions, int minutesPending, String createdByName) {}

    public record CommitteeDetail(CommitteeResponse committee, List<MemberResponse> members, List<MeetingResponse> meetings) {}

    public record GovernanceSummary(long committees, long meetingsOverdue, long charterReviewOverdue, long minutesPending, long actionsOpen, long actionsOverdue, long withoutQuorumCapacity) {}

    // ── Policies ──

    /** reviewMonths 0 means "use the default" (12 on create, unchanged on update). The text is required on create and ignored on update. */
    public record PolicyRequest(@NotBlank @Size(max = 255) String title, @NotNull PolicyType policyType, UUID ownerId, @NotBlank @Size(max = 255) String ownerName,
                                @Min(0) @Max(120) int reviewMonths, String content) {}

    public record VersionRequest(@NotBlank String content, String changeSummary) {}

    public record PublishRequest(LocalDate effectiveDate) {}

    public record PolicyResponse(UUID id, String reference, String title, PolicyType policyType, String ownerName, int reviewMonths, PolicyStatus status,
                                 Integer publishedVersion, LocalDate effectiveDate, LocalDate nextReviewDue, boolean reviewOverdue, boolean reviewDueSoon,
                                 PolicyVersionStatus openVersionStatus, Integer openVersion, String retiredReason, int openCampaigns) {}

    /** content is included only where the caller may read the text (the policy detail does; a list would not). */
    public record VersionResponse(UUID id, int versionNumber, PolicyVersionStatus status, String content, String changeSummary, String authorName, Instant submittedAt,
                                  String contentHash, String approvedByName, Instant approvedAt, String reviewNote, int returnedCount, LocalDate effectiveDate,
                                  String publishedByName, Instant publishedAt) {
        public static VersionResponse of(PolicyVersion v, boolean includeContent) {
            return new VersionResponse(v.getId(), v.getVersionNumber(), v.getStatus(), includeContent ? v.getContent() : null, v.getChangeSummary(), v.getAuthorName(),
                    v.getSubmittedAt(), v.getContentHash(), v.getApprovedByName(), v.getApprovedAt(), v.getReviewNote(), v.getReturnedCount(), v.getEffectiveDate(),
                    v.getPublishedByName(), v.getPublishedAt());
        }
    }

    public record PolicyDetailResponse(PolicyResponse policy, List<VersionResponse> versions) {}

    public record PolicySummary(long active, long reviewOverdue, long reviewDueSoon, long awaitingApproval, long drafts) {}

    // ── Attestations ──

    public record CampaignRequest(@NotNull UUID versionId, @NotBlank @Size(max = 255) String title, String instructions, @NotNull LocalDate dueDate,
                                  @Size(max = 100) String department) {}

    public record RespondRequest(@NotNull Boolean accept, String comment) {}

    public record CampaignResponse(UUID id, String reference, UUID policyId, String policyTitle, int versionNumber, String title, String instructions, LocalDate dueDate,
                                   boolean overdue, String department, CampaignStatus status, String launchedByName, Instant closedAt, String closeReason, int total,
                                   int accepted, int declined, int pending, int percentComplete) {}

    public record AttestationRow(UUID id, String userName, String userEmail, AttestationStatus status, Instant respondedAt, String comment) {
        public static AttestationRow of(Attestation a) {
            return new AttestationRow(a.getId(), a.getUserName(), a.getUserEmail(), a.getStatus(), a.getRespondedAt(), a.getComment());
        }
    }

    public record CampaignDetailResponse(CampaignResponse campaign, List<AttestationRow> attestations) {}

    /** One person's own attestation, with the exact policy text they are asked to accept. */
    public record MyAttestation(UUID id, UUID campaignId, String campaignTitle, String instructions, String policyTitle, int versionNumber, String content, LocalDate dueDate,
                                boolean overdue, boolean campaignOpen, AttestationStatus status, Instant respondedAt, String comment) {}

    // ── Incidents ──

    public record IncidentRequest(@NotNull IncidentType incidentType, @NotBlank @Size(max = 255) String title, @NotBlank String description, @NotNull FindingSeverity severity,
                                  Instant occurredAt, @NotNull Instant discoveredAt, boolean personalInfoInvolved, @Min(0) Integer dataSubjectsAffected, String dataCategories,
                                  UUID controlId) {}

    public record InvestigateRequest(UUID investigatorId, @NotBlank @Size(max = 255) String investigatorName) {}

    public record ResolveIncidentRequest(@NotBlank String containmentActions, @NotBlank String rootCause, @NotBlank String resolutionNote) {}

    public record AssessRequest(@NotNull Boolean required, @NotBlank String rationale) {}

    public record RegulatorNotifiedRequest(@NotNull Instant notifiedAt, @Size(max = 100) String reference) {}

    public record SubjectsNotifiedRequest(@NotNull Instant notifiedAt) {}

    public record IncidentResponse(UUID id, String reference, IncidentType incidentType, String title, String description, FindingSeverity severity, IncidentStatus status,
                                   Instant occurredAt, Instant discoveredAt, String reportedByName, UUID controlId, String controlLabel, boolean personalInfoInvolved,
                                   Integer dataSubjectsAffected, String dataCategories, String investigatorName, String containmentActions, String rootCause,
                                   String resolutionNote, Instant resolvedAt, Instant notificationAssessedAt, String notificationAssessedBy, Boolean notificationRequired,
                                   String notificationRationale, Instant regulatorNotifiedAt, String regulatorReference, Instant subjectsNotifiedAt,
                                   String subjectsNotNotifiedReason, String closedByName, Instant closedAt, String closureNote, boolean assessmentOverdue, int capaCount,
                                   int capaVerified, String closeBlocker) {}

    public record IncidentSummary(long open, long critical, long high, long awaitingAssessment, long awaitingNotification, long resolved) {}
}
