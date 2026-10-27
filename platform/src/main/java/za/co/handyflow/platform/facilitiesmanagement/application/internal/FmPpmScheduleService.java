package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAsset;
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

/**
 * Chunk 5: a schedule belongs to an asset, which belongs to a site — every user-facing operation checks
 * the caller can see that site. {@link #applyCompletion} is a system callback and stays unscoped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmPpmScheduleService {

    private final FmPpmScheduleRepository ppmScheduleRepository;
    private final FmAssetRepository assetRepository;
    private final FmAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public List<FmPpmScheduleResponse> getSchedulesForAsset(TenantId tenantId, UUID assetId) {
        requireAssetAccess(tenantId, assetId, accessPolicy.scope(tenantId));
        return ppmScheduleRepository.findByAsset(tenantId, assetId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public FmPpmScheduleResponse createSchedule(TenantId tenantId, CreateFmPpmScheduleRequest req) {
        requireAssetAccess(tenantId, req.assetId(), accessPolicy.scope(tenantId));

        FmPpmSchedule schedule = FmPpmSchedule.create(tenantId, req.assetId(), req.taskName(),
                req.description(), req.frequencyDays(), req.startDate());
        ppmScheduleRepository.save(schedule);
        log.info("FM PPM schedule created id={} asset={} tenant={}", schedule.getId(), req.assetId(), tenantId);
        return toResponse(schedule);
    }

    @Transactional
    public FmPpmScheduleResponse updateSchedule(TenantId tenantId, UUID id, UpdateFmPpmScheduleRequest req) {
        FmPpmSchedule schedule = findScoped(tenantId, id);
        schedule.update(req.taskName(), req.description(), req.frequencyDays());
        ppmScheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public FmPpmScheduleResponse deactivate(TenantId tenantId, UUID id) {
        FmPpmSchedule schedule = findScoped(tenantId, id);
        schedule.deactivate();
        ppmScheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public FmPpmScheduleResponse reactivate(TenantId tenantId, UUID id) {
        FmPpmSchedule schedule = findScoped(tenantId, id);
        schedule.reactivate();
        ppmScheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public void deleteSchedule(TenantId tenantId, UUID id) {
        FmPpmSchedule schedule = findScoped(tenantId, id);
        schedule.softDelete();
        ppmScheduleRepository.save(schedule);
    }

    /** System callback when a PPM-sourced work order completes — tenant-scoped, not user-scoped. */
    @Transactional
    public void applyCompletion(TenantId tenantId, UUID scheduleId, LocalDate completedDate) {
        ppmScheduleRepository.findActiveById(tenantId, scheduleId).ifPresentOrElse(schedule -> {
            schedule.recordCompleted(completedDate);
            ppmScheduleRepository.save(schedule);
            log.info("FM PPM schedule {} advanced to next due date {}", scheduleId, schedule.getNextDueDate());
        }, () -> log.warn("FM PPM schedule {} not found for tenant {} on completion — nothing to advance", scheduleId, tenantId));
    }

    private FmPpmSchedule findScoped(TenantId tenantId, UUID id) {
        FmAccessScope scope = accessPolicy.scope(tenantId);
        FmPpmSchedule schedule = ppmScheduleRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmPpmSchedule", id.toString()));
        if (!scope.unrestricted()) {
            FmAsset asset = assetRepository.findActiveById(tenantId, schedule.getAssetId())
                    .orElseThrow(() -> new ResourceNotFoundException("FmPpmSchedule", id.toString()));
            if (!scope.canSeeSite(asset.getSiteId())) {
                throw new ResourceNotFoundException("FmPpmSchedule", id.toString());
            }
        }
        return schedule;
    }

    private void requireAssetAccess(TenantId tenantId, UUID assetId, FmAccessScope scope) {
        FmAsset asset = assetRepository.findActiveById(tenantId, assetId)
                .orElseThrow(() -> new ResourceNotFoundException("FmAsset", assetId.toString()));
        if (!scope.canSeeSite(asset.getSiteId())) {
            throw new ResourceNotFoundException("FmAsset", assetId.toString());
        }
    }

    private FmPpmScheduleResponse toResponse(FmPpmSchedule s) {
        return new FmPpmScheduleResponse(s.getId(), s.getAssetId(), s.getTaskName(), s.getDescription(),
                s.getFrequencyDays(), s.getNextDueDate(), s.getLastCompletedDate(), s.isActive(), s.getCreatedAt());
    }
}