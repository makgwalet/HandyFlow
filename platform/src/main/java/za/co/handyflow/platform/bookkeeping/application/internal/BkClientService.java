package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookkeeping.domain.model.BkClient;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkClientRepository;
import za.co.handyflow.platform.bookkeeping.dto.BkClientResponse;
import za.co.handyflow.platform.bookkeeping.dto.CreateBkClientRequest;
import za.co.handyflow.platform.bookkeeping.dto.UpdateBkClientRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BkClientService {

    private final BkClientRepository clientRepository;
    private final BkNumberGenerator numberGenerator;

    @Transactional(readOnly = true)
    public Page<BkClientResponse> getClients(TenantId tenantId, Pageable pageable) {
        return clientRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BkClientResponse getClient(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public BkClientResponse createClient(TenantId tenantId, CreateBkClientRequest req) {
        String code = numberGenerator.nextClientCode(tenantId);
        BkClient client = BkClient.create(tenantId, code, req.tradingName(), req.registrationNumber(),
                req.vatNumber(), req.contactName(), req.contactEmail(), req.contactPhone(), req.address());
        clientRepository.save(client);
        log.info("Bookkeeping client created code={} tenant={}", code, tenantId);
        return toResponse(client);
    }

    @Transactional
    public BkClientResponse updateClient(TenantId tenantId, UUID id, UpdateBkClientRequest req) {
        BkClient client = findActive(tenantId, id);
        client.update(req.tradingName(), req.registrationNumber(), req.vatNumber(), req.contactName(),
                req.contactEmail(), req.contactPhone(), req.address());
        clientRepository.save(client);
        return toResponse(client);
    }

    @Transactional
    public BkClientResponse deactivate(TenantId tenantId, UUID id) {
        BkClient client = findActive(tenantId, id);
        client.deactivate();
        clientRepository.save(client);
        return toResponse(client);
    }

    @Transactional
    public BkClientResponse reactivate(TenantId tenantId, UUID id) {
        BkClient client = findActive(tenantId, id);
        client.reactivate();
        clientRepository.save(client);
        return toResponse(client);
    }

    @Transactional
    public void deleteClient(TenantId tenantId, UUID id) {
        BkClient client = findActive(tenantId, id);
        client.softDelete();
        clientRepository.save(client);
    }

    BkClient findActive(TenantId tenantId, UUID id) {
        return clientRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("BkClient", id.toString()));
    }

    private BkClientResponse toResponse(BkClient c) {
        return new BkClientResponse(c.getId(), c.getClientCode(), c.getTradingName(), c.getRegistrationNumber(),
                c.getVatNumber(), c.getContactName(), c.getContactEmail(), c.getContactPhone(), c.getAddress(),
                c.getStatus(), c.getCreatedAt());
    }
}
