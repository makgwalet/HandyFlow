package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.SupplierFacade;
import za.co.handyflow.platform.supplychain.domain.enums.SupplierStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplier;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class SupplierFacadeImpl implements SupplierFacade {

    private static final int MAX_LIST = 500;

    private final ScSupplierRepository supplierRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SupplierSummary> listActive(TenantId tenantId, int max) {
        int limit = Math.min(Math.max(max, 1), MAX_LIST);
        return supplierRepository.findByTenantIdAndStatus(tenantId.getValue(), SupplierStatus.ACTIVE, PageRequest.of(0, limit)).getContent().stream()
                .map(SupplierFacadeImpl::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplierSummary> find(TenantId tenantId, UUID supplierId) {
        return supplierRepository.findByTenantIdAndId(tenantId.getValue(), supplierId).map(SupplierFacadeImpl::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SupplierSummary> findAll(TenantId tenantId, Collection<UUID> ids) {
        Map<UUID, SupplierSummary> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) return out;
        for (ScSupplier s : supplierRepository.findAllById(ids)) {
            // findAllById is not tenant-scoped, so another tenant's supplier or a deleted one must be filtered out here
            if (tenantId.getValue().equals(s.getTenantId()) && s.getDeletedAt() == null) out.put(s.getId(), toSummary(s));
        }
        return out;
    }

    private static SupplierSummary toSummary(ScSupplier s) {
        return new SupplierSummary(s.getId(), s.getName(), s.getStatus() == null ? null : s.getStatus().name());
    }
}
