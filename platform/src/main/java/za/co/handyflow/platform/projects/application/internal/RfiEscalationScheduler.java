package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.projects.application.internal.rfi.RfiRules;
import za.co.handyflow.platform.projects.domain.model.ProjectRfi;
import za.co.handyflow.platform.projects.domain.repository.ProjectRfiRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Daily sweep (07:30 SAST) for unanswered RFIs past their due date: level 1 at a day overdue, 2 at a
 * week, 3 at two weeks — each level notifies once (see RfiEscalationWorker). Only RFIs on ACTIVE or
 * ON_HOLD projects are considered.
 *
 * Disable with app.projects.rfi.overdue-escalation=false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RfiEscalationScheduler {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ProjectRfiRepository rfiRepo;
    private final RfiEscalationWorker  worker;

    @Value("${app.projects.rfi.overdue-escalation:true}")
    private boolean enabled;

    @Scheduled(cron = "0 30 7 * * *", zone = "Africa/Johannesburg")
    public void escalateOverdueRfis() {
        if (!enabled) return;
        LocalDate today = LocalDate.now(SAST);

        List<ProjectRfi> candidates = rfiRepo.findOverdueForEscalation(today);
        int escalated = 0, failed = 0;
        for (ProjectRfi rfi : candidates) {
            int level = RfiRules.escalationLevel(rfi.getDueDate(), today);
            if (level <= rfi.getEscalationLevel()) continue;
            try {
                if (worker.escalate(rfi.getId(), level, RfiRules.daysOverdue(rfi.getDueDate(), today))) escalated++;
            } catch (Exception e) {
                failed++;
                log.error("RFI escalation failed for rfi={}: {}", rfi.getId(), e.getMessage());
            }
        }
        log.info("RFI escalation sweep: {} candidate(s), {} escalated, {} failed", candidates.size(), escalated, failed);
    }
}