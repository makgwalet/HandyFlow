package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthMaintenancePlanRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** Daily check of every company's maintenance plans. One company failing never stops the others. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EarthmovingPlanScheduler {
    private final EarthMaintenancePlanRepository plans;
    private final EarthMaintenancePlanService service;

    @Scheduled(cron = "0 15 5 * * *", zone = "Africa/Johannesburg")
    public void daily() { sweep(LocalDate.now(ZoneId.of("Africa/Johannesburg"))); }

    /** Returns how many work orders were raised. */
    public int sweep(LocalDate today) {
        int raised = 0;
        for (UUID t : plans.findTenantIdsWithActivePlans()) {
            try { raised += service.scan(TenantId.of(t), today).workOrdersRaised(); }
            catch (Exception e) { log.error("[Earthmoving] Plan check failed for tenant {}: {}", t, e.getMessage(), e); }
        }
        return raised;
    }
}
