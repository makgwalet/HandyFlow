package za.co.handyflow.platform.accounting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.accounting.domain.model.AccBankAccount;
import za.co.handyflow.platform.accounting.domain.model.AccVatPeriod;
import za.co.handyflow.platform.accounting.domain.repository.AccBankAccountRepository;
import za.co.handyflow.platform.accounting.domain.repository.AccVatPeriodRepository;
import za.co.handyflow.platform.accounting.dto.AgingReportResponse;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final AccBankAccountRepository bankAccountRepo;
    private final AccountingService      accountingService;
    private final AccountingReportPdfService reportPdfService;
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

                byte[] pdfBytes = null;
                try {
                    pdfBytes = reportPdfService.generateVat201(tenantId, period.getPeriodStart(), period.getPeriodEnd());
                } catch (Exception pdfEx) {
                    log.warn("Failed to generate VAT201 PDF for reminder tenantId={}, sending without attachment: {}",
                            period.getTenantId(), pdfEx.getMessage());
                }

                accountingService.sendVatReminder(tenantId, tenant.email(),
                        tenant.companyName(), period.getPeriodEnd(), estimated, pdfBytes);

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

                byte[] pdfBytes = null;
                try {
                    pdfBytes = reportPdfService.generateArAging(tenantId);
                } catch (Exception pdfEx) {
                    log.warn("Failed to generate AR aging PDF for alert tenant={}, sending without attachment: {}",
                            tenant.id(), pdfEx.getMessage());
                }

                accountingService.sendOverdueArAlert(tenantId, tenant.email(),
                        tenant.companyName(), aging, pdfBytes);

                log.info("Sent AR alert tenantId={} total={}", tenant.id(), aging.total());
            } catch (Exception e) {
                log.error("Failed to send AR alert for tenant={}: {}",
                        tenant.id(), e.getMessage(), e);
            }
        }
    }

    /**
     * Low bank balance alert — fires daily at 08:15 SAST (offset from the
     * other 06:00 UTC jobs above to avoid all three landing in the same
     * second). Only ever fires for accounts that actually have a
     * threshold set — see AccBankAccount.setLowBalanceThreshold()'s own
     * comment — so an account with no threshold generates no noise at
     * all. Repeats daily for as long as the balance stays below
     * threshold, same as sendOverdueArAlerts() above being a persistent
     * check rather than a one-shot reminder.
     */
    @Scheduled(cron = "0 15 6 * * *")   // 06:15 UTC = 08:15 SAST
    public void sendLowBalanceAlerts() {
        log.info("Running low balance alert check");

        List<AccBankAccount> lowAccounts = bankAccountRepo.findAllAccountsBelowThreshold();
        if (lowAccounts.isEmpty()) return;

        Map<UUID, List<AccBankAccount>> byTenant = lowAccounts.stream()
                .collect(Collectors.groupingBy(AccBankAccount::getTenantId));

        for (var entry : byTenant.entrySet()) {
            try {
                TenantId tenantId = TenantId.of(entry.getKey());
                TenantDetails tenant = tenantFacade.findTenantDetails(tenantId).orElse(null);
                if (tenant == null || tenant.email() == null) continue;

                accountingService.sendLowBalanceAlert(tenantId, tenant.email(),
                        tenant.companyName(), entry.getValue());

                log.info("Sent low balance alert tenantId={} accountCount={}",
                        entry.getKey(), entry.getValue().size());
            } catch (Exception e) {
                log.error("Failed to send low balance alert for tenant={}: {}",
                        entry.getKey(), e.getMessage(), e);
            }
        }
    }

    /**
     * VAT period overdue escalation — fires daily at 08:30 SAST (offset
     * from the other three 06:xx UTC jobs above). Distinct from
     * sendVatPeriodReminders(): that one warns while a period is still
     * OPEN and closing soon; this one only fires once the period's own
     * end date has already passed and it's STILL open — a missed
     * deadline, not an upcoming one. Repeats daily until the period is
     * actually closed, same reasoning as sendLowBalanceAlerts() above —
     * a missed SARS deadline is an active, worsening compliance risk,
     * not something that should go quiet after one email.
     */
    @Scheduled(cron = "0 30 6 * * *")   // 06:30 UTC = 08:30 SAST
    public void sendVatOverdueEscalations() {
        LocalDate today = LocalDate.now();
        log.info("Running VAT period overdue escalation check — today={}", today);

        List<AccVatPeriod> overdue = vatPeriodRepo.findOpenPeriodsOverdue(today);

        for (AccVatPeriod period : overdue) {
            try {
                TenantId tenantId = TenantId.of(period.getTenantId());
                TenantDetails tenant = tenantFacade.findTenantDetails(tenantId).orElse(null);
                if (tenant == null || tenant.email() == null) continue;

                long daysOverdue = ChronoUnit.DAYS.between(period.getPeriodEnd(), today);

                accountingService.sendVatOverdueEscalation(tenantId, tenant.email(),
                        tenant.companyName(), period.getPeriodEnd(), daysOverdue);

                log.info("Sent VAT overdue escalation tenantId={} periodEnd={} daysOverdue={}",
                        period.getTenantId(), period.getPeriodEnd(), daysOverdue);
            } catch (Exception e) {
                log.error("Failed to send VAT overdue escalation for period={}: {}",
                        period.getId(), e.getMessage(), e);
            }
        }
    }
}