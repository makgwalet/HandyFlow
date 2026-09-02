package za.co.handyflow.platform.insurancebrokerage.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.insurancebrokerage.domain.model.InsBrokInsurer;
import za.co.handyflow.platform.insurancebrokerage.domain.repository.InsBrokInsurerRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InsBrokInsurerService {

    private final InsBrokInsurerRepository repository;

    @Transactional(readOnly = true)
    public Page<InsBrokInsurer> list(TenantId tenantId, Pageable pageable) {
        return repository.findAllActive(tenantId.getValue(), pageable);
    }

    @Transactional(readOnly = true)
    public List<InsBrokInsurer> listAll(TenantId tenantId) {
        return repository.findAllActiveList(tenantId.getValue());
    }

    @Transactional(readOnly = true)
    public InsBrokInsurer get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    @Transactional
    public InsBrokInsurer create(TenantId tenantId, String name, String contactName, String contactEmail,
                                  String contactPhone, String notes) {
        InsBrokInsurer insurer = InsBrokInsurer.create(tenantId.getValue(), name, contactName, contactEmail,
                contactPhone, notes);
        return repository.save(insurer);
    }

    @Transactional
    public InsBrokInsurer update(TenantId tenantId, UUID id, String name, String contactName, String contactEmail,
                                  String contactPhone, String notes) {
        InsBrokInsurer insurer = findActive(tenantId, id);
        insurer.update(name, contactName, contactEmail, contactPhone, notes);
        return repository.save(insurer);
    }

    @Transactional
    public InsBrokInsurer deactivate(TenantId tenantId, UUID id) {
        InsBrokInsurer insurer = findActive(tenantId, id);
        insurer.deactivate();
        return repository.save(insurer);
    }

    @Transactional
    public InsBrokInsurer reactivate(TenantId tenantId, UUID id) {
        InsBrokInsurer insurer = findActive(tenantId, id);
        insurer.reactivate();
        return repository.save(insurer);
    }

    private InsBrokInsurer findActive(TenantId tenantId, UUID id) {
        return repository.findActiveById(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("InsBrokInsurer", id.toString()));
    }
}
