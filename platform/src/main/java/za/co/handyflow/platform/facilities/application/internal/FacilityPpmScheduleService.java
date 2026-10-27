package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityAsset;
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

/**
 * Chunk 3: a schedule belongs to an asset, which belongs to a site — so
 * every user-facing operation checks the caller can see that site.
 * {@link #applyCompletion} is a system callback and stays unscoped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityPpmScheduleService {

    private final FacilityPpmScheduleRepository ppmScheduleRepository;
    private final FacilityAssetRepository assetRepository;
    private final FacilityAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public List<PpmScheduleResponse> getSchedulesForAsset(TenantId tenantId, UUID assetId) {
        requireAssetAccess(tenantId, assetId, accessPolicy.scope(tenantId));
        return ppmScheduleRepository.findByAsset(tenantId, assetId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PpmScheduleResponse createSchedule(TenantId tenantId, CreatePpmScheduleRequest req) {
        requireAssetAccess(tenantId, req.assetId(), accessPolicy.scope(tenantId));

        FacilityPpmSchedule schedule = FacilityPpmSchedule.create(tenantId, req.assetId(), req.taskName(),
                req.description(), req.frequencyDays(), req.startDate());
        ppmScheduleRepository.save(schedule);
        log.info("PPM schedule created id={} asset={} tenant={}", schedule.getId(), req.assetId(), tenantId);
        return toResponse(schedule);
    }

    @Transactional
    public PpmScheduleResponse updateSchedule(TenantId tenantId, UUID id, UpdatePpmScheduleRequest req) {
        FacilityPpmSchedule schedule = findScoped(tenantId, id);
        schedule.update(req.taskName(), req.description(), req.frequencyDays());
        ppmScheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public PpmScheduleResponse deactivate(TenantId tenantId, UUID id) {
        FacilityPpmSchedule schedule = findScoped(tenantId, id);
        schedule.deactivate();
        ppmScheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public PpmScheduleResponse reactivate(TenantId tenantId, UUID id) {
        FacilityPpmSchedule schedule = findScoped(tenantId, id);
        schedule.reactivate();
        ppmScheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public void deleteSchedule(TenantId tenantId, UUID id) {
        FacilityPpmSchedule schedule = findScoped(tenantId, id);
        schedule.softDelete();
        ppmScheduleRepository.save(schedule);
    }

    /** System callback when a PPM-sourced work order completes — tenant-scoped, not user-scoped. */
    @Transactional
    public void applyCompletion(TenantId tenantId, UUID scheduleId, LocalDate completedDate) {
        ppmScheduleRepository.findActiveById(tenantId, scheduleId).ifPresentOrElse(schedule -> {
            schedule.recordCompleted(completedDate);
            ppmScheduleRepository.save(schedule);
            log.info("PPM schedule {} advanced to next due date {}", scheduleId, schedule.getNextDueDate());
        }, () -> log.warn("PPM schedule {} not found for tenant {} on completion — nothing to advance", scheduleId, tenantId));
    }

    private FacilityPpmSchedule findScoped(TenantId tenantId, UUID id) {
        AccessScope scope = accessPolicy.scope(tenantId);
        FacilityPpmSchedule schedule = ppmScheduleRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityPpmSchedule", id.toString()));
        if (!scope.allSites()) {
            // Restricted caller: the schedule's asset must still exist and be at a site they can see.
            FacilityAsset asset = assetRepository.findActiveById(tenantId, schedule.getAssetId())
                    .orElseThrow(() -> new ResourceNotFoundException("FacilityPpmSchedule", id.toString()));
            if (!scope.canSeeSite(asset.getSiteId())) {
                throw new ResourceNotFoundException("FacilityPpmSchedule", id.toString());
            }
        }
        return schedule;
    }

    private void requireAssetAccess(TenantId tenantId, UUID assetId, AccessScope scope) {
        FacilityAsset asset = assetRepository.findActiveById(tenantId, assetId)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityAsset", assetId.toString()));
        if (!scope.canSeeSite(asset.getSiteId())) {
            throw new ResourceNotFoundException("FacilityAsset", assetId.toString());
        }
    }

    private PpmScheduleResponse toResponse(FacilityPpmSchedule s) {
        return new PpmScheduleResponse(s.getId(), s.getAssetId(), s.getTaskName(), s.getDescription(),
                s.getFrequencyDays(), s.getNextDueDate(), s.getLastCompletedDate(), s.isActive(), s.getCreatedAt());
    }
}