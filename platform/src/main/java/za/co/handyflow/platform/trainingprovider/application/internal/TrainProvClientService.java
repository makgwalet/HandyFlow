package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvClient;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvClientRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainProvClientService {

    private final TrainProvClientRepository clientRepository;
    private final TrainProvNumberGenerator numberGenerator;

    @Transactional
    public TrainProvClient create(TenantId tenantId, String tradingName, String registrationNumber,
                                   String contactName, String contactEmail, String contactPhone, String address) {
        String clientCode = numberGenerator.nextClientCode(tenantId);
        TrainProvClient client = TrainProvClient.create(tenantId, clientCode, tradingName, registrationNumber,
                contactName, contactEmail, contactPhone, address);
        return clientRepository.save(client);
    }

    @Transactional
    public TrainProvClient update(TenantId tenantId, UUID id, String tradingName, String registrationNumber,
                                   String contactName, String contactEmail, String contactPhone, String address) {
        TrainProvClient client = getActive(tenantId, id);
        client.update(tradingName, registrationNumber, contactName, contactEmail, contactPhone, address);
        return clientRepository.save(client);
    }

    @Transactional
    public TrainProvClient deactivate(TenantId tenantId, UUID id) {
        TrainProvClient client = getActive(tenantId, id);
        client.deactivate();
        return clientRepository.save(client);
    }

    @Transactional
    public TrainProvClient reactivate(TenantId tenantId, UUID id) {
        TrainProvClient client = getActive(tenantId, id);
        client.reactivate();
        return clientRepository.save(client);
    }

    @Transactional
    public void softDelete(TenantId tenantId, UUID id) {
        TrainProvClient client = getActive(tenantId, id);
        client.softDelete();
        clientRepository.save(client);
    }

    public TrainProvClient get(TenantId tenantId, UUID id) {
        return getActive(tenantId, id);
    }

    public Page<TrainProvClient> list(TenantId tenantId, String status, String search, Pageable pageable) {
        return clientRepository.findAllActive(tenantId, status, search, pageable);
    }

    private TrainProvClient getActive(TenantId tenantId, UUID id) {
        return clientRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvClient", id.toString()));
    }
}
