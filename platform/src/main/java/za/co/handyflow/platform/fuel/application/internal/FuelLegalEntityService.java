package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDepot;
import za.co.handyflow.platform.fuel.domain.model.FuelLegalEntity;
import za.co.handyflow.platform.fuel.domain.repository.FuelDepotRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelLegalEntityRepository;
import za.co.handyflow.platform.fuel.dto.FuelProfileDtos.LegalEntityRequest;
import za.co.handyflow.platform.fuel.dto.FuelProfileDtos.LegalEntityResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Legal entities of the tenant's group. Phase 1 links an entity to depots only; licences, orders and stock ownership
 * attach to it in later phases. Nothing is inferred about which activities an entity may lawfully carry out.
 */
@Service
@RequiredArgsConstructor
public class FuelLegalEntityService {

    private final FuelLegalEntityRepository entities;
    private final FuelDepotRepository depots;

    @Transactional(readOnly = true)
    public List<LegalEntityResponse> list(TenantId tenantId) {
        return entities.findAllActive(tenantId).stream().map(e -> toResponse(tenantId, e)).toList();
    }

    @Transactional
    public LegalEntityResponse create(TenantId tenantId, LegalEntityRequest req, UUID by) {
        requireUniqueName(tenantId, req.name(), null);
        FuelLegalEntity e = entities.save(FuelLegalEntity.create(tenantId, req.name(), req.registrationNo(), req.taxNo(), req.vatNo(), req.address(), by));
        return toResponse(tenantId, e);
    }

    @Transactional
    public LegalEntityResponse update(TenantId tenantId, UUID id, LegalEntityRequest req) {
        FuelLegalEntity e = find(tenantId, id);
        requireUniqueName(tenantId, req.name(), id);
        e.update(req.name(), req.registrationNo(), req.taxNo(), req.vatNo(), req.address());
        return toResponse(tenantId, entities.save(e));
    }

    /** Removing an entity that depots still point to would orphan them, so it is refused. */
    @Transactional
    public void remove(TenantId tenantId, UUID id) {
        FuelLegalEntity e = find(tenantId, id);
        long used = depots.countByLegalEntity(tenantId, id);
        if (used > 0) throw new IllegalStateException(e.getName() + " is the owner of " + used + " depot(s). Move them to another entity first.");
        e.softDelete();
        entities.save(e);
    }

    /** Sets (or clears, with null) the entity a depot belongs to. The entity must be this tenant's and active. */
    @Transactional
    public void assignDepot(TenantId tenantId, UUID depotId, UUID legalEntityId) {
        FuelDepot d = depots.findActiveById(tenantId, depotId).orElseThrow(() -> new ResourceNotFoundException("Depot", depotId.toString()));
        if (legalEntityId != null) {
            FuelLegalEntity e = find(tenantId, legalEntityId);
            if (!e.isActive()) throw new IllegalStateException(e.getName() + " is inactive.");
        }
        d.assignLegalEntity(legalEntityId);
        depots.save(d);
    }

    private FuelLegalEntity find(TenantId tenantId, UUID id) {
        return entities.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Legal entity", id.toString()));
    }

    private void requireUniqueName(TenantId tenantId, String name, UUID excludeId) {
        if (name != null && entities.nameTaken(tenantId, name.trim(), excludeId)) throw new IllegalArgumentException("A legal entity called \"" + name.trim() + "\" already exists.");
    }

    private LegalEntityResponse toResponse(TenantId tenantId, FuelLegalEntity e) {
        return new LegalEntityResponse(e.getId(), e.getName(), e.getRegistrationNo(), e.getTaxNo(), e.getVatNo(), e.getAddress(), e.getStatus(), depots.countByLegalEntity(tenantId, e.getId()));
    }
}
