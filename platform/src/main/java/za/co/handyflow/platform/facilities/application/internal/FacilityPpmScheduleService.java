package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityPpmSchedule;
import za.co.handyflow.platform.facilities.domain.repository.FacilityAssetRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityPpmScheduleRepository;
import za.co.handyflow.platform.facilities.dto.CreatePpmScheduleRequest;
import za.co.handyflow.platform.facilities.dto.PpmScheduleResponse;
import za.co.handyflow.platform.facilities.dto.UpdatePpmScheduleRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityPpmScheduleService {

    private final FacilityPpmScheduleRepository ppmScheduleRepository;
    private final FacilityAssetRepository assetRepository;

    @Transactional(readOnly = true)
    public List<PpmScheduleResponse> getSchedulesForAsset(TenantId tenantId, UUID assetId) {
        return ppmScheduleRepository.findByAsset(tenantId, assetId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PpmScheduleResponse createSchedule(TenantId tenantId, CreatePpmScheduleRequest req) {
        assetRepository.findActiveById(tenantId, req.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("FacilityAsset", req.assetId().toString()));

        FacilityPpmSchedule schedule = FacilityPpmSchedule.create(tenantId, req.assetId(), req.taskName(),
                req.description(), req.frequencyDays(), req.startDate());
        ppmScheduleRepository.save(schedule);
        log.info("PPM schedule created id={} asset={} tenant={}", schedule.getId(), req.assetId(), tenantId);
        return toResponse(schedule);
    }

    @Transactional
    public PpmScheduleResponse updateSchedule(TenantId tenantId, UUID id, UpdatePpmScheduleRequest req) {
        FacilityPpmSchedule schedule = findActive(tenantId, id);
        schedule.update(req.taskName(), req.description(), req.frequencyDays());
        ppmScheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public PpmScheduleResponse deactivate(TenantId tenantId, UUID id) {
        FacilityPpmSchedule schedule = findActive(tenantId, id);
        schedule.deactivate();
        ppmScheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public PpmScheduleResponse reactivate(TenantId tenantId, UUID id) {
        FacilityPpmSchedule schedule = findActive(tenantId, id);
        schedule.reactivate();
        ppmScheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public void deleteSchedule(TenantId tenantId, UUID id) {
        FacilityPpmSchedule schedule = findActive(tenantId, id);
        schedule.softDelete();
        ppmScheduleRepository.save(schedule);
    }

    /**
     * Called by {@link FacilityWorkOrderService} when a work order generated
     * from this schedule completes — pushes {@code nextDueDate} forward.
     * Not exposed via any controller: this is an internal cross-service
     * callback, not a user-facing action.
     */
    @Transactional
    public void applyCompletion(UUID scheduleId, LocalDate completedDate) {
        ppmScheduleRepository.findById(scheduleId).ifPresent(schedule -> {
            schedule.recordCompleted(completedDate);
            ppmScheduleRepository.save(schedule);
            log.info("PPM schedule {} advanced to next due date {}", scheduleId, schedule.getNextDueDate());
        });
    }

    private FacilityPpmSchedule findActive(TenantId tenantId, UUID id) {
        return ppmScheduleRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityPpmSchedule", id.toString()));
    }

    private PpmScheduleResponse toResponse(FacilityPpmSchedule s) {
        return new PpmScheduleResponse(s.getId(), s.getAssetId(), s.getTaskName(), s.getDescription(),
                s.getFrequencyDays(), s.getNextDueDate(), s.getLastCompletedDate(), s.isActive(), s.getCreatedAt());
    }
}
