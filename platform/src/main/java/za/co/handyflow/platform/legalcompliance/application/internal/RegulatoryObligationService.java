package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.ObligationCategory;
import za.co.handyflow.platform.legalcompliance.domain.model.ObligationStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.RecurrenceInterval;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryObligation;
import za.co.handyflow.platform.legalcompliance.domain.repository.RegulatoryObligationRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for the regulatory obligation tracker — thin service over
 * the RegulatoryObligation aggregate, same "controller is thin, service
 * orchestrates + enforces lookups, entity owns its own state machine" split
 * as every other module in this codebase (see e.g. CustomerController's own
 * class Javadoc for the stated rule: "if you find yourself writing an
 * if/else in a controller, ask yourself: should this be in the service
 * instead?").
 */
@Service
@RequiredArgsConstructor
public class RegulatoryObligationService {

    private final RegulatoryObligationRepository repository;

    @Transactional
    public RegulatoryObligation create(TenantId tenantId, String title, ObligationCategory category,
                                        String regulationReference, String description,
                                        UUID responsibleUserId, String responsibleUserName,
                                        LocalDate reviewDate, RecurrenceInterval recurrence,
                                        UUID linkedContractId, UUID createdBy) {
        RegulatoryObligation obligation = RegulatoryObligation.create(tenantId, title, category,
                regulationReference, description, responsibleUserId, responsibleUserName,
                reviewDate, recurrence, createdBy);
        if (linkedContractId != null) {
            obligation.linkContract(linkedContractId);
        }
        return repository.save(obligation);
    }

    @Transactional(readOnly = true)
    public Page<RegulatoryObligation> list(TenantId tenantId, ObligationStatus status, Pageable pageable) {
        return repository.findAllActive(tenantId, status, pageable);
    }

    @Transactional(readOnly = true)
    public RegulatoryObligation get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    /** Unpaginated — used by LegalCompliancePdfService's register export, which needs every active record, not one page. */
    @Transactional(readOnly = true)
    public List<RegulatoryObligation> listAll(TenantId tenantId) {
        return repository.findAllActive(tenantId, null, Pageable.unpaged(Sort.by("reviewDate").ascending()))
                .getContent();
    }

    @Transactional(readOnly = true)
    public List<RegulatoryObligation> findDueWithin(TenantId tenantId, int days) {
        LocalDate today = LocalDate.now();
        return repository.findDueWithin(tenantId, today, today.plusDays(days));
    }

    @Transactional
    public RegulatoryObligation update(TenantId tenantId, UUID id, String title, String regulationReference,
                                        String description, UUID responsibleUserId, String responsibleUserName,
                                        LocalDate reviewDate, RecurrenceInterval recurrence) {
        RegulatoryObligation obligation = findActive(tenantId, id);
        obligation.update(title, regulationReference, description, responsibleUserId, responsibleUserName,
                reviewDate, recurrence);
        return repository.save(obligation);
    }

    @Transactional
    public RegulatoryObligation markReviewed(TenantId tenantId, UUID id, UUID reviewedBy, String reviewedByName,
                                              String notes) {
        RegulatoryObligation obligation = findActive(tenantId, id);
        obligation.markReviewed(reviewedBy, reviewedByName, notes);
        return repository.save(obligation);
    }

    @Transactional
    public RegulatoryObligation markNonCompliant(TenantId tenantId, UUID id, String notes) {
        RegulatoryObligation obligation = findActive(tenantId, id);
        obligation.markNonCompliant(notes);
        return repository.save(obligation);
    }

    @Transactional
    public RegulatoryObligation linkContract(TenantId tenantId, UUID id, UUID contractId) {
        RegulatoryObligation obligation = findActive(tenantId, id);
        obligation.linkContract(contractId);
        return repository.save(obligation);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id, UUID deletedBy) {
        RegulatoryObligation obligation = findActive(tenantId, id);
        obligation.softDelete(deletedBy);
        repository.save(obligation);
    }

    /**
     * Cross-tenant status refresh — called once daily by
     * LegalComplianceNotificationScheduler, ahead of grouping the result by
     * tenant for notification purposes. Deliberately returns the full,
     * now-refreshed list rather than re-querying per tenant: the entities
     * are already loaded and dirty-checked in this single transaction, so
     * a second cross-tenant query would just be wasted work.
     */
    @Transactional
    public List<RegulatoryObligation> refreshAllStatuses(int dueSoonThresholdDays) {
        LocalDate today = LocalDate.now();
        List<RegulatoryObligation> all = repository.findAllActiveAcrossTenants();
        all.forEach(o -> o.refreshStatus(today, dueSoonThresholdDays));
        return repository.saveAll(all);
    }

    private RegulatoryObligation findActive(TenantId tenantId, UUID id) {
        return repository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("RegulatoryObligation", id.toString()));
    }
}
