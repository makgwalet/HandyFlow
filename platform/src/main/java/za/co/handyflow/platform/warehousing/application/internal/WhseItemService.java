package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseItem;
import za.co.handyflow.platform.warehousing.domain.repository.WhseItemRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** CRUD for a client's SKU/item catalogue, scoped by clientId — see WhseItem's own Javadoc for why this is its own catalogue rather than a dependency on the shared `catalogue` module. */
@Service
@RequiredArgsConstructor
public class WhseItemService {

    private final WhseItemRepository repository;
    private final WhseClientService clientService;

    @Transactional(readOnly = true)
    public Page<WhseItem> listForClient(TenantId tenantId, UUID clientId, Pageable pageable) {
        clientService.findActive(tenantId, clientId);
        return repository.findByClient(tenantId.getValue(), clientId, pageable);
    }

    /** Unpaginated — used by the billing snapshot and order/shipment line pickers. */
    @Transactional(readOnly = true)
    public List<WhseItem> listAllActiveForClient(TenantId tenantId, UUID clientId) {
        return repository.findAllActiveForClient(tenantId.getValue(), clientId);
    }

    @Transactional(readOnly = true)
    public WhseItem get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    @Transactional
    public WhseItem create(TenantId tenantId, UUID clientId, String sku, String description, String uom,
                            BigDecimal storageRatePerUnitPerMonth) {
        clientService.findActive(tenantId, clientId);
        requireUniqueSku(tenantId, clientId, sku, null);
        WhseItem item = WhseItem.create(tenantId.getValue(), clientId, sku, description, uom,
                storageRatePerUnitPerMonth);
        return repository.save(item);
    }

    @Transactional
    public WhseItem update(TenantId tenantId, UUID id, String description, String uom,
                            BigDecimal storageRatePerUnitPerMonth) {
        WhseItem item = findActive(tenantId, id);
        item.update(description, uom, storageRatePerUnitPerMonth);
        return repository.save(item);
    }

    @Transactional
    public WhseItem deactivate(TenantId tenantId, UUID id) {
        WhseItem item = findActive(tenantId, id);
        item.deactivate();
        return repository.save(item);
    }

    @Transactional
    public WhseItem reactivate(TenantId tenantId, UUID id) {
        WhseItem item = findActive(tenantId, id);
        item.reactivate();
        return repository.save(item);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        WhseItem item = findActive(tenantId, id);
        item.softDelete();
        repository.save(item);
    }

    private void requireUniqueSku(TenantId tenantId, UUID clientId, String sku, UUID excludingId) {
        repository.findActiveByClientAndSku(tenantId.getValue(), clientId, sku).ifPresent(existing -> {
            if (excludingId == null || !existing.getId().equals(excludingId)) {
                throw new HandyFlowException("An item with SKU '" + sku + "' already exists for this client",
                        HttpStatus.CONFLICT, "DUPLICATE_SKU");
            }
        });
    }

    WhseItem findActive(TenantId tenantId, UUID id) {
        return repository.findActiveById(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("WhseItem", id.toString()));
    }
}
