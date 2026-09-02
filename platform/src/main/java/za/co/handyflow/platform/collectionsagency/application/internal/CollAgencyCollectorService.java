package za.co.handyflow.platform.collectionsagency.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyCollector;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyCollectorRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollAgencyCollectorService {

    private final CollAgencyCollectorRepository repository;

    @Transactional(readOnly = true)
    public List<CollAgencyCollector> list(TenantId tenantId) {
        return repository.findAllActive(tenantId.getValue());
    }

    @Transactional(readOnly = true)
    public CollAgencyCollector get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    @Transactional
    public CollAgencyCollector create(TenantId tenantId, UUID userId, String fullName, String registrationNumber,
                                       LocalDate registrationExpiryDate, String email, String phone) {
        CollAgencyCollector collector = CollAgencyCollector.create(tenantId.getValue(), userId, fullName,
                registrationNumber, registrationExpiryDate, email, phone);
        return repository.save(collector);
    }

    @Transactional
    public CollAgencyCollector update(TenantId tenantId, UUID id, String fullName, String registrationNumber,
                                       LocalDate registrationExpiryDate, String email, String phone) {
        CollAgencyCollector collector = findActive(tenantId, id);
        collector.update(fullName, registrationNumber, registrationExpiryDate, email, phone);
        return repository.save(collector);
    }

    @Transactional
    public CollAgencyCollector deactivate(TenantId tenantId, UUID id) {
        CollAgencyCollector collector = findActive(tenantId, id);
        collector.deactivate();
        return repository.save(collector);
    }

    @Transactional
    public CollAgencyCollector reactivate(TenantId tenantId, UUID id) {
        CollAgencyCollector collector = findActive(tenantId, id);
        collector.reactivate();
        return repository.save(collector);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        CollAgencyCollector collector = findActive(tenantId, id);
        collector.softDelete();
        repository.save(collector);
    }

    private CollAgencyCollector findActive(TenantId tenantId, UUID id) {
        return repository.findActiveById(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("CollAgencyCollector", id.toString()));
    }
}
