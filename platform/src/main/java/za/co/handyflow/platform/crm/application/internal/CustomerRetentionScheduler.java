package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.repository.CustomerActivityRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerConsentRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * CustomerRetentionScheduler — nightly POPIA retention policy review.
 *
 * WHY no TenantRepository dependency?
 * Same reason as CustomerInactivityScheduler — we derive tenant IDs
 * from the customers table instead of importing a cross-module repository.
 *
 * WHAT IT DOES:
 * Finds all consent records whose retention_expires_at has passed and
 * records a RETENTION_REVIEW_REQUIRED activity on each customer's timeline.
 * Staff see this in the CRM and decide: extend retention or delete.
 *
 * WHY not auto-delete?
 * POPIA requires judgment — an outstanding invoice means retention is still
 * legally necessary even after the consent period expires. Auto-deleting
 * would destroy data you're legally required to keep.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerRetentionScheduler {

    private final CustomerConsentRepository  consentRepository;
    private final CustomerActivityRepository activityRepository;
    private final CustomerRepository         customerRepository;

    @Scheduled(cron = "0 0 3 * * *")  // 3:00 AM daily
    public void reviewExpiredRetentionRecords() {
        log.info("[CRM] Retention review starting");

        var tenantIds  = customerRepository.findDistinctActiveTenantIds();
        int totalFlags = 0;

        for (UUID tenantId : tenantIds) {
            try {
                int flagged = reviewForTenant(TenantId.of(tenantId));
                totalFlags += flagged;
            } catch (Exception ex) {
                log.error("[CRM] Retention review failed for tenant={}: {}",
                        tenantId, ex.getMessage(), ex);
            }
        }

        log.info("[CRM] Retention review complete — {} records flagged for review", totalFlags);
    }

    @Transactional
    public int reviewForTenant(TenantId tenantId) {
        var expired = consentRepository.findExpiredForTenant(tenantId, Instant.now());
        int count   = 0;

        for (var consent : expired) {
            // Record a RETENTION_REVIEW_REQUIRED activity on the customer's timeline.
            // Staff see this without needing a separate retention UI.
            var activity = za.co.handyflow.platform.crm.domain.model.CustomerActivity
                    .systemEvent(
                            tenantId,
                            consent.getCustomerId(),
                            za.co.handyflow.platform.crm.domain.model.ActivityType.RETENTION_REVIEW_REQUIRED,
                            "Retention period expired. Review required: extend retention or delete customer record."
                    );
            activityRepository.save(activity);
            count++;

            log.info("[CRM] Retention expired: customer={} tenant={} expired={}",
                    consent.getCustomerId(), tenantId, consent.getRetentionExpiresAt());
        }

        return count;
    }
}
