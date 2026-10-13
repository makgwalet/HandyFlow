package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import za.co.handyflow.platform.debtcollection.application.LegalHandoverStatusSync;
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
 * <p>
 * STATUS HAND-BACK TO DEBT COLLECTION. After advanceStatus()/close() commit, the new status is reported through
 * LegalHandoverStatusSync so a matter that was opened from a debt-collection handover keeps that handover's legal
 * status current (and prompts the collector when the matter ends). It is registered for AFTER commit and wrapped
 * in a catch-all: a failure on the debt-collection side can never undo or fail a litigation update, and matters
 * that did not come from a handover are simply ignored by the callee. The bean is optional (ObjectProvider).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LitigationMatterService {

    private final LitigationMatterRepository repository;
    private final LegalComplianceNumberGenerator numberGenerator;
    private final ObjectProvider<LegalHandoverStatusSync> handoverSync;

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
        LitigationMatter saved = repository.save(matter);
        notifyHandover(tenantId, saved, matter.getMatterNumber() + " moved to " + newStatus);
        return saved;
    }

    @Transactional
    public LitigationMatter close(TenantId tenantId, UUID id, LitigationStatus finalStatus, String outcomeNotes) {
        LitigationMatter matter = findActive(tenantId, id);
        matter.close(finalStatus, outcomeNotes);
        LitigationMatter saved = repository.save(matter);
        notifyHandover(tenantId, saved, matter.getMatterNumber() + " closed as " + finalStatus
                + (outcomeNotes != null && !outcomeNotes.isBlank() ? ": " + outcomeNotes : ""));
        return saved;
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

    private void notifyHandover(TenantId tenantId, LitigationMatter matter, String detail) {
        LegalHandoverStatusSync sync = handoverSync.getIfAvailable();
        if (sync == null) return;
        UUID matterId = matter.getId();
        String status = matter.getStatus().name();
        Runnable call = () -> {
            try {
                sync.syncByMatter(tenantId, matterId, status, detail);
            } catch (Exception e) {
                log.warn("[Legal/Compliance] handover status hand-back failed for matter {}: {}", matterId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    call.run();
                }
            });
        } else {
            call.run();
        }
    }
}
