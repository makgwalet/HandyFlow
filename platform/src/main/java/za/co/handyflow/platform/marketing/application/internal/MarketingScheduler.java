package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingScheduler {

    private final MarketingService marketingService;

    // Process send queue every 2 minutes — 50 emails per run
    // WHY 50 per run? Prevents hammering the SMTP server.
    // At 50/2min = 1,500/hour which is well within Gmail SMTP limits.
    @Scheduled(cron = "0 */2 * * * *", zone = "Africa/Johannesburg")
    void processSendQueue() {
        log.debug("MarketingScheduler: processing send queue");
        marketingService.processSendQueue();
    }

    // Check for scheduled campaigns ready to launch — every 5 minutes
    @Scheduled(cron = "0 */5 * * * *", zone = "Africa/Johannesburg")
    void launchScheduledCampaigns() {
        log.debug("MarketingScheduler: checking scheduled campaigns");
        marketingService.launchScheduledCampaigns();
    }
}
