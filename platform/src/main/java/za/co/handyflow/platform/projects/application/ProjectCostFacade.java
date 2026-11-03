package za.co.handyflow.platform.projects.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.projects.application.internal.ProjectCostService;
import za.co.handyflow.platform.projects.application.internal.ProjectCostService.CostPosting;
import za.co.handyflow.platform.projects.application.internal.ProjectCostService.PostedEntry;
import za.co.handyflow.platform.projects.domain.enums.CostEntryType;
import za.co.handyflow.platform.projects.dto.CostEntryResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The ONLY entry point other modules (Procurement, Accounting/AP, Expenses, Invoicing, HR payroll)
 * should use to put money onto a project. Same pattern as EvidenceFacade / ApprovalFacade.
 * (A calling module must list "projects" in its @ApplicationModule allowedDependencies.)
 *
 * Every call REQUIRES an idempotency key — build it from the source document and event, e.g.
 * "PO:" + poId + ":approved" or "AP-BILL:" + billId + ":posted". Retries, redelivered events and
 * double-clicks then return the original entry (duplicate=true) instead of posting twice.
 *
 * To release a commitment when its invoice arrives, call commit() with a NEGATIVE amount and a
 * distinct key, then postActual() for the invoice. To undo a posting, call reverse().
 *
 * These methods are not wired to Procurement / Accounting yet — that integration lives in those
 * modules and is the next piece of work once they call this facade.
 */
@Service
@RequiredArgsConstructor
public class ProjectCostFacade {

    private final ProjectCostService costService;

    /** Records a commitment (e.g. an approved purchase order). Negative amount releases one. */
    public CostEntryResponse commit(TenantId tenantId, UUID projectId, UUID budgetLineId, BigDecimal amount,
                                    String sourceType, String sourceRef, String description, LocalDate date,
                                    String idempotencyKey, UUID actorId, String actorName) {
        return post(tenantId, projectId, budgetLineId, CostEntryType.COMMITMENT, amount,
                sourceType, sourceRef, description, date, idempotencyKey, actorId, actorName);
    }

    /** Records incurred cost (e.g. a supplier invoice or expense). Negative amount = credit note. */
    public CostEntryResponse postActual(TenantId tenantId, UUID projectId, UUID budgetLineId, BigDecimal amount,
                                        String sourceType, String sourceRef, String description, LocalDate date,
                                        String idempotencyKey, UUID actorId, String actorName) {
        return post(tenantId, projectId, budgetLineId, CostEntryType.ACTUAL, amount,
                sourceType, sourceRef, description, date, idempotencyKey, actorId, actorName);
    }

    public CostEntryResponse reverse(TenantId tenantId, UUID projectId, UUID entryId, String reason,
                                     UUID actorId, String actorName) {
        PostedEntry p = costService.reverse(tenantId, projectId, entryId, reason, actorId, actorName);
        return CostEntryResponse.of(p.entry(), p.duplicate(), p.lineOverBudget());
    }

    private CostEntryResponse post(TenantId tenantId, UUID projectId, UUID budgetLineId, CostEntryType type,
                                   BigDecimal amount, String sourceType, String sourceRef, String description,
                                   LocalDate date, String idempotencyKey, UUID actorId, String actorName) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new HandyFlowException("An idempotency key is required for system postings",
                    HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        }
        PostedEntry p = costService.post(tenantId, projectId, new CostPosting(
                budgetLineId, type, amount, sourceType, sourceRef, description, date,
                idempotencyKey, actorId, actorName));
        return CostEntryResponse.of(p.entry(), p.duplicate(), p.lineOverBudget());
    }
}