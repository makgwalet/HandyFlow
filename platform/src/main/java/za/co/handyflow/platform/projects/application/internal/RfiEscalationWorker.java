package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectRfi;
import za.co.handyflow.platform.projects.domain.model.ProjectRfiEvent;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRfiEventRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRfiRepository;

import java.util.UUID;

/**
 * Escalates ONE overdue RFI. A separate bean (not a method on the scheduler) so each RFI gets its
 * own transaction through the Spring proxy — one failure can't undo the others.
 *
 * The level is raised by an atomic conditional UPDATE; only the caller that actually moved it gets 1
 * back and goes on to record the event and notify. If several application instances run the sweep at
 * once, exactly one of them sends each notification.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RfiEscalationWorker {

    private final ProjectRfiRepository      rfiRepo;
    private final ProjectRfiEventRepository eventRepo;
    private final ProjectRepository         projectRepo;
    private final RfiNotificationService    notifications;

    @Transactional
    public boolean escalate(UUID rfiId, int level, int daysOverdue) {
        if (rfiRepo.raiseEscalationLevel(rfiId, level) == 0) return false;   // someone else got there first

        ProjectRfi rfi = rfiRepo.findById(rfiId).orElse(null);
        if (rfi == null) return false;
        String projectName = projectRepo.findById(rfi.getProjectId()).map(Project::getName).orElse("(project)");

        eventRepo.save(ProjectRfiEvent.record(rfi.getTenantId(), rfi.getProjectId(), rfi.getId(), "ESCALATED",
                "Level " + level + ": " + daysOverdue + " day(s) overdue", null, "System"));
        notifications.notifyOverdue(rfi.getTenantId(), projectName, rfi.getRfiNumber(), rfi.getTitle(),
                rfi.getDueDate(), daysOverdue, level, rfi.getAssignedToId());
        log.info("RFI {} escalated to level {} ({} day(s) overdue)", rfi.getRfiNumber(), level, daysOverdue);
        return true;
    }
}