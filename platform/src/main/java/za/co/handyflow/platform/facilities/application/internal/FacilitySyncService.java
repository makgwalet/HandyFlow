package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.facilities.domain.model.FacilitySyncOp;
import za.co.handyflow.platform.facilities.domain.repository.FacilitySyncOpRepository;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Applies a batch of operations a device queued while offline, idempotently. Each operation runs in its own
 * transaction through the normal services, so every existing rule (site scope, status checks, completion-time
 * validation, evidence, audit) still applies; sync is not a back door. Operations are applied in the order sent.
 * <p>
 * Only applied operations are recorded. A rejected one is not, so a device can fix and resend it under the same
 * opId. Per-operation authority is checked here too, because the endpoint admits anyone who may do any of them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilitySyncService {

    static final Set<String> WORK_ORDER_OPS = Set.of("WORK_ORDER_START", "WORK_ORDER_HOLD", "WORK_ORDER_COMPLETE");
    static final Set<String> INSPECTION_OPS = Set.of("INSPECTION_ANSWERS", "INSPECTION_COMPLETE");
    private static final Set<String> WORK_ORDER_AUTHORITIES = Set.of("FACILITIES_MANAGE", "FACILITIES_ADMIN", "FACILITIES_TECHNICIAN");
    private static final Set<String> INSPECTION_AUTHORITIES = Set.of("FACILITIES_MANAGE", "FACILITIES_ADMIN", "FACILITIES_TECHNICIAN", "FACILITIES_INSPECT");

    private final FacilitySyncOpRepository syncOpRepository;
    private final FacilitySyncExecutor executor;
    private final FacilityWorkOrderService workOrderService;
    private final FacilityInspectionService inspectionService;
    private final FacilityAccessPolicy accessPolicy;

    public SyncResponse process(TenantId tenantId, SyncRequest request) {
        UUID userId = TenantContext.getCurrentUserId();
        List<SyncResponse.Result> results = new ArrayList<>();
        for (SyncRequest.Operation op : request.operations()) {
            results.add(processOne(tenantId, userId, op));
        }
        return new SyncResponse(results);
    }

    private SyncResponse.Result processOne(TenantId tenantId, UUID userId, SyncRequest.Operation op) {
        String type = op.type() == null ? "" : op.type().trim().toUpperCase();
        try {
            if (!WORK_ORDER_OPS.contains(type) && !INSPECTION_OPS.contains(type)) {
                return rejected(op, "UNSUPPORTED_OP", "Unknown operation type '" + op.type() + "'");
            }
            if (!allowed(type)) {
                return rejected(op, "FORBIDDEN", "You are not allowed to do this");
            }
            boolean applied = executor.inNewTransaction(() -> {
                if (syncOpRepository.exists(tenantId, userId, op.opId())) return false;
                apply(tenantId, type, op);
                syncOpRepository.save(FacilitySyncOp.of(tenantId, userId, op.opId(), type, op.targetId()));
                return true;
            });
            return new SyncResponse.Result(op.opId(), op.targetId(), applied ? "APPLIED" : "DUPLICATE", null, null);
        } catch (DataIntegrityViolationException e) {
            // Two uploads of the same operation raced; the other one won and this one rolled back.
            return new SyncResponse.Result(op.opId(), op.targetId(), "DUPLICATE", null, null);
        } catch (ResourceNotFoundException e) {
            return rejected(op, "NOT_FOUND", e.getMessage());
        } catch (HandyFlowException e) {
            return rejected(op, e.getErrorCode(), e.getMessage());
        } catch (IllegalStateException e) {
            return rejected(op, "CONFLICT", e.getMessage());
        } catch (IllegalArgumentException e) {
            return rejected(op, "INVALID", e.getMessage());
        } catch (RuntimeException e) {
            log.error("[Facilities] Sync operation {} ({}) failed unexpectedly", op.opId(), type, e);
            return rejected(op, "INTERNAL", "Could not apply this operation — try again");
        }
    }

    private void apply(TenantId tenantId, String type, SyncRequest.Operation op) {
        switch (type) {
            case "WORK_ORDER_START" -> workOrderService.start(tenantId, op.targetId());
            case "WORK_ORDER_HOLD" -> workOrderService.putOnHold(tenantId, op.targetId(), new HoldWorkOrderRequest(op.text()));
            case "WORK_ORDER_COMPLETE" -> workOrderService.complete(tenantId, op.targetId(),
                    new CompleteWorkOrderRequest(op.text(), op.cost(), op.completedAt(), op.downtimeMinutes(), op.failureCause()));
            case "INSPECTION_ANSWERS" -> {
                if (op.answers() == null || op.answers().isEmpty()) throw new IllegalArgumentException("answers are required");
                inspectionService.answer(tenantId, op.targetId(), new InspectionAnswersRequest(op.answers().stream()
                        .map(a -> new InspectionAnswersRequest.Answer(a.itemId(), a.outcome(), a.comment())).toList()));
            }
            case "INSPECTION_COMPLETE" -> inspectionService.complete(tenantId, op.targetId(), new CompleteInspectionRequest(op.text()));
            default -> throw new IllegalArgumentException("Unknown operation type " + type);
        }
    }

    private boolean allowed(String type) {
        Set<String> needed = WORK_ORDER_OPS.contains(type) ? WORK_ORDER_AUTHORITIES : INSPECTION_AUTHORITIES;
        return needed.stream().anyMatch(accessPolicy::hasAuthority);
    }

    private static SyncResponse.Result rejected(SyncRequest.Operation op, String code, String message) {
        return new SyncResponse.Result(op.opId(), op.targetId(), "REJECTED", code, message);
    }
}
