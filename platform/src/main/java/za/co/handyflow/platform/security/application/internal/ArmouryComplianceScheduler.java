// security/application/internal/ArmouryComplianceScheduler.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Armoury;
import za.co.handyflow.platform.security.domain.repository.ArmouryRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ArmouryComplianceScheduler — nightly check for expiring SAPS firearm licenses.
 *
 * Runs at 07:15 daily — five minutes after PsiraComplianceScheduler (07:00) and
 * GuardScreeningService.checkScreeningExpiry() (06:30), so all three compliance
 * checks land in the same morning batch without competing for DB connections
 * at the exact same second.
 *
 * WHY 30 days warning, same as PSiRA?
 * SAPS firearm license renewal has a similarly long lead time to PSiRA
 * registration renewal — 30 days gives the operator enough runway to submit
 * paperwork before the license lapses and the firearm becomes legally
 * unissuable (ArmouryService.issue() hard-blocks on isLicenseExpired()).
 *
 * Email delivery is log-only until per-tenant admin email is wired — same
 * gap as PsiraComplianceScheduler and NoShowAlertScheduler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArmouryComplianceScheduler {

    private static final int WARN_DAYS_BEFORE = 30;

    private final ArmouryRepository armouryRepository;
    private final SiteRepository    siteRepository;
    private final EmailService      emailService;

    @Scheduled(cron = "0 15 7 * * *")
    public void checkLicenseExpiry() {
        LocalDate today    = LocalDate.now();
        LocalDate warnDate = today.plusDays(WARN_DAYS_BEFORE);

        List<UUID> tenantIds = siteRepository.findDistinctActiveTenantIds();

        for (UUID tenantId : tenantIds) {
            try {
                checkForTenant(TenantId.of(tenantId), warnDate);
            } catch (Exception e) {
                log.error("[Security] Armoury compliance check failed for tenant={}: {}",
                        tenantId, e.getMessage(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public void checkForTenant(TenantId tenantId, LocalDate warnDate) {
        List<Armoury> expiringSoon = armouryRepository.findLicenseExpiringBy(tenantId, warnDate);

        if (expiringSoon.isEmpty()) return;

        List<Armoury> expired = expiringSoon.stream()
                .filter(Armoury::isLicenseExpired)
                .toList();
        List<Armoury> expiringNotYetExpired = expiringSoon.stream()
                .filter(a -> !a.isLicenseExpired())
                .toList();

        if (!expired.isEmpty()) {
            log.warn("[Security] {} firearm(s) with EXPIRED SAPS license for tenant={}",
                    expired.size(), tenantId.getValue());
            expired.forEach(a -> log.warn(
                    "[Security]   serial={} license={} expired={}",
                    a.getFirearmSerial(), a.getSapsLicenseNumber(), a.getLicenseExpiry()));
        }
        if (!expiringNotYetExpired.isEmpty()) {
            log.info("[Security] {} firearm(s) with SAPS license expiring within {} days for tenant={}",
                    expiringNotYetExpired.size(), WARN_DAYS_BEFORE, tenantId.getValue());
            expiringNotYetExpired.forEach(a -> log.info(
                    "[Security]   serial={} license={} expiresAt={}",
                    a.getFirearmSerial(), a.getSapsLicenseNumber(), a.getLicenseExpiry()));
        }

        // Email delivery: log-only until per-tenant admin email is wired
        // (same pattern as PsiraComplianceScheduler.resolveAdminEmail()).
    }
}
