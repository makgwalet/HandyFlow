package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClient;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.dto.ComplianceClientResponse;
import za.co.handyflow.platform.complianceservices.dto.CreateComplianceClientRequest;
import za.co.handyflow.platform.complianceservices.dto.UpdateComplianceClientRequest;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Phase 1 of complianceservices — see this module's own package-info.java
 * for the full scoping. Deliberately just the client entity and its CRUD
 * for now; client-specific compliance/tender tracking (actually running
 * compliancetender's engine per-client rather than per-tenant) is a
 * separate, larger design question for a later phase, not bundled in here.
 * <p>
 * Validates a linked CRM customer actually exists at write time (fail
 * loudly, not silently) — the same asymmetric validation shape
 * TenderPersonnelService already established for HR employee references:
 * strict on write, tolerant on read (a since-deleted CRM customer doesn't
 * make an existing client record unreadable, it just reports
 * crmCustomerFound=false).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceClientService {

    private final ComplianceClientRepository clientRepository;
    private final CrmFacade crmFacade;

    @Transactional(readOnly = true)
    public Page<ComplianceClientResponse> getClients(TenantId tenantId, String status, Pageable pageable) {
        return clientRepository.findAll(tenantId, status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ComplianceClientResponse getClient(TenantId tenantId, UUID id) {
        return toResponse(find(tenantId, id));
    }

    @Transactional
    public ComplianceClientResponse create(TenantId tenantId, CreateComplianceClientRequest req, UUID createdBy) {
        if (req.crmCustomerId() != null) {
            if (!crmFacade.customerExists(tenantId, req.crmCustomerId())) {
                throw new HandyFlowException("No CRM customer found with id " + req.crmCustomerId(),
                        HttpStatus.BAD_REQUEST, "CRM_CUSTOMER_NOT_FOUND");
            }
            clientRepository.findByCrmCustomerId(tenantId, req.crmCustomerId()).ifPresent(existing -> {
                throw new HandyFlowException("CRM customer " + req.crmCustomerId()
                        + " is already linked to compliance client \"" + existing.getName() + "\"",
                        HttpStatus.CONFLICT, "CRM_CUSTOMER_ALREADY_LINKED");
            });
        }
        ComplianceClient client = ComplianceClient.create(tenantId, req.name(), req.crmCustomerId(),
                req.contactEmail(), req.contactPhone(), req.mandateNotes(), createdBy);
        clientRepository.save(client);
        log.info("Compliance client created id={} name={} crmCustomerId={} tenant={}",
                client.getId(), client.getName(), req.crmCustomerId(), tenantId);
        return toResponse(client);
    }

    @Transactional
    public ComplianceClientResponse update(TenantId tenantId, UUID id, UpdateComplianceClientRequest req, UUID updatedBy) {
        ComplianceClient client = find(tenantId, id);
        client.update(req.name(), req.contactEmail(), req.contactPhone(), req.mandateNotes(), updatedBy);
        clientRepository.save(client);
        return toResponse(client);
    }

    @Transactional
    public ComplianceClientResponse deactivate(TenantId tenantId, UUID id, UUID updatedBy) {
        ComplianceClient client = find(tenantId, id);
        client.deactivate(updatedBy);
        clientRepository.save(client);
        return toResponse(client);
    }

    @Transactional
    public ComplianceClientResponse reactivate(TenantId tenantId, UUID id, UUID updatedBy) {
        ComplianceClient client = find(tenantId, id);
        client.reactivate(updatedBy);
        clientRepository.save(client);
        return toResponse(client);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        clientRepository.delete(find(tenantId, id));
        log.info("Compliance client deleted id={} tenant={}", id, tenantId);
    }

    private ComplianceClient find(TenantId tenantId, UUID id) {
        return clientRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", id.toString()));
    }

    private ComplianceClientResponse toResponse(ComplianceClient c) {
        if (c.getCrmCustomerId() == null) {
            return new ComplianceClientResponse(c.getId(), c.getName(), null, false, null,
                    c.getContactEmail(), c.getContactPhone(), c.getMandateNotes(), c.getStatus(), c.getCreatedAt());
        }
        var customer = crmFacade.findCustomerById(c.getTenantId(), c.getCrmCustomerId());
        return new ComplianceClientResponse(c.getId(), c.getName(), c.getCrmCustomerId(),
                customer.isPresent(), customer.map(cs -> cs.name()).orElse(null),
                c.getContactEmail(), c.getContactPhone(), c.getMandateNotes(), c.getStatus(), c.getCreatedAt());
    }
}
