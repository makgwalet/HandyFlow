package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Puts scheduled price changes into force just after midnight (South African time). A missed night is caught up the next. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CataloguePriceScheduler {

    private final CataloguePricingService pricing;

    @Scheduled(cron = "0 5 0 * * *", zone = "Africa/Johannesburg")
    public void applyDue() {
        try {
            pricing.applyDue();
        } catch (RuntimeException e) {
            log.error("Applying scheduled catalogue prices failed; it will be retried tomorrow", e);
        }
    }
}
