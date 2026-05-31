// security/application/internal/ShiftService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository shiftRepository;

    @Transactional(readOnly = true)
    public Page<ShiftResponse> getShifts(TenantId tenantId, Pageable pageable) {
        return shiftRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional
    public ShiftResponse createShift(TenantId tenantId, CreateShiftRequest req) {
        // WHY overlap check? Two shifts for the same guard at the same time
        // would mean the guard is at two sites simultaneously — impossible.
        var overlapping = shiftRepository.findOverlapping(
                req.guardId(), req.startAt(), req.endAt());
        if (!overlapping.isEmpty()) {
            throw new IllegalArgumentException(
                    "Guard already has a shift scheduled during this time"
            );
        }
        Shift shift = Shift.create(tenantId, req.siteId(), req.guardId(),
                req.startAt(), req.endAt(), req.notes());
        shiftRepository.save(shift);
        log.info("Created shift guard={} site={}", req.guardId(), req.siteId());
        return toResponse(shift);
    }

    @Transactional
    public ShiftResponse startShift(TenantId tenantId, UUID id) {
        Shift shift = findActive(tenantId, id);
        shift.start();
        shiftRepository.save(shift);
        return toResponse(shift);
    }

    @Transactional
    public ShiftResponse completeShift(TenantId tenantId, UUID id) {
        Shift shift = findActive(tenantId, id);
        shift.complete();
        shiftRepository.save(shift);
        return toResponse(shift);
    }

    private Shift findActive(TenantId tenantId, UUID id) {
        return shiftRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", id.toString()));
    }

    private ShiftResponse toResponse(Shift s) {
        return new ShiftResponse(s.getId(), s.getSiteId(), s.getGuardId(),
                s.getStartAt(), s.getEndAt(), s.getStatus().name(),
                s.getNotes(), s.getCreatedAt());
    }
}