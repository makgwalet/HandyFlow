package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpAttorney;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatter;
import za.co.handyflow.platform.legalpractice.domain.model.LpTimeEntry;
import za.co.handyflow.platform.legalpractice.domain.repository.LpAttorneyRepository;
import za.co.handyflow.platform.legalpractice.domain.repository.LpMatterRepository;
import za.co.handyflow.platform.legalpractice.domain.repository.LpTimeEntryRepository;
import za.co.handyflow.platform.legalpractice.dto.CreateLpTimeEntryRequest;
import za.co.handyflow.platform.legalpractice.dto.LpTimeEntryResponse;
import za.co.handyflow.platform.legalpractice.dto.UpdateLpTimeEntryRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Logs attorney time against a matter. Two rules enforced here, both
 * ahead of the entity layer (which has no matter/attorney awareness of
 * its own):
 * <ul>
 *   <li>a new entry can't be logged against a CLOSED or ARCHIVED matter —
 *       the billable-work guard {@code LpMatter}'s own Javadoc calls out
 *       as a deliberate service-layer responsibility, mirroring
 *       {@code AgMovementRecordService}'s split relative to {@code AgAnimal};</li>
 *   <li>{@code hourlyRate} falls back to the attorney's own default rate
 *       when the caller doesn't supply a matter-specific override — the
 *       rate is then snapshotted onto the entry at creation, exactly as
 *       {@code LpTimeEntry}'s own Javadoc describes.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpTimeEntryService {

    private final LpTimeEntryRepository timeEntryRepo;
    private final LpMatterRepository matterRepo;
    private final LpAttorneyRepository attorneyRepo;

    @Transactional(readOnly = true)
    public Page<LpTimeEntryResponse> listForMatter(TenantId tenantId, UUID matterId, Pageable pageable) {
        return timeEntryRepo.findAllForMatter(tenantId, matterId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<LpTimeEntryResponse> listUnbilledForMatter(TenantId tenantId, UUID matterId) {
        return timeEntryRepo.findUnbilledByMatter(tenantId, matterId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public LpTimeEntryResponse createTimeEntry(TenantId tenantId, UUID matterId, CreateLpTimeEntryRequest req) {
        LpMatter matter = matterRepo.findActiveById(tenantId, matterId)
                .orElseThrow(() -> new ResourceNotFoundException("LpMatter", matterId.toString()));
        requireLoggableMatter(matter);

        BigDecimal hourlyRate = req.hourlyRate();
        if (hourlyRate == null) {
            LpAttorney attorney = attorneyRepo.findActiveById(tenantId, req.attorneyId())
                    .orElseThrow(() -> new ResourceNotFoundException("LpAttorney", req.attorneyId().toString()));
            hourlyRate = attorney.getHourlyRate();
        }
        if (hourlyRate == null) {
            throw new IllegalArgumentException(
                    "No hourly rate available — supply one on the request, or set a default rate on the attorney");
        }

        LpTimeEntry entry = LpTimeEntry.create(tenantId, matterId, req.attorneyId(), req.entryDate(),
                req.hours(), hourlyRate, req.description(), req.billable());
        timeEntryRepo.save(entry);
        return toResponse(entry);
    }

    @Transactional
    public LpTimeEntryResponse updateTimeEntry(TenantId tenantId, UUID entryId, UpdateLpTimeEntryRequest req) {
        LpTimeEntry entry = findOwn(tenantId, entryId);
        entry.update(req.entryDate(), req.hours(), req.hourlyRate(), req.description());
        timeEntryRepo.save(entry);
        return toResponse(entry);
    }

    @Transactional
    public LpTimeEntryResponse writeOff(TenantId tenantId, UUID entryId) {
        LpTimeEntry entry = findOwn(tenantId, entryId);
        entry.writeOff();
        timeEntryRepo.save(entry);
        return toResponse(entry);
    }

    private void requireLoggableMatter(LpMatter matter) {
        if ("CLOSED".equals(matter.getStatus()) || "ARCHIVED".equals(matter.getStatus())) {
            throw new IllegalStateException(
                    "Cannot log time against a matter in status " + matter.getStatus());
        }
    }

    private LpTimeEntry findOwn(TenantId tenantId, UUID entryId) {
        return timeEntryRepo.findActiveById(tenantId, entryId)
                .orElseThrow(() -> new ResourceNotFoundException("LpTimeEntry", entryId.toString()));
    }

    private LpTimeEntryResponse toResponse(LpTimeEntry t) {
        return new LpTimeEntryResponse(t.getId(), t.getMatterId(), t.getAttorneyId(), t.getEntryDate(),
                t.getHours(), t.getHourlyRate(), t.lineTotal(), t.getDescription(), t.isBillable(),
                t.getStatus(), t.getInvoiceId(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
