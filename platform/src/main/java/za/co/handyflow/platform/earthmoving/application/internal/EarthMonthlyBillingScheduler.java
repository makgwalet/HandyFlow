package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthSettingsRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * On the 2nd of each month, bills the month that just ended for every company that switched the run on: charges and DRAFT invoices
 * only. One company failing never stops the others, and a month that already ran on schedule is never run again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EarthMonthlyBillingScheduler {
    private final EarthSettingsRepository settings;
    private final EarthMonthlyBillingService billing;

    @Scheduled(cron = "0 30 6 2 * *", zone = "Africa/Johannesburg")
    public void monthly() { sweep(LocalDate.now(EarthHireService.ZONE)); }

    /** Returns how many companies were billed. */
    public int sweep(LocalDate today) {
        YearMonth last = YearMonth.from(today).minusMonths(1);
        int billed = 0;
        for (UUID t : settings.findTenantIdsWithAutoBilling()) {
            try { if (billing.runScheduled(TenantId.of(t), last, today) != null) billed++; }
            catch (Exception e) { log.error("[Earthmoving] Monthly billing failed for tenant {}: {}", t, e.getMessage(), e); }
        }
        return billed;
    }
}
