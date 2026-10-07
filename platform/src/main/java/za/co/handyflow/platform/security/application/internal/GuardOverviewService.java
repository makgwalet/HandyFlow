// security/application/internal/GuardOverviewService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.GuardScreeningRecord;
import za.co.handyflow.platform.security.domain.model.Incident;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.security.domain.repository.GuardScreeningRepository;
import za.co.handyflow.platform.security.domain.repository.IncidentRepository;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse.Counts;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse.IncidentItem;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse.ScreeningItem;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse.ShiftItem;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the Guard 360 overview. Reuses GuardService for the guard and its
 * documents so access rules (tenant scoping, soft delete) stay in one place.
 */
@Service
@RequiredArgsConstructor
public class GuardOverviewService {

    static final int SHIFT_BACK_DAYS = 90;
    static final int SHIFT_FORWARD_DAYS = 14;
    static final int INCIDENT_BACK_DAYS = 180;
    static final int LIST_CAP = 50;

    private final GuardService guardService;
    private final GuardScreeningService screeningService;
    private final GuardScreeningRepository screeningRepository;
    private final ShiftRepository shiftRepository;
    private final IncidentRepository incidentRepository;
    private final SiteRepository siteRepository;

    @Transactional(readOnly = true)
    public GuardOverviewResponse overview(TenantId tenantId, UUID guardId) {
        return overview(tenantId, guardId, Instant.now());
    }

    /** Overload with an explicit clock so the windows can be tested. */
    @Transactional(readOnly = true)
    GuardOverviewResponse overview(TenantId tenantId, UUID guardId, Instant now) {
        var guard = guardService.getGuard(tenantId, guardId);
        var documents = guardService.getDocuments(tenantId, guardId);

        List<ScreeningItem> screening = screeningRepository.findByGuard(tenantId, guardId).stream()
                .map(GuardOverviewService::toItem)
                .toList();

        Instant shiftFrom = now.minus(Duration.ofDays(SHIFT_BACK_DAYS));
        Instant shiftTo = now.plus(Duration.ofDays(SHIFT_FORWARD_DAYS));
        List<Shift> shifts = shiftRepository.findByGuardInRange(tenantId, guardId, shiftFrom, shiftTo);

        Instant incFrom = now.minus(Duration.ofDays(INCIDENT_BACK_DAYS));
        List<Incident> incidents = incidentRepository.findByGuardInRange(tenantId, guardId, incFrom, now);

        Map<UUID, String> siteNames = new HashMap<>();
        java.util.function.Function<UUID, String> siteName = id -> id == null ? null
                : siteNames.computeIfAbsent(id, k -> siteRepository.findActiveById(tenantId, k)
                        .map(s -> s.getName()).orElse(null));

        long past90 = shifts.stream().filter(s -> !s.getStartAt().isAfter(now)).count();
        long completed = shifts.stream()
                .filter(s -> !s.getStartAt().isAfter(now) && "COMPLETED".equals(s.getStatus().name()))
                .count();
        long open = incidents.stream().filter(i -> !"RESOLVED".equals(i.getStatus())).count();

        // Newest first, capped.
        List<ShiftItem> shiftItems = shifts.stream()
                .sorted(Comparator.comparing(Shift::getStartAt).reversed())
                .limit(LIST_CAP)
                .map(s -> new ShiftItem(s.getId(), s.getSiteId(), siteName.apply(s.getSiteId()),
                        s.getStartAt(), s.getEndAt(), s.getStatus().name()))
                .toList();
        List<IncidentItem> incidentItems = incidents.stream()
                .sorted(Comparator.comparing(Incident::getCreatedAt).reversed())
                .limit(LIST_CAP)
                .map(i -> new IncidentItem(i.getId(), i.getSiteId(), siteName.apply(i.getSiteId()),
                        i.getTitle(), i.getSeverity(), i.getStatus(), i.getCreatedAt()))
                .toList();

        return new GuardOverviewResponse(
                guard,
                screeningService.checkScreeningGate(guardId),
                documents,
                screening,
                shiftItems,
                incidentItems,
                new Counts((int) past90, (int) completed, incidents.size(), (int) open));
    }

    private static ScreeningItem toItem(GuardScreeningRecord r) {
        return new ScreeningItem(r.getId(), r.getScreeningType().name(), r.getReason().name(),
                r.getResult().name(), r.getConductedBy(), r.getConductedAt(), r.getNextDueAt(),
                r.getReportRef(), r.getCreatedAt());
    }
}
