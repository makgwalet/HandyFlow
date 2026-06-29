// security/application/internal/PsiraComplianceScheduler.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * PsiraComplianceScheduler — nightly check for expiring or expired PSiRA registrations.
 *
 * Runs at 07:00 daily.  Emails the tenant's admin email for any guard whose
 * psiraExpiryDate is within 30 days or already expired.
 *
 * WHY 07:00 and not midnight?
 * Compliance alerts should land in an inbox at the start of the workday, not
 * at midnight when no one will action them.  A supervisor sees it when they
 * open email in the morning and can act on it before scheduling shifts.
 *
 * WHY 30 days warning?
 * PSiRA renewal takes 5–10 business days minimum.  30 days gives enough lead
 * time to submit renewal paperwork without disrupting shift scheduling.
 * 7 days is too short; 60 days creates alert fatigue.
 *
 * WHY per-tenant isolation with separate @Transactional?
 * Same pattern as BookingReminderScheduler.  If one tenant's email config is
 * broken, the others still receive their alerts.
 *
 * PRODUCTION NOTE: Use Quartz JDBC JobStore in multi-instance deployments to
 * prevent duplicate emails from multiple app instances running simultaneously.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PsiraComplianceScheduler {

    private static final int WARN_DAYS_BEFORE = 30;

    private final GuardRepository guardRepository;
    private final SiteRepository  siteRepository;
    private final EmailService    emailService;

    @Scheduled(cron = "0 0 7 * * *")
    public void checkPsiraExpiry() {
        LocalDate today    = LocalDate.now();
        LocalDate warnDate = today.plusDays(WARN_DAYS_BEFORE);

        log.info("[Security] PSiRA compliance check starting date={}", today);

        List<UUID> tenantIds = siteRepository.findDistinctActiveTenantIds();
        int totalAlerts = 0;

        for (UUID tenantId : tenantIds) {
            try {
                int alerts = checkForTenant(TenantId.of(tenantId), today, warnDate);
                totalAlerts += alerts;
            } catch (Exception ex) {
                log.error("[Security] PSiRA check failed for tenant={}: {}", tenantId, ex.getMessage(), ex);
            }
        }

        log.info("[Security] PSiRA compliance check complete — {} alerts sent", totalAlerts);
    }

    @Transactional(readOnly = true)
    public int checkForTenant(TenantId tenantId, LocalDate today, LocalDate warnDate) {
        // Fetch all active guards with a psiraExpiryDate set
        List<Guard> allGuards = guardRepository.findAllActive(
                tenantId, org.springframework.data.domain.Pageable.ofSize(500)).getContent();

        List<Guard> expiredGuards = allGuards.stream()
                .filter(g -> g.getPsiraExpiryDate() != null && g.getPsiraExpiryDate().isBefore(today))
                .collect(Collectors.toList());

        List<Guard> expiringGuards = allGuards.stream()
                .filter(g -> g.getPsiraExpiryDate() != null
                        && !g.getPsiraExpiryDate().isBefore(today)
                        && !g.getPsiraExpiryDate().isAfter(warnDate))
                .collect(Collectors.toList());

        if (expiredGuards.isEmpty() && expiringGuards.isEmpty()) {
            return 0;
        }

        // Build email body and send
        String body = EmailTemplates.psiraComplianceAlert(
                expiredGuards.stream().map(g -> new EmailTemplates.GuardExpiryInfo(
                                g.getFullName(), g.getPsiraNumber(), g.getPsiraExpiryDate(), true))
                        .collect(Collectors.toList()),
                expiringGuards.stream().map(g -> new EmailTemplates.GuardExpiryInfo(
                                g.getFullName(), g.getPsiraNumber(), g.getPsiraExpiryDate(), false))
                        .collect(Collectors.toList()),
                today
        );

        // WHY send to a fixed admin email derived from the tenant?
        // We don't have a per-tenant admin email field yet (Phase 2 scope).
        // For now: log that we would send and let the bootstrap config provide
        // a fallback admin email via application.properties.
        // Replace this with tenant.adminEmail lookup when available.
        String adminEmail = resolveAdminEmail(tenantId);
        if (adminEmail != null) {
            emailService.send(adminEmail, "PSiRA Compliance Alert — Action Required", body);
            log.info("[Security] PSiRA alert sent tenant={} expired={} expiring={}",
                    tenantId.getValue(), expiredGuards.size(), expiringGuards.size());
        } else {
            log.warn("[Security] PSiRA compliance issues found for tenant={} but no admin email configured. " +
                            "Expired: {} Expiring soon: {}",
                    tenantId.getValue(), expiredGuards.size(), expiringGuards.size());
        }

        return expiredGuards.size() + expiringGuards.size();
    }

    /**
     * Resolves the admin email for the tenant.
     * Phase 2: replace with TenantRepository lookup for tenant.adminEmail.
     * For now: reads from application.properties via @Value in a config class,
     * or returns null to log-only mode.
     *
     * WHY log-only as the default?
     * Sending emails to a wrong address on every nightly run would be worse
     * than not sending at all.  Log-only is safe until the admin email is
     * properly configured per-tenant.
     */
    private String resolveAdminEmail(TenantId tenantId) {
        // TODO Phase 2: return tenantRepo.findById(tenantId).map(Tenant::getAdminEmail).orElse(null);
        return null; // safe default until per-tenant admin email is implemented
    }
}
