package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceDeadline;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceDeadlineRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.dto.ClientComplianceDeadlineResponse;
import za.co.handyflow.platform.complianceservices.dto.CreateClientComplianceDeadlineRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Client-scoped counterpart to compliancetender.ComplianceDeadlineService — same 14-day due-soon window. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientComplianceDeadlineService {

    private static final int DUE_SOON_DAYS = 14;

    private final ClientComplianceDeadlineRepository deadlineRepository;
    private final ComplianceClientRepository clientRepository;

    @Transactional(readOnly = true)
    public List<ClientComplianceDeadlineResponse> getPendingDeadlines(TenantId tenantId, UUID clientId) {
        requireClient(tenantId, clientId);
        return deadlineRepository.findPendingByClient(tenantId, clientId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ClientComplianceDeadlineResponse create(TenantId tenantId, UUID clientId,
                                                    CreateClientComplianceDeadlineRequest req, UUID createdBy) {
        requireClient(tenantId, clientId);
        ClientComplianceDeadline deadline = ClientComplianceDeadline.create(tenantId, clientId, req.registrationId(),
                req.deadlineType(), req.description(), req.dueDate(), createdBy);
        deadlineRepository.save(deadline);
        log.info("Client compliance deadline created id={} client={} type={} due={} tenant={}",
                deadline.getId(), clientId, deadline.getDeadlineType(), deadline.getDueDate(), tenantId);
        return toResponse(deadline);
    }

    @Transactional
    public ClientComplianceDeadlineResponse markDone(TenantId tenantId, UUID id, UUID completedBy) {
        ClientComplianceDeadline deadline = find(tenantId, id);
        deadline.markDone(completedBy);
        deadlineRepository.save(deadline);
        return toResponse(deadline);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        deadlineRepository.delete(find(tenantId, id));
        log.info("Client compliance deadline deleted id={} tenant={}", id, tenantId);
    }

    private void requireClient(TenantId tenantId, UUID clientId) {
        if (clientRepository.findByIdForTenant(tenantId, clientId).isEmpty())
            throw new ResourceNotFoundException("ComplianceClient", clientId.toString());
    }

    private ClientComplianceDeadline find(TenantId tenantId, UUID id) {
        return deadlineRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ClientComplianceDeadline", id.toString()));
    }

    private ClientComplianceDeadlineResponse toResponse(ClientComplianceDeadline d) {
        return new ClientComplianceDeadlineResponse(d.getId(), d.getClientId(), d.getRegistrationId(),
                d.getDeadlineType(), d.getDescription(), d.getDueDate(), d.getStatus(),
                d.isDueWithin(DUE_SOON_DAYS), d.getCompletedAt());
    }
}
