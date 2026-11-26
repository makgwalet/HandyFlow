package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AttemptDtos {
    private AttemptDtos() {
    }

    public record RecordAttempt(@NotNull UUID instrumentId, @NotNull UUID memberId, @NotNull LocalDate assessedOn, @NotNull UUID assessorId,
                                BigDecimal mark, String notes) {
    }

    public record CorrectMark(BigDecimal mark, @NotBlank String reason) {
    }

    /** recordedForAssessor is required when the assessor has no login: how their decision reached the person recording it. */
    public record DecisionRequest(@NotBlank String decision, @NotBlank String basis, String recordedForAssessor) {
        public DecisionRequest(String decision, String basis) {
            this(decision, basis, null);
        }
    }

    /** Who the practitioner was and what their appointment said when the subject was recorded. standing is null for a backfilled row. */
    public record AppointmentSnapshotView(UUID id, String subjectType, UUID subjectId, String role, UUID practitionerId, String practitionerNo, String fullName,
                                          String employmentType, boolean hasLogin, UUID appointmentId, UUID programmeId, UUID authorityId, String authorityName,
                                          String credentialRef, LocalDate validFrom, LocalDate validTo, String verificationSource, LocalDate verifiedOn,
                                          String standing, String standingNote, LocalDate standingOn, boolean backfilled, Instant capturedAt) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record DecisionView(String decision, String basis, UUID decidedBy, LocalDate decidedOn) {
    }

    public record AttemptEvent(UUID id, String action, String note, UUID actedBy, Instant actedAt) {
    }

    public record AttemptView(UUID id, String attemptNo, UUID instrumentId, String instrumentNo, String instrumentTitle, BigDecimal maxMark,
                              boolean requiresModeration, UUID memberId, UUID cohortId, String cohortName, UUID enrolmentId, UUID learnerId, String learnerName,
                              int attemptSeq, LocalDate assessedOn, UUID assessorId, String assessorName, BigDecimal mark, String notes, String status,
                              String voidReason, DecisionView decision, List<AttemptEvent> history) {
    }
}
