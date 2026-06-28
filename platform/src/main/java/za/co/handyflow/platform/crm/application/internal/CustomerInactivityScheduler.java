package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.CustomerStatus;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * CustomerInactivityScheduler — automatic churn-risk flagging.
 *
 * WHY no TenantRepository dependency?
 * We don't import TenantRepository because the Tenant module's repository
 * package path varies per project and creates a fragile cross-module import.
 * Instead we query distinct tenant_ids directly from the customers table —
 * CRM already owns that table, so this is a safe self-contained query.
 * Any tenant that has at least one customer record is a tenant we need to
 * process. Tenants with no customers produce an empty list — harmless.
 *
 * PRODUCTION NOTE:
 * For multi-instance deployments (2+ app servers), replace @Scheduled with
 * Quartz JDBC JobStore so only one instance runs the job at a time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerInactivityScheduler {

    private static final int INACTIVITY_THRESHOLD_DAYS = 90;

    private final CustomerRepository customerRepository;

    @Scheduled(cron = "0 0 2 * * *")  // 2:00 AM daily
    public void markInactiveCustomers() {
        var cutoff = Instant.now().minus(INACTIVITY_THRESHOLD_DAYS, ChronoUnit.DAYS);
        log.info("[CRM] Inactivity check starting — threshold: {} days, cutoff: {}",
                INACTIVITY_THRESHOLD_DAYS, cutoff);

        // Derive tenant list directly from the customers table.
        // This avoids any cross-module TenantRepository dependency.
        List<UUID> tenantIds = customerRepository.findDistinctActiveTenantIds();
        int totalMarked = 0;

        for (UUID tenantId : tenantIds) {
            try {
                int marked = markInactiveForTenant(TenantId.of(tenantId), cutoff);
                totalMarked += marked;
            } catch (Exception ex) {
                log.error("[CRM] Inactivity check failed for tenant={}: {}",
                        tenantId, ex.getMessage(), ex);
            }
        }

        log.info("[CRM] Inactivity check complete — {} customers marked INACTIVE across {} tenants",
                totalMarked, tenantIds.size());
    }

    /**
     * Per-tenant processing — marks eligible customers INACTIVE.
     * Each tenant runs in its own transaction so one failure doesn't
     * roll back the others.
     */
    @Transactional
    public int markInactiveForTenant(TenantId tenantId, Instant cutoff) {
        var customers = customerRepository.findActiveUpdatedBefore(tenantId, cutoff);
        int count = 0;

        for (var customer : customers) {
            customer.changeStatus(CustomerStatus.INACTIVE, null); // null = system-triggered
            customerRepository.save(customer);
            count++;
        }

        if (count > 0) {
            log.info("[CRM] Marked {} customers INACTIVE for tenant={}", count, tenantId);
        }
        return count;
    }
}
