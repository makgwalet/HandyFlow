package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Privacy impact assessments, PAIA, regulator filings, FICA and third parties. ReasonRequest / FindingNoteRequest / EvidenceRef come from AssuranceDtos. */
public final class InfoGovDtos {

    private InfoGovDtos() {}

    // ── PIA ──
    public record PiaRequest(@NotBlank @Size(max = 255) String title, UUID popiaActivityId, @NotBlank String description, @NotBlank String necessity, @NotBlank String risks,
                             @NotBlank String mitigations, @NotNull RiskLevel residualRisk) {}

    public record PiaApproveRequest(String note, int reviewMonths) {}

    public record PiaResponse(UUID id, String reference, String title, UUID popiaActivityId, String activityName, String description, String necessity, String risks, String mitigations,
                              RiskLevel residualRisk, PiaStatus status, String assessorName, Instant submittedAt, String reviewerName, Instant reviewedAt, String reviewNote,
                              LocalDate nextReviewDue, boolean reviewOverdue) {}

    public record PiaSummary(long draft, long submitted, long approved, long rejected, long highResidual, long reviewOverdue) {}

    // ── PAIA ──
    public record PaiaRequestBody(@NotBlank @Size(max = 255) String requesterName, @Size(max = 255) String requesterContact, @Size(max = 255) String onBehalfOf,
                                  @NotBlank String recordDescription, @NotNull LocalDate receivedDate, @Size(max = 255) String assignedTo) {}

    public record PaiaExtendRequest(@NotNull LocalDate until, @NotBlank String reason) {}

    public record PaiaFeeRequest(BigDecimal amount, LocalDate paidOn) {}

    public record PaiaDecideRequest(@NotNull PaiaDecision decision, @NotNull LocalDate decisionDate, @NotBlank String reason, @NotBlank @Size(max = 255) String decidedBy) {}

    public record PaiaComplaintRequest(@NotNull LocalDate lodgedOn, @Size(max = 100) String reference, String outcome) {}

    public record PaiaResponse(UUID id, String reference, String requesterName, String requesterContact, String onBehalfOf, String recordDescription, LocalDate receivedDate, LocalDate dueDate,
                               LocalDate extendedUntil, String extensionReason, LocalDate effectiveDue, PaiaStatus status, String assignedToName, BigDecimal feeAmount, LocalDate feePaidOn,
                               PaiaDecision decision, LocalDate decisionDate, String decisionReason, String decidedByName, LocalDate complaintLodgedOn, String complaintReference,
                               String complaintOutcome, boolean overdue, boolean dueSoon, boolean canExtend, LocalDate maxExtension) {}

    public record PaiaSummary(long open, long overdue, long dueSoon, long decided, long refusedOrPartial) {}

    // ── Filings ──
    public record FilingRequest(@NotNull FilingType filingType, @NotBlank @Size(max = 255) String title, @Size(max = 50) String periodLabel, @NotNull LocalDate dueDate,
                                @NotBlank @Size(max = 255) String ownerName, String notes) {}

    public record FilingSubmitRequest(@NotNull LocalDate submittedOn, @NotBlank @Size(max = 255) String submittedBy) {}

    public record RegulatorRefRequest(@NotBlank @Size(max = 100) String regulatorReference) {}

    public record FilingResponse(UUID id, String reference, FilingType filingType, String title, String periodLabel, LocalDate dueDate, String ownerName, FilingStatus status,
                                 LocalDate submittedOn, String submittedByName, String regulatorReference, List<AssuranceDtos.EvidenceRef> proof, int liveProofCount, String notes,
                                 boolean overdue, boolean dueSoon) {}

    public record FilingSummary(long pending, long overdue, long dueSoon, long submitted) {}

    // ── FICA ──
    public record FicaProfileRequest(boolean accountableInstitution, @NotBlank String basis, @Size(max = 255) String complianceOfficerName, UUID rmcpPolicyId, LocalDate rmcpApprovedOn,
                                     LocalDate rmcpNextReview) {}

    public record FicaProfileResponse(boolean configured, boolean accountableInstitution, String basis, String complianceOfficerName, UUID rmcpPolicyId, String rmcpPolicyTitle,
                                      LocalDate rmcpApprovedOn, LocalDate rmcpNextReview, boolean rmcpReviewOverdue, String updatedByName) {}

    public record FicaPartyRequest(@NotBlank @Size(max = 255) String partyName, @NotNull FicaPartyType partyType, @Size(max = 8) String identifierHint, @NotNull RiskLevel riskRating,
                                   boolean pep, String notes) {}

    public record ScreenedRequest(@NotNull LocalDate screenedOn) {}

    public record FicaVerifyRequest(String seniorApprover) {}

    public record OwnerRequest(@NotBlank @Size(max = 255) String ownerName, BigDecimal ownershipPercent, @Size(max = 255) String controlBasis) {}

    public record OwnerResponse(UUID id, String ownerName, BigDecimal ownershipPercent, String controlBasis, boolean verified) {
        public static OwnerResponse of(FicaBeneficialOwner o) { return new OwnerResponse(o.getId(), o.getOwnerName(), o.getOwnershipPercent(), o.getControlBasis(), o.isVerified()); }
    }

    public record FicaPartyResponse(UUID id, String reference, String partyName, FicaPartyType partyType, String identifierHint, RiskLevel riskRating, boolean pep, FicaPartyStatus status,
                                    LocalDate sanctionsScreenedOn, LocalDate verifiedOn, String verifiedByName, String seniorApprovalName, LocalDate nextReviewDue, boolean reviewOverdue,
                                    String notes, String createdByName, List<OwnerResponse> owners) {}

    public record FicaSummary(long pending, long enhancedRequired, long verified, long reviewOverdue, long openEscalations) {}

    public record EscalationRequest(UUID partyId, @NotBlank String description) {}

    public record EscalationDecideRequest(@NotNull EscalationDecision decision, @NotBlank String note, @Size(max = 100) String externalReference) {}

    public record EscalationResponse(UUID id, String reference, UUID partyId, String partyLabel, String description, String raisedByName, EscalationStatus status, EscalationDecision decision,
                                     String decisionNote, String decidedByName, Instant decidedAt, String externalReference) {}

    // ── Third parties ──
    public record ThirdPartyRequest(@NotBlank @Size(max = 255) String name, @NotNull ThirdPartyType partyType, @NotNull Criticality criticality, @NotNull RiskLevel riskRating,
                                    @NotBlank @Size(max = 255) String ownerName, UUID supplierRef, boolean handlesPersonalInfo, int reviewMonths, String notes) {}

    public record ThirdPartyReviewRequest(RiskLevel riskRating, @NotBlank String note) {}

    public record ItemRequest(@NotNull ItemKind kind, @NotBlank @Size(max = 255) String title, @Size(max = 255) String issuer, @Size(max = 100) String referenceNo, boolean required,
                              LocalDate validTo) {}

    public record ItemResponse(UUID id, ItemKind kind, String title, String issuer, String referenceNo, boolean required, ItemStatus status, LocalDate validTo, String expiry,
                               String verifiedByName, LocalDate verifiedOn, String note, int evidenceCount) {}

    public record ThirdPartyResponse(UUID id, String reference, String name, ThirdPartyType partyType, Criticality criticality, RiskLevel riskRating, ThirdPartyStatus status, String ownerName,
                                     UUID supplierRef, boolean handlesPersonalInfo, int reviewMonths, LocalDate lastReviewedOn, LocalDate nextReviewDue, boolean reviewOverdue,
                                     String approvedByName, LocalDate approvedOn, String notes, String createdByName, List<String> blockers, int itemCount, int expiringCount, int expiredCount) {}

    public record ThirdPartyDetail(ThirdPartyResponse party, List<ItemResponse> items) {}

    public record ThirdPartySummary(long onboarding, long approved, long suspended, long reviewOverdue, long expiring, long expired) {}
}
