package za.co.handyflow.platform.crm.application.internal;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.shared.TenantId;

/** Every morning, runs the journey steps that have come due in active campaigns. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrmJourneyScheduler {
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final CustomerRepository customerRepository;
    private final CampaignService campaignService;

    @Scheduled(cron = "0 45 6 * * *", zone = "Africa/Johannesburg")
    public void sweep() {
        LocalDate today = LocalDate.now(ZONE);
        int total = 0;
        for (UUID tenantId : customerRepository.findDistinctActiveTenantIds()) {
            try {
                total += campaignService.runDue(TenantId.of(tenantId), today);
            } catch (Exception ex) {
                log.error("[CRM] Campaign journeys failed for tenant={}: {}", tenantId, ex.getMessage(), ex);
            }
        }
        log.info("[CRM] Campaign journey sweep complete: {} steps run", total);
    }
}
