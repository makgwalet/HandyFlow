package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.legalpractice.domain.model.*;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.TaskDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Tasks and workflows against in-memory stand-ins for the repositories. Migration constraints and triggers were run against PostgreSQL separately. */
class LpTaskServiceTest {

    private final TenantId firm = TenantId.of(UUID.randomUUID());
    private final TenantId otherFirm = TenantId.of(UUID.randomUUID());
    private final UUID anele = UUID.randomUUID();
    private final UUID thandi = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(LpTaskService.ZONE);

    private final Map<UUID, LpTask> tasks = new LinkedHashMap<>();
    private final List<LpTaskEvent> events = new ArrayList<>();
    private final Map<UUID, LpWorkflowTemplate> templates = new LinkedHashMap<>();
    private final List<LpWorkflowStep> steps = new ArrayList<>();
    private final List<LpWorkflowRun> runs = new ArrayList<>();
    private final Map<UUID, LpMatter> matters = new LinkedHashMap<>();
    private final List<UUID> walledMatters = new ArrayList<>(), walledClients = new ArrayList<>();

    private LpTaskService service;
    private LpMatter matter;

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Function<Object[], Object>> handlers) {
        InvocationHandler h = (proxy, m, args) -> {
            Function<Object[], Object> f = handlers.get(m.getName());
            if (f != null) return f.apply(args == null ? new Object[0] : args);
            if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, args);
            throw new UnsupportedOperationException(m.getName());
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, h);
    }

    @BeforeEach
    void setUp() {
        tasks.clear(); events.clear(); templates.clear(); steps.clear(); runs.clear(); matters.clear(); walledMatters.clear(); walledClients.clear();
        var taskRepo = fake(LpTaskRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(tasks.get((UUID) a[1])).filter(t -> t.getTenantId().equals(a[0])),
                "findForMatter", a -> tasks.values().stream().filter(t -> t.getTenantId().equals(a[0]) && t.getMatterId().equals(a[1])).toList(),
                "save", a -> { LpTask t = (LpTask) a[0]; tasks.put(t.getId(), t); return t; },
                "findFirmExcluding", a -> {
                    boolean mine = (Boolean) a[3]; UUID who = (UUID) a[4]; boolean closed = (Boolean) a[5]; String status = (String) a[6]; boolean overdueOnly = (Boolean) a[7]; LocalDate day = (LocalDate) a[8];
                    String pat = (String) a[9]; String needle = pat.substring(1, pat.length() - 1);
                    return new PageImpl<>(tasks.values().stream().filter(t -> t.getTenantId().equals(a[0]) && !((List<?>) a[1]).contains(t.getMatterId()) && !((List<?>) a[2]).contains(t.getClientId())
                            && (!mine || who.equals(t.getAssigneeId())) && (closed || t.live()) && (status.isEmpty() || t.getStatus().equals(status))
                            && (!overdueOnly || TaskRules.overdue(t.getStatus(), t.getDueDate(), day)) && t.getTitle().toLowerCase().contains(needle)).toList());
                },
                "countOverdueFor", a -> tasks.values().stream().filter(t -> t.getTenantId().equals(a[0]) && !((List<?>) a[1]).contains(t.getMatterId()) && !((List<?>) a[2]).contains(t.getClientId())
                        && a[3].equals(t.getAssigneeId()) && TaskRules.overdue(t.getStatus(), t.getDueDate(), (LocalDate) a[4])).count()));
        var eventRepo = fake(LpTaskEventRepository.class, Map.of(
                "save", a -> { events.add((LpTaskEvent) a[0]); return a[0]; },
                "findRecentForTask", a -> { List<LpTaskEvent> l = new ArrayList<>(events.stream().filter(e -> e.getTenantId().equals(a[0]) && e.getTaskId().equals(a[1])).toList()); Collections.reverse(l); return l; }));
        var templateRepo = fake(LpWorkflowTemplateRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(templates.get((UUID) a[1])).filter(t -> t.getTenantId().equals(a[0])),
                "findAllForTenant", a -> templates.values().stream().filter(t -> t.getTenantId().equals(a[0]) && ((Boolean) a[1] || t.isActive())).toList(),
                "findActiveByName", a -> templates.values().stream().filter(t -> t.getTenantId().equals(a[0]) && t.isActive() && t.getName().toLowerCase().equals(a[1]) && !t.getId().equals(a[2])).toList(),
                "save", a -> { LpWorkflowTemplate t = (LpWorkflowTemplate) a[0]; templates.put(t.getId(), t); return t; }));
        var stepRepo = fake(LpWorkflowStepRepository.class, Map.of(
                "findForTemplate", a -> steps.stream().filter(s -> s.getTenantId().equals(a[0]) && s.getTemplateId().equals(a[1])).sorted(Comparator.comparing(LpWorkflowStep::getStepNo)).toList(),
                "deleteForTemplate", a -> { steps.removeIf(s -> s.getTenantId().equals(a[0]) && s.getTemplateId().equals(a[1])); return null; },
                "save", a -> { steps.add((LpWorkflowStep) a[0]); return a[0]; }));
        var runRepo = fake(LpWorkflowRunRepository.class, Map.of(
                "findForMatter", a -> runs.stream().filter(r -> r.getTenantId().equals(a[0]) && r.getMatterId().equals(a[1])).toList(),
                "findOne", a -> runs.stream().filter(r -> r.getTenantId().equals(a[0]) && r.getMatterId().equals(a[1]) && r.getTemplateId().equals(a[2])).findFirst(),
                "save", a -> { runs.add((LpWorkflowRun) a[0]); return a[0]; }));
        var matterRepo = fake(LpMatterRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(matters.get((UUID) a[1])).filter(m -> m.getTenantId().equals(a[0])),
                "findAllById", a -> { List<LpMatter> l = new ArrayList<>(); for (Object id : (Iterable<?>) a[0]) if (matters.containsKey(id)) l.add(matters.get(id)); return l; }));
        LpWallGuard guard = new LpWallGuard() {
            @Override public void requireAccess(TenantId t, LpMatter m) { }
            @Override public void requireMatterAccess(TenantId t, UUID id) { if (walledMatters.contains(id)) throw new AccessDeniedException("walled"); }
            @Override public void requireClientAccess(TenantId t, UUID id) { if (walledClients.contains(id)) throw new AccessDeniedException("walled"); }
            @Override public void requireScopeAccess(TenantId t, UUID m, UUID c) { }
            @Override public Barred barred(TenantId t) { return Barred.of(walledMatters, walledClients); }
        };
        service = new LpTaskService(taskRepo, eventRepo, templateRepo, stepRepo, runRepo, matterRepo, guard);
        matter = newMatter("M-1", "Dlamini v Nkosi", clientId);
    }

    private LpMatter newMatter(String no, String name, UUID client) {
        LpMatter m = LpMatter.create(firm, client, UUID.randomUUID(), no, "LITIGATION", name, null, "HOURLY", null, LocalDate.of(2026, 1, 5), null);
        matters.put(m.getId(), m);
        return m;
    }

    private LocalDate weekday(int plusDays) {
        LocalDate d = today.plusDays(plusDays);
        while (!LitigationRules.dateWarnings(d).isEmpty()) d = d.plusDays(1);
        return d;
    }

    private TaskDetail add(String title, LocalDate due) {
        return service.create(firm, new CreateTaskRequest(matter.getId(), title, null, "NORMAL", due, anele, "Anele"), anele, "Anele");
    }

    private List<String> eventTypes() { return events.stream().map(LpTaskEvent::getEventType).toList(); }

    private static StepRequest step(String title, int offset, String assign) { return new StepRequest(title, null, "NORMAL", offset, assign); }

    private TemplateDetail template(String name, StepRequest... s) {
        return service.createTemplate(firm, new TemplateRequest(name, null, null, List.of(s)), anele, "Anele");
    }

    // ---- tasks ---------------------------------------------------------------------------------------------------------

    @Test
    void addingATaskLogsIt() {
        var d = add("Draft particulars", weekday(5));
        assertEquals("OPEN", d.task().status());
        assertEquals("M-1 Dlamini v Nkosi", d.task().matterLabel());
        assertEquals(List.of("CREATED"), eventTypes());
        assertEquals(1, service.forMatter(firm, matter.getId()).size());
    }

    @Test
    void aTaskNeedsAMatterAndATitle() {
        assertThrows(IllegalArgumentException.class, () -> service.create(firm, new CreateTaskRequest(null, "x task", null, "NORMAL", null, null, null), anele, "Anele"));
        assertThrows(IllegalArgumentException.class, () -> add(" ", null));
        assertTrue(tasks.isEmpty());
    }

    @Test
    void noTasksOnClosedMatters() {
        matter.close(LocalDate.now());
        var ex = assertThrows(IllegalStateException.class, () -> add("Late task", null));
        assertTrue(ex.getMessage().contains("closed"));
    }

    @Test
    void anotherFirmCannotSeeOrChangeATask() {
        var d = add("Draft particulars", null);
        UUID id = d.task().id();
        assertThrows(ResourceNotFoundException.class, () -> service.get(otherFirm, id));
        assertThrows(ResourceNotFoundException.class, () -> service.complete(otherFirm, id, anele, "Anele"));
        assertThrows(ResourceNotFoundException.class, () -> service.create(otherFirm, new CreateTaskRequest(matter.getId(), "Sneaky", null, "NORMAL", null, null, null), anele, "Anele"));
    }

    @Test
    void workingATaskThroughToDone() {
        UUID id = add("Draft particulars", null).task().id();
        assertEquals("IN_PROGRESS", service.start(firm, id, anele, "Anele").task().status());
        assertEquals("OPEN", service.stop(firm, id, anele, "Anele").task().status());
        service.start(firm, id, anele, "Anele");
        var done = service.complete(firm, id, anele, "Anele");
        assertEquals("DONE", done.task().status());
        assertEquals("Anele", done.completedByName());
        assertEquals(List.of("CREATED", "STARTED", "STOPPED", "STARTED", "COMPLETED"), eventTypes());
    }

    @Test
    void reopeningNeedsAReason() {
        UUID id = add("Draft particulars", null).task().id();
        service.complete(firm, id, anele, "Anele");
        assertThrows(IllegalArgumentException.class, () -> service.reopen(firm, id, new ReasonRequest("oops"), anele, "Anele"));
        assertEquals("DONE", service.get(firm, id).task().status());
        assertEquals("OPEN", service.reopen(firm, id, new ReasonRequest("Counsel asked for changes"), anele, "Anele").task().status());
        assertEquals("REOPENED", eventTypes().get(eventTypes().size() - 1));
    }

    @Test
    void cancellingNeedsAReasonAndIsFinal() {
        UUID id = add("Draft particulars", null).task().id();
        assertThrows(IllegalArgumentException.class, () -> service.cancel(firm, id, new ReasonRequest("no"), anele, "Anele"));
        var c = service.cancel(firm, id, new ReasonRequest("Matter settled before trial"), anele, "Anele");
        assertEquals("CANCELLED", c.task().status());
        assertEquals("Matter settled before trial", c.cancelReason());
        assertThrows(IllegalStateException.class, () -> service.start(firm, id, anele, "Anele"));
        assertThrows(IllegalStateException.class, () -> service.complete(firm, id, anele, "Anele"));
        assertThrows(IllegalStateException.class, () -> service.edit(firm, id, new EditTaskRequest("New title", null, "HIGH", null), anele, "Anele"));
    }

    @Test
    void aCompletedTaskCannotBeStartedOrCancelled() {
        UUID id = add("Draft particulars", null).task().id();
        service.complete(firm, id, anele, "Anele");
        assertThrows(IllegalStateException.class, () -> service.start(firm, id, anele, "Anele"));
        assertThrows(IllegalStateException.class, () -> service.cancel(firm, id, new ReasonRequest("Matter settled before trial"), anele, "Anele"));
        assertThrows(IllegalStateException.class, () -> service.complete(firm, id, anele, "Anele"));
    }

    @Test
    void reassigningIsLoggedWithWhoItWentTo() {
        UUID id = add("Draft particulars", null).task().id();
        var d = service.assign(firm, id, new AssignRequest(thandi, "Thandi"), anele, "Anele");
        assertEquals("Thandi", d.task().assigneeName());
        assertTrue(d.events().get(0).detail().contains("Anele to Thandi"));
        var un = service.assign(firm, id, new AssignRequest(null, null), anele, "Anele");
        assertNull(un.task().assigneeId());
        assertThrows(IllegalStateException.class, () -> service.assign(firm, id, new AssignRequest(null, null), anele, "Anele"));
        assertEquals("Unknown", service.assign(firm, id, new AssignRequest(thandi, " "), anele, "Anele").task().assigneeName());
    }

    @Test
    void editingChangesTheDetailsAndIsLogged() {
        UUID id = add("Draft particulars", null).task().id();
        var d = service.edit(firm, id, new EditTaskRequest("Draft amended particulars", "Per counsel", "URGENT", weekday(3)), anele, "Anele");
        assertEquals("Draft amended particulars", d.task().title());
        assertEquals("URGENT", d.task().priority());
        assertEquals("EDITED", eventTypes().get(eventTypes().size() - 1));
    }

    @Test
    void overdueShowsOnlyForLiveTasksPastTheirDate() {
        UUID a = add("Late one", today.minusDays(3)).task().id();
        UUID b = add("Done late", today.minusDays(3)).task().id();
        service.complete(firm, b, anele, "Anele");
        add("Not due", today.plusDays(3));
        var list = service.forMatter(firm, matter.getId());
        assertEquals(1L, list.stream().filter(TaskSummary::overdue).count());
        assertEquals(1L, service.myOverdue(firm, anele).overdue());
        assertEquals(0L, service.myOverdue(firm, thandi).overdue());
        assertTrue(service.get(firm, a).task().overdue());
    }

    // ---- lists and walls -----------------------------------------------------------------------------------------------

    @Test
    void firmListFiltersAndHidesClosed() {
        add("Draft particulars", null);
        UUID done = add("Serve summons", null).task().id();
        service.complete(firm, done, anele, "Anele");
        var page = PageRequest.of(0, 20);
        assertEquals(1, service.firmList(firm, anele, false, null, null, false, false, page).getContent().size());
        assertEquals(2, service.firmList(firm, anele, false, null, null, true, false, page).getContent().size());
        assertEquals(1, service.firmList(firm, anele, false, "serve", null, true, false, page).getContent().size());
        assertEquals(0, service.firmList(firm, thandi, true, null, null, false, false, page).getContent().size());
        assertEquals(1, service.firmList(firm, anele, true, null, null, false, false, page).getContent().size());
    }

    @Test
    void wallsHideTasksEverywhere() {
        UUID id = add("Draft particulars", null).task().id();
        LpMatter other = newMatter("M-2", "Other", UUID.randomUUID());
        service.create(firm, new CreateTaskRequest(other.getId(), "Visible task", null, "NORMAL", today.minusDays(2), anele, "Anele"), anele, "Anele");
        walledMatters.add(matter.getId());
        assertThrows(AccessDeniedException.class, () -> service.get(firm, id));
        assertThrows(AccessDeniedException.class, () -> service.start(firm, id, anele, "Anele"));
        assertThrows(AccessDeniedException.class, () -> service.forMatter(firm, matter.getId()));
        assertThrows(AccessDeniedException.class, () -> add("More", null));
        var page = service.firmList(firm, anele, false, null, null, true, false, PageRequest.of(0, 20));
        assertEquals(1, page.getContent().size());
        assertEquals("Visible task", page.getContent().get(0).title());
        assertEquals(1L, service.myOverdue(firm, anele).overdue());
    }

    @Test
    void wallsOnAClientHideTheirTasks() {
        add("Draft particulars", today.minusDays(2));
        walledClients.add(clientId);
        assertTrue(service.firmList(firm, anele, false, null, null, true, false, PageRequest.of(0, 20)).getContent().isEmpty());
        assertEquals(0L, service.myOverdue(firm, anele).overdue());
    }

    // ---- workflows -----------------------------------------------------------------------------------------------------

    @Test
    void creatingATemplateKeepsStepsInOrder() {
        var t = template("Open litigation file", step("Open file", 0, "STARTER"), step("Letter of demand", 7, "STARTER"), step("Calendar prescription", 1, "NONE"));
        assertEquals(3, t.steps().size());
        assertEquals(List.of(1, 2, 3), t.steps().stream().map(StepResponse::stepNo).toList());
        assertEquals("Letter of demand", t.steps().get(1).title());
        assertEquals(1, service.templates(firm, false).size());
        assertEquals(3, service.templates(firm, false).get(0).steps());
    }

    @Test
    void badTemplatesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> template("Empty"));
        assertThrows(IllegalArgumentException.class, () -> template("x", step("Open file", 0, "STARTER")));
        assertThrows(IllegalArgumentException.class, () -> template("Bad offset", step("Open file", -3, "STARTER")));
        assertTrue(templates.isEmpty());
        assertTrue(steps.isEmpty());
    }

    @Test
    void templateNamesAreUniqueAmongActiveOnes() {
        var t = template("Open file", step("One", 0, "STARTER"));
        assertThrows(IllegalStateException.class, () -> template("OPEN FILE", step("One", 0, "STARTER")));
        service.setTemplateActive(firm, t.template().id(), false);
        var again = template("open file", step("One", 0, "STARTER"));
        assertThrows(IllegalStateException.class, () -> service.setTemplateActive(firm, t.template().id(), true));
        assertTrue(again.template().active());
    }

    @Test
    void editingATemplateReplacesItsSteps() {
        var t = template("Open file", step("One", 0, "STARTER"), step("Two", 1, "STARTER"));
        var u = service.updateTemplate(firm, t.template().id(), new TemplateRequest("Open file v2", "Updated", "LITIGATION", List.of(step("Only", 2, "NONE"))), anele, "Anele");
        assertEquals("Open file v2", u.template().name());
        assertEquals(1, u.steps().size());
        assertEquals("Only", u.steps().get(0).title());
        assertEquals(1, steps.size());
    }

    @Test
    void aFailedTemplateEditLeavesTheOldStepsAlone() {
        var t = template("Open file", step("One", 0, "STARTER"));
        assertThrows(IllegalArgumentException.class, () -> service.updateTemplate(firm, t.template().id(), new TemplateRequest("Open file", null, null, List.of()), anele, "Anele"));
        assertEquals(1, steps.size());
    }

    @Test
    void templatesAreFirmPrivate() {
        var t = template("Open file", step("One", 0, "STARTER"));
        assertThrows(ResourceNotFoundException.class, () -> service.template(otherFirm, t.template().id()));
        assertTrue(service.templates(otherFirm, true).isEmpty());
    }

    @Test
    void applyingCreatesOneTaskPerStepWithDueDatesFromTheStart() {
        var t = template("Open file", step("Open file", 0, "STARTER"), step("Letter of demand", 14, "STARTER"), step("Check conflicts again", 30, "NONE"));
        LocalDate start = weekday(1);
        var r = service.apply(firm, t.template().id(), new ApplyRequest(matter.getId(), start), thandi, "Thandi");
        assertEquals(3, r.tasks().size());
        assertEquals(3, r.run().tasksCreated());
        assertEquals("Open file", r.run().templateName());
        assertEquals(start.plusDays(14), r.tasks().get(1).dueDate());
        assertEquals("Thandi", r.tasks().get(0).assigneeName());
        assertNull(r.tasks().get(2).assigneeId());
        assertEquals(3, service.forMatter(firm, matter.getId()).size());
        assertTrue(service.forMatter(firm, matter.getId()).stream().allMatch(x -> x.workflowRunId() != null));
        assertEquals(1, service.runsForMatter(firm, matter.getId()).size());
        assertEquals(3L, eventTypes().stream().filter("CREATED"::equals).count());
    }

    @Test
    void theSameWorkflowCannotBeAppliedTwice() {
        var t = template("Open file", step("One", 0, "STARTER"));
        service.apply(firm, t.template().id(), new ApplyRequest(matter.getId(), today), anele, "Anele");
        assertThrows(IllegalStateException.class, () -> service.apply(firm, t.template().id(), new ApplyRequest(matter.getId(), today), anele, "Anele"));
        assertEquals(1, tasks.size());
    }

    @Test
    void aTurnedOffWorkflowCannotBeApplied() {
        var t = template("Open file", step("One", 0, "STARTER"));
        service.setTemplateActive(firm, t.template().id(), false);
        assertThrows(IllegalStateException.class, () -> service.apply(firm, t.template().id(), new ApplyRequest(matter.getId(), today), anele, "Anele"));
        assertTrue(tasks.isEmpty());
    }

    @Test
    void applyingNeedsAWorkableStartDateAndAnOpenMatter() {
        var t = template("Open file", step("One", 0, "STARTER"));
        assertThrows(IllegalArgumentException.class, () -> service.apply(firm, t.template().id(), new ApplyRequest(matter.getId(), null), anele, "Anele"));
        assertThrows(IllegalArgumentException.class, () -> service.apply(firm, t.template().id(), new ApplyRequest(matter.getId(), today.plusDays(500)), anele, "Anele"));
        matter.close(LocalDate.now());
        assertThrows(IllegalStateException.class, () -> service.apply(firm, t.template().id(), new ApplyRequest(matter.getId(), today), anele, "Anele"));
        assertTrue(tasks.isEmpty());
        assertTrue(runs.isEmpty());
    }

    @Test
    void applyingToAWalledMatterIsRefused() {
        var t = template("Open file", step("One", 0, "STARTER"));
        walledMatters.add(matter.getId());
        assertThrows(AccessDeniedException.class, () -> service.apply(firm, t.template().id(), new ApplyRequest(matter.getId(), today), anele, "Anele"));
        assertThrows(AccessDeniedException.class, () -> service.runsForMatter(firm, matter.getId()));
        assertTrue(tasks.isEmpty());
    }

    @Test
    void applyWarnsAboutWeekendsAndWrongMatterType() {
        LocalDate sat = today;
        while (sat.getDayOfWeek() != java.time.DayOfWeek.SATURDAY) sat = sat.plusDays(1);
        service.createTemplate(firm, new TemplateRequest("Conveyancing start", null, "CONVEYANCING", List.of(step("One", 0, "STARTER"))), anele, "Anele");
        UUID id = templates.keySet().iterator().next();
        var r = service.apply(firm, id, new ApplyRequest(matter.getId(), sat), anele, "Anele");
        assertEquals(2, r.warnings().size());
        assertTrue(r.warnings().get(0).contains("weekend"));
        assertTrue(r.warnings().get(1).contains("CONVEYANCING"));
    }

    @Test
    void applyingToAnotherFirmsMatterFindsNothing() {
        var t = template("Open file", step("One", 0, "STARTER"));
        assertThrows(ResourceNotFoundException.class, () -> service.apply(otherFirm, t.template().id(), new ApplyRequest(matter.getId(), today), anele, "Anele"));
    }
}
