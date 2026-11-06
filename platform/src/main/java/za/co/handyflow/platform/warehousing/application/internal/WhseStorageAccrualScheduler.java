package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.warehousing.domain.model.WhseProfile;
import za.co.handyflow.platform.warehousing.domain.repository.WhseProfileRepository;

import java.time.LocalDate;

/** Daily accrual for every warehousing tenant. One tenant failing does not stop the others. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhseStorageAccrualScheduler {

    private final WhseProfileRepository profileRepository;
    private final WhseStorageAccrualService accrualService;

    /** Days re-checked on every run, so a night the job missed is made up. Days already accrued are left alone. */
    static final int CATCH_UP_DAYS = 3;

    /** Just after midnight, so the day being accrued is complete and includes late movements. */
    @Scheduled(cron = "0 15 0 * * *", zone = "${handyflow.warehousing.billing-zone:Africa/Johannesburg}")
    public void accrueRecentDays() {
        LocalDate yesterday = LocalDate.now(accrualService.zone()).minusDays(1);
        for (WhseProfile profile : profileRepository.findAll()) {
            for (int back = CATCH_UP_DAYS - 1; back >= 0; back--) {
                LocalDate day = yesterday.minusDays(back);
                try {
                    accrualService.accrueForTenant(profile.getTenantId(), day);
                } catch (Exception e) {
                    log.error("[Warehousing] Storage accrual failed tenant={} date={}: {}",
                            profile.getTenantId(), day, e.getMessage(), e);
                }
            }
        }
    }
}
