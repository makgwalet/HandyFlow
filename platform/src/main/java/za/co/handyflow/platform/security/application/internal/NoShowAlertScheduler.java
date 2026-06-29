// security/application/internal/NoShowAlertScheduler.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.security.domain.model.ShiftStatus;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * NoShowAlertScheduler — detects missed/late shift starts and unauthorised overtime.
 *
 * Runs every 5 minutes (not once daily like PSiRA checks) because a missed shift
 * needs to be actioned within minutes, not hours.
 *
 * Three checks per run:
 *
 * 1. LATE START — SCHEDULED shift whose startAt + GRACE_MINUTES has passed but no
 *    active device session exists (Phase 2 — device sessions).  For now: no session
 *    infrastructure yet, so we check whether the shift is still in SCHEDULED status
 *    well past its start time.  Phase 2 replaces this with the device-session check.
 *
 * 2. NO SHOW — SCHEDULED shift whose startAt + NO_SHOW_MINUTES has passed.  More
 *    severe than late — triggers a supervisor alert.
 *
 * 3. OVERTIME — ACTIVE shift whose endAt + OVERTIME_GRACE_MINUTES has passed and
 *    the shift is still ACTIVE.  Guard may have forgotten to clock out, or may be
 *    legitimately on approved overtime.  Alert goes to supervisor to verify.
 *
 * WHY 5-minute run interval and not 1 minute?
 * A 1-minute cron on 100 tenants × potentially large shift tables would cause
 * a DB load spike every minute.  5 minutes is granular enough for operational
 * response while being infrastructure-friendly.  The grace periods below mean
 * no real-world difference in outcome.
 *
 * PRODUCTION NOTE: use Quartz JDBC JobStore to prevent duplicate alerts from
 * multiple app instances.  This is a fire-and-forget alert, not a state-mutation,
 * so duplicate emails are the worst case — not correctness failures.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoShowAlertScheduler {

    /** Grace window after scheduled start before alerting LATE */
    private static final int GRACE_MINUTES      = 15;
    /** Time after scheduled start before treating as full NO_SHOW */
    private static final int NO_SHOW_MINUTES    = 45;
    /** Grace window after scheduled end before alerting OVERTIME */
    private static final int OVERTIME_GRACE_MIN = 20;

    private final ShiftRepository shiftRepository;
    private final SiteRepository  siteRepository;
    private final EmailService    emailService;

    @Scheduled(cron = "0 */5 * * * *")   // every 5 minutes
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
        // Find SCHEDULED shifts that should have started by now (past start time)
        Instant lateThreshold   = now.minus(GRACE_MINUTES,   ChronoUnit.MINUTES);
        Instant noShowThreshold = now.minus(NO_SHOW_MINUTES, ChronoUnit.MINUTES);

        List<Shift> overdueScheduled = shiftRepository
                .findScheduledStartingBefore(tenantId, lateThreshold);

        for (Shift shift : overdueScheduled) {
            boolean isNoShow = shift.getStartAt().isBefore(noShowThreshold);
            String level = isNoShow ? "NO_SHOW" : "LATE";

            log.warn("[Security] {} detected shiftId={} guardId={} scheduledStart={}",
                    level, shift.getId(), shift.getGuardId(), shift.getStartAt());

            sendShiftAlert(tenantId, shift, level);
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
            sendShiftAlert(tenantId, shift, "OVERTIME");
        }
    }

    // ── Alert dispatch ─────────────────────────────────────────────────────────

    /**
     * Sends a shift alert email to the tenant's admin.
     * Currently log-only until per-tenant admin email is wired (same pattern as
     * PsiraComplianceScheduler.resolveAdminEmail()).
     */
    private void sendShiftAlert(TenantId tenantId, Shift shift, String alertType) {
        String adminEmail = resolveAdminEmail(tenantId);
        if (adminEmail == null) {
            log.info("[Security] {} alert for shift={} — no admin email configured for tenant={}",
                    alertType, shift.getId(), tenantId.getValue());
            return;
        }

        String subject = buildSubject(alertType);
        String body    = buildBody(alertType, shift);

        try {
            emailService.send(adminEmail, subject, body);
            log.info("[Security] {} alert sent for shift={}", alertType, shift.getId());
        } catch (Exception e) {
            log.error("[Security] Failed to send {} alert for shift={}: {}",
                    alertType, shift.getId(), e.getMessage());
        }
    }

    private String buildSubject(String alertType) {
        return switch (alertType) {
            case "LATE"     -> "[HandyFlow Security] Guard late to shift";
            case "NO_SHOW"  -> "[HandyFlow Security] ⚠ Guard no-show alert";
            case "OVERTIME" -> "[HandyFlow Security] Guard overtime — please verify";
            default         -> "[HandyFlow Security] Shift alert";
        };
    }

    private String buildBody(String alertType, Shift shift) {
        String alertDesc = switch (alertType) {
            case "LATE"    -> "A guard has not started their shift and is past the "
                    + GRACE_MINUTES + "-minute grace period.";
            case "NO_SHOW" -> "A guard has not started their shift and is "
                    + NO_SHOW_MINUTES + " minutes overdue. This may be a no-show.";
            case "OVERTIME"-> "A guard's shift ended over " + OVERTIME_GRACE_MIN
                    + " minutes ago but they have not clocked out.";
            default        -> "A shift alert has been triggered.";
        };

        // Inline HTML — same styling as EmailTemplates.wrap() but kept local to
        // avoid adding a dependency on the shift-specific data the template would need
        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family:Arial,sans-serif;background:#F1F5F9;padding:20px">
            <div style="max-width:520px;margin:auto;background:#fff;border-radius:10px;overflow:hidden;
                        box-shadow:0 2px 12px rgba(0,0,0,0.08)">
              <div style="background:#1B3A6B;padding:20px 28px">
                <h2 style="color:#fff;margin:0;font-size:18px">HandyFlow Security</h2>
                <p style="color:rgba(255,255,255,0.7);margin:4px 0 0;font-size:13px">Shift Alert</p>
              </div>
              <div style="padding:24px 28px">
                <p style="color:#374151">%s</p>
                <div style="background:#FEF2F2;border-left:3px solid #DC2626;padding:12px 16px;
                            border-radius:0 8px 8px 0;margin:16px 0">
                  <p style="margin:0;color:#991B1B;font-weight:600">
                    Shift ID: %s<br/>
                    Guard ID: %s<br/>
                    Scheduled start: %s<br/>
                    Scheduled end: %s
                  </p>
                </div>
                <p style="color:#374151">
                  Please check your HandyFlow dashboard and contact the guard or site supervisor
                  to resolve this immediately.
                </p>
              </div>
            </div>
            </body>
            </html>
            """.formatted(alertDesc,
                shift.getId(), shift.getGuardId(),
                shift.getStartAt(), shift.getEndAt());
    }

    /**
     * Placeholder — returns null (log-only) until per-tenant admin email is wired.
     * Wire this the same way PsiraComplianceScheduler.resolveAdminEmail() is
     * intended to work once the tenant admin-email field is added.
     */
    private String resolveAdminEmail(TenantId tenantId) {
        return null;
    }
}
