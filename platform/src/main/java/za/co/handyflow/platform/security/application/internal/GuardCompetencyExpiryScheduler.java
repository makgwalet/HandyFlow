// security/application/internal/GuardCompetencyExpiryScheduler.java
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
import za.co.handyflow.platform.security.domain.model.GuardCompetency;
import za.co.handyflow.platform.security.domain.repository.GuardCompetencyRepository;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Daily warning that skills and certifications on guards' files expire within the next 30 days (first aid,
 * firearm competency and so on). It follows the other compliance schedulers: one digest per tenant, sent to the
 * tenant's administrators, repeated each morning while the competency is still due (no dedup, by convention).
 * Runs at 06:45 SAST, between the screening check (06:30) and the PSiRA check (07:00).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardCompetencyExpiryScheduler {

    static final int WARN_DAYS_BEFORE = 30;
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final GuardCompetencyRepository competencyRepository;
    private final GuardRepository guardRepository;
    private final SiteRepository siteRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 45 6 * * *", zone = "Africa/Johannesburg")
    public void checkCompetencyExpiry() {
        LocalDate today = LocalDate.now(SAST);
        for (UUID tenantId : siteRepository.findDistinctActiveTenantIds()) {
            try {
                checkForTenant(TenantId.of(tenantId), today);
            } catch (Exception e) {
                log.error("[Security] Competency expiry check failed for tenant={}: {}", tenantId, e.getMessage(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public void checkForTenant(TenantId tenantId, LocalDate today) {
        List<GuardCompetency> due = competencyRepository.findExpiringBetween(tenantId, today, today.plusDays(WARN_DAYS_BEFORE));
        if (due.isEmpty()) return;

        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.warn("[Security] {} competencies expiring for tenant={} but no admin recipients could be resolved", due.size(), tenantId.getValue());
            return;
        }
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.GUARD_COMPETENCY_EXPIRING)
                .title(due.size() + " guard skill(s) or certificate(s) expire within " + WARN_DAYS_BEFORE + " days")
                .message(summary(tenantId, due))
                .actionUrl("/security/guards")
                .sourceModule("security")
                .recipients(admins)
                .build());
        log.info("[Security] Competency expiry alert sent tenant={} count={}", tenantId.getValue(), due.size());
    }

    private String summary(TenantId tenantId, List<GuardCompetency> due) {
        return due.stream().map(c -> {
            String name = guardRepository.findActiveById(tenantId, c.getGuardId()).map(Guard::getFullName).orElse("a guard");
            return name + " (" + label(c) + ", expires " + c.getExpiryDate() + ")";
        }).collect(Collectors.joining(", "));
    }

    static String label(GuardCompetency c) {
        if (c.getTitle() != null && !c.getTitle().isBlank()) return c.getTitle();
        return c.getType().name().toLowerCase().replace('_', ' ');
    }
}
