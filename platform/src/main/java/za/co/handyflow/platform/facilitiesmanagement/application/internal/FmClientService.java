package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClient;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.CreateFmClientRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmClientResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpdateFmClientRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Chunk 5: every read and write is filtered through the caller's {@link FmAccessScope}; deactivating,
 * reactivating and deleting a client are audited; a client-restricted user cannot create new clients
 * (they would immediately fall outside their own scope).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmClientService {

    private final FmClientRepository clientRepository;
    private final FmNumberGenerator numberGenerator;
    private final FmAccessPolicy accessPolicy;
    private final FmAuditService auditService;

    @Transactional(readOnly = true)
    public Page<FmClientResponse> getClients(TenantId tenantId, Pageable pageable) {
        FmAccessScope scope = accessPolicy.scope(tenantId);
        return clientRepository.findAllActiveInScope(tenantId, scope.unrestricted(), scope.clientIds(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FmClientResponse getClient(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id, accessPolicy.scope(tenantId)));
    }

    @Transactional
    public FmClientResponse createClient(TenantId tenantId, CreateFmClientRequest req) {
        if (!accessPolicy.scope(tenantId).unrestricted()) {
            throw new HandyFlowException("Client-restricted users cannot create new clients",
                    HttpStatus.FORBIDDEN, "CLIENT_SCOPE_RESTRICTED");
        }
        String code = numberGenerator.nextClientCode(tenantId);
        FmClient client = FmClient.create(tenantId, code, req.tradingName(), req.registrationNumber(),
                req.contactName(), req.contactEmail(), req.contactPhone(), req.address());
        client.changeVatNumber(req.vatNumber());
        clientRepository.save(client);
        log.info("FM client created code={} tenant={}", code, tenantId);
        return toResponse(client);
    }

    @Transactional
    public FmClientResponse updateClient(TenantId tenantId, UUID id, UpdateFmClientRequest req) {
        FmClient client = findActive(tenantId, id, accessPolicy.scope(tenantId));
        client.update(req.tradingName(), req.registrationNumber(), req.contactName(),
                req.contactEmail(), req.contactPhone(), req.address());
        String beforeVat = client.getVatNumber();
        client.changeVatNumber(req.vatNumber());
        clientRepository.save(client);
        if (!java.util.Objects.equals(beforeVat, client.getVatNumber())) {
            auditService.record(tenantId, "CLIENT_VAT_NUMBER_CHANGED", "CLIENT", id,
                    client.getClientCode() + " " + beforeVat + " -> " + client.getVatNumber());
        }
        return toResponse(client);
    }

    @Transactional
    public FmClientResponse deactivate(TenantId tenantId, UUID id) {
        FmClient client = findActive(tenantId, id, accessPolicy.scope(tenantId));
        client.deactivate();
        clientRepository.save(client);
        auditService.record(tenantId, "CLIENT_DEACTIVATED", "CLIENT", id, client.getClientCode() + " " + client.getTradingName());
        return toResponse(client);
    }

    @Transactional
    public FmClientResponse reactivate(TenantId tenantId, UUID id) {
        FmClient client = findActive(tenantId, id, accessPolicy.scope(tenantId));
        client.reactivate();
        clientRepository.save(client);
        auditService.record(tenantId, "CLIENT_REACTIVATED", "CLIENT", id, client.getClientCode() + " " + client.getTradingName());
        return toResponse(client);
    }

    @Transactional
    public void deleteClient(TenantId tenantId, UUID id) {
        FmClient client = findActive(tenantId, id, accessPolicy.scope(tenantId));
        client.softDelete();
        clientRepository.save(client);
        auditService.record(tenantId, "CLIENT_DELETED", "CLIENT", id, client.getClientCode() + " " + client.getTradingName());
    }

    private FmClient findActive(TenantId tenantId, UUID id, FmAccessScope scope) {
        FmClient client = clientRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmClient", id.toString()));
        scope.requireClient(client.getId());
        return client;
    }

    private FmClientResponse toResponse(FmClient c) {
        return new FmClientResponse(c.getId(), c.getClientCode(), c.getTradingName(), c.getRegistrationNumber(),
                c.getContactName(), c.getContactEmail(), c.getContactPhone(), c.getAddress(), c.getStatus(), c.getCreatedAt(), c.getVatNumber());
    }
}