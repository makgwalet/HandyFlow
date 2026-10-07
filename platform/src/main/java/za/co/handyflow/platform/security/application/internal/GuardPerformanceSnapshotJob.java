// security/application/internal/GuardPerformanceSnapshotJob.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.PerformanceResponse;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.RecommendationItem;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nightly (02:15 SAST): takes a snapshot of every active guard's operational score and risk recommendations, for
 * the Performance tab's trend, and tells administrators when a guard has a recommendation that was not on their
 * previous snapshot. A recommendation that keeps standing does not repeat. A guard with no earlier snapshot is
 * only recorded (a baseline), so the first night does not announce everything that already stands.
 * Recommendations are for a person to review; nothing here changes a guard's status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardPerformanceSnapshotJob {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final GuardRepository guardRepository;
    private final SiteRepository siteRepository;
    private final GuardPerformanceService performanceService;
    private final GuardScoreHistoryStore historyStore;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 15 2 * * *", zone = "Africa/Johannesburg")
    public void run() {
        Instant now = Instant.now();
        for (UUID tenantId : siteRepository.findDistinctActiveTenantIds()) {
            try {
                runForTenant(TenantId.of(tenantId), now);
            } catch (Exception e) {
                log.error("[Security] Performance snapshot failed for tenant={}: {}", tenantId, e.getMessage(), e);
            }
        }
    }

    public void runForTenant(TenantId tenantId, Instant now) {
        LocalDate today = now.atZone(SAST).toLocalDate();
        List<String> lines = new ArrayList<>();
        int taken = 0;
        for (Guard g : guardRepository.findAllActiveList(tenantId)) {
            if (!g.isActive()) continue;
            try {
                PerformanceResponse p = performanceService.performance(tenantId, g.getId());
                String current = encode(p.recommendations());
                Optional<String> previous = historyStore.previousRecommendations(tenantId, g.getId(), today);
                historyStore.save(tenantId, g.getId(), today, p.score(), p.band(), p.coverage(), current);
                taken++;
                if (previous.isPresent()) {
                    Set<String> fresh = newCodes(previous.get(), current);
                    List<String> titles = p.recommendations().stream().filter(r -> fresh.contains(r.code())).map(RecommendationItem::title).toList();
                    if (!titles.isEmpty()) lines.add(g.getFullName() + ": " + String.join("; ", titles));
                }
            } catch (Exception e) {
                log.error("[Security] Performance snapshot failed for guard={}: {}", g.getId(), e.getMessage(), e);
            }
        }
        log.info("[Security] Performance snapshots taken tenant={} guards={} newRecommendations={}", tenantId.getValue(), taken, lines.size());
        if (!lines.isEmpty()) notify(tenantId, lines);
    }

    private void notify(TenantId tenantId, List<String> lines) {
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.warn("[Security] New risk recommendations for tenant={} but no admin recipients could be resolved", tenantId.getValue());
            return;
        }
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.GUARD_RISK_RECOMMENDATION)
                .title(lines.size() + " guard(s) with a new risk recommendation")
                .message(String.join(" | ", lines) + ". These are for a person to review; nothing has been changed.")
                .actionUrl("/security/guards")
                .sourceModule("security")
                .recipients(admins)
                .build());
    }

    /** "CODE:LEVEL,CODE:LEVEL" for the snapshot row. */
    static String encode(List<RecommendationItem> recs) {
        return recs.stream().map(r -> r.code() + ":" + r.level()).collect(Collectors.joining(","));
    }

    /** Codes in `current` that were not in `previous`. A change of level on a standing code is not new. */
    static Set<String> newCodes(String previous, String current) {
        Set<String> before = codes(previous);
        Set<String> out = new LinkedHashSet<>(codes(current));
        out.removeAll(before);
        return out;
    }

    private static Set<String> codes(String encoded) {
        Set<String> out = new LinkedHashSet<>();
        if (encoded == null || encoded.isBlank()) return out;
        for (String part : encoded.split(",")) {
            String code = part.split(":")[0].trim();
            if (!code.isEmpty()) out.add(code);
        }
        return out;
    }
}
