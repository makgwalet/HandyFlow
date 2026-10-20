package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every morning, warns about documents that are about to expire or have expired. */
@Component
@RequiredArgsConstructor
public class EarthDocumentExpiryScheduler {
    private final EarthDocumentExpiryService expiry;

    @Scheduled(cron = "0 15 6 * * *", zone = "Africa/Johannesburg")
    public void daily() { expiry.sweep(EarthHireService.today()); }
}
