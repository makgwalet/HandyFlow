package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.*;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.TaskDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tasks on matters, and workflow templates that create a set of tasks. Ethical walls apply to every call: a person barred from a matter cannot see,
 * add to or change its tasks, and does not find them in the firm-wide or My tasks lists. Nothing is deleted; every change is written to the task's history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpTaskService {

    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private static final int EVENTS_SHOWN = 100;
    private static final UUID NOBODY = new UUID(0L, 0L);

    private final LpTaskRepository taskRepo;
    private final LpTaskEventRepository eventRepo;
    private final LpWorkflowTemplateRepository templateRepo;
    private final LpWorkflowStepRepository stepRepo;
    private final LpWorkflowRunRepository runRepo;
    private final LpMatterRepository matterRepo;
    private final LpWallGuard walls;

    private static LocalDate today() { return LocalDate.now(ZONE); }

    // ---- reading tasks -------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TaskSummary> forMatter(TenantId tenantId, UUID matterId) {
        walls.requireMatterAccess(tenantId, matterId);
        String label = label(findMatter(tenantId, matterId));
        LocalDate today = today();
        return taskRepo.findForMatter(tenantId, matterId).stream().map(t -> summary(t, label, today)).toList();
    }

    @Transactional(readOnly = true)
    public Page<TaskSummary> firmList(TenantId tenantId, UUID me, boolean mine, String search, String status, boolean includeClosed, boolean overdueOnly, Pageable pageable) {
        var barred = walls.barred(tenantId);
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "");
        LocalDate today = today();
        Page<LpTask> page = taskRepo.findFirmExcluding(tenantId, barred.matterIds(), barred.clientIds(), mine, mine ? me : NOBODY, includeClosed, status == null ? "" : status, overdueOnly, today, "%" + q + "%", pageable);
        Map<UUID, String> labels = labels(tenantId, page.getContent().stream().map(LpTask::getMatterId).collect(Collectors.toSet()));
        return page.map(t -> summary(t, labels.getOrDefault(t.getMatterId(), ""), today));
    }

    @Transactional(readOnly = true)
    public MyTasks myOverdue(TenantId tenantId, UUID me) {
        var barred = walls.barred(tenantId);
        return new MyTasks(taskRepo.countOverdueFor(tenantId, barred.matterIds(), barred.clientIds(), me, today()));
    }

    @Transactional(readOnly = true)
    public TaskDetail get(TenantId tenantId, UUID id) { return detail(find(tenantId, id)); }

    // ---- working a task ------------------------------------------------------------------------------------------------

    @Transactional
    public TaskDetail create(TenantId tenantId, CreateTaskRequest r, UUID by, String byName) {
        if (r.matterId() == null) throw new IllegalArgumentException("Choose the matter");
        walls.requireMatterAccess(tenantId, r.matterId());
        LpMatter matter = findLiveMatter(tenantId, r.matterId());
        LpTask t = LpTask.create(tenantId, matter.getId(), matter.getClientId(), r.title(), r.description(), r.priority() == null ? "NORMAL" : r.priority(), r.dueDate(),
                r.assigneeId(), r.assigneeName(), null, by, byName, Instant.now());
        taskRepo.save(t);
        event(tenantId, t, "CREATED", by, byName, t.getTitle() + (t.getAssigneeName() == null ? "" : " (assigned to " + t.getAssigneeName() + ")"));
        return detail(t);
    }

    @Transactional
    public TaskDetail edit(TenantId tenantId, UUID id, EditTaskRequest r, UUID by, String byName) {
        LpTask t = find(tenantId, id);
        t.edit(r.title(), r.description(), r.priority() == null ? t.getPriority() : r.priority(), r.dueDate(), Instant.now());
        taskRepo.save(t);
        event(tenantId, t, "EDITED", by, byName, null);
        return detail(t);
    }

    @Transactional
    public TaskDetail assign(TenantId tenantId, UUID id, AssignRequest r, UUID by, String byName) {
        LpTask t = find(tenantId, id);
        String was = t.reassign(r.assigneeId(), r.assigneeName(), Instant.now());
        taskRepo.save(t);
        event(tenantId, t, "REASSIGNED", by, byName, (was == null ? "Unassigned" : was) + " to " + (t.getAssigneeName() == null ? "nobody" : t.getAssigneeName()));
        return detail(t);
    }

    @Transactional
    public TaskDetail start(TenantId tenantId, UUID id, UUID by, String byName) {
        LpTask t = find(tenantId, id);
        t.start(Instant.now());
        taskRepo.save(t);
        event(tenantId, t, "STARTED", by, byName, null);
        return detail(t);
    }

    @Transactional
    public TaskDetail stop(TenantId tenantId, UUID id, UUID by, String byName) {
        LpTask t = find(tenantId, id);
        t.stop(Instant.now());
        taskRepo.save(t);
        event(tenantId, t, "STOPPED", by, byName, null);
        return detail(t);
    }

    @Transactional
    public TaskDetail complete(TenantId tenantId, UUID id, UUID by, String byName) {
        LpTask t = find(tenantId, id);
        t.complete(by, byName, Instant.now());
        taskRepo.save(t);
        event(tenantId, t, "COMPLETED", by, byName, null);
        return detail(t);
    }

    @Transactional
    public TaskDetail reopen(TenantId tenantId, UUID id, ReasonRequest r, UUID by, String byName) {
        LpTask t = find(tenantId, id);
        t.reopen(r.reason(), Instant.now());
        taskRepo.save(t);
        event(tenantId, t, "REOPENED", by, byName, r.reason().trim());
        return detail(t);
    }

    @Transactional
    public TaskDetail cancel(TenantId tenantId, UUID id, ReasonRequest r, UUID by, String byName) {
        LpTask t = find(tenantId, id);
        t.cancel(r.reason(), Instant.now());
        taskRepo.save(t);
        event(tenantId, t, "CANCELLED", by, byName, r.reason().trim());
        return detail(t);
    }

    // ---- workflow templates --------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TemplateSummary> templates(TenantId tenantId, boolean includeOff) {
        return templateRepo.findAllForTenant(tenantId, includeOff).stream().map(t -> templateSummary(t, stepRepo.findForTemplate(tenantId, t.getId()).size())).toList();
    }

    @Transactional(readOnly = true)
    public TemplateDetail template(TenantId tenantId, UUID id) { return templateDetail(findTemplate(tenantId, id)); }

    @Transactional
    public TemplateDetail createTemplate(TenantId tenantId, TemplateRequest r, UUID by, String byName) {
        List<TaskRules.Step> steps = steps(r);
        LpWorkflowTemplate t = LpWorkflowTemplate.create(tenantId, r.name(), r.description(), r.matterType(), by, byName, Instant.now());
        requireNameFree(tenantId, t.getName(), t.getId());
        templateRepo.save(t);
        saveSteps(tenantId, t.getId(), steps);
        return templateDetail(t);
    }

    @Transactional
    public TemplateDetail updateTemplate(TenantId tenantId, UUID id, TemplateRequest r, UUID by, String byName) {
        LpWorkflowTemplate t = findTemplate(tenantId, id);
        List<TaskRules.Step> steps = steps(r);
        t.edit(r.name(), r.description(), r.matterType(), Instant.now());
        if (t.isActive()) requireNameFree(tenantId, t.getName(), t.getId());
        templateRepo.save(t);
        stepRepo.deleteForTemplate(tenantId, t.getId());
        saveSteps(tenantId, t.getId(), steps);
        return templateDetail(t);
    }

    @Transactional
    public TemplateDetail setTemplateActive(TenantId tenantId, UUID id, boolean on) {
        LpWorkflowTemplate t = findTemplate(tenantId, id);
        if (on) requireNameFree(tenantId, t.getName(), t.getId());
        t.setActive(on, Instant.now());
        templateRepo.save(t);
        return templateDetail(t);
    }

    @Transactional
    public ApplyResult apply(TenantId tenantId, UUID templateId, ApplyRequest r, UUID by, String byName) {
        if (r.matterId() == null) throw new IllegalArgumentException("Choose the matter");
        walls.requireMatterAccess(tenantId, r.matterId());
        LpWorkflowTemplate tpl = findTemplate(tenantId, templateId);
        if (!tpl.isActive()) throw new IllegalStateException("This workflow is turned off");
        LpMatter matter = findLiveMatter(tenantId, r.matterId());
        LocalDate today = today();
        String problem = TaskRules.applyProblem(r.startDate(), today);
        if (problem != null) throw new IllegalArgumentException(problem);
        if (runRepo.findOne(tenantId, matter.getId(), tpl.getId()).isPresent()) throw new IllegalStateException("This workflow has already been applied to this matter");
        List<LpWorkflowStep> steps = stepRepo.findForTemplate(tenantId, tpl.getId());
        if (steps.isEmpty()) throw new IllegalStateException("This workflow has no steps");
        Instant now = Instant.now();
        LpWorkflowRun run = runRepo.save(LpWorkflowRun.of(tenantId, tpl.getId(), tpl.getName(), matter.getId(), r.startDate(), steps.size(), by, byName, now));
        List<TaskSummary> made = new ArrayList<>();
        int offDays = 0;
        String label = label(matter);
        for (LpWorkflowStep s : steps) {
            LocalDate due = TaskRules.dueFor(r.startDate(), s.getOffsetDays());
            boolean mine = "STARTER".equals(s.getAssignTo());
            LpTask t = LpTask.create(tenantId, matter.getId(), matter.getClientId(), s.getTitle(), s.getDescription(), s.getPriority(), due, mine ? by : null, mine ? byName : null, run.getId(), by, byName, now);
            taskRepo.save(t);
            event(tenantId, t, "CREATED", by, byName, "From workflow " + tpl.getName() + ", step " + s.getStepNo());
            made.add(summary(t, label, today));
            if (!LitigationRules.dateWarnings(due).isEmpty()) offDays++;
        }
        List<String> warnings = new ArrayList<>();
        if (offDays > 0) warnings.add(offDays + (offDays == 1 ? " task falls" : " tasks fall") + " on a weekend or public holiday");
        if (tpl.getMatterType() != null && !tpl.getMatterType().equalsIgnoreCase(matter.getMatterType())) warnings.add("This workflow is meant for " + tpl.getMatterType() + " matters. This matter is " + matter.getMatterType());
        log.info("Applied workflow={} to matter={} tenant={} tasks={}", tpl.getId(), matter.getId(), tenantId, steps.size());
        return new ApplyResult(runDto(run), made, warnings);
    }

    @Transactional(readOnly = true)
    public List<RunResponse> runsForMatter(TenantId tenantId, UUID matterId) {
        walls.requireMatterAccess(tenantId, matterId);
        findMatter(tenantId, matterId);
        return runRepo.findForMatter(tenantId, matterId).stream().map(LpTaskService::runDto).toList();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------

    private List<TaskRules.Step> steps(TemplateRequest r) {
        List<TaskRules.Step> steps = new ArrayList<>();
        if (r.steps() != null) for (StepRequest s : r.steps()) steps.add(new TaskRules.Step(s.title(), s.description(), s.priority() == null ? "NORMAL" : s.priority(), s.offsetDays() == null ? 0 : s.offsetDays(), s.assignTo() == null ? "STARTER" : s.assignTo()));
        String problem = TaskRules.templateProblem(r.name(), r.description(), r.matterType());
        if (problem == null) problem = TaskRules.stepsProblem(steps);
        if (problem != null) throw new IllegalArgumentException(problem);
        return steps;
    }

    private void saveSteps(TenantId tenantId, UUID templateId, List<TaskRules.Step> steps) {
        int no = 1;
        for (TaskRules.Step s : steps) stepRepo.save(LpWorkflowStep.of(tenantId, templateId, no++, s.title(), s.description(), s.priority(), s.offsetDays(), s.assignTo()));
    }

    private void requireNameFree(TenantId tenantId, String name, UUID exceptId) {
        if (!templateRepo.findActiveByName(tenantId, name.trim().toLowerCase(Locale.ROOT), exceptId).isEmpty()) throw new IllegalStateException("There is already a workflow with that name");
    }

    private LpTask find(TenantId tenantId, UUID id) {
        LpTask t = taskRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("LpTask", id.toString()));
        walls.requireMatterAccess(tenantId, t.getMatterId());
        return t;
    }

    private LpWorkflowTemplate findTemplate(TenantId tenantId, UUID id) {
        return templateRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("LpWorkflowTemplate", id.toString()));
    }

    private LpMatter findMatter(TenantId tenantId, UUID matterId) {
        return matterRepo.findActiveById(tenantId, matterId).orElseThrow(() -> new ResourceNotFoundException("LpMatter", matterId.toString()));
    }

    private LpMatter findLiveMatter(TenantId tenantId, UUID matterId) {
        LpMatter m = findMatter(tenantId, matterId);
        if ("CLOSED".equals(m.getStatus()) || "ARCHIVED".equals(m.getStatus())) throw new IllegalStateException("This matter is " + m.getStatus().toLowerCase() + ". Reopen it to add tasks");
        return m;
    }

    private static String label(LpMatter m) { return m.getMatterNumber() + " " + m.getMatterName(); }

    private Map<UUID, String> labels(TenantId tenantId, Set<UUID> matterIds) {
        Map<UUID, String> out = new HashMap<>();
        if (matterIds.isEmpty()) return out;
        for (LpMatter m : matterRepo.findAllById(matterIds)) if (m.getTenantId().equals(tenantId)) out.put(m.getId(), label(m));
        return out;
    }

    private void event(TenantId tenantId, LpTask t, String type, UUID by, String byName, String detail) {
        eventRepo.save(LpTaskEvent.record(tenantId, t.getId(), t.getMatterId(), type, by, byName, detail, Instant.now()));
    }

    private static TaskSummary summary(LpTask t, String matterLabel, LocalDate today) {
        return new TaskSummary(t.getId(), t.getMatterId(), matterLabel, t.getClientId(), t.getTitle(), t.getPriority(), t.getStatus(), t.getAssigneeId(), t.getAssigneeName(),
                t.getDueDate(), TaskRules.overdue(t.getStatus(), t.getDueDate(), today), t.getWorkflowRunId(), t.getUpdatedAt());
    }

    private TaskDetail detail(LpTask t) {
        TenantId tn = t.getTenantId();
        String label = label(findMatter(tn, t.getMatterId()));
        return new TaskDetail(summary(t, label, today()), t.getDescription(), t.getCompletedAt(), t.getCompletedByName(), t.getCancelReason(), t.getCreatedByName(), t.getCreatedAt(),
                eventRepo.findRecentForTask(tn, t.getId(), PageRequest.of(0, EVENTS_SHOWN)).stream().map(e -> new EventResponse(e.getEventType(), e.getActorName(), e.getDetail(), e.getOccurredAt())).toList());
    }

    private static TemplateSummary templateSummary(LpWorkflowTemplate t, int steps) {
        return new TemplateSummary(t.getId(), t.getName(), t.getDescription(), t.getMatterType(), t.isActive(), steps, t.getUpdatedAt());
    }

    private TemplateDetail templateDetail(LpWorkflowTemplate t) {
        List<StepResponse> steps = stepRepo.findForTemplate(t.getTenantId(), t.getId()).stream()
                .map(s -> new StepResponse(s.getStepNo(), s.getTitle(), s.getDescription(), s.getPriority(), s.getOffsetDays(), s.getAssignTo())).toList();
        return new TemplateDetail(templateSummary(t, steps.size()), t.getCreatedByName(), steps);
    }

    private static RunResponse runDto(LpWorkflowRun r) {
        return new RunResponse(r.getId(), r.getTemplateId(), r.getTemplateName(), r.getStartDate(), r.getTasksCreated(), r.getAppliedByName(), r.getAppliedAt());
    }
}
