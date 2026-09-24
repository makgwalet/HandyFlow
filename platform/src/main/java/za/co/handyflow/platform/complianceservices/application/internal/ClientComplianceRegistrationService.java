package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceRegistration;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceRegistrationRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.dto.ClientComplianceRegistrationResponse;
import za.co.handyflow.platform.complianceservices.dto.CreateClientComplianceRegistrationRequest;
import za.co.handyflow.platform.complianceservices.dto.UpdateClientComplianceRegistrationRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * See ClientComplianceRegistration's own Javadoc for why this is a
 * parallel entity/table rather than compliancetender's own
 * ComplianceRegistration with an added client dimension.
 * EXPIRING_SOON_DAYS matches compliancetender's own
 * ComplianceRegistrationService constant — same 30-day window, not a
 * fresh number picked for this module.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientComplianceRegistrationService {

    private static final int EXPIRING_SOON_DAYS = 30;

    private final ClientComplianceRegistrationRepository registrationRepository;
    private final ComplianceClientRepository clientRepository;

    @Transactional(readOnly = true)
    public List<ClientComplianceRegistrationResponse> getRegistrations(TenantId tenantId, UUID clientId) {
        requireClient(tenantId, clientId);
        return registrationRepository.findByClient(tenantId, clientId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClientComplianceRegistrationResponse getRegistration(TenantId tenantId, UUID id) {
        return toResponse(find(tenantId, id));
    }

    @Transactional
    public ClientComplianceRegistrationResponse create(TenantId tenantId, UUID clientId,
                                                        CreateClientComplianceRegistrationRequest req, UUID createdBy) {
        requireClient(tenantId, clientId);
        ClientComplianceRegistration registration = ClientComplianceRegistration.create(tenantId, clientId,
                req.authority(), req.registrationType(), req.registrationNumber(), req.issuedDate(),
                req.expiryDate(), req.notes(), createdBy);
        registrationRepository.save(registration);
        log.info("Client compliance registration created id={} client={} authority={} type={} tenant={}",
                registration.getId(), clientId, registration.getAuthority(), registration.getRegistrationType(), tenantId);
        return toResponse(registration);
    }

    @Transactional
    public ClientComplianceRegistrationResponse update(TenantId tenantId, UUID id,
                                                        UpdateClientComplianceRegistrationRequest req, UUID updatedBy) {
        ClientComplianceRegistration registration = find(tenantId, id);
        registration.update(req.registrationNumber(), req.status(), req.issuedDate(), req.expiryDate(), req.notes(), updatedBy);
        registrationRepository.save(registration);
        return toResponse(registration);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        registrationRepository.delete(find(tenantId, id));
        log.info("Client compliance registration deleted id={} tenant={}", id, tenantId);
    }

    private void requireClient(TenantId tenantId, UUID clientId) {
        if (clientRepository.findByIdForTenant(tenantId, clientId).isEmpty())
            throw new ResourceNotFoundException("ComplianceClient", clientId.toString());
    }

    private ClientComplianceRegistration find(TenantId tenantId, UUID id) {
        return registrationRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ClientComplianceRegistration", id.toString()));
    }

    private ClientComplianceRegistrationResponse toResponse(ClientComplianceRegistration r) {
        return new ClientComplianceRegistrationResponse(r.getId(), r.getClientId(), r.getAuthority(),
                r.getRegistrationType(), r.getRegistrationNumber(), r.getStatus(), r.getIssuedDate(),
                r.getExpiryDate(), r.getNotes(), r.isExpiringWithin(EXPIRING_SOON_DAYS), r.getCreatedAt());
    }
}
