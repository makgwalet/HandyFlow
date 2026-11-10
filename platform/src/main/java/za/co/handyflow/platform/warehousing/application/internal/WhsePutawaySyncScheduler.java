package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseProfile;
import za.co.handyflow.platform.warehousing.domain.repository.WhseProfileRepository;

/**
 * Creates put-away tasks for stock that has landed on a dock or staging location, for every warehousing tenant,
 * so the worklist is current without anyone opening the screen. One tenant failing does not stop the others.
 * Safe to run at any time: stock already covered by an open task is skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhsePutawaySyncScheduler {

    private final WhseProfileRepository profileRepository;
    private final WhseTaskService taskService;

    @Scheduled(fixedDelayString = "${handyflow.warehousing.putaway-sync-ms:300000}",
            initialDelayString = "${handyflow.warehousing.putaway-sync-initial-ms:60000}")
    public void syncAll() {
        for (WhseProfile profile : profileRepository.findAll()) {
            try {
                int created = taskService.syncPutaway(TenantId.of(profile.getTenantId()));
                if (created > 0) {
                    log.info("[Warehousing] Put-away sync created {} task(s) tenant={}", created, profile.getTenantId());
                }
            } catch (Exception e) {
                log.error("[Warehousing] Put-away sync failed tenant={}: {}", profile.getTenantId(), e.getMessage(), e);
            }
        }
    }
}
