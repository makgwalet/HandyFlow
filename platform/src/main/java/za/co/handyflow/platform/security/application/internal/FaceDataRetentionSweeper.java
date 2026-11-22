package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;

/**
 * Daily backstop: deletes the face template of every guard who has left (terminated or removed). The status change
 * and delete paths already clear it at the time; this catches templates stored before that rule existed and anything
 * set by another route (a direct status edit, an import).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FaceDataRetentionSweeper {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SecurityJobRunner jobs;

    private final GuardRepository guardRepository;

    @Scheduled(cron = "0 30 2 * * *", zone = "Africa/Johannesburg")
    void sweep() {
        SecurityJobRunner.exclusively(jobs, "security-face-retention", java.time.Duration.ofMinutes(20), this::sweepNow);
    }

    int sweepNow() {
        try {
            Integer n = SecurityJobRunner.txGet(jobs, false, guardRepository::wipeFaceDataOfLeavers);
            int cleared = n == null ? 0 : n;
            if (cleared > 0) log.info("[Security] Face retention: cleared {} templates of guards who have left", cleared);
            return cleared;
        } catch (Exception e) {
            log.error("[Security] Face retention sweep failed: {}", e.getMessage(), e);
            return 0;
        }
    }
}
