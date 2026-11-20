package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.legalcompliance.domain.model.DeclarationCampaign;
import za.co.handyflow.platform.legalcompliance.domain.repository.DeclarationCampaignRepository;

import java.time.LocalDate;
import java.time.ZoneId;

/** Daily at 07:38: reminds people who have not answered an open declaration campaign (7 days before, the day before, on the day, then weekly while overdue). Each campaign is isolated. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeclarationCampaignScheduler {

    private final DeclarationCampaignRepository campaigns;
    private final DeclarationCampaignService service;

    @Scheduled(cron = "0 38 7 * * *", zone = "Africa/Johannesburg")
    public void sweep() {
        try { run(LocalDate.now(ZoneId.of("Africa/Johannesburg"))); }
        catch (Exception e) { log.error("[Legal/Compliance] Declaration reminder sweep failed: {}", e.getMessage(), e); }
    }

    int run(LocalDate today) {
        int reminded = 0;
        for (DeclarationCampaign c : campaigns.findOpenAcrossTenants()) {
            try { reminded += service.remind(c.getTenantId(), c, today); }
            catch (Exception e) { log.warn("[Legal/Compliance] Reminder failed for campaign {}: {}", c.getId(), e.getMessage()); }
        }
        return reminded;
    }
}
