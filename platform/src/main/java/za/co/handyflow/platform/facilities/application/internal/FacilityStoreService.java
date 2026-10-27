package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityStockItem;
import za.co.handyflow.platform.facilities.domain.model.FacilityStockLevel;
import za.co.handyflow.platform.facilities.domain.model.FacilityStore;
import za.co.handyflow.platform.facilities.domain.repository.FacilitySiteRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityStockItemRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityStockLevelRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityStoreRepository;
import za.co.handyflow.platform.facilities.dto.StoreRequest;
import za.co.handyflow.platform.facilities.dto.StoreResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Stores: where stock is held. Exactly one is the default (used when a movement names no store); the default cannot be
 * deactivated; a store that still holds stock cannot be deactivated. Every change is audited.
 */
@Service
@RequiredArgsConstructor
public class FacilityStoreService {

    private static final UUID NONE = new UUID(0L, 0L);

    private final FacilityStoreRepository storeRepository;
    private final FacilityStockLevelRepository levelRepository;
    private final FacilityStockItemRepository itemRepository;
    private final FacilitySiteRepository siteRepository;
    private final FacilityAuditService auditService;

    @Transactional(readOnly = true)
    public List<StoreResponse> list(TenantId tenantId) {
        List<FacilityStore> stores = storeRepository.findAllForTenant(tenantId);
        Map<UUID, FacilityStockItem> items = new HashMap<>();
        List<FacilityStockLevel> levels = levelRepository.findAllPositive(tenantId);
        if (!levels.isEmpty())
            itemRepository.findByIds(tenantId, levels.stream().map(FacilityStockLevel::getItemId).distinct().toList()).forEach(i -> items.put(i.getId(), i));
        Map<UUID, List<FacilityStockLevel>> byStore = levels.stream().collect(Collectors.groupingBy(FacilityStockLevel::getStoreId));
        return stores.stream().map(s -> {
            List<FacilityStockLevel> held = byStore.getOrDefault(s.getId(), List.of());
            BigDecimal value = held.stream().map(l -> valueOf(l, items.get(l.getItemId()))).reduce(BigDecimal.ZERO, BigDecimal::add);
            return toResponse(s, held.size(), value);
        }).toList();
    }

    @Transactional
    public StoreResponse create(TenantId tenantId, StoreRequest req) {
        String code = FacilityStore.normaliseCode(req.code());
        if (storeRepository.codeExists(tenantId, code, NONE))
            throw new HandyFlowException("A store with code " + code + " already exists", HttpStatus.CONFLICT, "STORE_CODE_TAKEN");
        requireSite(tenantId, req.siteId());
        // The first store a tenant creates becomes its default so there is always exactly one.
        boolean first = storeRepository.findDefault(tenantId).isEmpty();
        FacilityStore s = FacilityStore.create(tenantId, code, req.name(), req.siteId(), first);
        storeRepository.save(s);
        auditService.record(tenantId, "STORE_CREATED", "STORE", s.getId(), code + " " + s.getName() + (first ? " (default)" : ""));
        return toResponse(s, 0, BigDecimal.ZERO);
    }

    @Transactional
    public StoreResponse update(TenantId tenantId, UUID id, StoreRequest req) {
        FacilityStore s = find(tenantId, id);
        if (!s.getCode().equals(FacilityStore.normaliseCode(req.code())))
            throw new IllegalArgumentException("A store's code cannot be changed");
        requireSite(tenantId, req.siteId());
        s.update(req.name(), req.siteId());
        storeRepository.save(s);
        auditService.record(tenantId, "STORE_UPDATED", "STORE", id, s.getCode() + " " + s.getName());
        return toResponse(s);
    }

    @Transactional
    public StoreResponse makeDefault(TenantId tenantId, UUID id) {
        FacilityStore s = find(tenantId, id);
        if (s.isDefaultStore()) return toResponse(s);
        try {
            s.makeDefault();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "STORE_INACTIVE");
        }
        // Clear the old default first and flush, so the one-default-per-tenant index is never violated mid-way.
        storeRepository.findDefault(tenantId).ifPresent(old -> { old.clearDefault(); storeRepository.saveAndFlush(old); });
        storeRepository.save(s);
        auditService.record(tenantId, "STORE_DEFAULT_CHANGED", "STORE", id, s.getCode() + " is now the default store");
        return toResponse(s);
    }

    @Transactional
    public StoreResponse setActive(TenantId tenantId, UUID id, boolean active) {
        FacilityStore s = find(tenantId, id);
        if (active) {
            s.reactivate();
        } else {
            if (levelRepository.countPositiveInStore(tenantId, id) > 0)
                throw new HandyFlowException(s.getName() + " still holds stock; transfer it out first", HttpStatus.CONFLICT, "STORE_NOT_EMPTY");
            try {
                s.deactivate();
            } catch (IllegalStateException e) {
                throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "STORE_IS_DEFAULT");
            }
        }
        storeRepository.save(s);
        auditService.record(tenantId, active ? "STORE_REACTIVATED" : "STORE_DEACTIVATED", "STORE", id, s.getCode());
        return toResponse(s);
    }

    private void requireSite(TenantId tenantId, UUID siteId) {
        if (siteId != null && siteRepository.findActiveById(tenantId, siteId).isEmpty())
            throw new ResourceNotFoundException("FacilitySite", siteId.toString());
    }

    private FacilityStore find(TenantId tenantId, UUID id) {
        return storeRepository.findByIdForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("FacilityStore", id.toString()));
    }

    private StoreResponse toResponse(FacilityStore s) {
        List<FacilityStockLevel> held = levelRepository.findAllPositive(s.getTenantId()).stream().filter(l -> l.getStoreId().equals(s.getId())).toList();
        Map<UUID, FacilityStockItem> items = new HashMap<>();
        if (!held.isEmpty())
            itemRepository.findByIds(s.getTenantId(), held.stream().map(FacilityStockLevel::getItemId).toList()).forEach(i -> items.put(i.getId(), i));
        return toResponse(s, held.size(), held.stream().map(l -> valueOf(l, items.get(l.getItemId()))).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static StoreResponse toResponse(FacilityStore s, long lines, BigDecimal value) {
        return new StoreResponse(s.getId(), s.getCode(), s.getName(), s.getSiteId(), s.isDefaultStore(), s.isActive(), lines,
                value.setScale(2, RoundingMode.HALF_UP));
    }

    static BigDecimal valueOf(FacilityStockLevel l, FacilityStockItem item) {
        return item == null ? BigDecimal.ZERO : l.getOnHand().multiply(item.getUnitCost());
    }
}
