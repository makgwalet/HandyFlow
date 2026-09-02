package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClient;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.CreateFmClientRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmClientResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpdateFmClientRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FmClientService {

    private final FmClientRepository clientRepository;
    private final FmNumberGenerator numberGenerator;

    @Transactional(readOnly = true)
    public Page<FmClientResponse> getClients(TenantId tenantId, Pageable pageable) {
        return clientRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FmClientResponse getClient(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public FmClientResponse createClient(TenantId tenantId, CreateFmClientRequest req) {
        String code = numberGenerator.nextClientCode(tenantId);
        FmClient client = FmClient.create(tenantId, code, req.tradingName(), req.registrationNumber(),
                req.contactName(), req.contactEmail(), req.contactPhone(), req.address());
        clientRepository.save(client);
        log.info("FM client created code={} tenant={}", code, tenantId);
        return toResponse(client);
    }

    @Transactional
    public FmClientResponse updateClient(TenantId tenantId, UUID id, UpdateFmClientRequest req) {
        FmClient client = findActive(tenantId, id);
        client.update(req.tradingName(), req.registrationNumber(), req.contactName(),
                req.contactEmail(), req.contactPhone(), req.address());
        clientRepository.save(client);
        return toResponse(client);
    }

    @Transactional
    public FmClientResponse deactivate(TenantId tenantId, UUID id) {
        FmClient client = findActive(tenantId, id);
        client.deactivate();
        clientRepository.save(client);
        return toResponse(client);
    }

    @Transactional
    public FmClientResponse reactivate(TenantId tenantId, UUID id) {
        FmClient client = findActive(tenantId, id);
        client.reactivate();
        clientRepository.save(client);
        return toResponse(client);
    }

    @Transactional
    public void deleteClient(TenantId tenantId, UUID id) {
        FmClient client = findActive(tenantId, id);
        client.softDelete();
        clientRepository.save(client);
    }

    FmClient findActive(TenantId tenantId, UUID id) {
        return clientRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmClient", id.toString()));
    }

    private FmClientResponse toResponse(FmClient c) {
        return new FmClientResponse(c.getId(), c.getClientCode(), c.getTradingName(), c.getRegistrationNumber(),
                c.getContactName(), c.getContactEmail(), c.getContactPhone(), c.getAddress(), c.getStatus(), c.getCreatedAt());
    }
}
