package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpClient;
import za.co.handyflow.platform.legalpractice.domain.repository.LpClientRepository;
import za.co.handyflow.platform.legalpractice.dto.CreateLpClientRequest;
import za.co.handyflow.platform.legalpractice.dto.LpClientResponse;
import za.co.handyflow.platform.legalpractice.dto.UpdateLpClientRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** CRUD for the firm's client portfolio. Trust-balance mutation lives on {@code LpTrustTransactionService}, never here. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpClientService {

    private final LpClientRepository clientRepo;

    @Transactional(readOnly = true)
    public Page<LpClientResponse> listClients(TenantId tenantId, Pageable pageable) {
        return clientRepo.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public LpClientResponse getClient(TenantId tenantId, UUID clientId) {
        return toResponse(findOwn(tenantId, clientId));
    }

    @Transactional
    public LpClientResponse createClient(TenantId tenantId, CreateLpClientRequest req) {
        LpClient client = LpClient.create(tenantId, req.name(), req.email(), req.phone(),
                req.clientType(), req.idOrRegistrationNumber(), req.notes());
        clientRepo.save(client);
        log.info("Created legal practice client={} name={} tenant={}", client.getId(), client.getName(), tenantId);
        return toResponse(client);
    }

    @Transactional
    public LpClientResponse updateClient(TenantId tenantId, UUID clientId, UpdateLpClientRequest req) {
        LpClient client = findOwn(tenantId, clientId);
        client.update(req.name(), req.email(), req.phone(), req.idOrRegistrationNumber(), req.notes());
        clientRepo.save(client);
        return toResponse(client);
    }

    @Transactional
    public LpClientResponse deactivateClient(TenantId tenantId, UUID clientId) {
        LpClient client = findOwn(tenantId, clientId);
        client.deactivate();
        clientRepo.save(client);
        return toResponse(client);
    }

    @Transactional
    public LpClientResponse reactivateClient(TenantId tenantId, UUID clientId) {
        LpClient client = findOwn(tenantId, clientId);
        client.reactivate();
        clientRepo.save(client);
        return toResponse(client);
    }

    /** Hard delete — ADMIN-gated at the controller. Genuinely removes the client row; no soft-delete flag exists on this entity. */
    @Transactional
    public void deleteClient(TenantId tenantId, UUID clientId) {
        LpClient client = findOwn(tenantId, clientId);
        clientRepo.delete(client);
        log.info("Deleted legal practice client={} tenant={}", clientId, tenantId);
    }

    private LpClient findOwn(TenantId tenantId, UUID clientId) {
        return clientRepo.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("LpClient", clientId.toString()));
    }

    private LpClientResponse toResponse(LpClient c) {
        return new LpClientResponse(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getClientType(),
                c.getIdOrRegistrationNumber(), c.getTrustBalance(), c.getStatus(), c.getNotes(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
