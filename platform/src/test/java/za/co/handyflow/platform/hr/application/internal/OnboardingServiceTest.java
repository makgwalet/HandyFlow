package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.OnboardingDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OnboardingServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID actor = UUID.randomUUID();
    private static final Set<String> MANAGER = Set.of("HR_ONBOARDING_MANAGE", "HR_READ");
    private static final Set<String> NOBODY = Set.of();

    HrOnboardingTemplateRepository templateRepo;
    HrOnboardingRepository onboardingRepo;
    HrOnboardingTaskRepository taskRepo;
    HrEmployeeRepository employeeRepo;
    EmployeeViewService viewService;
    OnboardingService service;

    HrEmployee emp;
    final List<HrOnboardingTemplate> templates = new ArrayList<>();
    final List<HrOnboarding> onboardings = new ArrayList<>();
    final List<HrOnboardingTask> tasks = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        templateRepo = mock(HrOnboardingTemplateRepository.class);
        onboardingRepo = mock(HrOnboardingRepository.class);
        taskRepo = mock(HrOnboardingTaskRepository.class);
        employeeRepo = mock(HrEmployeeRepository.class);
        viewService = mock(EmployeeViewService.class);
        service = new OnboardingService(templateRepo, onboardingRepo, taskRepo, employeeRepo, viewService);

        emp = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.of(2026, 11, 2), "PERMANENT", new BigDecimal("30000"), "MONTHLY");
        when(employeeRepo.findActiveById(any(), eq(emp.getId()))).thenReturn(Optional.of(emp));
        when(employeeRepo.findAllById(any())).thenReturn(List.of(emp));

        when(templateRepo.forTenant(any())).thenAnswer(i -> new ArrayList<>(templates));
        when(templateRepo.activeForTenant(any())).thenAnswer(i -> templates.stream().filter(HrOnboardingTemplate::isActive).toList());
        when(templateRepo.save(any())).thenAnswer(i -> { HrOnboardingTemplate t = i.getArgument(0); if (!templates.contains(t)) templates.add(t); return t; });

        when(onboardingRepo.save(any())).thenAnswer(i -> { HrOnboarding o = i.getArgument(0); if (!onboardings.contains(o)) onboardings.add(o); return o; });
        when(onboardingRepo.forTenant(any())).thenAnswer(i -> new ArrayList<>(onboardings));
        when(onboardingRepo.findByEmployee(any(), any())).thenAnswer(i -> onboardings.stream().filter(o -> o.getEmployeeId().equals(i.getArgument(1))).findFirst());
        when(onboardingRepo.findByTenantAndId(any(), any())).thenAnswer(i -> onboardings.stream().filter(o -> o.getId().equals(i.getArgument(1))).findFirst());

        when(taskRepo.save(any())).thenAnswer(i -> { HrOnboardingTask t = i.getArgument(0); if (!tasks.contains(t)) tasks.add(t); return t; });
        when(taskRepo.forOnboardings(any())).thenAnswer(i -> {
            Collection<UUID> ids = i.getArgument(0);
            return tasks.stream().filter(t -> ids.contains(t.getOnboardingId())).toList();
        });
        when(taskRepo.findByTenantAndId(any(), any())).thenAnswer(i -> tasks.stream().filter(t -> t.getId().equals(i.getArgument(1))).findFirst());
    }

    private HrOnboardingTask taskTitled(String title) {
        return tasks.stream().filter(t -> t.getTitle().equals(title)).findFirst().orElseThrow();
    }

    private HrOnboardingTask firstManualRequired() {
        return tasks.stream().filter(t -> t.isRequired() && !OnboardingRules.isCheck(t.getCheckKey())).findFirst().orElseThrow();
    }

    @Test
    void startingMakesTheTenantListFromTheStandardOneAndCopiesItIntoTasks() {
        Detail d = service.startFor(tenant, emp.getId(), actor, MANAGER);

        assertThat(templates).hasSize(OnboardingRules.defaults().size());
        assertThat(tasks).hasSize(OnboardingRules.defaults().size());
        assertThat(d.summary().status()).isEqualTo("IN_PROGRESS");
        assertThat(d.summary().startDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(d.summary().probationEndDate()).isEqualTo(LocalDate.of(2027, 2, 2));
    }

    @Test
    void startingTwiceReturnsTheSameOnboarding() {
        service.startFor(tenant, emp.getId(), actor, MANAGER);
        int tasksAfterFirst = tasks.size();
        service.startFor(tenant, emp.getId(), actor, MANAGER);

        assertThat(onboardings).hasSize(1);
        assertThat(tasks).hasSize(tasksAfterFirst);
    }

    @Test
    void inactiveTemplateTasksAreNotCopied() {
        service.templates(tenant);
        templates.get(0).setActive(false);
        service.startFor(tenant, emp.getId(), actor, MANAGER);

        assertThat(tasks).hasSize(OnboardingRules.defaults().size() - 1);
    }

    @Test
    void aCheckedTaskCannotBeTickedByHand() {
        service.startFor(tenant, emp.getId(), actor, MANAGER);
        HrOnboardingTask idTask = taskTitled("ID number recorded");

        assertThatThrownBy(() -> service.complete(tenant, idTask.getId(), null, actor, MANAGER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("employee record");
    }

    @Test
    void aCheckedTaskTicksItselfWhenTheRecordIsFixed() {
        service.startFor(tenant, emp.getId(), actor, MANAGER);
        Detail before = service.get(tenant, onboardings.get(0).getId(), MANAGER, actor);
        assertThat(before.tasks().stream().filter(t -> t.title().equals("ID number recorded")).findFirst().orElseThrow().status()).isEqualTo("OPEN");

        emp.setIdNumber("8001015009087");
        Detail after = service.get(tenant, onboardings.get(0).getId(), MANAGER, actor);
        assertThat(after.tasks().stream().filter(t -> t.title().equals("ID number recorded")).findFirst().orElseThrow().status()).isEqualTo("DONE");
    }

    @Test
    void aManagerCanTickAManualTask() {
        service.startFor(tenant, emp.getId(), actor, MANAGER);
        HrOnboardingTask t = firstManualRequired();

        service.complete(tenant, t.getId(), "Signed copy filed", actor, MANAGER);

        assertThat(t.getStatus()).isEqualTo("DONE");
        assertThat(t.getResolvedBy()).isEqualTo(actor);
    }

    @Test
    void anAssigneeCanTickTheirOwnTaskButNobodyElseCan() {
        service.startFor(tenant, emp.getId(), actor, MANAGER);
        HrOnboardingTask t = firstManualRequired();
        UUID assignee = UUID.randomUUID();
        service.assign(tenant, t.getId(), assignee, actor, MANAGER);

        assertThatThrownBy(() -> service.complete(tenant, t.getId(), null, UUID.randomUUID(), NOBODY)).isInstanceOf(AccessDeniedException.class);
        service.complete(tenant, t.getId(), null, assignee, NOBODY);

        assertThat(t.getStatus()).isEqualTo("DONE");
    }

    @Test
    void waivingNeedsAReasonAndManagementPermission() {
        service.startFor(tenant, emp.getId(), actor, MANAGER);
        HrOnboardingTask t = firstManualRequired();

        assertThatThrownBy(() -> service.waive(tenant, t.getId(), " ", actor, MANAGER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.waive(tenant, t.getId(), "Not applicable", actor, NOBODY)).isInstanceOf(AccessDeniedException.class);
        service.waive(tenant, t.getId(), "Not applicable to a contractor", actor, MANAGER);

        assertThat(t.getStatus()).isEqualTo("WAIVED");
    }

    @Test
    void reopeningPutsATaskBack() {
        service.startFor(tenant, emp.getId(), actor, MANAGER);
        HrOnboardingTask t = firstManualRequired();
        service.complete(tenant, t.getId(), null, actor, MANAGER);

        service.reopen(tenant, t.getId(), actor, MANAGER);

        assertThat(t.getStatus()).isEqualTo("OPEN");
        assertThat(t.getResolvedBy()).isNull();
    }

    @Test
    void cannotFinishWhileRequiredTasksAreOpen() {
        service.startFor(tenant, emp.getId(), actor, MANAGER);
        UUID id = onboardings.get(0).getId();

        assertThatThrownBy(() -> service.finish(tenant, id, actor, MANAGER)).isInstanceOf(IllegalStateException.class).hasMessageContaining("required task");
        assertThat(onboardings.get(0).getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void finishesOnceEveryRequiredTaskIsDoneOrWaived() {
        service.startFor(tenant, emp.getId(), actor, MANAGER);
        UUID id = onboardings.get(0).getId();
        // Make the record satisfy every check.
        ReflectionTestUtils.setField(emp, "idNumber", "8001015009087");
        ReflectionTestUtils.setField(emp, "taxNumber", "0123456789");
        ReflectionTestUtils.setField(emp, "bankName", "FNB");
        ReflectionTestUtils.setField(emp, "bankAccountNumber", "62123456789");
        ReflectionTestUtils.setField(emp, "bankBranchCode", "250655");
        ReflectionTestUtils.setField(emp, "managerId", UUID.randomUUID());
        ReflectionTestUtils.setField(emp, "userId", UUID.randomUUID());
        ReflectionTestUtils.setField(emp, "emergencyContactName", "John");
        ReflectionTestUtils.setField(emp, "emergencyContactPhone", "0821234567");
        for (HrOnboardingTask t : tasks) {
            if (t.isRequired() && !OnboardingRules.isCheck(t.getCheckKey())) service.complete(tenant, t.getId(), null, actor, MANAGER);
        }

        Detail d = service.finish(tenant, id, actor, MANAGER);

        assertThat(d.summary().status()).isEqualTo("COMPLETE");
        assertThat(onboardings.get(0).getCompletedBy()).isEqualTo(actor);
    }

    @Test
    void aFinishedOnboardingCannotBeChanged() {
        service.startFor(tenant, emp.getId(), actor, MANAGER);
        UUID id = onboardings.get(0).getId();
        service.cancel(tenant, id, "Offer withdrawn", actor, MANAGER);

        HrOnboardingTask t = firstManualRequired();
        assertThatThrownBy(() -> service.complete(tenant, t.getId(), null, actor, MANAGER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.cancel(tenant, id, "again", actor, MANAGER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancellingNeedsAReason() {
        service.startFor(tenant, emp.getId(), actor, MANAGER);
        UUID id = onboardings.get(0).getId();

        assertThatThrownBy(() -> service.cancel(tenant, id, "  ", actor, MANAGER)).isInstanceOf(IllegalArgumentException.class);
        service.cancel(tenant, id, "Did not start", actor, MANAGER);

        assertThat(onboardings.get(0).getStatus()).isEqualTo("CANCELLED");
        assertThat(onboardings.get(0).getCancelReason()).isEqualTo("Did not start");
    }

    @Test
    void probationDateMustFallAfterTheStart() {
        service.startFor(tenant, emp.getId(), actor, MANAGER);
        UUID id = onboardings.get(0).getId();

        assertThatThrownBy(() -> service.setProbation(tenant, id, LocalDate.of(2026, 10, 1), actor, MANAGER)).isInstanceOf(IllegalArgumentException.class);
        service.setProbation(tenant, id, LocalDate.of(2027, 5, 1), actor, MANAGER);

        assertThat(onboardings.get(0).getProbationEndDate()).isEqualTo(LocalDate.of(2027, 5, 1));
    }

    @Test
    void aTeamManagerOnlySeesTheirOwnPeople() {
        service.startFor(tenant, emp.getId(), actor, MANAGER);
        Set<String> team = Set.of("HR_TEAM_READ");
        UUID teamLead = UUID.randomUUID();

        when(viewService.teamOf(any(), eq(teamLead))).thenReturn(Set.of());
        assertThat(service.list(tenant, null, team, teamLead)).isEmpty();
        assertThatThrownBy(() -> service.get(tenant, onboardings.get(0).getId(), team, teamLead)).isInstanceOf(AccessDeniedException.class);

        when(viewService.teamOf(any(), eq(teamLead))).thenReturn(Set.of(emp.getId()));
        assertThat(service.list(tenant, null, team, teamLead)).hasSize(1);
    }

    @Test
    void addingATaskNeedsATitleAndManagement() {
        service.startFor(tenant, emp.getId(), actor, MANAGER);
        UUID id = onboardings.get(0).getId();
        int before = tasks.size();

        assertThatThrownBy(() -> service.addTask(tenant, id, new TaskRequest(" ", null, null, null, null, null), actor, MANAGER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addTask(tenant, id, new TaskRequest("Issue laptop", null, "ACCESS", true, null, null), actor, NOBODY)).isInstanceOf(AccessDeniedException.class);
        service.addTask(tenant, id, new TaskRequest("Issue laptop", null, "ACCESS", true, null, null), actor, MANAGER);

        assertThat(tasks).hasSize(before + 1);
    }

    @Test
    void anEditedTemplateAffectsOnlyFutureOnboardings() {
        service.startFor(tenant, emp.getId(), actor, MANAGER);
        UUID templateId = templates.get(0).getId();
        String oldTitle = templates.get(0).getTitle();

        service.saveTemplate(tenant, templateId, new TemplateRequest("Renamed task", null, "INDUCTION", true, 0, "START", null), MANAGER);

        assertThat(templates.get(0).getTitle()).isEqualTo("Renamed task");
        assertThat(tasks.stream().anyMatch(t -> t.getTitle().equals(oldTitle))).isTrue();
    }
}
