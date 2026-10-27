package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmSyncOp;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmSyncOpRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.*;
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
 * transaction through the normal services, so every existing rule (access scope, status checks, completion-time
 * validation, cost-line pricing restrictions, audit) still applies; sync is not a back door. Operations are applied
 * in the order sent.
 * <p>
 * Only applied operations are recorded. A rejected one is not, so a device can fix and resend it under the same
 * opId. Per-operation authority is checked here too, because the endpoint admits anyone who may do any of them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmSyncService {

    static final Set<String> OPS = Set.of("WORK_ORDER_START", "WORK_ORDER_HOLD", "WORK_ORDER_COMPLETE", "COST_LINE_ADD");
    private static final Set<String> AUTHORITIES = Set.of("FACILITIESMANAGEMENT_MANAGE", "FACILITIESMANAGEMENT_ADMIN", "FACILITIESMANAGEMENT_TECHNICIAN");

    private final FmSyncOpRepository syncOpRepository;
    private final FmSyncExecutor executor;
    private final FmWorkOrderService workOrderService;
    private final FmWorkOrderCostService costService;
    private final FmAccessPolicy accessPolicy;

    public FmSyncResponse process(TenantId tenantId, FmSyncRequest request) {
        UUID userId = TenantContext.getCurrentUserId();
        List<FmSyncResponse.Result> results = new ArrayList<>();
        for (FmSyncRequest.Operation op : request.operations()) {
            results.add(processOne(tenantId, userId, op));
        }
        return new FmSyncResponse(results);
    }

    private FmSyncResponse.Result processOne(TenantId tenantId, UUID userId, FmSyncRequest.Operation op) {
        String type = op.type() == null ? "" : op.type().trim().toUpperCase();
        try {
            if (!OPS.contains(type)) return rejected(op, "UNSUPPORTED_OP", "Unknown operation type '" + op.type() + "'");
            if (AUTHORITIES.stream().noneMatch(accessPolicy::hasAuthority)) return rejected(op, "FORBIDDEN", "You are not allowed to do this");
            boolean applied = executor.inNewTransaction(() -> {
                if (syncOpRepository.exists(tenantId, userId, op.opId())) return false;
                apply(tenantId, type, op);
                syncOpRepository.save(FmSyncOp.of(tenantId, userId, op.opId(), type, op.targetId()));
                return true;
            });
            return new FmSyncResponse.Result(op.opId(), op.targetId(), applied ? "APPLIED" : "DUPLICATE", null, null);
        } catch (DataIntegrityViolationException e) {
            // Two uploads of the same operation raced; the other one won and this one rolled back.
            return new FmSyncResponse.Result(op.opId(), op.targetId(), "DUPLICATE", null, null);
        } catch (ResourceNotFoundException e) {
            return rejected(op, "NOT_FOUND", e.getMessage());
        } catch (HandyFlowException e) {
            return rejected(op, e.getErrorCode(), e.getMessage());
        } catch (IllegalStateException e) {
            return rejected(op, "CONFLICT", e.getMessage());
        } catch (IllegalArgumentException e) {
            return rejected(op, "INVALID", e.getMessage());
        } catch (RuntimeException e) {
            log.error("[FM] Sync operation {} ({}) failed unexpectedly", op.opId(), type, e);
            return rejected(op, "INTERNAL", "Could not apply this operation — try again");
        }
    }

    private void apply(TenantId tenantId, String type, FmSyncRequest.Operation op) {
        switch (type) {
            case "WORK_ORDER_START" -> workOrderService.start(tenantId, op.targetId());
            // A device never pauses the SLA clock: that is a managerial decision, taken online.
            case "WORK_ORDER_HOLD" -> workOrderService.putOnHold(tenantId, op.targetId(), new HoldFmWorkOrderRequest(op.text(), false));
            case "WORK_ORDER_COMPLETE" -> workOrderService.complete(tenantId, op.targetId(),
                    new CompleteFmWorkOrderRequest(op.text(), op.cost(), op.completedAt(), op.downtimeMinutes(), op.failureCause()));
            case "COST_LINE_ADD" -> {
                if (op.costType() == null || op.costType().isBlank()) throw new IllegalArgumentException("costType is required");
                if (op.text() == null || op.text().isBlank()) throw new IllegalArgumentException("A description is required");
                if (op.quantity() == null || op.unitCost() == null) throw new IllegalArgumentException("quantity and unitCost are required");
                costService.add(tenantId, op.targetId(), new AddFmCostLineRequest(op.costType(), op.text(), op.quantity(), op.unitCost(), null, null));
            }
            default -> throw new IllegalArgumentException("Unknown operation type " + type);
        }
    }

    private static FmSyncResponse.Result rejected(FmSyncRequest.Operation op, String code, String message) {
        return new FmSyncResponse.Result(op.opId(), op.targetId(), "REJECTED", code, message);
    }
}
