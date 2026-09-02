package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmPpmSchedule;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmAssetRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmPpmScheduleRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.CreateFmPpmScheduleRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPpmScheduleResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpdateFmPpmScheduleRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FmPpmScheduleService {

    private final FmPpmScheduleRepository ppmScheduleRepository;
    private final FmAssetRepository assetRepository;

    @Transactional(readOnly = true)
    public List<FmPpmScheduleResponse> getSchedulesForAsset(TenantId tenantId, UUID assetId) {
        return ppmScheduleRepository.findByAsset(tenantId, assetId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public FmPpmScheduleResponse createSchedule(TenantId tenantId, CreateFmPpmScheduleRequest req) {
        assetRepository.findActiveById(tenantId, req.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("FmAsset", req.assetId().toString()));

        FmPpmSchedule schedule = FmPpmSchedule.create(tenantId, req.assetId(), req.taskName(),
                req.description(), req.frequencyDays(), req.startDate());
        ppmScheduleRepository.save(schedule);
        log.info("FM PPM schedule created id={} asset={} tenant={}", schedule.getId(), req.assetId(), tenantId);
        return toResponse(schedule);
    }

    @Transactional
    public FmPpmScheduleResponse updateSchedule(TenantId tenantId, UUID id, UpdateFmPpmScheduleRequest req) {
        FmPpmSchedule schedule = findActive(tenantId, id);
        schedule.update(req.taskName(), req.description(), req.frequencyDays());
        ppmScheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public FmPpmScheduleResponse deactivate(TenantId tenantId, UUID id) {
        FmPpmSchedule schedule = findActive(tenantId, id);
        schedule.deactivate();
        ppmScheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public FmPpmScheduleResponse reactivate(TenantId tenantId, UUID id) {
        FmPpmSchedule schedule = findActive(tenantId, id);
        schedule.reactivate();
        ppmScheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public void deleteSchedule(TenantId tenantId, UUID id) {
        FmPpmSchedule schedule = findActive(tenantId, id);
        schedule.softDelete();
        ppmScheduleRepository.save(schedule);
    }

    /**
     * Called by {@link FmWorkOrderService} when a work order generated from
     * this schedule completes — pushes {@code nextDueDate} forward. Not
     * exposed via any controller: an internal cross-service callback, not a
     * user-facing action — mirrors {@code FacilityPpmScheduleService}'s own
     * identically-named method exactly.
     */
    @Transactional
    public void applyCompletion(UUID scheduleId, LocalDate completedDate) {
        ppmScheduleRepository.findById(scheduleId).ifPresent(schedule -> {
            schedule.recordCompleted(completedDate);
            ppmScheduleRepository.save(schedule);
            log.info("FM PPM schedule {} advanced to next due date {}", scheduleId, schedule.getNextDueDate());
        });
    }

    FmPpmSchedule findActive(TenantId tenantId, UUID id) {
        return ppmScheduleRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmPpmSchedule", id.toString()));
    }

    private FmPpmScheduleResponse toResponse(FmPpmSchedule s) {
        return new FmPpmScheduleResponse(s.getId(), s.getAssetId(), s.getTaskName(), s.getDescription(),
                s.getFrequencyDays(), s.getNextDueDate(), s.getLastCompletedDate(), s.isActive(), s.getCreatedAt());
    }
}
