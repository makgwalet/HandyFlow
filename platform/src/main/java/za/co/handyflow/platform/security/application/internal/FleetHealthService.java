package za.co.handyflow.platform.security.application.internal;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.application.internal.FleetHealthRules.Issue;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.SecurityDevice;
import za.co.handyflow.platform.security.domain.repository.DeviceSessionRepository;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.SecurityDeviceRepository;
import za.co.handyflow.platform.security.dto.FleetHealthResponse;
import za.co.handyflow.platform.shared.TenantId;

/** Which of the tenant's phones need attention right now: silent on shift, never seen, low battery, old app. */
@Service
@RequiredArgsConstructor
public class FleetHealthService {

    private final SecurityDeviceRepository devices;
    private final DeviceSessionRepository sessions;
    private final GuardRepository guards;

    @Value("${handyflow.security.fleet.offline-minutes:30}")
    private int offlineMinutes;
    @Value("${handyflow.security.fleet.never-seen-hours:24}")
    private int neverSeenHours;
    @Value("${handyflow.security.fleet.low-battery:20}")
    private int lowBattery;
    @Value("${handyflow.security.fleet.critical-battery:10}")
    private int criticalBattery;
    /** Empty = no app version check. */
    @Value("${handyflow.security.fleet.min-app-version:}")
    private String minAppVersion;

    record Hit(SecurityDevice d, List<Issue> issues, boolean on) {}
    record Found(int active, List<Hit> hits) {}

    FleetHealthRules.Config config() {
        return new FleetHealthRules.Config(offlineMinutes, neverSeenHours, lowBattery, criticalBattery,
                minAppVersion == null || minAppVersion.isBlank() ? null : minAppVersion.trim());
    }

    /** The phones with problems right now. A phone on shift counts as heard from at least since its session started. */
    Found assess(TenantId tenantId, FleetHealthRules.Config cfg, Instant now) {
        Map<UUID, Instant> sessionStart = new java.util.HashMap<>();
        sessions.findOpenByTenant(tenantId).forEach(s -> sessionStart.merge(s.getDeviceId(), s.getStartedAt(),
                (a, b) -> a.isAfter(b) ? a : b));
        int active = 0;
        var hits = new java.util.ArrayList<Hit>();
        for (SecurityDevice d : devices.findAllByTenant(tenantId)) {
            if (d.isActive()) active++;
            boolean on = sessionStart.containsKey(d.getId());
            Instant seen = FleetHealthRules.effectiveLastSeen(d.getLastSeenAt(), sessionStart.get(d.getId()));
            List<Issue> issues = FleetHealthRules.assess(new FleetHealthRules.Facts(
                    d.isActive(), seen, d.getCreatedAt(), d.getBatteryPct(), d.getAppVersion(), on), cfg, now);
            if (!issues.isEmpty()) hits.add(new Hit(d, issues, on));
        }
        return new Found(active, hits);
    }

    @Transactional(readOnly = true)
    public FleetHealthResponse health(TenantId tenantId) {
        return build(tenantId, Instant.now());
    }

    FleetHealthResponse build(TenantId tenantId, Instant now) {
        var found = assess(tenantId, config(), now);
        int active = found.active();
        var items = new java.util.ArrayList<FleetHealthResponse.Item>();
        var hits = found.hits();
        Map<UUID, String> names = guards.findAllById(hits.stream().map(h -> h.d().getGuardId()).filter(java.util.Objects::nonNull).toList())
                .stream().filter(g -> g.getTenantId().equals(tenantId))
                .collect(Collectors.toMap(Guard::getId, Guard::getFullName, (a, b) -> a));
        int critical = 0;
        for (Hit h : hits) {
            boolean crit = h.issues().stream().anyMatch(FleetHealthRules::critical);
            if (crit) critical++;
            SecurityDevice d = h.d();
            items.add(new FleetHealthResponse.Item(d.getId(), d.getDeviceName(), d.getDeviceType() == null ? null : d.getDeviceType().name(),
                    d.getSiteId(), d.getGuardId(), d.getGuardId() == null ? null : names.get(d.getGuardId()),
                    d.getLastSeenAt(), d.getBatteryPct(), d.getAppVersion(), h.on(), crit,
                    h.issues().stream().map(Issue::name).toList()));
        }
        items.sort(Comparator.comparing(FleetHealthResponse.Item::critical).reversed()
                .thenComparing(i -> i.deviceName() == null ? "" : i.deviceName()));
        return new FleetHealthResponse(now, active, items.size(), critical, items);
    }
}
