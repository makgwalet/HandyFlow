package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvClient;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvDelegate;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvClientRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvDelegateRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainProvDelegateService {

    private final TrainProvDelegateRepository delegateRepository;
    private final TrainProvClientRepository clientRepository;
    private final TrainProvNumberGenerator numberGenerator;

    @Transactional
    public TrainProvDelegate create(TenantId tenantId, UUID clientId, String fullName, String idNumber,
                                     String email, String phone, String jobTitle) {
        TrainProvClient client = clientRepository.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvClient", clientId.toString()));
        if (!"ACTIVE".equals(client.getStatus())) {
            throw new IllegalStateException("Cannot add a delegate for an inactive client");
        }
        String delegateNumber = numberGenerator.nextDelegateNumber(tenantId, clientId);
        TrainProvDelegate delegate = TrainProvDelegate.create(tenantId, clientId, delegateNumber, fullName,
                idNumber, email, phone, jobTitle);
        return delegateRepository.save(delegate);
    }

    @Transactional
    public TrainProvDelegate update(TenantId tenantId, UUID id, String fullName, String idNumber, String email,
                                     String phone, String jobTitle) {
        TrainProvDelegate delegate = getActive(tenantId, id);
        delegate.update(fullName, idNumber, email, phone, jobTitle);
        return delegateRepository.save(delegate);
    }

    @Transactional
    public TrainProvDelegate deactivate(TenantId tenantId, UUID id) {
        TrainProvDelegate delegate = getActive(tenantId, id);
        delegate.deactivate();
        return delegateRepository.save(delegate);
    }

    @Transactional
    public TrainProvDelegate reactivate(TenantId tenantId, UUID id) {
        TrainProvDelegate delegate = getActive(tenantId, id);
        delegate.reactivate();
        return delegateRepository.save(delegate);
    }

    @Transactional
    public void softDelete(TenantId tenantId, UUID id) {
        TrainProvDelegate delegate = getActive(tenantId, id);
        delegate.softDelete();
        delegateRepository.save(delegate);
    }

    public TrainProvDelegate get(TenantId tenantId, UUID id) {
        return getActive(tenantId, id);
    }

    public Page<TrainProvDelegate> list(TenantId tenantId, UUID clientId, String search, Pageable pageable) {
        return delegateRepository.findAllActive(tenantId, clientId, search, pageable);
    }

    private TrainProvDelegate getActive(TenantId tenantId, UUID id) {
        return delegateRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvDelegate", id.toString()));
    }
}
