// security/application/internal/GuardScreeningComplianceScheduler.java

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
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.GuardScreeningRecord;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.GuardScreeningRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GuardScreeningComplianceScheduler — nightly check for guard screening
 * renewals due soon (V213).
 *
 * MIGRATION NOTE: this logic used to live as a @Scheduled method directly on
 * GuardScreeningService (checkScreeningExpiry()), and only ever logged what
 * it found -- exactly the gap the platform-wide notification audit flagged:
 * "never migrated to the NotificationService pipeline... a guard's
 * polygraph/screening renewal lapsing silently doesn't reach anyone, while
 * the near-identical PSiRA and firearm-license expiry cases do."
 *
 * Extracted into its own @Component here for two reasons:
 *   1. Matches the file-per-scheduler convention PsiraComplianceScheduler
 *      and ArmouryComplianceScheduler already established -- this was the
 *      one compliance check still mixed into its service class instead of
 *      living alongside its siblings.
 *   2. Keeps GuardScreeningService focused on screening-record CRUD and the
 *      scheduling gate, not scheduled-job orchestration.
 *
 * The old checkScreeningExpiry() method has been REMOVED from
 * GuardScreeningService -- this class replaces it entirely, not
 * supplements it. Same 06:30 cron, same 30-day warning window, same
 * "before PSiRA at 07:00" ordering rationale as before.
 *
 * WHY no edge-trigger dedup here (unlike NoShowAlertScheduler)?
 * This runs once daily, not every 5 minutes -- re-alerting on the same
 * still-due screening the next morning is the intended behaviour (same as
 * PsiraComplianceScheduler and ArmouryComplianceScheduler, neither of which
 * dedup either). Dedup only matters for schedulers that could otherwise
 * fire dozens of times before the underlying condition resolves.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardScreeningComplianceScheduler {

    private static final int WARN_DAYS_BEFORE = 30;

    private final GuardScreeningRepository screeningRepository;
    private final GuardRepository          guardRepository;
    private final SiteRepository           siteRepository;
    private final NotificationService      notificationService;
    private final TenantAdminRecipients    tenantAdminRecipients;

    @Scheduled(cron = "0 30 6 * * *")
    public void checkScreeningExpiry() {
        LocalDate today    = LocalDate.now();
        LocalDate warnDate = today.plusDays(WARN_DAYS_BEFORE);

        List<UUID> tenantIds = siteRepository.findDistinctActiveTenantIds();

        for (UUID tenantId : tenantIds) {
            try {
                checkForTenant(TenantId.of(tenantId), warnDate);
            } catch (Exception e) {
                log.error("[Security] Guard screening compliance check failed for tenant={}: {}",
                        tenantId, e.getMessage(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public void checkForTenant(TenantId tenantId, LocalDate warnDate) {
        List<GuardScreeningRecord> dueSoon = screeningRepository.findDueSoon(tenantId, warnDate);
        if (dueSoon.isEmpty()) return;

        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.warn("[Security] {} screening(s) due soon for tenant={} but no admin recipients "
                            + "could be resolved",
                    dueSoon.size(), tenantId.getValue());
            return;
        }

        String title   = dueSoon.size() + " guard screening(s) due within " + WARN_DAYS_BEFORE + " days";
        String message = buildSummary(tenantId, dueSoon);

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.GUARD_SCREENING_DUE)
                .title(title)
                .message(message)
                .actionUrl("/security/guards")
                .sourceModule("security")
                .recipients(admins)
                .build());

        log.info("[Security] Guard screening compliance alert sent tenant={} count={}",
                tenantId.getValue(), dueSoon.size());
    }

    private String buildSummary(TenantId tenantId, List<GuardScreeningRecord> dueSoon) {
        return dueSoon.stream()
                .map(r -> {
                    String guardName = guardRepository.findActiveById(tenantId, r.getGuardId())
                            .map(Guard::getFullName).orElse(r.getGuardId().toString());
                    return guardName + " (" + r.getScreeningType() + ", due " + r.getNextDueAt() + ")";
                })
                .collect(Collectors.joining(", "));
    }
}