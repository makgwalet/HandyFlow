package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectActionItem;
import za.co.handyflow.platform.projects.domain.model.ProjectIssue;
import za.co.handyflow.platform.projects.domain.model.ProjectMember;
import za.co.handyflow.platform.projects.domain.repository.ProjectActionItemRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectIssueRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRiskRepository;
import za.co.handyflow.platform.projects.dto.IssueDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The issues register and action items. An issue can come from a risk (same project). Owners must be project members.
 * Action items hang off a risk or an issue in the same project. Closed or cancelled projects are read-only.
 */
@Service
@RequiredArgsConstructor
public class ProjectIssueService {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ProjectRepository projects;
    private final ProjectIssueRepository issues;
    private final ProjectActionItemRepository actions;
    private final ProjectRiskRepository risks;
    private final ProjectMemberService members;
    private final SequenceService sequences;

    static LocalDate today() { return LocalDate.now(SAST); }

    // ── Issues ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<IssueResponse> list(TenantId t, UUID projectId) {
        project(t, projectId);
        Map<UUID, Long> open = actions.findByProject(t.getValue(), projectId).stream()
                .filter(a -> "ISSUE".equals(a.getParentType()) && a.isOpen())
                .collect(Collectors.groupingBy(ProjectActionItem::getParentId, Collectors.counting()));
        LocalDate today = today();
        return issues.findByProject(t.getValue(), projectId).stream()
                .map(i -> IssueResponse.of(i, today, open.getOrDefault(i.getId(), 0L).intValue())).toList();
    }

    @Transactional
    public IssueResponse raise(TenantId t, UUID projectId, CreateIssueRequest req, UUID by, String byName) {
        editable(project(t, projectId));
        if (req.riskId() != null) {
            boolean sameProject = risks.findByTenantAndId(t.getValue(), req.riskId()).map(r -> projectId.equals(r.getProjectId())).orElse(false);
            if (!sameProject) throw bad("The risk is not on this project", "RISK_NOT_IN_PROJECT");
        }
        ProjectIssue i = ProjectIssue.raise(t.getValue(), projectId, sequences.nextIssueNumber(t.getValue(), projectId), req.title().trim(),
                blankToNull(req.description()), req.priority(), req.riskId(), req.dueDate(), by, byName);
        if (req.ownerId() != null) { ProjectMember m = member(t, projectId, req.ownerId()); i.assign(m.getUserId(), m.getUserName()); }
        return IssueResponse.of(issues.save(i), today(), 0);
    }

    @Transactional
    public IssueResponse update(TenantId t, UUID issueId, UpdateIssueRequest req) {
        ProjectIssue i = issue(t, issueId);
        editable(project(t, i.getProjectId()));
        i.edit(req.title(), req.description(), req.priority(), req.dueDate());
        if (Boolean.TRUE.equals(req.clearOwner())) i.assign(null, null);
        else if (req.ownerId() != null) { ProjectMember m = member(t, i.getProjectId(), req.ownerId()); i.assign(m.getUserId(), m.getUserName()); }
        return IssueResponse.of(issues.save(i), today(), openActions(t, i));
    }

    @Transactional
    public IssueResponse act(TenantId t, UUID issueId, IssueActionRequest req, String byName) {
        ProjectIssue i = issue(t, issueId);
        editable(project(t, i.getProjectId()));
        switch (req.action()) {
            case "START"   -> i.start();
            case "RESOLVE" -> i.resolve(req.resolution(), byName);
            case "CLOSE"   -> i.close();
            case "REOPEN"  -> i.reopen();
            default        -> throw bad("Unknown action", "BAD_REQUEST");
        }
        return IssueResponse.of(issues.save(i), today(), openActions(t, i));
    }

    // ── Action items ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ActionItemResponse> actions(TenantId t, UUID projectId) {
        project(t, projectId);
        LocalDate today = today();
        return actions.findByProject(t.getValue(), projectId).stream().map(a -> ActionItemResponse.of(a, today)).toList();
    }

    @Transactional
    public ActionItemResponse addAction(TenantId t, UUID projectId, CreateActionItemRequest req, UUID by, String byName) {
        editable(project(t, projectId));
        boolean parentHere = "RISK".equals(req.parentType())
                ? risks.findByTenantAndId(t.getValue(), req.parentId()).map(r -> projectId.equals(r.getProjectId())).orElse(false)
                : issues.findByTenantAndId(t.getValue(), req.parentId()).map(i -> projectId.equals(i.getProjectId())).orElse(false);
        if (!parentHere) throw bad("That " + req.parentType().toLowerCase() + " is not on this project", "PARENT_NOT_IN_PROJECT");
        UUID ownerId = null; String ownerName = null;
        if (req.ownerId() != null) { ProjectMember m = member(t, projectId, req.ownerId()); ownerId = m.getUserId(); ownerName = m.getUserName(); }
        ProjectActionItem a = ProjectActionItem.create(t.getValue(), projectId, req.parentType(), req.parentId(), req.title(),
                ownerId, ownerName, req.dueDate(), by, byName);
        return ActionItemResponse.of(actions.save(a), today());
    }

    @Transactional
    public ActionItemResponse updateAction(TenantId t, UUID actionId, UpdateActionItemRequest req) {
        ProjectActionItem a = action(t, actionId);
        editable(project(t, a.getProjectId()));
        a.edit(req.title(), req.dueDate());
        if (Boolean.TRUE.equals(req.clearOwner())) a.assign(null, null);
        else if (req.ownerId() != null) { ProjectMember m = member(t, a.getProjectId(), req.ownerId()); a.assign(m.getUserId(), m.getUserName()); }
        return ActionItemResponse.of(actions.save(a), today());
    }

    @Transactional
    public ActionItemResponse actOnAction(TenantId t, UUID actionId, ActionItemActionRequest req, String byName) {
        ProjectActionItem a = action(t, actionId);
        editable(project(t, a.getProjectId()));
        switch (req.action()) {
            case "DONE"   -> a.markDone(byName);
            case "CANCEL" -> a.cancel();
            case "REOPEN" -> a.reopen();
            default       -> throw bad("Unknown action", "BAD_REQUEST");
        }
        return ActionItemResponse.of(actions.save(a), today());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private int openActions(TenantId t, ProjectIssue i) {
        return (int) actions.findByProject(t.getValue(), i.getProjectId()).stream()
                .filter(a -> "ISSUE".equals(a.getParentType()) && i.getId().equals(a.getParentId()) && a.isOpen()).count();
    }

    private Project project(TenantId t, UUID projectId) {
        return projects.findByTenantAndId(t.getValue(), projectId).orElseThrow(() -> notFound("Project"));
    }

    private ProjectIssue issue(TenantId t, UUID id) { return issues.findByTenantAndId(t.getValue(), id).orElseThrow(() -> notFound("Issue")); }

    private ProjectActionItem action(TenantId t, UUID id) { return actions.findByTenantAndId(t.getValue(), id).orElseThrow(() -> notFound("Action item")); }

    private ProjectMember member(TenantId t, UUID projectId, UUID userId) {
        return members.find(t, projectId, userId).orElseThrow(() -> bad("Only a member of the project can be the owner", "OWNER_NOT_MEMBER"));
    }

    private static void editable(Project p) {
        if (!p.getStatus().isEditable())
            throw new HandyFlowException("Project is " + p.getStatus() + " and can no longer be modified", HttpStatus.CONFLICT, "PROJECT_NOT_EDITABLE");
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static HandyFlowException bad(String m, String code) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, code); }
    private static HandyFlowException notFound(String what) { return new HandyFlowException(what + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND"); }
}
