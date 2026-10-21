package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.OnboardingDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Onboarding of new employees. Every new employee gets one, from the tenant's own task list (made from the standard list the
 * first time). Tasks that depend on the employee record are checked against it; the rest are ticked by a person or waived with
 * a reason. An onboarding is finished only when every required task is done or waived.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final HrOnboardingTemplateRepository templateRepo;
    private final HrOnboardingRepository onboardingRepo;
    private final HrOnboardingTaskRepository taskRepo;
    private final HrEmployeeRepository employeeRepo;
    private final EmployeeViewService viewService;

    // ── Starting ────────────────────────────────────────────────────────────────────────────────

    /** Starts the onboarding of an employee. If there already is one it is left as it is. */
    @Transactional
    public Detail startFor(TenantId tenantId, UUID employeeId, UUID actor, Set<String> held) {
        HrEmployee emp = employee(tenantId, employeeId);
        Optional<HrOnboarding> existing = onboardingRepo.findByEmployee(tenantId, employeeId);
        if (existing.isPresent()) return detail(tenantId, existing.get(), emp, held, actor);

        LocalDate start = emp.getStartDate() != null ? emp.getStartDate() : LocalDate.now();
        LocalDate probationEnd = OnboardingRules.probationEnd(start, OnboardingRules.DEFAULT_PROBATION_MONTHS);
        HrOnboarding o = onboardingRepo.save(HrOnboarding.start(tenantId.getValue(), employeeId, start, probationEnd, actor));

        int order = 0;
        for (HrOnboardingTemplate t : activeTemplates(tenantId)) {
            OnboardingRules.Spec s = t.toSpec();
            taskRepo.save(HrOnboardingTask.of(tenantId.getValue(), o.getId(), s,
                    OnboardingRules.dueDate(start, probationEnd, s.dueOffsetDays(), s.dueBasis()), order++));
        }
        log.info("Started onboarding for employee={} with {} tasks", emp.getEmployeeNumber(), order);
        return detail(tenantId, o, emp, held, actor);
    }

    /** The tenant's task list, made from the standard list if it has never been set up. */
    private List<HrOnboardingTemplate> activeTemplates(TenantId tenantId) {
        if (templateRepo.forTenant(tenantId).isEmpty()) seedTemplates(tenantId);
        return templateRepo.activeForTenant(tenantId);
    }

    private void seedTemplates(TenantId tenantId) {
        int order = 0;
        for (OnboardingRules.Spec s : OnboardingRules.defaults()) templateRepo.save(HrOnboardingTemplate.of(tenantId.getValue(), s, order++));
    }

    // ── Reading ─────────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Summary> list(TenantId tenantId, String status, Set<String> held, UUID actor) {
        List<HrOnboarding> all = onboardingRepo.forTenant(tenantId).stream()
                .filter(o -> status == null || status.isBlank() || status.equals(o.getStatus())).toList();
        if (HrAccess.teamOnly(held)) {
            Set<UUID> team = viewService.teamOf(tenantId, actor);
            all = all.stream().filter(o -> team.contains(o.getEmployeeId())).toList();
        }
        if (all.isEmpty()) return List.of();
        Map<UUID, HrEmployee> emps = employeeRepo.findAllById(all.stream().map(HrOnboarding::getEmployeeId).toList()).stream()
                .collect(Collectors.toMap(HrEmployee::getId, e -> e));
        Map<UUID, List<HrOnboardingTask>> tasks = taskRepo.forOnboardings(all.stream().map(HrOnboarding::getId).toList()).stream()
                .collect(Collectors.groupingBy(HrOnboardingTask::getOnboardingId));
        LocalDate today = LocalDate.now();
        List<Summary> out = new ArrayList<>();
        for (HrOnboarding o : all) {
            HrEmployee e = emps.get(o.getEmployeeId());
            if (e == null) continue;
            out.add(summary(o, e, tasks.getOrDefault(o.getId(), List.of()), today));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Detail get(TenantId tenantId, UUID id, Set<String> held, UUID actor) {
        HrOnboarding o = onboarding(tenantId, id);
        checkMayView(tenantId, o, held, actor);
        return detail(tenantId, o, employee(tenantId, o.getEmployeeId()), held, actor);
    }

    @Transactional(readOnly = true)
    public Optional<Detail> forEmployee(TenantId tenantId, UUID employeeId, Set<String> held, UUID actor) {
        Optional<HrOnboarding> o = onboardingRepo.findByEmployee(tenantId, employeeId);
        if (o.isEmpty()) return Optional.empty();
        checkMayView(tenantId, o.get(), held, actor);
        return Optional.of(detail(tenantId, o.get(), employee(tenantId, employeeId), held, actor));
    }

    private void checkMayView(TenantId tenantId, HrOnboarding o, Set<String> held, UUID actor) {
        if (HrAccess.teamOnly(held) && !viewService.teamOf(tenantId, actor).contains(o.getEmployeeId()))
            throw new AccessDeniedException("You can only see onboarding for the people in your own team.");
    }

    private Detail detail(TenantId tenantId, HrOnboarding o, HrEmployee emp, Set<String> held, UUID actor) {
        List<HrOnboardingTask> tasks = taskRepo.forOnboardings(List.of(o.getId()));
        LocalDate today = LocalDate.now();
        OnboardingRules.Facts facts = facts(emp, today);
        List<Task> views = tasks.stream().map(t -> task(t, facts, today)).toList();
        List<String> problems = OnboardingRules.finishProblems(views.stream()
                .map(t -> new OnboardingRules.TaskLite(t.title(), t.required(), t.status())).toList());
        return new Detail(summary(o, emp, tasks, today), o.getCancelReason(), views, problems, canManage(held));
    }

    private Summary summary(HrOnboarding o, HrEmployee e, List<HrOnboardingTask> tasks, LocalDate today) {
        OnboardingRules.Facts facts = facts(e, today);
        List<OnboardingRules.Item> items = tasks.stream()
                .map(t -> new OnboardingRules.Item(t.isRequired(), OnboardingRules.effective(t.getStatus(), t.getCheckKey(), facts), t.getDueDate())).toList();
        OnboardingRules.Progress p = OnboardingRules.progress(items, today);
        return new Summary(o.getId(), e.getId(), e.getEmployeeNumber(), e.getFullName(), e.getJobTitle(), e.getDepartment(),
                o.getStartDate(), o.getProbationEndDate(), o.getStatus(), p.total(), p.finished(), p.requiredOpen(), p.overdue(), p.percent());
    }

    private Task task(HrOnboardingTask t, OnboardingRules.Facts facts, LocalDate today) {
        String eff = OnboardingRules.effective(t.getStatus(), t.getCheckKey(), facts);
        return new Task(t.getId(), t.getTitle(), t.getDescription(), t.getCategory(), t.isRequired(), t.getDueDate(),
                OnboardingRules.isCheck(t.getCheckKey()), t.getCheckKey(), t.getAssigneeId(), eff,
                OnboardingRules.overdue(eff, t.getDueDate(), today), t.getNote(), t.getResolvedAt());
    }

    private static OnboardingRules.Facts facts(HrEmployee e, LocalDate today) {
        return OnboardingRules.facts(e.getIdNumber(), e.getTaxNumber(), e.getBankName(), e.getBankAccountNumber(), e.getBankBranchCode(),
                e.getGrossSalary(), e.getManagerId(), e.getUserId(), e.getEmergencyContactName(), e.getEmergencyContactPhone(), today);
    }

    // ── Working the tasks ───────────────────────────────────────────────────────────────────────

    @Transactional
    public Detail complete(TenantId tenantId, UUID taskId, String note, UUID actor, Set<String> held) {
        HrOnboardingTask t = task(tenantId, taskId);
        HrOnboarding o = openOnboarding(tenantId, t);
        mayWork(t, actor, held);
        HrEmployee emp = employee(tenantId, o.getEmployeeId());
        String eff = OnboardingRules.effective(t.getStatus(), t.getCheckKey(), facts(emp, LocalDate.now()));
        fail(OnboardingRules.completeProblem(t.getCheckKey(), eff, facts(emp, LocalDate.now())));
        t.complete(actor, note);
        taskRepo.save(t);
        return detail(tenantId, o, emp, held, actor);
    }

    @Transactional
    public Detail waive(TenantId tenantId, UUID taskId, String reason, UUID actor, Set<String> held) {
        HrOnboardingTask t = task(tenantId, taskId);
        HrOnboarding o = openOnboarding(tenantId, t);
        requireManage(held);
        HrEmployee emp = employee(tenantId, o.getEmployeeId());
        String eff = OnboardingRules.effective(t.getStatus(), t.getCheckKey(), facts(emp, LocalDate.now()));
        fail(OnboardingRules.waiveProblem(eff, reason));
        t.waive(actor, reason);
        taskRepo.save(t);
        return detail(tenantId, o, emp, held, actor);
    }

    @Transactional
    public Detail reopen(TenantId tenantId, UUID taskId, UUID actor, Set<String> held) {
        HrOnboardingTask t = task(tenantId, taskId);
        HrOnboarding o = openOnboarding(tenantId, t);
        requireManage(held);
        if (OnboardingRules.OPEN.equals(t.getStatus())) throw new IllegalStateException("This task is already open.");
        t.reopen();
        taskRepo.save(t);
        return detail(tenantId, o, employee(tenantId, o.getEmployeeId()), held, actor);
    }

    @Transactional
    public Detail assign(TenantId tenantId, UUID taskId, UUID assignee, UUID actor, Set<String> held) {
        HrOnboardingTask t = task(tenantId, taskId);
        HrOnboarding o = openOnboarding(tenantId, t);
        requireManage(held);
        t.assignTo(assignee);
        taskRepo.save(t);
        return detail(tenantId, o, employee(tenantId, o.getEmployeeId()), held, actor);
    }

    @Transactional
    public Detail setDue(TenantId tenantId, UUID taskId, LocalDate due, UUID actor, Set<String> held) {
        HrOnboardingTask t = task(tenantId, taskId);
        HrOnboarding o = openOnboarding(tenantId, t);
        requireManage(held);
        t.dueOn(due);
        taskRepo.save(t);
        return detail(tenantId, o, employee(tenantId, o.getEmployeeId()), held, actor);
    }

    @Transactional
    public Detail addTask(TenantId tenantId, UUID onboardingId, TaskRequest req, UUID actor, Set<String> held) {
        requireManage(held);
        HrOnboarding o = onboarding(tenantId, onboardingId);
        if (!OnboardingRules.IN_PROGRESS.equals(o.getStatus())) throw new IllegalStateException("This onboarding is " + o.getStatus().toLowerCase() + ".");
        String category = req.category() == null ? "INDUCTION" : req.category();
        fail(OnboardingRules.templateProblem(req.title(), category, OnboardingRules.START, null, 0));
        int next = taskRepo.forOnboardings(List.of(onboardingId)).size();
        HrOnboardingTask t = HrOnboardingTask.of(tenantId.getValue(), onboardingId,
                new OnboardingRules.Spec(req.title().trim(), req.description(), category, !Boolean.FALSE.equals(req.required()), 0, OnboardingRules.START, null),
                req.dueDate(), next);
        t.assignTo(req.assigneeId());
        taskRepo.save(t);
        return detail(tenantId, o, employee(tenantId, o.getEmployeeId()), held, actor);
    }

    @Transactional
    public Detail setProbation(TenantId tenantId, UUID onboardingId, LocalDate end, UUID actor, Set<String> held) {
        requireManage(held);
        HrOnboarding o = onboarding(tenantId, onboardingId);
        fail(OnboardingRules.probationProblem(o.getStartDate(), end));
        o.setProbationEndDate(end);
        onboardingRepo.save(o);
        return detail(tenantId, o, employee(tenantId, o.getEmployeeId()), held, actor);
    }

    @Transactional
    public Detail finish(TenantId tenantId, UUID onboardingId, UUID actor, Set<String> held) {
        requireManage(held);
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        HrOnboarding o = onboarding(tenantId, onboardingId);
        if (!OnboardingRules.IN_PROGRESS.equals(o.getStatus())) throw new IllegalStateException("This onboarding is already " + o.getStatus().toLowerCase() + ".");
        HrEmployee emp = employee(tenantId, o.getEmployeeId());
        OnboardingRules.Facts facts = facts(emp, LocalDate.now());
        List<String> problems = OnboardingRules.finishProblems(taskRepo.forOnboardings(List.of(o.getId())).stream()
                .map(t -> new OnboardingRules.TaskLite(t.getTitle(), t.isRequired(), OnboardingRules.effective(t.getStatus(), t.getCheckKey(), facts))).toList());
        if (!problems.isEmpty()) throw new IllegalStateException(problems.size() + " required task(s) are still open. " + problems.get(0));
        o.finish(actor);
        onboardingRepo.save(o);
        return detail(tenantId, o, emp, held, actor);
    }

    @Transactional
    public Detail cancel(TenantId tenantId, UUID onboardingId, String reason, UUID actor, Set<String> held) {
        requireManage(held);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give the reason for cancelling the onboarding.");
        HrOnboarding o = onboarding(tenantId, onboardingId);
        if (!OnboardingRules.IN_PROGRESS.equals(o.getStatus())) throw new IllegalStateException("This onboarding is already " + o.getStatus().toLowerCase() + ".");
        o.cancel(reason.trim());
        onboardingRepo.save(o);
        return detail(tenantId, o, employee(tenantId, o.getEmployeeId()), held, actor);
    }

    // ── The task list ───────────────────────────────────────────────────────────────────────────

    @Transactional
    public List<Template> templates(TenantId tenantId) {
        if (templateRepo.forTenant(tenantId).isEmpty()) seedTemplates(tenantId);
        return templateRepo.forTenant(tenantId).stream().map(OnboardingService::template).toList();
    }

    @Transactional
    public Template saveTemplate(TenantId tenantId, UUID id, TemplateRequest req, Set<String> held) {
        requireManage(held);
        OnboardingRules.Spec spec = specOf(req);
        fail(OnboardingRules.templateProblem(spec.title(), spec.category(), spec.dueBasis(), spec.check(), spec.dueOffsetDays()));
        HrOnboardingTemplate t;
        if (id == null) {
            if (templateRepo.forTenant(tenantId).isEmpty()) seedTemplates(tenantId);
            t = HrOnboardingTemplate.of(tenantId.getValue(), spec, templateRepo.forTenant(tenantId).size());
        } else {
            t = templateRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("OnboardingTemplate", id.toString()));
            t.apply(spec);
        }
        return template(templateRepo.save(t));
    }

    @Transactional
    public Template setTemplateActive(TenantId tenantId, UUID id, boolean active, Set<String> held) {
        requireManage(held);
        HrOnboardingTemplate t = templateRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("OnboardingTemplate", id.toString()));
        t.setActive(active);
        return template(templateRepo.save(t));
    }

    private static OnboardingRules.Spec specOf(TemplateRequest r) {
        return new OnboardingRules.Spec(r.title() == null ? null : r.title().trim(), r.description(), r.category(), !Boolean.FALSE.equals(r.required()),
                r.dueOffsetDays() == null ? 0 : r.dueOffsetDays(), r.dueBasis() == null ? OnboardingRules.START : r.dueBasis(),
                r.check() == null || r.check().isBlank() ? null : r.check());
    }

    private static Template template(HrOnboardingTemplate t) {
        return new Template(t.getId(), t.getTitle(), t.getDescription(), t.getCategory(), t.isRequired(), t.getDueOffsetDays(), t.getDueBasis(),
                t.getCheckKey(), t.getSortOrder(), t.isActive());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────

    static boolean canManage(Set<String> held) { return held.contains("HR_ONBOARDING_MANAGE") || held.contains("HR_MANAGE"); }

    private static void requireManage(Set<String> held) {
        if (!canManage(held)) throw new AccessDeniedException("You need the onboarding management permission for this.");
    }

    private static void mayWork(HrOnboardingTask t, UUID actor, Set<String> held) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        if (canManage(held) || actor.equals(t.getAssigneeId())) return;
        throw new AccessDeniedException("Only the person this task is assigned to, or onboarding management, can tick it.");
    }

    private static void fail(String problem) { if (problem != null) throw new IllegalArgumentException(problem); }

    private HrEmployee employee(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }

    private HrOnboarding onboarding(TenantId tenantId, UUID id) {
        return onboardingRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Onboarding", id.toString()));
    }

    private HrOnboardingTask task(TenantId tenantId, UUID id) {
        return taskRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("OnboardingTask", id.toString()));
    }

    private HrOnboarding openOnboarding(TenantId tenantId, HrOnboardingTask t) {
        HrOnboarding o = onboarding(tenantId, t.getOnboardingId());
        if (!OnboardingRules.IN_PROGRESS.equals(o.getStatus())) throw new IllegalStateException("This onboarding is " + o.getStatus().toLowerCase() + ", so its tasks can no longer be changed.");
        return o;
    }
}
