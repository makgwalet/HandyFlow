package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseLocation;
import za.co.handyflow.platform.warehousing.domain.repository.WhseLocationRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** CRUD for the operator's own warehouse location/bin structure — not per-client, see WhseLocation's own Javadoc. */
@Service
@RequiredArgsConstructor
public class WhseLocationService {

    private final WhseLocationRepository repository;

    @Transactional(readOnly = true)
    public List<WhseLocation> listAll(TenantId tenantId) {
        return repository.findAllActive(tenantId.getValue());
    }

    @Transactional(readOnly = true)
    public WhseLocation get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    @Transactional
    public WhseLocation create(TenantId tenantId, String code, String zone, String description,
                                BigDecimal capacityUnits) {
        requireUniqueCode(tenantId, code, null);
        WhseLocation location = WhseLocation.create(tenantId.getValue(), code, zone, description, capacityUnits);
        return repository.save(location);
    }

    @Transactional
    public WhseLocation update(TenantId tenantId, UUID id, String code, String zone, String description,
                                BigDecimal capacityUnits) {
        WhseLocation location = findActive(tenantId, id);
        requireUniqueCode(tenantId, code, id);
        location.update(code, zone, description, capacityUnits);
        return repository.save(location);
    }

    @Transactional
    public WhseLocation deactivate(TenantId tenantId, UUID id) {
        WhseLocation location = findActive(tenantId, id);
        location.deactivate();
        return repository.save(location);
    }

    @Transactional
    public WhseLocation reactivate(TenantId tenantId, UUID id) {
        WhseLocation location = findActive(tenantId, id);
        location.reactivate();
        return repository.save(location);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        WhseLocation location = findActive(tenantId, id);
        location.softDelete();
        repository.save(location);
    }

    private void requireUniqueCode(TenantId tenantId, String code, UUID excludingId) {
        repository.findActiveByCode(tenantId.getValue(), code).ifPresent(existing -> {
            if (excludingId == null || !existing.getId().equals(excludingId)) {
                throw new HandyFlowException("A location with code '" + code + "' already exists",
                        HttpStatus.CONFLICT, "DUPLICATE_LOCATION_CODE");
            }
        });
    }

    WhseLocation findActive(TenantId tenantId, UUID id) {
        return repository.findActiveById(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("WhseLocation", id.toString()));
    }
}
