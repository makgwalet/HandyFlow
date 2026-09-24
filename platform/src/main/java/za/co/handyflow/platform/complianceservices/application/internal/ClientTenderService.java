package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTender;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderRequirement;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRequirementRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.dto.*;
import za.co.handyflow.platform.identity.TenantNumberingFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Client-scoped counterpart to compliancetender.TenderService — same
 * lifecycle delegation, same numbering approach. Numbering uses
 * TenantNumberingFacade with documentType "CLIENT_TENDER" and default
 * code "CTND" — a distinct code from compliancetender's own tender
 * numbering ("TND"), since these are numbered independently per tenant
 * and mixing the two prefixes in one tenant's number sequence would be
 * confusing to whoever reads them later.
 * <p>
 * Deliberately does NOT yet capture a submission snapshot on reaching
 * SUBMITTED, unlike compliancetender.TenderService's own
 * TenderSnapshotService integration — that's real, separate work for a
 * later phase, not assumed complete here just because the tenant-scoped
 * version has it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientTenderService {

    private final ClientTenderRepository tenderRepository;
    private final ClientTenderRequirementRepository requirementRepository;
    private final ComplianceClientRepository clientRepository;
    private final TenantNumberingFacade numberingFacade;

    @Transactional(readOnly = true)
    public Page<ClientTenderResponse> getTenders(TenantId tenantId, UUID clientId, String status, Pageable pageable) {
        requireClient(tenantId, clientId);
        return tenderRepository.findByClient(tenantId, clientId, status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ClientTenderResponse getTender(TenantId tenantId, UUID id) {
        return toResponse(find(tenantId, id));
    }

    @Transactional
    public ClientTenderResponse create(TenantId tenantId, UUID clientId, CreateClientTenderRequest req, UUID createdBy) {
        requireClient(tenantId, clientId);
        String tenderNumber = numberingFacade.next(tenantId, "CLIENT_TENDER", "CTND");
        ClientTender tender = ClientTender.create(tenantId, clientId, tenderNumber, req.name(), req.tenderAuthority(),
                req.authorityReferenceNumber(), req.closingDate(), req.briefingDate(), req.siteInspectionDate(),
                req.estimatedValue(), req.industry(), req.requiredClassOfWork(), createdBy);
        tenderRepository.save(tender);
        log.info("Client tender created id={} number={} client={} tenant={}", tender.getId(), tenderNumber, clientId, tenantId);
        return toResponse(tender);
    }

    @Transactional
    public ClientTenderResponse transition(TenantId tenantId, UUID id, TransitionClientTenderRequest req, UUID updatedBy) {
        ClientTender tender = find(tenantId, id);
        tender.transitionTo(req.newStatus(), updatedBy);
        tenderRepository.save(tender);
        log.info("Client tender transitioned id={} newStatus={} tenant={}", id, req.newStatus(), tenantId);
        return toResponse(tender);
    }

    @Transactional
    public ClientTenderResponse recordOutcome(TenantId tenantId, UUID id, RecordClientTenderOutcomeRequest req, UUID updatedBy) {
        ClientTender tender = find(tenantId, id);
        tender.recordOutcome(req.outcome(), req.reason(), req.awardedValue(), updatedBy);
        tenderRepository.save(tender);
        log.info("Client tender outcome recorded id={} outcome={} tenant={}", id, req.outcome(), tenantId);
        return toResponse(tender);
    }

    @Transactional(readOnly = true)
    public List<ClientTenderRequirementResponse> getRequirements(TenantId tenantId, UUID clientTenderId) {
        find(tenantId, clientTenderId);
        return requirementRepository.findByTender(tenantId, clientTenderId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ClientTenderRequirementResponse addRequirement(TenantId tenantId, UUID clientTenderId,
                                                           CreateClientTenderRequirementRequest req, UUID createdBy) {
        find(tenantId, clientTenderId);
        ClientTenderRequirement requirement = ClientTenderRequirement.create(tenantId, clientTenderId,
                req.clientRequirementId(), req.description(), req.source(), createdBy);
        requirementRepository.save(requirement);
        return toResponse(requirement);
    }

    @Transactional
    public ClientTenderRequirementResponse updateRequirementStatus(TenantId tenantId, UUID requirementId,
                                                                    UpdateClientTenderRequirementStatusRequest req, UUID updatedBy) {
        ClientTenderRequirement requirement = requirementRepository.findByIdForTenant(tenantId, requirementId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientTenderRequirement", requirementId.toString()));
        requirement.setStatus(req.status(), updatedBy);
        requirementRepository.save(requirement);
        return toResponse(requirement);
    }

    private void requireClient(TenantId tenantId, UUID clientId) {
        if (clientRepository.findByIdForTenant(tenantId, clientId).isEmpty())
            throw new ResourceNotFoundException("ComplianceClient", clientId.toString());
    }

    private ClientTender find(TenantId tenantId, UUID id) {
        return tenderRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ClientTender", id.toString()));
    }

    private ClientTenderResponse toResponse(ClientTender t) {
        return new ClientTenderResponse(t.getId(), t.getClientId(), t.getTenderNumber(), t.getName(), t.getTenderAuthority(),
                t.getAuthorityReferenceNumber(), t.getClosingDate(), t.getBriefingDate(), t.getSiteInspectionDate(),
                t.getEstimatedValue(), t.getIndustry(), t.getRequiredClassOfWork(), t.getStatus(),
                t.getOutcomeReason(), t.getAwardedValue(), t.getSubmittedAt(), t.getCreatedAt());
    }

    private ClientTenderRequirementResponse toResponse(ClientTenderRequirement r) {
        return new ClientTenderRequirementResponse(r.getId(), r.getClientTenderId(), r.getClientRequirementId(),
                r.getDescription(), r.getSource(), r.getStatus());
    }
}
