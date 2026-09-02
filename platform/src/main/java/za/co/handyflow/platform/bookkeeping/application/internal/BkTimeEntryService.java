package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookkeeping.domain.model.BkTimeEntry;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkClientRepository;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkTimeEntryRepository;
import za.co.handyflow.platform.bookkeeping.dto.BkTimeEntryResponse;
import za.co.handyflow.platform.bookkeeping.dto.CreateBkTimeEntryRequest;
import za.co.handyflow.platform.bookkeeping.dto.UpdateBkTimeEntryRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Staff time-logging against a client under a TIME_AND_MATERIALS {@link
 * za.co.handyflow.platform.bookkeeping.domain.model.BkServiceAgreement}.
 * Mirrors {@code accountant.TimeEntry}'s own CRUD shape almost exactly,
 * including the billed-locks-editing guard enforced by the entity itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BkTimeEntryService {

    private final BkTimeEntryRepository timeEntryRepository;
    private final BkClientRepository clientRepository;

    @Transactional(readOnly = true)
    public Page<BkTimeEntryResponse> getTimeEntries(TenantId tenantId, UUID clientId, Pageable pageable) {
        return timeEntryRepository.findByClient(tenantId, clientId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BkTimeEntryResponse getTimeEntry(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<BkTimeEntryResponse> getUnbilledByClient(TenantId tenantId, UUID clientId) {
        return timeEntryRepository.findUnbilledByClient(tenantId, clientId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public BkTimeEntryResponse logTime(TenantId tenantId, CreateBkTimeEntryRequest req) {
        clientRepository.findActiveById(tenantId, req.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("BkClient", req.clientId().toString()));

        BkTimeEntry entry = BkTimeEntry.create(tenantId, req.clientId(), req.practitionerId(), req.practitionerName(),
                req.entryDate(), req.activityType(), req.description(), req.hours(), req.hourlyRate(), req.billable());
        timeEntryRepository.save(entry);
        log.info("Bookkeeping time entry logged client={} tenant={} hours={}", req.clientId(), tenantId, req.hours());
        return toResponse(entry);
    }

    @Transactional
    public BkTimeEntryResponse update(TenantId tenantId, UUID id, UpdateBkTimeEntryRequest req) {
        BkTimeEntry entry = findActive(tenantId, id);
        entry.update(req.entryDate(), req.activityType(), req.description(), req.hours(), req.hourlyRate(), req.billable());
        timeEntryRepository.save(entry);
        return toResponse(entry);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        BkTimeEntry entry = findActive(tenantId, id);
        if (!entry.isEditable()) {
            throw new IllegalStateException("Cannot delete a time entry that has already been billed");
        }
        timeEntryRepository.delete(entry);
    }

    @Transactional
    public BkTimeEntryResponse writeOff(TenantId tenantId, UUID id) {
        BkTimeEntry entry = findActive(tenantId, id);
        entry.writeOff();
        timeEntryRepository.save(entry);
        return toResponse(entry);
    }

    BkTimeEntry findActive(TenantId tenantId, UUID id) {
        return timeEntryRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("BkTimeEntry", id.toString()));
    }

    private BkTimeEntryResponse toResponse(BkTimeEntry t) {
        return new BkTimeEntryResponse(t.getId(), t.getClientId(), t.getPractitionerId(), t.getPractitionerName(),
                t.getEntryDate(), t.getActivityType(), t.getDescription(), t.getHours(), t.getHourlyRate(),
                t.lineTotal(), t.isBillable(), t.getStatus(), t.getInvoiceId(), t.getCreatedAt());
    }
}
