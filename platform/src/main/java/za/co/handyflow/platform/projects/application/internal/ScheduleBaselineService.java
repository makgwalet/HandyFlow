package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.ProjectTask;
import za.co.handyflow.platform.projects.domain.repository.ProjectTaskRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Snapshots every task's planned dates as its baseline when the project baseline is locked
 * (called by ProjectLifecycleService on APPROVE, or on first ACTIVATE from PLANNING).
 *
 * Only tasks WITHOUT a baseline are snapshotted, so calling it twice never overwrites an
 * existing baseline. Tasks created after the lock keep a null baseline — they are scope that
 * was added later, and the report shows them as having no baseline rather than inventing one.
 *
 * Re-baselining (versioned, approved through change control) is deliberately NOT here; it
 * belongs to the change-control chunk so an approved baseline is never silently overwritten.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleBaselineService {

    private final ProjectTaskRepository taskRepo;

    @Transactional
    public int snapshot(TenantId tenantId, UUID projectId) {
        List<ProjectTask> tasks = taskRepo.findByProject(projectId);
        List<ProjectTask> changed = tasks.stream()
                .filter(t -> tenantId.getValue().equals(t.getTenantId()))   // belt-and-braces tenant guard
                .filter(ProjectTask::snapshotBaseline)
                .toList();
        if (!changed.isEmpty()) taskRepo.saveAll(changed);
        log.info("Task baseline snapshot project={} tasks={}", projectId, changed.size());
        return changed.size();
    }
}