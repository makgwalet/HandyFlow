package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingScheduler {

    private final MarketingService         marketingService;
    private final MarketingDeliveryService deliveryService;

    // Batch size is marketing.send.batch-size (default 50) per run. Safe on multiple instances:
    // the queue is claimed with FOR UPDATE SKIP LOCKED, and a run already in progress on this
    // instance is skipped rather than overlapped.
    @Scheduled(cron = "0 */2 * * * *", zone = "Africa/Johannesburg")
    void processSendQueue() {
        deliveryService.processQueue();   // never throws; logs its own failures
    }

    // Each due campaign launches in its own transaction (via the service proxy), so one
    // failing campaign can't roll back or block the others.
    @Scheduled(cron = "0 */5 * * * *", zone = "Africa/Johannesburg")
    void launchScheduledCampaigns() {
        try {
            for (MarketingService.DueCampaign due : marketingService.findDueScheduledCampaigns()) {
                try {
                    marketingService.launchDueCampaign(due);
                } catch (Exception e) {
                    log.error("MarketingScheduler: failed to launch scheduled campaign={}", due.campaignId(), e);
                }
            }
        } catch (Exception e) {
            log.error("MarketingScheduler: scheduled-campaign sweep failed", e);
        }
    }
}