package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.DataCategory;
import za.co.handyflow.platform.legalcompliance.domain.model.DsarRequest;
import za.co.handyflow.platform.legalcompliance.domain.model.DsarRequestType;
import za.co.handyflow.platform.legalcompliance.domain.model.DsarStatus;
import za.co.handyflow.platform.legalcompliance.domain.repository.DsarRequestRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for the org-wide DSAR (data subject access request)
 * register — see DsarRequest's own class Javadoc for why this is
 * deliberately NOT hard-linked to crm.Customer, and for the flagged
 * "receivedDate + 30 days" dueDate default.
 */
@Service
@RequiredArgsConstructor
public class DsarRequestService {

    private final DsarRequestRepository repository;
    private final LegalComplianceNumberGenerator numberGenerator;

    @Transactional
    public DsarRequest create(TenantId tenantId, DsarRequestType requestType, DataCategory dataCategory,
                               String requesterName, String requesterEmail, String requesterContact,
                               LocalDate receivedDate, UUID createdBy) {
        String requestNumber = numberGenerator.nextDsarNumber(tenantId);
        DsarRequest request = DsarRequest.create(tenantId, requestNumber, requestType, dataCategory, requesterName,
                requesterEmail, requesterContact, receivedDate, createdBy);
        return repository.save(request);
    }

    @Transactional(readOnly = true)
    public Page<DsarRequest> list(TenantId tenantId, DsarStatus status, Pageable pageable) {
        return repository.findAllActive(tenantId, status, pageable);
    }

    @Transactional(readOnly = true)
    public DsarRequest get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    /** Unpaginated — used by LegalCompliancePdfService's register export. */
    @Transactional(readOnly = true)
    public List<DsarRequest> listAll(TenantId tenantId) {
        return repository.findAllActive(tenantId, null, Pageable.unpaged(Sort.by("dueDate").ascending()))
                .getContent();
    }

    @Transactional(readOnly = true)
    public List<DsarRequest> listOpen(TenantId tenantId) {
        return repository.findOpen(tenantId);
    }

    @Transactional
    public DsarRequest assign(TenantId tenantId, UUID id, UUID userId, String userName) {
        DsarRequest request = findActive(tenantId, id);
        request.assign(userId, userName);
        return repository.save(request);
    }

    @Transactional
    public DsarRequest complete(TenantId tenantId, UUID id, String resolutionNotes) {
        DsarRequest request = findActive(tenantId, id);
        request.complete(resolutionNotes);
        return repository.save(request);
    }

    @Transactional
    public DsarRequest reject(TenantId tenantId, UUID id, String resolutionNotes) {
        DsarRequest request = findActive(tenantId, id);
        request.reject(resolutionNotes);
        return repository.save(request);
    }

    @Transactional
    public DsarRequest withdraw(TenantId tenantId, UUID id, String resolutionNotes) {
        DsarRequest request = findActive(tenantId, id);
        request.withdraw(resolutionNotes);
        return repository.save(request);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id, UUID deletedBy) {
        DsarRequest request = findActive(tenantId, id);
        request.softDelete(deletedBy);
        repository.save(request);
    }

    private DsarRequest findActive(TenantId tenantId, UUID id) {
        return repository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("DsarRequest", id.toString()));
    }
}
