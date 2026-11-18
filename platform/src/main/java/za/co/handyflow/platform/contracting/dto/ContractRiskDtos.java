package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Risk rules and risk checks. The rules come from the tenant's legal reviewer; none are built in. */
public final class ContractRiskDtos {
    private ContractRiskDtos() {}

    public record RuleRequest(@NotBlank String name, String description, @NotBlank String ruleType, String paramText, BigDecimal paramNumber,
                              String appliesToTypes, @NotBlank String severity, boolean blocksSending, @NotBlank String message,
                              String remediation, String legalArea, String jurisdiction) {}

    public record ApproveRuleRequest(@NotBlank String reviewerName, @NotBlank String reviewerProfessionalDetails, @NotNull LocalDate reviewDate,
                                     LocalDate nextReviewDate, String notes) {}

    public record WithdrawRuleRequest(@NotBlank String reason) {}

    /** legalStatus: NOT_REVIEWED, REVIEWED, REVIEW_EXPIRED, SUPERSEDED or WITHDRAWN. A review is evidence, not a guarantee. */
    public record RuleResponse(UUID id, UUID familyId, int versionNo, String name, String description, String ruleType, String paramText, BigDecimal paramNumber,
                               String appliesToTypes, String severity, boolean blocksSending, String message, String remediation, String legalArea,
                               String jurisdiction, String status, String legalStatus, String reviewerName, String reviewerProfessionalDetails,
                               LocalDate reviewDate, LocalDate nextReviewDate, String reviewNotes, String withdrawnReason, boolean active) {}

    /** A rule the contract tripped, with who approved the rule and when so the finding can be traced. */
    public record Finding(UUID ruleId, String ruleName, int ruleVersion, String severity, String message, String remediation, boolean blocksSending,
                          String reviewedBy, LocalDate reviewDate) {}

    /** A library clause in the contract that is no longer current (superseded, withdrawn, or its review lapsed). Not a legal rule. */
    public record IntegrityFinding(UUID clauseId, String clauseTitle, int clauseVersion, String state, String message) {}

    /**
     * riskLevel: NOT_ASSESSED when no published rule applied (nothing was checked, which is not the same as low risk), NONE when rules ran
     * and none failed, otherwise the highest severity among the failed rules.
     */
    public record AssessmentResponse(UUID id, UUID contractId, Integer contractVersionNo, String riskLevel, int rulesEvaluated, int rulesSkipped,
                                     List<Finding> findings, List<IntegrityFinding> integrityFindings, Instant assessedAt) {}
}
