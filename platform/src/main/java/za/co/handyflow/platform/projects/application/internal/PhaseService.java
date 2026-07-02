package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.ProjectPhase;
import za.co.handyflow.platform.projects.domain.repository.ProjectPhaseRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.dto.CreatePhaseRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Project phase management.
 *
 * CHANGE FROM ORIGINAL — sort_order race condition
 * ─────────────────────────────────────────────────
 * The original code used:
 *   int nextOrder = phaseRepo.findMaxSortOrder(projectId) + 1;
 *
 * This is the same MAX + 1 race condition as project numbering.  Two concurrent
 * "Add Phase" requests both read the same MAX, both compute the same nextOrder,
 * and both write a phase with the same sort_order value.  The UI then renders
 * two phases in an arbitrary order.
 *
 * Unlike project_number, sort_order has no UNIQUE constraint — so the DB does
 * not catch this.  Two phases with sort_order = 3 will render inconsistently
 * depending on which row the DB returns first.
 *
 * FIX: SequenceService.nextSortOrder() uses the atomic pm_counters upsert to
 * guarantee a unique, monotonically increasing sort_order per project.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhaseService {

    private final ProjectPhaseRepository phaseRepo;
    private final ProjectRepository      projectRepo;
    private final SequenceService        sequenceService;

    @Transactional(readOnly = true)
    public List<ProjectPhase> getPhases(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        return phaseRepo.findByProject(projectId);
    }

    @Transactional
    public ProjectPhase createPhase(TenantId tenantId, UUID projectId, CreatePhaseRequest req) {
        verifyProject(tenantId, projectId);

        /*
         * FIX: was phaseRepo.findMaxSortOrder(projectId) + 1 — race condition.
         * SequenceService uses pm_counters with an atomic PostgreSQL upsert,
         * so two concurrent createPhase calls always get distinct sort_order values.
         *
         * If the caller explicitly supplies a sort_order (e.g. "insert between
         * phase 2 and phase 3"), we honour that and skip the counter.
         */
        // sortOrder() returns primitive int — cannot be null.
        // 0 (the default) means "auto-assign"; any positive value is an explicit override.
        int sortOrder = req.sortOrder() > 0
                ? req.sortOrder()
                : sequenceService.nextSortOrder(tenantId.getValue(), projectId, "PHASE");

        ProjectPhase phase = ProjectPhase.create(
                tenantId.getValue(), projectId,
                req.name(), req.description(), sortOrder,
                req.startDate(), req.endDate());

        phaseRepo.save(phase);
        log.info("Created phase='{}' sortOrder={} project={}", req.name(), sortOrder, projectId);
        return phase;
    }

    @Transactional
    public ProjectPhase updatePhase(TenantId tenantId, UUID phaseId, CreatePhaseRequest req) {
        ProjectPhase phase = find(tenantId, phaseId);
        if (req.name()        != null) phase.setName(req.name());
        if (req.description() != null) phase.setDescription(req.description());
        if (req.startDate()   != null) phase.setStartDate(req.startDate());
        if (req.endDate()     != null) phase.setEndDate(req.endDate());
        if (req.sortOrder() > 0) phase.setSortOrder(req.sortOrder());
        return phaseRepo.save(phase);
    }

    @Transactional
    public ProjectPhase updatePhaseStatus(TenantId tenantId, UUID phaseId, String action) {
        ProjectPhase phase = find(tenantId, phaseId);
        switch (action.toUpperCase()) {
            case "START"    -> phase.start();
            case "COMPLETE" -> phase.complete();
            case "SKIP"     -> phase.skip();
            default -> throw new HandyFlowException(
                    "Unknown phase action: " + action, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        }
        return phaseRepo.save(phase);
    }

    @Transactional
    public void deletePhase(TenantId tenantId, UUID phaseId) {
        ProjectPhase phase = find(tenantId, phaseId);
        phaseRepo.delete(phase);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProjectPhase find(TenantId tenantId, UUID id) {
        return phaseRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> notFound("Phase"));
    }

    private void verifyProject(TenantId tenantId, UUID projectId) {
        projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> notFound("Project"));
    }

    private HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}