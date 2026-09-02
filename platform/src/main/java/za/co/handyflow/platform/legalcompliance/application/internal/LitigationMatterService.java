package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationMatter;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationMatterType;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationStatus;
import za.co.handyflow.platform.legalcompliance.domain.repository.LitigationMatterRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Business logic for the litigation / dispute register. matterNumber is
 * assigned here (not left to the caller or the entity) via
 * LegalComplianceNumberGenerator, same "service owns numbering, entity owns
 * state" split as PM's use of SequenceService and invoicing's use of
 * InvoiceNumberGenerator.
 */
@Service
@RequiredArgsConstructor
public class LitigationMatterService {

    private final LitigationMatterRepository repository;
    private final LegalComplianceNumberGenerator numberGenerator;

    @Transactional
    public LitigationMatter create(TenantId tenantId, String title, LitigationMatterType matterType,
                                    String opposingParty, String ourSide, BigDecimal estimatedExposure,
                                    String legalRepresentative, String courtOrForum, String caseReference,
                                    LocalDate openedDate, LocalDate nextKeyDate, String description,
                                    UUID linkedContractId, UUID createdBy) {
        String matterNumber = numberGenerator.nextMatterNumber(tenantId);
        LitigationMatter matter = LitigationMatter.create(tenantId, matterNumber, title, matterType,
                opposingParty, ourSide, estimatedExposure, legalRepresentative, courtOrForum, caseReference,
                openedDate, nextKeyDate, description, createdBy);
        if (linkedContractId != null) {
            matter.linkContract(linkedContractId);
        }
        return repository.save(matter);
    }

    @Transactional(readOnly = true)
    public Page<LitigationMatter> list(TenantId tenantId, LitigationStatus status, Pageable pageable) {
        return repository.findAllActive(tenantId, status, pageable);
    }

    @Transactional(readOnly = true)
    public LitigationMatter get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    /** Unpaginated — used by LegalCompliancePdfService's register export. */
    @Transactional(readOnly = true)
    public java.util.List<LitigationMatter> listAll(TenantId tenantId) {
        return repository.findAllActive(tenantId, null, Pageable.unpaged(Sort.by("openedDate").descending()))
                .getContent();
    }

    @Transactional(readOnly = true)
    public long count(TenantId tenantId) {
        return repository.countByTenant(tenantId);
    }

    @Transactional
    public LitigationMatter update(TenantId tenantId, UUID id, String title, String opposingParty, String ourSide,
                                    BigDecimal estimatedExposure, String legalRepresentative, String courtOrForum,
                                    String caseReference, LocalDate nextKeyDate, String description) {
        LitigationMatter matter = findActive(tenantId, id);
        matter.update(title, opposingParty, ourSide, estimatedExposure, legalRepresentative, courtOrForum,
                caseReference, nextKeyDate, description);
        return repository.save(matter);
    }

    @Transactional
    public LitigationMatter advanceStatus(TenantId tenantId, UUID id, LitigationStatus newStatus) {
        LitigationMatter matter = findActive(tenantId, id);
        matter.advanceStatus(newStatus);
        return repository.save(matter);
    }

    @Transactional
    public LitigationMatter close(TenantId tenantId, UUID id, LitigationStatus finalStatus, String outcomeNotes) {
        LitigationMatter matter = findActive(tenantId, id);
        matter.close(finalStatus, outcomeNotes);
        return repository.save(matter);
    }

    @Transactional
    public LitigationMatter linkContract(TenantId tenantId, UUID id, UUID contractId) {
        LitigationMatter matter = findActive(tenantId, id);
        matter.linkContract(contractId);
        return repository.save(matter);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id, UUID deletedBy) {
        LitigationMatter matter = findActive(tenantId, id);
        matter.softDelete(deletedBy);
        repository.save(matter);
    }

    private LitigationMatter findActive(TenantId tenantId, UUID id) {
        return repository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("LitigationMatter", id.toString()));
    }
}
