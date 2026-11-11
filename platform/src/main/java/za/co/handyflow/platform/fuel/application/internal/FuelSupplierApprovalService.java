package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelSupplier;
import za.co.handyflow.platform.fuel.domain.repository.FuelSupplierRepository;
import za.co.handyflow.platform.fuel.dto.CreateSupplierRequest;
import za.co.handyflow.platform.fuel.dto.SupplierResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Supplier approval (gap B04). When the company turns on "require supplier approval", a new supplier starts as
 * PENDING_APPROVAL and cannot be ordered from until someone other than its creator approves it. A supplier can be
 * suspended at any time with a reason. Existing suppliers are APPROVED and nothing changes for companies that leave the setting off.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelSupplierApprovalService {

    private final FuelSupplierRepository suppliers;
    private final FuelSettingsService settings;

    @Transactional
    public SupplierResponse create(TenantId tenantId, CreateSupplierRequest req, UUID by) {
        FuelSupplier s = FuelSupplier.create(tenantId, req.name(), req.contactName(), req.contactPhone(), req.contactEmail(), req.accountNumber());
        if (settings.current(tenantId).isRequireSupplierApproval()) s.requireApproval(by);
        return toResponse(suppliers.save(s));
    }

    @Transactional
    public SupplierResponse approve(TenantId tenantId, UUID id, UUID by) {
        FuelSupplier s = find(tenantId, id);
        s.approve(by);
        log.info("Fuel supplier approved tenant={} supplier={} by={}", tenantId, id, by);
        return toResponse(suppliers.save(s));
    }

    @Transactional
    public SupplierResponse suspend(TenantId tenantId, UUID id, String reason, UUID by) {
        FuelSupplier s = find(tenantId, id);
        s.suspend(by, reason);
        log.info("Fuel supplier suspended tenant={} supplier={} by={} reason={}", tenantId, id, by, reason);
        return toResponse(suppliers.save(s));
    }

    private FuelSupplier find(TenantId tenantId, UUID id) {
        return suppliers.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Supplier", id.toString()));
    }

    private static SupplierResponse toResponse(FuelSupplier s) {
        return new SupplierResponse(s.getId(), s.getName(), s.getContactName(), s.getContactPhone(), s.getContactEmail(), s.getAccountNumber(),
                s.getCreatedAt(), s.getApprovalStatus(), s.getStatusReason());
    }
}
