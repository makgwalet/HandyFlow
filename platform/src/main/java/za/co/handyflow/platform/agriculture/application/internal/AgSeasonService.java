package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgSeason;
import za.co.handyflow.platform.agriculture.domain.repository.AgSeasonRepository;
import za.co.handyflow.platform.agriculture.dto.CreateSeasonRequest;
import za.co.handyflow.platform.agriculture.dto.SeasonResponse;
import za.co.handyflow.platform.agriculture.dto.UpdateSeasonRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** Farm-scoped CRUD plus activate/close — mirrors AgProductionAreaService's own shape. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgSeasonService {

    private final AgSeasonRepository seasonRepository;

    @Transactional(readOnly = true)
    public Page<SeasonResponse> getSeasonsForFarm(TenantId tenantId, UUID farmId, String status, Pageable pageable) {
        Page<AgSeason> page = (status != null && !status.isBlank())
                ? seasonRepository.findByStatusForFarm(tenantId, farmId, status, pageable)
                : seasonRepository.findAllActiveForFarm(tenantId, farmId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SeasonResponse getSeason(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public SeasonResponse createSeason(TenantId tenantId, CreateSeasonRequest req) {
        AgSeason season = AgSeason.create(tenantId, req.farmId(), req.name(), req.startDate(),
                req.endDate(), req.notes());
        seasonRepository.save(season);
        log.info("Season created id={} farm={} tenant={}", season.getId(), req.farmId(), tenantId.getValue());
        return toResponse(season);
    }

    @Transactional
    public SeasonResponse updateSeason(TenantId tenantId, UUID id, UpdateSeasonRequest req) {
        AgSeason season = findActive(tenantId, id);
        season.update(req.name(), req.startDate(), req.endDate(), req.notes());
        return toResponse(season);
    }

    @Transactional
    public SeasonResponse activateSeason(TenantId tenantId, UUID id) {
        AgSeason season = findActive(tenantId, id);
        season.activate();
        return toResponse(season);
    }

    @Transactional
    public SeasonResponse closeSeason(TenantId tenantId, UUID id) {
        AgSeason season = findActive(tenantId, id);
        season.close();
        return toResponse(season);
    }

    @Transactional
    public void deleteSeason(TenantId tenantId, UUID id) {
        AgSeason season = findActive(tenantId, id);
        season.softDelete();
        log.info("Season deleted id={} tenant={}", id, tenantId.getValue());
    }

    private AgSeason findActive(TenantId tenantId, UUID id) {
        return seasonRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Season", id.toString()));
    }

    private SeasonResponse toResponse(AgSeason s) {
        return new SeasonResponse(
                s.getId(), s.getFarmId(), s.getName(), s.getStartDate(), s.getEndDate(),
                s.getStatus(), s.getNotes(), s.getCreatedAt(), s.getUpdatedAt()
        );
    }
}
