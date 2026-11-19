package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response bodies for the organisation profile, applicability assessments and obligation generation. */
public final class ApplicabilityDtos {
    private ApplicabilityDtos() {}

    public record ProfileRequest(List<String> industries, List<String> activities, Integer employeeCount,
                                 @Size(max = 100) String entityType) {}

    public record ProfileResponse(List<String> industries, List<String> activities, Integer employeeCount,
                                  String entityType, Instant updatedAt) {
        public static ProfileResponse of(OrganisationProfile p) {
            return new ProfileResponse(p.getIndustries(), p.getActivities(), p.getEmployeeCount(), p.getEntityType(), p.getUpdatedAt());
        }
        public static ProfileResponse empty() { return new ProfileResponse(List.of(), List.of(), null, null, null); }
    }

    /** justification is mandatory for both outcomes (15+ characters) — a bare tick-box is not a decision. */
    public record AssessRequest(@NotNull UUID requirementId, @NotNull ApplicabilityDecision decision,
                                @NotBlank String justification) {}

    /** retireLinkedObligation: approving NOT_APPLICABLE when a live obligation exists needs this explicit yes. */
    public record ApproveRequest(String note, boolean retireLinkedObligation) {}

    public record RejectRequest(@NotBlank String reason) {}

    public record AssessmentResponse(
            UUID id, UUID requirementId, UUID versionId, ApplicabilityDecision decision, String justification,
            AssessmentStatus status, ApplicabilitySuggestion suggestion, List<String> suggestionReasons,
            String assessedByName, Instant assessedAt, String decidedByName, Instant decidedAt, String decisionNote,
            Instant supersededAt) {
        public static AssessmentResponse of(ApplicabilityAssessment a) {
            return new AssessmentResponse(a.getId(), a.getRequirementId(), a.getVersionId(), a.getDecision(),
                    a.getJustification(), a.getStatus(), a.getSuggestion(), a.getSuggestionReasons(),
                    a.getAssessedByName(), a.getAssessedAt(), a.getDecidedByName(), a.getDecidedAt(),
                    a.getDecisionNote(), a.getSupersededAt());
        }
    }

    /** One row of the assessor's worklist. state = UNASSESSED | PENDING | APPLICABLE | NOT_APPLICABLE. */
    public record WorklistRow(
            RegulatoryLibraryDtos.RequirementResponse requirement, String instrumentTitle, String instrumentIdentifier,
            String versionLabel, ApplicabilitySuggestion suggestion, List<String> suggestionReasons, String state,
            AssessmentResponse assessment, UUID obligationId) {}

    public record GenerateObligationRequest(
            /** Optional for now: the current UI assigns an owner by name. Supply it when a user picker exists so alerts can target the person. */
            UUID responsibleUserId,
            @NotBlank String responsibleUserName,
            @NotNull LocalDate reviewDate,
            RecurrenceInterval recurrence,
            @Size(max = 150) String departmentName) {}

    public record GenerateObligationResponse(boolean created, RegulatoryObligationResponse obligation) {}
}
