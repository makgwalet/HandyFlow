package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.CustomerCommunication;
import za.co.handyflow.platform.crm.domain.repository.CustomerCommunicationRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.dto.CommunicationResponse;
import za.co.handyflow.platform.crm.dto.LogCommunicationRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerCommunicationService {

    private final CustomerCommunicationRepository communicationRepository;
    private final CustomerRepository              customerRepository;

    @Transactional
    public CommunicationResponse log(TenantId tenantId, UUID customerId,
                                     LogCommunicationRequest req, UUID loggedBy) {
        if (!customerRepository.existsActiveById(tenantId, customerId)) {
            throw new ResourceNotFoundException("Customer", customerId.toString());
        }
        var communication = CustomerCommunication.create(
                tenantId, customerId, req.type(), req.direction(), req.summary(), req.occurredAt(), loggedBy);
        communicationRepository.save(communication);
        log.info("[CRM] Communication logged customer={} type={} direction={} tenant={}",
                customerId, req.type(), req.direction(), tenantId);
        return toResponse(communication);
    }

    @Transactional(readOnly = true)
    public List<CommunicationResponse> getForCustomer(TenantId tenantId, UUID customerId) {
        return communicationRepository.findByCustomer(tenantId, customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(TenantId tenantId, UUID communicationId) {
        var communication = communicationRepository.findByIdAndTenant(tenantId, communicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Communication", communicationId.toString()));
        communicationRepository.delete(communication);
    }

    private CommunicationResponse toResponse(CustomerCommunication c) {
        return new CommunicationResponse(
                c.getId(), c.getCustomerId(), c.getType().name(), c.getDirection().name(),
                c.getSummary(), c.getOccurredAt(), c.getLoggedBy(), c.getCreatedAt()
        );
    }
}