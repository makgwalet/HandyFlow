package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SamplingDtos {
    private SamplingDtos() {
    }

    /** The settings of one rule version. */
    public record SettingsRequest(@NotNull LocalDate effectiveFrom, @NotBlank String basis, BigDecimal percentage, Integer minCount, Integer maxCount,
                                  @NotBlank String selectionMethod, String riskFactors, Integer extraTriggerQueries, Integer extraLookback,
                                  BigDecimal extraPercentage, @NotBlank String rationale) {
    }

    public record RuleRequest(@NotBlank String name, @NotBlank String scopeType, UUID programmeId, UUID cohortId, String assessmentType, @Valid @NotNull SettingsRequest settings) {
    }

    public record RenameRequest(@NotBlank String name) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record VersionView(UUID id, int versionNo, LocalDate effectiveFrom, String basis, BigDecimal percentage, Integer minCount, Integer maxCount,
                              String selectionMethod, String riskFactors, Integer extraTriggerQueries, Integer extraLookback, BigDecimal extraPercentage,
                              String rationale, Instant createdAt) {
    }

    public record RuleView(UUID id, String ruleNo, String name, String scopeType, UUID programmeId, String programmeTitle, UUID cohortId, String cohortName,
                           String assessmentType, String status, String retiredReason, VersionView current, List<VersionView> versions) {
    }

    public record TargetRequest(@NotNull UUID attemptId, @NotBlank String note) {
    }

    public record DrawRequest(@NotNull UUID ruleId, @NotNull LocalDate decidedFrom, @NotNull LocalDate decidedTo, Long seed, @Valid List<TargetRequest> targeted) {
    }

    public record ItemView(UUID id, UUID attemptId, String attemptNo, String instrumentTitle, String learnerName, UUID assessorId, String assessorName,
                           String decision, int riskScore, String riskFactors, boolean selected, String selectedReason, String targetNote,
                           String moderationNo, String moderationOutcome, boolean moderationResolved) {
    }

    public record ActionView(UUID id, UUID itemId, String description, String ownerName, LocalDate dueOn, String status, LocalDate closedOn, String closedNote) {
    }

    public record DrawRow(UUID id, String drawNo, String ruleNo, String ruleName, int versionNo, LocalDate drawnOn, LocalDate decidedFrom, LocalDate decidedTo,
                          int eligibleCount, int requiredCount, int selectedCount, String status, String decision) {
    }

    public record DrawView(UUID id, String drawNo, UUID ruleId, String ruleNo, String ruleName, int versionNo, String ruleSnapshot, LocalDate drawnOn,
                           LocalDate decidedFrom, LocalDate decidedTo, long seed, int eligibleCount, int requiredCount, int selectedCount, String status,
                           UUID drawnBy, String decision, String decisionNote, String summary, String voidReason, int moderatedCount,
                           List<ItemView> items, List<ActionView> actions) {
    }

    public record CloseRequest(@NotBlank String decision, @NotBlank String note, boolean confirmAlone) {
    }

    public record ActionRequest(UUID itemId, @NotBlank String description, @NotBlank String ownerName, LocalDate dueOn) {
    }

    public record ActionCloseRequest(boolean done, @NotBlank String note) {
    }
}
