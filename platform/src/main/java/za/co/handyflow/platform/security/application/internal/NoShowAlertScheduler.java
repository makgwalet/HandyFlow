// security/application/internal/NoShowAlertScheduler.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * NoShowAlertScheduler — REWRITE (2026): two changes from the original.
 *
 * 1. EDGE-TRIGGERED DEDUP (the load-bearing fix).
 *    The original re-evaluated ALL overdue shifts on every 5-minute run with
 *    no memory of what it had already alerted on — a shift stuck overdue
 *    generated a fresh notification every single run until it resolved. At
 *    1000+ guards with clustered shift-change times (e.g. every site's 06:00
 *    handover), a bad morning could produce hundreds of emails/SMS in under
 *    an hour. This now queries only shifts where the relevant
 *    *_alert_sent_at column is still NULL (ShiftRepository.find*NotYetAlerted),
 *    and marks it immediately after building the digest — mark-before-send,
 *    same convention as PsiraComplianceScheduler / ArmouryComplianceScheduler.
 *
 *    NOTE: LATE and NO_SHOW are tracked independently. A shift crosses the
 *    LATE threshold first (one digest), then may later cross the NO_SHOW
 *    threshold (a second, separate digest) — that's intentional escalation,
 *    not a duplicate alert for the same condition.
 *
 * 2. SITE-GROUPED DIGESTS.
 *    Previously one notification per shift. Now one notification per
 *    (tenant, site, alert-type) per scheduler run — "3 guards late across
 *    2 sites" instead of 3 separate emails. Matches the digest-not-per-item
 *    convention already established in the CRM module.
 *
 * Runs every 5 minutes — unchanged rationale from the original.
 *
 * Downstream actions (dismiss no-show / force-close overtime / pull from
 * site) are supervisor-initiated via ShiftController and don't touch this
 * scheduler directly — they operate on the same *_alert_sent_at/status
 * columns this scheduler reads, so a dismissed/closed/pulled shift simply
 * stops appearing in the "not yet alerted" queries on its own.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoShowAlertScheduler {

    private static final int GRACE_MINUTES      = 15;
    private static final int NO_SHOW_MINUTES    = 45;
    private static final int OVERTIME_GRACE_MIN = 20;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.of("Africa/Johannesburg"));

    private final ShiftRepository shiftRepository;
    private final SiteRepository  siteRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 */5 * * * *")
    public void checkShiftAlerts() {
        Instant now = Instant.now();
        log.debug("[Security] Running shift alert checks at {}", now);

        List<UUID> tenantIds = siteRepository.findDistinctActiveTenantIds();
        for (UUID tenantId : tenantIds) {
            try {
                checkForTenant(TenantId.of(tenantId), now);
            } catch (Exception e) {
                log.error("[Security] Shift alert check failed for tenant={}: {}",
                        tenantId, e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void checkForTenant(TenantId tenantId, Instant now) {
        checkLate(tenantId, now);
        checkNoShow(tenantId, now);
        checkOvertime(tenantId, now);
    }

    // ── Late ───────────────────────────────────────────────────────────────────

    private void checkLate(TenantId tenantId, Instant now) {
        Instant lateThreshold = now.minus(GRACE_MINUTES, ChronoUnit.MINUTES);
        List<Shift> lateShifts = shiftRepository.findLateNotYetAlerted(tenantId, lateThreshold);
        if (lateShifts.isEmpty()) return;

        sendGroupedDigest(tenantId, lateShifts, NotificationType.GUARD_LATE,
                "guard(s) late to shift", Shift::markLateAlertSent);
    }

    // ── No-show ────────────────────────────────────────────────────────────────

    private void checkNoShow(TenantId tenantId, Instant now) {
        Instant noShowThreshold = now.minus(NO_SHOW_MINUTES, ChronoUnit.MINUTES);
        List<Shift> noShowShifts = shiftRepository.findNoShowNotYetAlerted(tenantId, noShowThreshold);
        if (noShowShifts.isEmpty()) return;

        sendGroupedDigest(tenantId, noShowShifts, NotificationType.GUARD_NO_SHOW,
                "possible no-show(s)", Shift::markNoShowAlertSent);
    }

    // ── Overtime ───────────────────────────────────────────────────────────────

    private void checkOvertime(TenantId tenantId, Instant now) {
        Instant overtimeThreshold = now.minus(OVERTIME_GRACE_MIN, ChronoUnit.MINUTES);
        List<Shift> overdueActive = shiftRepository.findOvertimeNotYetAlerted(tenantId, overtimeThreshold);
        if (overdueActive.isEmpty()) return;

        sendGroupedDigest(tenantId, overdueActive, NotificationType.GUARD_OVERTIME_UNCONFIRMED,
                "guard(s) in unconfirmed overtime", Shift::markOvertimeAlertSent);
    }

    // ── Shared digest builder ─────────────────────────────────────────────────

    /**
     * Groups the given shifts by siteId and sends one notification per site,
     * then marks each shift's dedup column via the supplied marker so this
     * batch never re-fires. All shifts passed in are already tenant-scoped
     * and pre-filtered to "not yet alerted" by the repository query.
     */
    private void sendGroupedDigest(TenantId tenantId, List<Shift> shifts, NotificationType type,
                                   String subjectSuffix, java.util.function.Consumer<Shift> marker) {
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.info("[Security] {} shift(s) flagged ({}) for tenant={} but no admin recipients resolved",
                    shifts.size(), type, tenantId.getValue());
            // Still mark as alerted — an unresolvable recipient list won't fix
            // itself by re-trying every 5 minutes, and doing so would defeat
            // the entire point of this rewrite.
            shifts.forEach(marker);
            shiftRepository.saveAll(shifts);
            return;
        }

        Map<UUID, List<Shift>> bySite = shifts.stream().collect(Collectors.groupingBy(Shift::getSiteId));

        for (Map.Entry<UUID, List<Shift>> entry : bySite.entrySet()) {
            UUID siteId = entry.getKey();
            List<Shift> siteShifts = entry.getValue();
            String siteName = siteRepository.findActiveById(tenantId, siteId)
                    .map(Site::getName).orElse("Unknown site");

            String title = siteShifts.size() + " " + subjectSuffix + " — " + siteName;
            String message = siteShifts.stream()
                    .map(s -> TIME_FMT.format(s.getStartAt()) + "–" + TIME_FMT.format(s.getEndAt())
                            + " (shift " + s.getId().toString().substring(0, 8) + "…)")
                    .collect(Collectors.joining(", "));

            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenantId)
                    .type(type)
                    .title(title)
                    .message(message)
                    .actionUrl("/security/sites/" + siteId)
                    .sourceModule("security")
                    .sourceEntityId(siteId.toString())
                    .recipients(admins)
                    .build());

            log.info("[Security] {} digest sent tenant={} site={} count={}",
                    type, tenantId.getValue(), siteId, siteShifts.size());
        }

        shifts.forEach(marker);
        shiftRepository.saveAll(shifts);
    }
}