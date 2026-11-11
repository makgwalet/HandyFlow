package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelStockAdjustmentRequest;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.FuelStockAdjustmentRequestRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankRepository;
import za.co.handyflow.platform.fuel.dto.AdjustStockRequest;
import za.co.handyflow.platform.fuel.dto.FuelWholesaleDtos.AdjustOutcome;
import za.co.handyflow.platform.fuel.dto.FuelWholesaleDtos.AdjustmentRequestView;
import za.co.handyflow.platform.fuel.dto.MovementResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Second-person control on large stock adjustments (gap B01). An adjustment smaller than the company's threshold (or any
 * adjustment when no threshold is set) goes straight through {@link FuelStockService#adjust}, exactly as before. A larger one
 * is held as a request, and stock only moves when someone else approves it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelStockAdjustmentApprovalService {

    private final FuelStockService stock;
    private final FuelSettingsService settings;
    private final FuelStockAdjustmentRequestRepository requests;
    private final FuelTankRepository tanks;

    @Transactional
    public AdjustOutcome adjust(TenantId tenantId, UUID tankId, AdjustStockRequest req, UUID by) {
        if (!settings.current(tenantId).adjustmentNeedsApproval(req.litres())) {
            return new AdjustOutcome(false, stock.adjust(tenantId, tankId, req), null);
        }
        // Make sure the tank exists for this company before holding a request against it.
        tanks.findActiveById(tenantId, tankId).orElseThrow(() -> new ResourceNotFoundException("Tank", tankId.toString()));
        FuelStockAdjustmentRequest r = requests.save(FuelStockAdjustmentRequest.create(tenantId, tankId, req.litres(), req.reason(), by));
        log.info("Stock adjustment held for approval tenant={} tank={} litres={} by={}", tenantId, tankId, req.litres(), by);
        return new AdjustOutcome(true, null, view(tenantId, r, new HashMap<>()));
    }

    @Transactional(readOnly = true)
    public List<AdjustmentRequestView> list(TenantId tenantId) {
        Map<UUID, String> names = new HashMap<>();
        return requests.findAllForTenant(tenantId).stream().map(r -> view(tenantId, r, names)).toList();
    }

    /** Applies the adjustment. The approver must be a different person from the requester. */
    @Transactional
    public AdjustmentRequestView approve(TenantId tenantId, UUID requestId, UUID by) {
        FuelStockAdjustmentRequest r = lock(tenantId, requestId);
        if (!r.isPending()) throw new IllegalStateException("This adjustment has already been " + r.getStatus().toLowerCase() + ".");
        if (by == null || by.equals(r.getRequestedBy())) throw new IllegalStateException("A second person must approve this adjustment. You cannot approve your own request.");
        // If the stock can no longer take it (for example it is now reserved), this throws and the request stays pending.
        MovementResponse m = stock.adjust(tenantId, r.getTankId(), new AdjustStockRequest(r.getLitres(), r.getReason()));
        r.approve(by, m.id());
        log.info("Stock adjustment approved tenant={} request={} by={}", tenantId, requestId, by);
        return view(tenantId, requests.save(r), new HashMap<>());
    }

    @Transactional
    public AdjustmentRequestView reject(TenantId tenantId, UUID requestId, String note, UUID by) {
        FuelStockAdjustmentRequest r = lock(tenantId, requestId);
        r.reject(by, note);
        return view(tenantId, requests.save(r), new HashMap<>());
    }

    @Transactional
    public AdjustmentRequestView withdraw(TenantId tenantId, UUID requestId, UUID by) {
        FuelStockAdjustmentRequest r = lock(tenantId, requestId);
        r.cancel(by);
        return view(tenantId, requests.save(r), new HashMap<>());
    }

    private FuelStockAdjustmentRequest lock(TenantId tenantId, UUID id) {
        return requests.findByIdForUpdate(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Adjustment request", id.toString()));
    }

    private AdjustmentRequestView view(TenantId tenantId, FuelStockAdjustmentRequest r, Map<UUID, String> names) {
        String tank = names.computeIfAbsent(r.getTankId(), id -> tanks.findActiveById(tenantId, id).map(FuelTank::getName).orElse("Unknown tank"));
        return new AdjustmentRequestView(r.getId(), r.getTankId(), tank, r.getLitres(), r.getReason(), r.getStatus(), r.getRequestedBy(),
                r.getRequestedAt(), r.getDecidedBy(), r.getDecidedAt(), r.getDecisionNote());
    }
}
