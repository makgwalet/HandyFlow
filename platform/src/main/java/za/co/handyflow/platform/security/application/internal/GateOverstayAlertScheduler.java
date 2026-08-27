// security/application/internal/GateOverstayAlertScheduler.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.security.domain.model.AccessPoint;
import za.co.handyflow.platform.security.domain.model.GateRegisterEntry;
import za.co.handyflow.platform.security.domain.repository.AccessPointRepository;
import za.co.handyflow.platform.security.domain.repository.GateRegisterEntryRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GateOverstayAlertScheduler — same shape as NoShowAlertScheduler,
 * confirmed directly against that class before writing this one:
 * edge-triggered dedup via a *_alert_sent_at column (never re-evaluates
 * an entry that's already been folded into a digest), mark-before-send
 * even when no recipients resolve (an unresolvable recipient list
 * won't fix itself by re-checking every run — same reasoning
 * NoShowAlertScheduler's own confirmed comment states explicitly), and
 * one digest per (tenant, site) per run rather than one notification
 * per entry.
 * <p>
 * Threshold is a fixed hours-since-arrival window, not a per-entry
 * expected-departure time — the plan's own §5 named both as options,
 * but the per-entry version depends on VisitorPreRegistration
 * (Phase B), which doesn't exist yet. Shipping the honest, simpler MVP
 * version rather than a half-wired Phase B dependency.
 * <p>
 * ⚠ NEEDS A NEW NotificationType ENTRY — GATE_OVERSTAY isn't confirmed
 * to already exist in the catalogue. Add it to that enum before this
 * compiles; I don't have that file's complete structure confirmed to
 * safely edit it directly here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GateOverstayAlertScheduler {

    @Value("${security.gate-access.overstay-threshold-hours:12}")
    private int overstayThresholdHours;

    private final GateRegisterEntryRepository entryRepository;
    private final AccessPointRepository       accessPointRepository;
    private final SiteRepository              siteRepository;
    private final NotificationService         notificationService;
    private final TenantAdminRecipients       tenantAdminRecipients;

    /** Hourly — overstays don't need NoShowAlertScheduler's 5-minute granularity; a visitor being on site an extra hour isn't urgent the way a guard no-show is. */
    @Scheduled(cron = "0 0 * * * *")
    public void checkOverstays() {
        Instant now = Instant.now();
        log.debug("[Security] Running gate overstay check at {}", now);

        List<UUID> tenantIds = siteRepository.findDistinctActiveTenantIds();
        for (UUID tenantId : tenantIds) {
            try {
                checkForTenant(TenantId.of(tenantId), now);
            } catch (Exception e) {
                log.error("[Security] Gate overstay check failed for tenant={}: {}",
                        tenantId, e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void checkForTenant(TenantId tenantId, Instant now) {
        Instant threshold = now.minus(overstayThresholdHours, ChronoUnit.HOURS);
        List<GateRegisterEntry> overdue = entryRepository.findOverstayedNotYetAlerted(tenantId, threshold);
        if (overdue.isEmpty()) return;

        // Status transition happens regardless of whether a notification
        // can be sent — ON_SITE -> OVERSTAYED is a fact about the entry
        // itself, independent of recipient resolution.
        overdue.forEach(GateRegisterEntry::markOverstayed);
        entryRepository.saveAll(overdue);

        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.info("[Security] {} gate entry(ies) overstayed for tenant={} but no admin recipients resolved",
                    overdue.size(), tenantId.getValue());
            // Still mark as alerted — same reasoning as NoShowAlertScheduler's
            // own confirmed comment: an unresolvable recipient list won't
            // fix itself by re-trying every run.
            overdue.forEach(GateRegisterEntry::markOverstayAlertSent);
            entryRepository.saveAll(overdue);
            return;
        }

        // One digest per site, not one per entry — same digest-not-per-item
        // convention NoShowAlertScheduler's own class doc confirms.
        Map<UUID, List<GateRegisterEntry>> bySite = overdue.stream()
                .collect(Collectors.groupingBy(GateRegisterEntry::getSiteId));

        bySite.forEach((siteId, entries) -> {
            String summary = buildSummary(tenantId, entries);
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenantId)
                    .type(NotificationType.GATE_OVERSTAY)
                    .title(entries.size() + " overstayed at site — gate register")
                    .message(entries.size() + " visitor(s)/vehicle(s) have been on site over "
                            + overstayThresholdHours + " hours without logging an exit: " + summary)
                    .actionUrl("/security/sites/" + siteId + "/on-site")
                    .sourceModule("security")
                    .recipients(admins)
                    .build());
        });

        overdue.forEach(GateRegisterEntry::markOverstayAlertSent);
        entryRepository.saveAll(overdue);

        log.info("[Security] Gate overstay digest sent tenant={} sites={} entries={}",
                tenantId.getValue(), bySite.size(), overdue.size());
    }

    private String buildSummary(TenantId tenantId, List<GateRegisterEntry> entries) {
        return entries.stream()
                .map(e -> {
                    String apName = accessPointRepository.findById(e.getAccessPointId())
                            .map(AccessPoint::getName).orElse("Unknown access point");
                    return e.getPersonName() + " (" + e.getEntryType() + " at " + apName
                            + ", logged in " + e.getLoggedInAt() + ")";
                })
                .collect(Collectors.joining("; "));
    }
}