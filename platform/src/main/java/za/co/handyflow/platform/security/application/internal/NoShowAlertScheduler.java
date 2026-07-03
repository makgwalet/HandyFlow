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
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * NoShowAlertScheduler — detects missed/late shift starts and unauthorised overtime.
 *
 * MIGRATION NOTE (2026): resolveAdminEmail() previously always returned null
 * — every LATE/NO_SHOW/OVERTIME alert this scheduler detected was logged and
 * then discarded, meaning site supervisors never actually found out about a
 * no-show unless they happened to notice the live map. This now routes
 * through NotificationService using TenantAdminRecipients, which also means
 * a NO_SHOW (the most severe case) now reaches admins by SMS as well as
 * in-app + email, since NotificationType.GUARD_NO_SHOW defaults to CRITICAL.
 *
 * Runs every 5 minutes — see original rationale below (unchanged).
 *
 * Three checks per run:
 *   1. LATE START  — SCHEDULED shift past startAt + GRACE_MINUTES, still not active.
 *   2. NO SHOW     — SCHEDULED shift past startAt + NO_SHOW_MINUTES.
 *   3. OVERTIME    — ACTIVE shift past endAt + OVERTIME_GRACE_MINUTES, guard hasn't clocked out.
 *
 * PRODUCTION NOTE: use Quartz JDBC JobStore to prevent duplicate alerts from
 * multiple app instances. This is a fire-and-forget alert, not a
 * state-mutation, so a duplicate notification is the worst case, not a
 * correctness failure.
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

    @Transactional(readOnly = true)
    public void checkForTenant(TenantId tenantId, Instant now) {
        checkLateAndNoShow(tenantId, now);
        checkOvertime(tenantId, now);
    }

    // ── Late / No-show ─────────────────────────────────────────────────────────

    private void checkLateAndNoShow(TenantId tenantId, Instant now) {
        Instant lateThreshold   = now.minus(GRACE_MINUTES,   ChronoUnit.MINUTES);
        Instant noShowThreshold = now.minus(NO_SHOW_MINUTES, ChronoUnit.MINUTES);

        List<Shift> overdueScheduled = shiftRepository
                .findScheduledStartingBefore(tenantId, lateThreshold);

        for (Shift shift : overdueScheduled) {
            boolean isNoShow = shift.getStartAt().isBefore(noShowThreshold);
            NotificationType type = isNoShow ? NotificationType.GUARD_NO_SHOW : NotificationType.GUARD_LATE;

            log.warn("[Security] {} detected shiftId={} guardId={} scheduledStart={}",
                    type, shift.getId(), shift.getGuardId(), shift.getStartAt());

            sendShiftAlert(tenantId, shift, type,
                    isNoShow
                            ? "A guard has not started their shift and is " + NO_SHOW_MINUTES
                            + " minutes overdue. This may be a no-show."
                            : "A guard has not started their shift and is past the "
                            + GRACE_MINUTES + "-minute grace period.");
        }
    }

    // ── Overtime ───────────────────────────────────────────────────────────────

    private void checkOvertime(TenantId tenantId, Instant now) {
        Instant overtimeThreshold = now.minus(OVERTIME_GRACE_MIN, ChronoUnit.MINUTES);

        List<Shift> overdueActive = shiftRepository
                .findActiveEndingBefore(tenantId, overtimeThreshold);

        for (Shift shift : overdueActive) {
            long minutesOver = ChronoUnit.MINUTES.between(shift.getEndAt(), now);
            log.warn("[Security] OVERTIME detected shiftId={} guardId={} scheduledEnd={} +{}min",
                    shift.getId(), shift.getGuardId(), shift.getEndAt(), minutesOver);
            sendShiftAlert(tenantId, shift, NotificationType.GUARD_OVERTIME_UNCONFIRMED,
                    "A guard's shift ended over " + OVERTIME_GRACE_MIN
                            + " minutes ago but they have not clocked out.");
        }
    }

    // ── Alert dispatch ─────────────────────────────────────────────────────────

    private void sendShiftAlert(TenantId tenantId, Shift shift, NotificationType type, String description) {
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.info("[Security] {} alert for shift={} — no admin recipients resolved for tenant={}",
                    type, shift.getId(), tenantId.getValue());
            return;
        }

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(type)
                .title(buildSubject(type))
                .message(description + " Scheduled " + TIME_FMT.format(shift.getStartAt())
                        + "–" + TIME_FMT.format(shift.getEndAt()) + ".")
                .actionUrl("/security/shifts/" + shift.getId())
                .sourceModule("security")
                .sourceEntityId(shift.getId().toString())
                .recipients(admins)
                .build());

        log.info("[Security] {} alert sent for shift={}", type, shift.getId());
    }

    private String buildSubject(NotificationType type) {
        return switch (type) {
            case GUARD_LATE                   -> "Guard late to shift";
            case GUARD_NO_SHOW                -> "⚠ Guard no-show alert";
            case GUARD_OVERTIME_UNCONFIRMED   -> "Guard overtime — please verify";
            default                           -> "Shift alert";
        };
    }
}