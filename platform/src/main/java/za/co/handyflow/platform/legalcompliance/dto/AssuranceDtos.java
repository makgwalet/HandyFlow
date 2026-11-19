package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.*;
import za.co.handyflow.platform.legalcompliance.domain.model.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request/response bodies for control testing and findings. */
public final class AssuranceDtos {
    private AssuranceDtos() {}

    // ── Control tests ──
    public record ScheduleTestRequest(@NotNull UUID controlId, @NotNull ControlTestType testType, @NotNull LocalDate plannedDate,
                                      UUID reviewerId, @Size(max = 255) String reviewerName, UUID retestOfTestId) {}

    public record ProgressRequest(Integer sampleSize, String sampleDescription, String notes) {}

    public record SubmitTestRequest(@NotNull TestResult result, @NotNull @Min(1) Integer sampleSize, String sampleDescription, @NotBlank String notes) {}

    public record NoteRequest(String note) {}

    public record ReasonRequest(@NotBlank String reason) {}

    public record EvidenceRef(UUID id, String fileName, long fileSizeBytes, String uploadedByName) {}

    public record TestResponse(UUID id, String reference, UUID controlId, String controlReference, String controlTitle, ControlTestType testType,
                               ControlTestStatus status, LocalDate plannedDate, boolean overdue, String procedure, String expectedEvidence,
                               DesignRating designRating, Integer sampleSize, String sampleDescription, String testerName, String reviewerName,
                               Instant startedAt, String testNotes, TestResult result, Instant submittedAt, String reviewedByName, Instant reviewedAt,
                               String reviewNote, int returnedCount, UUID retestOfTestId, String cancelReason, List<Map<String, Object>> evidenceSnapshot) {
        public static TestResponse of(ControlTest t, LocalDate today) {
            return new TestResponse(t.getId(), t.getReference(), t.getControlId(), t.getControlReference(), t.getControlTitle(), t.getTestType(), t.getStatus(),
                    t.getPlannedDate(), t.isOpen() && t.getStatus() != ControlTestStatus.SUBMITTED && t.getPlannedDate().isBefore(today), t.getProcedureText(),
                    t.getExpectedEvidence(), t.getDesignRating(), t.getSampleSize(), t.getSampleDescription(), t.getTesterName(), t.getReviewerName(), t.getStartedAt(),
                    t.getTestNotes(), t.getResult(), t.getSubmittedAt(), t.getReviewedByName(), t.getReviewedAt(), t.getReviewNote(), t.getReturnedCount(),
                    t.getRetestOfTestId(), t.getCancelReason(), t.getEvidenceSnapshot());
        }
    }

    /** liveEvidence = what is attached right now (via the evidence module); the test's own snapshot is inside `test`. */
    public record TestDetailResponse(TestResponse test, List<EvidenceRef> liveEvidence, FindingResponse finding) {}

    public record TestSummary(long planned, long inProgress, long awaitingReview, long overdue, long controlsDueForTest, long controlsNeverTested) {}

    // ── Findings ──
    public record RemediationRequest(@NotBlank String plan, UUID ownerId, @NotBlank @Size(max = 255) String ownerName, @NotNull LocalDate dueDate) {}

    public record FindingNoteRequest(@NotBlank String note) {}

    public record CloseFindingRequest(@NotBlank String note, @NotNull UUID retestId) {}

    public record FindingResponse(UUID id, String reference, String sourceType, UUID sourceId, UUID controlId, String controlLabel, String title, String description,
                                  FindingSeverity severity, FindingStatus status, String ownerName, LocalDate dueDate, boolean overdue, String remediationPlan,
                                  String resolvedByName, Instant resolvedAt, String resolutionNote, String verifiedByName, Instant verifiedAt, String verificationNote,
                                  UUID closingTestId, int reopenedCount, Instant closedAt, Instant createdAt) {
        public static FindingResponse of(Finding f, String controlLabel, LocalDate today) {
            return new FindingResponse(f.getId(), f.getReference(), f.getSourceType(), f.getSourceId(), f.getControlId(), controlLabel, f.getTitle(), f.getDescription(),
                    f.getSeverity(), f.getStatus(), f.getOwnerName(), f.getDueDate(), f.isOverdue(today), f.getRemediationPlan(), f.getResolvedByName(), f.getResolvedAt(),
                    f.getResolutionNote(), f.getVerifiedByName(), f.getVerifiedAt(), f.getVerificationNote(), f.getClosingTestId(), f.getReopenedCount(), f.getClosedAt(), f.getCreatedAt());
        }
    }

    public record FindingSummary(long open, long overdue, long critical, long high, long awaitingVerification) {}
}
