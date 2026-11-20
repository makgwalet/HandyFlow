package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.legalcompliance.domain.model.WrittenResolution;
import za.co.handyflow.platform.legalcompliance.domain.repository.WrittenResolutionRepository;

import java.time.LocalDate;
import java.time.ZoneId;

/** Daily at 07:42: closes written resolutions whose deadline has passed, and reminds members who have not answered (2 days before and on the deadline day). Each resolution is isolated. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WrittenResolutionScheduler {

    private final WrittenResolutionRepository resolutions;
    private final WrittenResolutionService service;

    @Scheduled(cron = "0 42 7 * * *", zone = "Africa/Johannesburg")
    public void sweep() {
        try { run(LocalDate.now(ZoneId.of("Africa/Johannesburg"))); }
        catch (Exception e) { log.error("[Legal/Compliance] Written resolution sweep failed: {}", e.getMessage(), e); }
    }

    /** Returns how many were closed. */
    int run(LocalDate today) {
        int closed = 0;
        for (WrittenResolution w : resolutions.findOpenAcrossTenants()) {
            try { if (service.sweep(w.getTenantId(), w, today)) closed++; }
            catch (Exception e) { log.warn("[Legal/Compliance] Written resolution {} could not be processed: {}", w.getId(), e.getMessage()); }
        }
        return closed;
    }
}
