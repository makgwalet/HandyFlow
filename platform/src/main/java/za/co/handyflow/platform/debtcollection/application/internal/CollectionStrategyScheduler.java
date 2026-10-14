package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Daily 08:00: raises the work items the active strategies call for (after the 07:45 work-item sweep). Sends nothing. */
@Component
@RequiredArgsConstructor
public class CollectionStrategyScheduler {
    private static final Logger log = LoggerFactory.getLogger(CollectionStrategyScheduler.class);
    private final CollectionStrategyEngine engine;

    @Scheduled(cron = "0 0 8 * * *")
    public void run() {
        try {
            int n = engine.applyAllTenants(LocalDate.now());
            if (n > 0) log.info("[Debt Collection] strategies raised {} work item(s)", n);
        } catch (RuntimeException e) {
            log.error("[Debt Collection] strategy job failed", e);
        }
    }
}
