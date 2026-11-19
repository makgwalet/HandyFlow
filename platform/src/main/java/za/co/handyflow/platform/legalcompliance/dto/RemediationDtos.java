package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.EvidenceRef;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Audits, audit findings and CAPA. NoteRequest / ReasonRequest / EvidenceRef are reused from AssuranceDtos. */
public final class RemediationDtos {

    private RemediationDtos() {}

    // ── Audits ──
    public record AuditRequest(@NotBlank @Size(max = 255) String title, @NotNull AuditType auditType, @NotBlank String scope, @NotBlank String objectives, String workProgramme,
                               UUID auditorId, @NotBlank @Size(max = 255) String leadAuditorName, @Size(max = 255) String auditingFirm,
                               @NotNull LocalDate plannedStart, @NotNull LocalDate plannedEnd, List<UUID> controlIds) {}

    public record CompleteAuditRequest(@NotNull AuditRating rating, @NotBlank String conclusion) {}

    public record AuditResponse(UUID id, String reference, String title, AuditType auditType, String scope, String objectives, String workProgramme, String leadAuditorName,
                                String auditingFirm, LocalDate plannedStart, LocalDate plannedEnd, AuditStatus status, boolean overdue, List<UUID> controlIds,
                                List<String> controlLabels, AuditRating overallRating, String conclusion, Instant startedAt, Instant completedAt, String completedByName,
                                String cancelReason, int findingCount, int openFindingCount) {
        public static AuditResponse of(Audit a, List<String> controlLabels, int findings, int openFindings, LocalDate today) {
            return new AuditResponse(a.getId(), a.getReference(), a.getTitle(), a.getAuditType(), a.getScope(), a.getObjectives(), a.getWorkProgramme(), a.getLeadAuditorName(),
                    a.getAuditingFirm(), a.getPlannedStart(), a.getPlannedEnd(), a.getStatus(), a.isOverdue(today), a.getControlIds(), controlLabels, a.getOverallRating(),
                    a.getConclusion(), a.getStartedAt(), a.getCompletedAt(), a.getCompletedByName(), a.getCancelReason(), findings, openFindings);
        }
    }

    public record AuditSummary(long planned, long inFieldwork, long reporting, long overdue, long completedThisYear, long openFindings, long overdueFindings) {}

    // ── Audit findings ──
    public record AuditFindingRequest(UUID controlId, @NotNull AuditFindingType findingType, @NotBlank @Size(max = 255) String title, @NotBlank String description, String rootCause,
                                      @NotNull FindingSeverity severity, UUID ownerId, @Size(max = 255) String ownerName, LocalDate dueDate) {}

    public record ManagementResponseRequest(@NotBlank String response, UUID ownerId, @NotBlank @Size(max = 255) String ownerName, @NotNull LocalDate dueDate) {}

    public record AuditFindingResponse(UUID id, String reference, UUID auditId, String auditReference, UUID controlId, String controlLabel, AuditFindingType findingType, String title,
                                       String description, String rootCause, FindingSeverity severity, AuditFindingStatus status, String ownerName, LocalDate dueDate, boolean overdue,
                                       String managementResponse, String respondedByName, Instant respondedAt, String closedByName, Instant closedAt, String closureNote,
                                       int capaCount, int capaVerified, String closeBlocker) {
        public static AuditFindingResponse of(AuditFinding f, String auditReference, String controlLabel, int capaCount, int capaVerified, String closeBlocker, LocalDate today) {
            return new AuditFindingResponse(f.getId(), f.getReference(), f.getAuditId(), auditReference, f.getControlId(), controlLabel, f.getFindingType(), f.getTitle(), f.getDescription(),
                    f.getRootCause(), f.getSeverity(), f.getStatus(), f.getOwnerName(), f.getDueDate(), f.isOverdue(today), f.getManagementResponse(), f.getRespondedByName(),
                    f.getRespondedAt(), f.getClosedByName(), f.getClosedAt(), f.getClosureNote(), capaCount, capaVerified, closeBlocker);
        }
    }

    // ── CAPA ──
    /** Exactly one of findingId (control-test finding), auditFindingId or incidentId. */
    public record CapaRequest(UUID findingId, UUID auditFindingId, UUID incidentId, @NotNull CapaType capaType, @NotBlank @Size(max = 255) String title, @NotBlank String actionPlan, @NotBlank String rootCause,
                              UUID ownerId, @NotBlank @Size(max = 255) String ownerName, @NotNull LocalDate dueDate) {}

    public record CapaReviseRequest(@NotNull CapaType capaType, @NotBlank @Size(max = 255) String title, @NotBlank String actionPlan, @NotBlank String rootCause,
                                    UUID ownerId, @NotBlank @Size(max = 255) String ownerName, @NotNull LocalDate dueDate) {}

    public record CompleteCapaRequest(@NotBlank String note) {}

    public record VerifyCapaRequest(boolean effective, @NotBlank String note) {}

    public record CapaResponse(UUID id, String reference, String parentKind, UUID parentId, String parentLabel, CapaType capaType, String title, String actionPlan, String rootCause,
                               String ownerName, LocalDate dueDate, boolean overdue, CapaStatus status, String createdByName, String approvedByName, Instant approvedAt, String approvalNote,
                               int returnedCount, String completedByName, Instant completedAt, String completionNote, List<Map<String, Object>> evidenceSnapshot, String verifiedByName,
                               Instant verifiedAt, String verificationNote, int reopenedCount, String cancelReason) {
        public static CapaResponse of(Capa c, String parentLabel, LocalDate today) {
            String kind = c.getIncidentId() != null ? "INCIDENT" : c.getAuditFindingId() != null ? "AUDIT_FINDING" : "FINDING";
            UUID parentId = c.getIncidentId() != null ? c.getIncidentId() : c.getAuditFindingId() != null ? c.getAuditFindingId() : c.getFindingId();
            return new CapaResponse(c.getId(), c.getReference(), kind, parentId, parentLabel, c.getCapaType(), c.getTitle(),
                    c.getActionPlan(), c.getRootCause(), c.getOwnerName(), c.getDueDate(), c.isOverdue(today), c.getStatus(), c.getCreatedByName(), c.getApprovedByName(), c.getApprovedAt(),
                    c.getApprovalNote(), c.getReturnedCount(), c.getCompletedByName(), c.getCompletedAt(), c.getCompletionNote(), c.getEvidenceSnapshot(), c.getVerifiedByName(),
                    c.getVerifiedAt(), c.getVerificationNote(), c.getReopenedCount(), c.getCancelReason());
        }
    }

    public record CapaDetailResponse(CapaResponse capa, List<EvidenceRef> liveEvidence) {}

    public record CapaSummary(long proposed, long approved, long inProgress, long awaitingVerification, long overdue) {}
}
