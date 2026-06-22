package za.co.handyflow.platform.accounting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.accounting.domain.model.AccVatPeriod;
import za.co.handyflow.platform.accounting.domain.repository.AccVatPeriodRepository;
import za.co.handyflow.platform.accounting.dto.AgingReportResponse;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled jobs for accounting email notifications.
 *
 * WHY @Scheduled and not events?
 * VAT reminders and AR alerts are time-based, not triggered by user actions.
 * A daily cron job checking all tenants is the correct pattern.
 * Events would require storing state about "has this reminder been sent today".
 *
 * Runs at 08:00 SAST every day (06:00 UTC).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountingNotificationScheduler {

    private final AccVatPeriodRepository vatPeriodRepo;
    private final AccountingService      accountingService;
    private final TenantFacade           tenantFacade;

    /**
     * VAT period closing reminder — fires 7 days before and 1 day before period end.
     * Finds all OPEN VAT periods across all tenants closing within 7 days.
     */
    @Scheduled(cron = "0 0 6 * * *")   // 06:00 UTC = 08:00 SAST
    public void sendVatPeriodReminders() {
        LocalDate today   = LocalDate.now();
        LocalDate in7days = today.plusDays(7);

        log.info("Running VAT period reminder check — today={}", today);

        List<AccVatPeriod> closingSoon = vatPeriodRepo.findOpenPeriodsEndingBetween(today, in7days);

        for (AccVatPeriod period : closingSoon) {
            try {
                TenantId tenantId = TenantId.of(period.getTenantId());
                TenantDetails tenant = tenantFacade.findTenantDetails(tenantId).orElse(null);
                if (tenant == null || tenant.email() == null) continue;

                // Estimate VAT payable from invoices in the period
                var vat = accountingService.getVat201(tenantId,
                        period.getPeriodStart(), period.getPeriodEnd());
                BigDecimal estimated = vat.netVatPayable();

                accountingService.sendVatReminder(tenantId, tenant.email(),
                        tenant.companyName(), period.getPeriodEnd(), estimated);

                log.info("Sent VAT reminder tenantId={} periodEnd={} estimated={}",
                        period.getTenantId(), period.getPeriodEnd(), estimated);
            } catch (Exception e) {
                log.error("Failed to send VAT reminder for period={}: {}",
                        period.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Overdue AR alert — fires daily at 08:00 SAST.
     * Sends alert only when there are invoices overdue > 30 days.
     * Skips tenants with no overdue invoices to avoid noise.
     */
    @Scheduled(cron = "0 0 6 * * *")   // 06:00 UTC = 08:00 SAST
    public void sendOverdueArAlerts() {
        log.info("Running overdue AR alert check");

        // Get all tenants that have accounting active
        // WHY findAllActive? We only want enabled tenants, not trial-expired ones.
        List<TenantDetails> tenants = tenantFacade.findAllActive();

        for (TenantDetails tenant : tenants) {
            try {
                TenantId tenantId = TenantId.of(tenant.id());
                AgingReportResponse aging = accountingService.getArAging(tenantId);

                // Only send if there are invoices in the 31-60, 61-90, or 90+ buckets
                boolean hasSignificantOverdue =
                        aging.days31to60().compareTo(BigDecimal.ZERO) > 0 ||
                                aging.days61to90().compareTo(BigDecimal.ZERO) > 0 ||
                                aging.over90().compareTo(BigDecimal.ZERO) > 0;

                if (!hasSignificantOverdue) continue;
                if (tenant.email() == null) continue;

                accountingService.sendOverdueArAlert(tenantId, tenant.email(),
                        tenant.companyName(), aging);

                log.info("Sent AR alert tenantId={} total={}", tenant.id(), aging.total());
            } catch (Exception e) {
                log.error("Failed to send AR alert for tenant={}: {}",
                        tenant.id(), e.getMessage(), e);
            }
        }
    }
}
