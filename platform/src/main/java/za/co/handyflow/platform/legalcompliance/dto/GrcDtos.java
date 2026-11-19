package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response bodies for the risk register, control catalogue and the links between them. */
public final class GrcDtos {
    private GrcDtos() {}

    // ── Risk ──
    public record RiskRequest(@NotBlank @Size(max = 255) String title, String description, @NotNull RiskCategory category, boolean emerging,
                              UUID ownerId, @NotBlank @Size(max = 255) String ownerName, @NotNull LocalDate nextReviewDate) {}

    public record AssessRiskRequest(@Min(1) @Max(5) int inherentLikelihood, @Min(1) @Max(5) int inherentImpact,
                                    @Min(1) @Max(5) int residualLikelihood, @Min(1) @Max(5) int residualImpact, @NotBlank String rationale) {}

    public record TreatmentRequest(@NotNull TreatmentStrategy strategy, @NotBlank String plan, @NotNull LocalDate dueDate) {}

    public record AcceptRiskRequest(@NotBlank String reason, @NotNull LocalDate expiresOn) {}

    public record ReasonRequest(@NotBlank String reason) {}

    public record SettingsRequest(@Min(1) @Max(24) int appetiteMax, @Min(2) @Max(25) int toleranceMax) {}

    public record SettingsResponse(int appetiteMax, int toleranceMax) {}

    public record AssessmentResponse(UUID id, int inherentLikelihood, int inherentImpact, int residualLikelihood, int residualImpact,
                                     int inherentScore, int residualScore, RiskRating inherentRating, RiskRating residualRating,
                                     int appetiteMax, int toleranceMax, RiskPosition residualPosition, String rationale,
                                     String assessedByName, Instant assessedAt, boolean current) {
        public static AssessmentResponse of(RiskAssessment a) {
            return new AssessmentResponse(a.getId(), a.getInherentLikelihood(), a.getInherentImpact(), a.getResidualLikelihood(), a.getResidualImpact(),
                    a.getInherentScore(), a.getResidualScore(), a.getInherentRating(), a.getResidualRating(), a.getAppetiteMax(), a.getToleranceMax(),
                    a.residualPosition(), a.getRationale(), a.getAssessedByName(), a.getAssessedAt(), a.isInForce());
        }
    }

    public record RiskResponse(UUID id, String reference, String title, String description, RiskCategory category, RiskStatus status, boolean emerging,
                               String ownerName, LocalDate nextReviewDate, boolean reviewOverdue, TreatmentStrategy treatmentStrategy, String treatmentPlan,
                               LocalDate treatmentDue, String acceptedByName, Instant acceptedAt, String acceptanceReason, LocalDate acceptanceExpires,
                               String closeReason, AssessmentResponse assessment, int controlCount, int obligationCount) {
        public static RiskResponse of(Risk r, RiskAssessment a, int controls, int obligations, LocalDate today) {
            return new RiskResponse(r.getId(), r.getReference(), r.getTitle(), r.getDescription(), r.getCategory(), r.getStatus(), r.isEmerging(),
                    r.getOwnerName(), r.getNextReviewDate(), r.isOpen() && r.getNextReviewDate().isBefore(today), r.getTreatmentStrategy(), r.getTreatmentPlan(),
                    r.getTreatmentDue(), r.getAcceptedByName(), r.getAcceptedAt(), r.getAcceptanceReason(), r.getAcceptanceExpires(), r.getCloseReason(),
                    a == null ? null : AssessmentResponse.of(a), controls, obligations);
        }
    }

    public record RiskDetailResponse(RiskResponse risk, List<AssessmentResponse> history, List<LinkItem> links) {}

    public record HeatCell(int likelihood, int impact, int count, List<String> references) {}

    public record HeatMapResponse(String basis, List<HeatCell> cells, int unassessed, int total) {}

    public record RiskSummary(long open, long critical, long high, long aboveAppetite, long outsideTolerance, long overdueReview, long accepted, long unassessed) {}

    // ── Control ──
    public record ControlRequest(@NotBlank @Size(max = 255) String title, String objective, String procedure, @NotNull ControlType controlType,
                                 @NotNull ControlNature nature, @NotNull ControlFrequency frequency, UUID ownerId,
                                 @NotBlank @Size(max = 255) String ownerName, String expectedEvidence) {}

    public record DesignAssessmentRequest(@NotNull DesignRating rating, String note) {}

    public record ControlResponse(UUID id, String reference, String title, String objective, String procedure, ControlType controlType, ControlNature nature,
                                  ControlFrequency frequency, String ownerName, String expectedEvidence, ControlStatus status, DesignRating designRating,
                                  String designAssessedByName, Instant designAssessedAt, String designNote, OperatingEffectiveness operatingEffectiveness,
                                  Instant lastTestedAt, LocalDate nextTestDue, boolean testOverdue, boolean currentlyEffective, String retireReason, int obligationCount, int riskCount) {
        public static ControlResponse of(Control c, int obligations, int risks) {
            LocalDate today = LocalDate.now();
            return new ControlResponse(c.getId(), c.getReference(), c.getTitle(), c.getObjective(), c.getProcedureText(), c.getControlType(), c.getNature(),
                    c.getFrequency(), c.getOwnerName(), c.getExpectedEvidence(), c.getStatus(), c.getDesignRating(), c.getDesignAssessedByName(),
                    c.getDesignAssessedAt(), c.getDesignNote(), c.getOperatingEffectiveness(), c.getLastTestedAt(), c.getNextTestDue(),
                    c.isActive() && c.getNextTestDue() != null && c.getNextTestDue().isBefore(today),
                    za.co.handyflow.platform.legalcompliance.application.internal.ControlTestRules.currentlyEffective(c.getOperatingEffectiveness(), c.getNextTestDue(), today),
                    c.getRetireReason(), obligations, risks);
        }
    }

    public record ControlDetailResponse(ControlResponse control, List<LinkItem> links) {}

    // ── Links ──
    public record LinkRequest(@NotNull GrcLinkType type, @NotNull UUID sourceId, @NotNull UUID targetId, @Size(max = 500) String note) {}

    /** One related item, seen from the entity being viewed. kind is RISK, CONTROL or OBLIGATION. */
    public record LinkItem(UUID linkId, String kind, UUID id, String reference, String title, String status) {}

    // ── Coverage ──
    public record ObligationCoverage(UUID obligationId, String title, String regulationReference, String category, String state,
                                     int activeControls, int testedEffectiveControls, int risks) {}

    public record RiskCoverage(UUID riskId, String reference, String title, RiskRating residualRating, int activeControls) {}

    public record CoverageResponse(int obligations, int uncovered, int coveredUntested, int coveredEffective, int risksWithoutControl,
                                   int draftControlsUnlinked, List<ObligationCoverage> obligationRows, List<RiskCoverage> unmitigatedRisks) {}
}
