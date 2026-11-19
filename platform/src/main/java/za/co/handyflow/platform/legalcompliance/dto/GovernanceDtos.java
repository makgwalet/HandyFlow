package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.*;

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
}
