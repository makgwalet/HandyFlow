package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.warehousing.domain.model.WhseProfile;
import za.co.handyflow.platform.warehousing.domain.repository.WhseProfileRepository;

import java.time.LocalDate;

/** End-of-day accrual for every warehousing tenant. One tenant failing does not stop the others. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhseStorageAccrualScheduler {

    private final WhseProfileRepository profileRepository;
    private final WhseStorageAccrualService accrualService;

    @Scheduled(cron = "0 55 23 * * *")
    public void accrueToday() {
        LocalDate today = LocalDate.now();
        for (WhseProfile profile : profileRepository.findAll()) {
            try {
                accrualService.accrueForTenant(profile.getTenantId(), today);
            } catch (Exception e) {
                log.error("[Warehousing] Storage accrual failed tenant={} date={}: {}",
                        profile.getTenantId(), today, e.getMessage(), e);
            }
        }
    }
}
