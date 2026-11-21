package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Armoury;
import za.co.handyflow.platform.security.domain.repository.ArmouryRepository;

import java.time.Instant;

/**
 * A lost or stolen firearm must be reported to SAPS within 24 hours. This chases reports that are overdue
 * (no case number recorded): one alert when it becomes overdue, then a reminder every 24 hours.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SapsReportSweeper {

    /** Lease + per-tenant transactions (optional so hand-built instances in tests keep working). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SecurityJobRunner jobs;

    private final ArmouryRepository armouryRepository;
    private final SecurityAlertNotifier alerts;

    @Scheduled(cron = "0 */30 * * * *", zone = "Africa/Johannesburg")
    void sweep() {
        SecurityJobRunner.exclusively(jobs, "security-saps-overdue-sweep", java.time.Duration.ofMinutes(25), this::sweepNow);
    }

    void sweepNow() {
        try {
            SecurityJobRunner.tx(jobs, false, () -> sweepAt(Instant.now()));
        } catch (Exception e) {
            log.error("[Security] SAPS overdue sweep failed: {}", e.getMessage(), e);
        }
    }

    @Transactional
    int sweepAt(Instant now) {
        int n = 0;
        for (Armoury a : armouryRepository.findLostAwaitingSapsCase(now)) {
            if (!SapsChase.shouldAlert(now, a.getSapsReportDueAt(), a.getSapsCaseNumber(), a.getSapsOverdueAlertedAt())) continue;
            alerts.sapsOverdue(a.getTenantId(), a.getId(), a.getFirearmSerial());
            a.markSapsOverdueAlerted(now);
            armouryRepository.save(a);
            n++;
        }
        if (n > 0) log.warn("[Security] {} lost firearm(s) overdue for a SAPS report", n);
        return n;
    }
}
