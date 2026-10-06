package za.co.handyflow.platform.compliancetender.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.compliancetender.domain.model.TenderRequirement;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceRequirementRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRequirementRepository;
import za.co.handyflow.platform.compliancetender.dto.*;
import za.co.handyflow.platform.identity.TenantNumberingFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Phase 2 core tender service. Numbering uses TenantNumberingFacade the
 * same way every other document type migrated this session does — type
 * code "TND", document type "TENDER".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenderService {

    private final TenderRepository tenderRepository;
    private final TenderRequirementRepository requirementRepository;
    private final TenantNumberingFacade numberingFacade;
    private final TenderSnapshotService snapshotService;
    private final ComplianceRequirementRepository catalogueRepository;

    @Transactional(readOnly = true)
    public Page<TenderResponse> getTenders(TenantId tenantId, String status, Pageable pageable) {
        return tenderRepository.findAll(tenantId, status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TenderResponse getTender(TenantId tenantId, UUID id) {
        return toResponse(find(tenantId, id));
    }

    @Transactional
    public TenderResponse create(TenantId tenantId, CreateTenderRequest req, UUID createdBy) {
        String tenderNumber = numberingFacade.next(tenantId, "TENDER", "TND");
        Tender tender = Tender.create(tenantId, tenderNumber, req.name(), req.tenderAuthority(),
                req.authorityReferenceNumber(), req.closingDate(), req.briefingDate(), req.siteInspectionDate(),
                req.estimatedValue(), req.industry(), req.requiredClassOfWork(), createdBy);
        tenderRepository.save(tender);
        log.info("Tender created id={} number={} tenant={}", tender.getId(), tenderNumber, tenantId);
        return toResponse(tender);
    }

    @Transactional
    public TenderResponse update(TenantId tenantId, UUID id, UpdateTenderRequest req, UUID updatedBy) {
        Tender tender = find(tenantId, id);
        tender.updateDetails(req.name(), req.tenderAuthority(), req.authorityReferenceNumber(), req.closingDate(), req.briefingDate(),
                req.siteInspectionDate(), req.estimatedValue(), req.industry(), req.requiredClassOfWork(), updatedBy);
        tenderRepository.save(tender);
        log.info("Tender updated id={} tenant={}", id, tenantId);
        return toResponse(tender);
    }

    @Transactional
    public TenderResponse transition(TenantId tenantId, UUID id, TransitionTenderRequest req, UUID updatedBy) {
        Tender tender = find(tenantId, id);
        tender.transitionTo(req.newStatus(), updatedBy);
        tenderRepository.save(tender);
        log.info("Tender transitioned id={} newStatus={} tenant={}", id, req.newStatus(), tenantId);

        // A snapshot is captured automatically, not left as a separate step a
        // caller could forget — "submitted with no record of what was
        // submitted" would defeat the entire reason TenderSnapshotService
        // exists. See TenderSubmissionSnapshot's own Javadoc.
        if ("SUBMITTED".equals(req.newStatus())) {
            snapshotService.captureSnapshot(tenantId, id, updatedBy);
        }

        return toResponse(tender);
    }

    @Transactional
    public TenderResponse recordOutcome(TenantId tenantId, UUID id, RecordTenderOutcomeRequest req, UUID updatedBy) {
        Tender tender = find(tenantId, id);
        tender.recordOutcome(req.outcome(), req.reason(), req.awardedValue(), updatedBy);
        tenderRepository.save(tender);
        log.info("Tender outcome recorded id={} outcome={} tenant={}", id, req.outcome(), tenantId);
        return toResponse(tender);
    }

    // ── Requirement matrix ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TenderRequirementResponse> getRequirements(TenantId tenantId, UUID tenderId) {
        find(tenantId, tenderId); // confirms the tender exists and belongs to this tenant
        return requirementRepository.findByTender(tenantId, tenderId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TenderRequirementResponse addRequirement(TenantId tenantId, UUID tenderId,
                                                     CreateTenderRequirementRequest req, UUID createdBy) {
        find(tenantId, tenderId);
        // the foreign key only proves the row exists, not that it is THIS tenant's: check it is one of your own tracked requirements before attaching it
        if (req.complianceRequirementId() != null) {
            catalogueRepository.findByIdForTenant(tenantId, req.complianceRequirementId())
                    .orElseThrow(() -> new IllegalArgumentException("That requirement is not one of your tracked requirements"));
        }
        for (TenderRequirement existing : requirementRepository.findByTender(tenantId, tenderId)) {
            boolean sameTracked = req.complianceRequirementId() != null && req.complianceRequirementId().equals(existing.getComplianceRequirementId());
            boolean sameText = existing.getDescription() != null && req.description() != null
                    && existing.getDescription().trim().equalsIgnoreCase(req.description().trim());
            if (sameTracked || sameText) throw new IllegalStateException("\"" + existing.getDescription() + "\" is already on this tender.");
        }
        TenderRequirement requirement = TenderRequirement.create(tenantId, tenderId, req.complianceRequirementId(),
                req.description(), req.source(), createdBy);
        requirementRepository.save(requirement);
        return toResponse(requirement);
    }

    @Transactional
    public TenderRequirementResponse updateRequirementStatus(TenantId tenantId, UUID requirementId,
                                                              UpdateTenderRequirementStatusRequest req, UUID updatedBy) {
        TenderRequirement requirement = requirementRepository.findByIdForTenant(tenantId, requirementId)
                .orElseThrow(() -> new ResourceNotFoundException("TenderRequirement", requirementId.toString()));
        requirement.setStatus(req.status(), updatedBy);
        requirementRepository.save(requirement);
        return toResponse(requirement);
    }

    @Transactional
    public TenderRequirementResponse renameRequirement(TenantId tenantId, UUID requirementId, UpdateTenderRequirementRequest req, UUID updatedBy) {
        TenderRequirement requirement = requirementFor(tenantId, requirementId);
        if (requirement.getComplianceRequirementId() != null) {
            throw new IllegalStateException("This line comes from your tracked requirements. Change its wording under Requirements, or remove it and add a custom line.");
        }
        for (TenderRequirement other : requirementRepository.findByTender(tenantId, requirement.getTenderId())) {
            if (!other.getId().equals(requirement.getId()) && other.getDescription() != null
                    && other.getDescription().trim().equalsIgnoreCase(req.description().trim())) {
                throw new IllegalStateException("\"" + other.getDescription() + "\" is already on this tender.");
            }
        }
        requirement.rename(req.description(), updatedBy);
        requirementRepository.save(requirement);
        return toResponse(requirement);
    }

    @Transactional
    public void removeRequirement(TenantId tenantId, UUID requirementId) {
        TenderRequirement requirement = requirementFor(tenantId, requirementId);
        requirementRepository.delete(requirement);
        log.info("Tender requirement removed id={} tender={} tenant={}", requirementId, requirement.getTenderId(), tenantId);
    }

    /** The requirement, only if its tender is still being prepared: once submitted the matrix is the record of what was submitted. */
    private TenderRequirement requirementFor(TenantId tenantId, UUID requirementId) {
        TenderRequirement requirement = requirementRepository.findByIdForTenant(tenantId, requirementId)
                .orElseThrow(() -> new ResourceNotFoundException("TenderRequirement", requirementId.toString()));
        if (!find(tenantId, requirement.getTenderId()).isPreparing()) {
            throw new IllegalStateException("The requirement matrix can only be changed while the tender is being prepared.");
        }
        return requirement;
    }

    private Tender find(TenantId tenantId, UUID id) {
        return tenderRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));
    }

    private TenderResponse toResponse(Tender t) {
        return new TenderResponse(t.getId(), t.getTenderNumber(), t.getName(), t.getTenderAuthority(),
                t.getAuthorityReferenceNumber(), t.getClosingDate(), t.getBriefingDate(), t.getSiteInspectionDate(),
                t.getEstimatedValue(), t.getIndustry(), t.getRequiredClassOfWork(), t.getStatus(),
                t.getOutcomeReason(), t.getAwardedValue(), t.getSubmittedAt(), t.getCreatedAt());
    }

    private TenderRequirementResponse toResponse(TenderRequirement r) {
        return new TenderRequirementResponse(r.getId(), r.getTenderId(), r.getComplianceRequirementId(),
                r.getDescription(), r.getSource(), r.getStatus());
    }
}
