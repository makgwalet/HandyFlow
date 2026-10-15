package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AuditActionPlan;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditActionPlanRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditFindingRepository;
import za.co.handyflow.platform.internalaudit.dto.ActionPlanDtos.CreateRequest;
import za.co.handyflow.platform.internalaudit.dto.ActionPlanDtos.Response;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Management action plans on audit findings: create, complete with a note, validate or return, cancel. */
@Service
@RequiredArgsConstructor
public class ActionPlanService {

    private final AuditActionPlanRepository repo;
    private final AuditFindingRepository findingRepo;
    private final UserRepository userRepo;
    private final AuditTrailFacade trail;

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    @Transactional
    public Response create(TenantId tenantId, UUID findingId, CreateRequest req, UUID by) {
        var finding = findingRepo.findByTenantAndId(tenantId.getValue(), findingId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditFinding", findingId.toString()));
        if ("CLOSED".equals(finding.getStatus())) {
            throw new HandyFlowException("This finding is closed. Reopen it to add an action.", HttpStatus.CONFLICT, "FINDING_CLOSED");
        }
        AuditActionPlan p;
        try {
            p = AuditActionPlan.create(tenantId.getValue(), findingId, req.description(), req.owner(), req.dueDate(), by);
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "INVALID_ACTION");
        }
        repo.save(p);
        trail.record(tenantId, "ACTION_PLAN_CREATED", "ACTION_PLAN", p.getId(), null,
                Map.of("findingId", findingId.toString(), "dueDate", p.getDueDate().toString(), "owner", String.valueOf(p.getOwner())));
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public List<Response> forFinding(TenantId tenantId, UUID findingId) {
        return repo.findByFinding(tenantId.getValue(), findingId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<Response> overdue(TenantId tenantId) {
        return repo.findOverdue(tenantId.getValue(), today()).stream().map(this::toResponse).toList();
    }

    /** The plan's owner, or an audit manager, may mark an action complete. */
    @Transactional
    public Response complete(TenantId tenantId, UUID planId, String note, UUID by, boolean isManager) {
        AuditActionPlan p = get(tenantId, planId);
        if (!isManager && !by.equals(p.getOwner())) {
            throw new HandyFlowException("Only the owner of this action, or an audit manager, can mark it complete", HttpStatus.FORBIDDEN, "NOT_OWNER");
        }
        return change(tenantId, p, "ACTION_PLAN_COMPLETED", () -> p.complete(by, note), Map.of("note", String.valueOf(note)));
    }

    @Transactional
    public Response validate(TenantId tenantId, UUID planId, String note, UUID by) {
        AuditActionPlan p = get(tenantId, planId);
        return change(tenantId, p, "ACTION_PLAN_VALIDATED", () -> p.validate(by, note), Map.of("note", String.valueOf(note)));
    }

    @Transactional
    public Response returnForRework(TenantId tenantId, UUID planId, String note, UUID by) {
        AuditActionPlan p = get(tenantId, planId);
        return change(tenantId, p, "ACTION_PLAN_RETURNED", () -> p.returnForRework(by, note), Map.of("note", String.valueOf(note)));
    }

    @Transactional
    public Response cancel(TenantId tenantId, UUID planId, String reason) {
        AuditActionPlan p = get(tenantId, planId);
        return change(tenantId, p, "ACTION_PLAN_CANCELLED", () -> p.cancel(reason), Map.of("reason", String.valueOf(reason)));
    }

    private AuditActionPlan get(TenantId tenantId, UUID planId) {
        return repo.findByTenantAndId(tenantId.getValue(), planId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditActionPlan", planId.toString()));
    }

    private Response change(TenantId tenantId, AuditActionPlan p, String action, Runnable doIt, Map<String, ?> detail) {
        String before = p.getStatus();
        try {
            doIt.run();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATUS");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "INVALID_NOTE");
        }
        repo.save(p);
        java.util.Map<String, Object> after = new java.util.LinkedHashMap<>(detail);
        after.put("status", p.getStatus());
        trail.record(tenantId, action, "ACTION_PLAN", p.getId(), Map.of("status", before), after);
        return toResponse(p);
    }

    private Response toResponse(AuditActionPlan p) {
        String ownerName = p.getOwner() == null ? null
                : userRepo.findById(p.getOwner()).map(u -> (u.getFirstName() + " " + u.getLastName()).trim()).orElse("Unknown");
        return new Response(p.getId(), p.getFindingId(), p.getDescription(), p.getOwner(), ownerName, p.getDueDate(), p.getStatus(),
                p.isOverdue(today()), p.getCompletionNote(), p.getCompletedAt(), p.getValidationNote(), p.getValidatedAt(),
                p.getTimesReturned(), p.getCancelReason(), p.getCreatedAt());
    }
}
