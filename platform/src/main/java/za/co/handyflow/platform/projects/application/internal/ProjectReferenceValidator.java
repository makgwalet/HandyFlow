package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.projects.domain.repository.ProjectPhaseRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectTaskRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Guards "loose reference" columns (phase_id, task_id, parent_task_id) that have
 * no enforced relationship in the request DTOs.
 *
 * WHY: request bodies carry raw UUIDs. Without this check a caller could attach a
 * task to ANOTHER project's phase — or another tenant's — and the row would be
 * saved, silently corrupting roll-ups and leaking structure across tenants.
 *
 * "Not found" and "belongs elsewhere" return the SAME message on purpose, so an
 * attacker can't use the difference to discover which IDs exist in other tenants.
 * A null reference is always allowed (the field is optional).
 */
@Component
@RequiredArgsConstructor
public class ProjectReferenceValidator {

    private final ProjectPhaseRepository phaseRepo;
    private final ProjectTaskRepository  taskRepo;

    public void requirePhaseInProject(TenantId tenantId, UUID projectId, UUID phaseId) {
        if (phaseId == null) return;
        boolean ok = phaseRepo.findByTenantAndId(tenantId.getValue(), phaseId)
                .map(p -> projectId.equals(p.getProjectId()))
                .orElse(false);
        if (!ok) throw invalid("Phase");
    }

    public void requireTaskInProject(TenantId tenantId, UUID projectId, UUID taskId) {
        if (taskId == null) return;
        boolean ok = taskRepo.findByTenantAndId(tenantId.getValue(), taskId)
                .map(t -> projectId.equals(t.getProjectId()))
                .orElse(false);
        if (!ok) throw invalid("Task");
    }

    private static HandyFlowException invalid(String what) {
        return new HandyFlowException(
                what + " not found in this project", HttpStatus.BAD_REQUEST, "REFERENCE_MISMATCH");
    }
}