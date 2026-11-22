package za.co.handyflow.platform.security.application.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.notifications.domain.model.NotificationSeverity;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.SecurityDeviceRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.shared.TenantId;

/**
 * Every 10 minutes: for each tenant, one digest of phones that went silent on shift or have an almost flat battery.
 * A phone's problem is alerted once, reminded after 6 hours if it lasts, and forgotten when it clears
 * ({@link FleetAlertRules}). The state is saved before the message is sent, so a failing send cannot repeat.
 * Off unless {@code handyflow.security.fleet.alerts-enabled=true}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FleetAlertScheduler {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SecurityJobRunner jobs;

    @Value("${handyflow.security.fleet.alerts-enabled:false}")
    private boolean enabled;

    private final SiteRepository siteRepository;
    private final SecurityDeviceRepository deviceRepository;
    private final GuardRepository guardRepository;
    private final FleetHealthService fleetHealth;
    private final NotificationService notificationService;
    private final TenantAdminRecipients recipients;

    @Scheduled(cron = "0 2/10 * * * *", zone = "Africa/Johannesburg")
    void sweep() {
        if (!enabled) return;
        SecurityJobRunner.exclusively(jobs, "security-fleet-alerts", java.time.Duration.ofMinutes(9), this::sweepNow);
    }

    void sweepNow() {
        Instant now = Instant.now();
        for (UUID tenant : siteRepository.findDistinctActiveTenantIds()) {
            try {
                SecurityJobRunner.tx(jobs, false, () -> forTenant(TenantId.of(tenant), now));
            } catch (Exception e) {
                log.error("[Security] Fleet alert check failed for tenant={}: {}", tenant, e.getMessage(), e);
            }
        }
    }

    int forTenant(TenantId tenantId, Instant now) {
        var found = fleetHealth.assess(tenantId, fleetHealth.config(), now);
        var hitIds = found.hits().stream().map(h -> h.d().getId()).collect(Collectors.toSet());
        var lines = new ArrayList<FleetAlertRules.Line>();
        var guardIds = found.hits().stream().map(h -> h.d().getGuardId()).filter(java.util.Objects::nonNull).toList();
        Map<UUID, String> names = guardIds.isEmpty() ? Map.of() : guardRepository.findAllById(guardIds).stream()
                .filter(g -> g.getTenantId().equals(tenantId)).collect(Collectors.toMap(Guard::getId, Guard::getFullName, (a, b) -> a));

        for (var h : found.hits()) {
            var d = h.d();
            Set<String> current = h.issues().stream().map(Enum::name).collect(Collectors.toSet());
            var decision = FleetAlertRules.decide(current, FleetAlertRules.parse(d.getFleetAlertedIssues()), d.getFleetAlertedAt(), now);
            if (decision.changed()) {
                d.markFleetAlerted(FleetAlertRules.format(decision.newAlerted()), decision.alertNow().isEmpty() ? d.getFleetAlertedAt() : now);
                deviceRepository.save(d);          // saved before sending
            }
            if (!decision.alertNow().isEmpty()) {
                lines.add(new FleetAlertRules.Line(d.getDeviceName(), d.getGuardId() == null ? null : names.get(d.getGuardId()), decision.alertNow()));
            }
        }
        // Phones that no longer have any problem forget what they were alerted about.
        for (var d : deviceRepository.findAllByTenant(tenantId)) {
            if (!hitIds.contains(d.getId()) && d.getFleetAlertedIssues() != null) {
                d.markFleetAlerted(null, null);
                deviceRepository.save(d);
            }
        }
        if (lines.isEmpty()) return 0;
        send(tenantId, FleetAlertRules.digest(lines));
        return lines.size();
    }

    private void send(TenantId tenantId, FleetAlertRules.Text t) {
        List<Recipient> to = SecurityRecipients.forAlerts(recipients, tenantId);
        if (to.isEmpty()) {
            log.warn("[Security] No recipients for fleet alert tenant={}", tenantId.getValue());
            return;
        }
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.INCIDENT_REPORTED)
                .severity(NotificationSeverity.CRITICAL)
                .channels(Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL))
                .title(t.title())
                .message(t.message())
                .actionUrl("/security")
                .sourceModule("security")
                .sourceEntityId("fleet-health")
                .recipients(to)
                .build());
    }
}
