package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RplDtos {
    private RplDtos() {
    }

    public record ApplicationRequest(@NotNull UUID enrolmentId, UUID moduleId, @NotBlank String claim, @NotNull LocalDate appliedOn) {
    }

    public record EvidenceRequest(@NotBlank String kind, @NotBlank String description, String sourceRef, @NotNull LocalDate receivedOn) {
    }

    public record ReviewRequest(@NotBlank String review, @NotBlank String note, @NotNull LocalDate reviewedOn) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record DecisionRequest(@NotBlank String outcome, @NotBlank String basis, @NotNull UUID assessorId, @NotNull LocalDate decidedOn, boolean confirmAlone,
                                  String singleReason, String recordedForAssessor) {
        public DecisionRequest(String outcome, String basis, UUID assessorId, LocalDate decidedOn, boolean confirmAlone, String singleReason) {
            this(outcome, basis, assessorId, decidedOn, confirmAlone, singleReason, null);
        }
    }

    public record ConfirmRequest(@NotNull LocalDate confirmedOn) {
    }

    public record EvidenceView(UUID id, UUID applicationId, String kind, String description, String sourceRef, LocalDate receivedOn, String status, String withdrawReason,
                               String review, String reviewNote, LocalDate reviewedOn) {
    }

    public record ApplicationView(UUID id, String rplNo, UUID enrolmentId, String enrolmentNo, String learnerName, String programmeTitle, UUID moduleId, String moduleTitle,
                                  String claim, LocalDate appliedOn, String status, String outcome, String decisionBasis, String assessorName, UUID assessorId, boolean legacyAssessorText, String recordedForAssessor, LocalDate decidedOn,
                                  String singleReason, LocalDate confirmedOn, String withdrawReason, int liveEvidence, int unreviewedEvidence, int acceptedEvidence,
                                  String stateLabel) {
    }

    public record ApplicationDetail(ApplicationView application, List<EvidenceView> evidence) {
    }
}
