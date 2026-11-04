package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.application.ContractSummary;
import za.co.handyflow.platform.contracting.application.ContractingFacade;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Contracts that belong to a project (the client contract, subcontracts, supply and hire agreements), through Contracting's
 * public facade. Contracting owns the contracts; this only links them (ref_type PROJECT) and starts drafts.
 */
@Service
@RequiredArgsConstructor
public class ProjectContractService {

    public static final String REF_TYPE = "PROJECT";
    static final Set<String> TYPES = Set.of("SUBCONTRACTOR", "SUPPLY", "CONSULTING", "EQUIPMENT_HIRE", "SERVICE_LEVEL", "JOINT_VENTURE", "NDA");

    private final ContractingFacade contracting;
    private final ProjectRepository projects;
    private final ProjectActivityService activity;

    @Transactional(readOnly = true)
    public List<ContractSummary> forProject(TenantId t, UUID projectId) {
        project(t, projectId);
        return contracting.findByReference(t, REF_TYPE, projectId);
    }

    @Transactional(readOnly = true)
    public List<ContractSummary> linkable(TenantId t) { return contracting.listUnlinked(t); }

    @Transactional
    public ContractSummary link(TenantId t, UUID projectId, UUID contractId) {
        Project p = editable(t, projectId);
        ContractSummary c = contracting.linkToReference(t, contractId, REF_TYPE, projectId);
        log(t, p, "CONTRACT_LINKED", "Contract " + c.contractNumber() + " linked: " + c.title());
        return c;
    }

    @Transactional
    public void unlink(TenantId t, UUID projectId, UUID contractId) {
        Project p = editable(t, projectId);
        String label = contracting.findById(t, contractId).map(c -> c.contractNumber() + ": " + c.title()).orElse(contractId.toString());
        contracting.unlinkFromReference(t, contractId, REF_TYPE, projectId);
        log(t, p, "CONTRACT_UNLINKED", "Contract " + label + " unlinked");
    }

    /** A DRAFT contract for this project from Contracting's template of that type; finished and sent in Contracting. */
    @Transactional
    public ContractSummary draft(TenantId t, UUID projectId, String title, String type, BigDecimal value, LocalDate start, LocalDate end) {
        Project p = editable(t, projectId);
        if (!TYPES.contains(type)) throw new HandyFlowException("Choose one of: " + String.join(", ", TYPES), HttpStatus.BAD_REQUEST, "BAD_CONTRACT_TYPE");
        String name = title == null || title.isBlank() ? p.getName() + " — " + type.replace('_', ' ').toLowerCase() : title.trim();
        ContractSummary c = contracting.createDraft(t, new ContractingFacade.DraftContract(name, type,
                Map.of("project_name", p.getName(), "project_number", p.getProjectNumber(), "site_address", p.getSiteAddress() == null ? "" : p.getSiteAddress()),
                null, value, start != null ? start : p.getStartDate(), end != null ? end : p.getEndDate(),
                "Started from project " + p.getProjectNumber(), REF_TYPE, projectId, null), TenantContext.getCurrentUserId());
        log(t, p, "CONTRACT_LINKED", "Draft contract " + c.contractNumber() + " started: " + c.title());
        return c;
    }

    private void log(TenantId t, Project p, String kind, String summary) {
        activity.record(t.getValue(), p.getId(), kind, summary, null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
    }

    private Project project(TenantId t, UUID id) {
        return projects.findByTenantAndId(t.getValue(), id).orElseThrow(() -> new HandyFlowException("Project not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private Project editable(TenantId t, UUID id) {
        Project p = project(t, id);
        if (!p.getStatus().isEditable())
            throw new HandyFlowException("Project is " + p.getStatus() + " and can no longer be modified", HttpStatus.CONFLICT, "PROJECT_NOT_EDITABLE");
        return p;
    }
}
