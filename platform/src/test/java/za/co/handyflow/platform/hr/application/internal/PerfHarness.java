package za.co.handyflow.platform.hr.application.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** The performance services wired to in-memory stand-ins for the repositories. */
final class PerfHarness {

    static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    static final Set<String> HR = Set.of("HR_PERF_READ", "HR_PERF_MANAGE", "HR_ER_READ", "HR_ER_MANAGE");
    static final Set<String> READER = Set.of("HR_PERF_READ");
    static final Set<String> NONE = Set.of();

    final HrPerfCycleRepository cycleRepo = mock(HrPerfCycleRepository.class);
    final HrPerfReviewRepository reviewRepo = mock(HrPerfReviewRepository.class);
    final HrPerfGoalRepository goalRepo = mock(HrPerfGoalRepository.class);
    final HrPerfFeedbackRepository feedbackRepo = mock(HrPerfFeedbackRepository.class);
    final HrPerfPlanRepository planRepo = mock(HrPerfPlanRepository.class);
    final HrPerfCheckinRepository checkinRepo = mock(HrPerfCheckinRepository.class);
    final HrPerfEventRepository eventRepo = mock(HrPerfEventRepository.class);
    final HrEmployeeRepository employees = mock(HrEmployeeRepository.class);
    final HrErCaseRepository cases = mock(HrErCaseRepository.class);
    final HrErStepRepository steps = mock(HrErStepRepository.class);
    final HrErEventRepository caseEvents = mock(HrErEventRepository.class);
    final HrErPolicyRepository policyRepo = mock(HrErPolicyRepository.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    final List<HrPerfCycle> cycleStore = new ArrayList<>();
    final List<HrPerfReview> reviewStore = new ArrayList<>();
    final List<HrPerfGoal> goalStore = new ArrayList<>();
    final List<HrPerfFeedback> feedbackStore = new ArrayList<>();
    final List<HrPerfPlan> planStore = new ArrayList<>();
    final List<HrPerfCheckin> checkinStore = new ArrayList<>();
    final List<HrPerfEvent> eventStore = new ArrayList<>();
    final Map<UUID, HrEmployee> staff = new LinkedHashMap<>();

    final ErPolicyService policy = new ErPolicyService(policyRepo);
    final ErCaseSupport caseSupport = new ErCaseSupport(cases, steps, caseEvents, employees, jdbc);
    final NotificationService notificationService = mock(NotificationService.class);
    final PerfNotifier notifier = new PerfNotifier(notificationService, employees);
    final PerfSupport support = new PerfSupport(employees, cycleRepo, reviewRepo, eventRepo);
    final PerfGoalService goals = new PerfGoalService(goalRepo, cycleRepo, employees, support);
    final PerfFeedbackService feedback = new PerfFeedbackService(feedbackRepo, reviewRepo, cycleRepo, support, notifier);
    final PerfReviewViews views = new PerfReviewViews(cycleRepo, support, goals, feedback);
    final PerfCycleService cycles = new PerfCycleService(cycleRepo, reviewRepo, employees, support, views, notifier);
    final PerfReviewService reviews = new PerfReviewService(reviewRepo, employees, support, views, notifier);
    final PerfPlanService plans = new PerfPlanService(planRepo, checkinRepo, cases, caseSupport, policy, support);

    PerfHarness() {
        lenient().when(cycleRepo.forTenant(eq(TENANT))).thenAnswer(inv -> new ArrayList<>(cycleStore));
        lenient().when(cycleRepo.findByTenantAndId(eq(TENANT), any())).thenAnswer(inv -> cycleStore.stream().filter(c -> c.getId().equals(inv.getArgument(1))).findFirst());
        lenient().when(cycleRepo.findAllById(any())).thenAnswer(inv -> {
            Set<UUID> ids = new HashSet<>();
            for (Object o : (Iterable<?>) inv.getArgument(0)) ids.add((UUID) o);
            return cycleStore.stream().filter(c -> ids.contains(c.getId())).toList();
        });
        lenient().when(cycleRepo.save(any(HrPerfCycle.class))).thenAnswer(inv -> {
            HrPerfCycle c = inv.getArgument(0);
            if (!cycleStore.contains(c)) cycleStore.add(c);
            return c;
        });

        lenient().when(reviewRepo.findByTenantAndId(eq(TENANT), any())).thenAnswer(inv -> reviewStore.stream().filter(r -> r.getId().equals(inv.getArgument(1))).findFirst());
        lenient().when(reviewRepo.forCycle(eq(TENANT), any())).thenAnswer(inv -> reviewStore.stream().filter(r -> r.getCycleId().equals(inv.getArgument(1))).toList());
        lenient().when(reviewRepo.forEmployee(eq(TENANT), any())).thenAnswer(inv -> reviewStore.stream().filter(r -> r.getEmployeeId().equals(inv.getArgument(1))).toList());
        lenient().when(reviewRepo.forReviewer(eq(TENANT), any())).thenAnswer(inv -> reviewStore.stream().filter(r -> inv.getArgument(1).equals(r.getReviewerId())).toList());
        lenient().when(reviewRepo.save(any(HrPerfReview.class))).thenAnswer(inv -> {
            HrPerfReview r = inv.getArgument(0);
            if (!reviewStore.contains(r)) reviewStore.add(r);
            return r;
        });

        lenient().when(goalRepo.findByTenantAndId(eq(TENANT), any())).thenAnswer(inv -> goalStore.stream().filter(g -> g.getId().equals(inv.getArgument(1))).findFirst());
        lenient().when(goalRepo.forEmployee(eq(TENANT), any())).thenAnswer(inv -> goalStore.stream().filter(g -> g.getEmployeeId().equals(inv.getArgument(1))).toList());
        lenient().when(goalRepo.forTenant(eq(TENANT))).thenAnswer(inv -> new ArrayList<>(goalStore));
        lenient().when(goalRepo.save(any(HrPerfGoal.class))).thenAnswer(inv -> {
            HrPerfGoal g = inv.getArgument(0);
            if (!goalStore.contains(g)) goalStore.add(g);
            return g;
        });

        lenient().when(feedbackRepo.findByTenantAndId(eq(TENANT), any())).thenAnswer(inv -> feedbackStore.stream().filter(f -> f.getId().equals(inv.getArgument(1))).findFirst());
        lenient().when(feedbackRepo.forReview(any())).thenAnswer(inv -> feedbackStore.stream().filter(f -> f.getReviewId().equals(inv.getArgument(0))).toList());
        lenient().when(feedbackRepo.forProvider(eq(TENANT), any())).thenAnswer(inv -> feedbackStore.stream().filter(f -> f.getProviderId().equals(inv.getArgument(1))).toList());
        lenient().when(feedbackRepo.save(any(HrPerfFeedback.class))).thenAnswer(inv -> {
            HrPerfFeedback f = inv.getArgument(0);
            if (!feedbackStore.contains(f)) feedbackStore.add(f);
            return f;
        });

        lenient().when(planRepo.findByTenantAndId(eq(TENANT), any())).thenAnswer(inv -> planStore.stream().filter(p -> p.getId().equals(inv.getArgument(1))).findFirst());
        lenient().when(planRepo.forTenant(eq(TENANT))).thenAnswer(inv -> new ArrayList<>(planStore));
        lenient().when(planRepo.forEmployee(eq(TENANT), any())).thenAnswer(inv -> planStore.stream().filter(p -> p.getEmployeeId().equals(inv.getArgument(1))).toList());
        lenient().when(planRepo.forCase(eq(TENANT), any())).thenAnswer(inv -> planStore.stream().filter(p -> inv.getArgument(1).equals(p.getCaseId())).toList());
        lenient().when(planRepo.save(any(HrPerfPlan.class))).thenAnswer(inv -> {
            HrPerfPlan p = inv.getArgument(0);
            if (!planStore.contains(p)) planStore.add(p);
            return p;
        });
        lenient().when(checkinRepo.forPlan(any())).thenAnswer(inv -> checkinStore.stream().filter(c -> c.getPlanId().equals(inv.getArgument(0))).toList());
        lenient().when(checkinRepo.save(any(HrPerfCheckin.class))).thenAnswer(inv -> {
            HrPerfCheckin c = inv.getArgument(0);
            checkinStore.add(c);
            return c;
        });
        lenient().when(eventRepo.save(any(HrPerfEvent.class))).thenAnswer(inv -> {
            HrPerfEvent e = inv.getArgument(0);
            eventStore.add(e);
            return e;
        });
        lenient().when(eventRepo.forRef(any(), any())).thenAnswer(inv -> eventStore.stream().filter(e -> e.getKind().equals(inv.getArgument(0)) && e.getRefId().equals(inv.getArgument(1))).toList());

        lenient().when(employees.findAllById(any())).thenAnswer(inv -> {
            Set<UUID> ids = new HashSet<>();
            for (Object o : (Iterable<?>) inv.getArgument(0)) ids.add((UUID) o);
            return staff.values().stream().filter(e -> ids.contains(e.getId())).toList();
        });
        lenient().when(employees.findNotTerminated(TENANT)).thenAnswer(inv -> staff.values().stream().filter(e -> !"TERMINATED".equals(e.getStatus())).toList());
    }

    static LocalDate today() { return LocalDate.now(DisciplineRules.ZONE); }

    /** An employee who exists in this company, reporting to {@code managerId} (or to nobody). */
    UUID employee(String first, String last, UUID managerId) {
        HrEmployee e = mock(HrEmployee.class);
        UUID id = UUID.randomUUID();
        lenient().when(e.getId()).thenReturn(id);
        lenient().when(e.getFirstName()).thenReturn(first);
        lenient().when(e.getLastName()).thenReturn(last);
        lenient().when(e.getFullName()).thenReturn(first + " " + last);
        lenient().when(e.getJobTitle()).thenReturn("Clerk");
        lenient().when(e.getStartDate()).thenReturn(LocalDate.of(2020, 1, 1));
        lenient().when(e.getManagerId()).thenReturn(managerId);
        lenient().when(e.getStatus()).thenReturn("ACTIVE");
        lenient().when(employees.findActiveById(TENANT, id)).thenReturn(Optional.of(e));
        staff.put(id, e);
        return id;
    }

    UUID employee(String first, String last) { return employee(first, last, null); }

    /** Someone who joined on the given day. */
    UUID startedOn(String first, String last, UUID managerId, LocalDate start) {
        UUID id = employee(first, last, managerId);
        lenient().when(staff.get(id).getStartDate()).thenReturn(start);
        return id;
    }

    /** Someone who left on the given day. */
    UUID leftOn(String first, String last, UUID managerId, LocalDate end) {
        UUID id = employee(first, last, managerId);
        lenient().when(staff.get(id).getEndDate()).thenReturn(end);
        return id;
    }

    void terminate(UUID id) { lenient().when(staff.get(id).getStatus()).thenReturn("TERMINATED"); }

    /** Makes this person the one the signed-in user is. */
    UUID signedInAs(UUID employeeId) {
        UUID user = UUID.randomUUID();
        lenient().when(employees.findByUserId(TENANT, user)).thenReturn(Optional.of(staff.get(employeeId)));
        return user;
    }

    HrErCase poorPerformanceCase(UUID employeeId, boolean confidential, UUID ownerUser) {
        LocalDate today = today();
        HrErCase c = HrErCase.open(TENANT.getValue(), "ER-2026-0009", "INCAPACITY", "POOR_PERFORMANCE", employeeId, "Invoice errors", "Repeated errors",
                today.minusDays(10), today.minusDays(9), confidential, ownerUser, ownerUser);
        lenient().when(cases.findByTenantAndId(TENANT, c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    HrErCase caseOf(String type, String category, UUID employeeId) {
        LocalDate today = today();
        UUID owner = UUID.randomUUID();
        HrErCase c = HrErCase.open(TENANT.getValue(), "ER-2026-0010", type, category, employeeId, "Case", "Facts", today.minusDays(10), today.minusDays(9), false, owner, owner);
        lenient().when(cases.findByTenantAndId(TENANT, c.getId())).thenReturn(Optional.of(c));
        return c;
    }
}
