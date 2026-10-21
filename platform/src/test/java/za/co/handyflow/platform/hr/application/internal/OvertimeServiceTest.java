package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrOvertimeClaimRepository;
import za.co.handyflow.platform.hr.dto.AttendanceDtos.Day;
import za.co.handyflow.platform.hr.dto.AttendanceDtos.Timesheet;
import za.co.handyflow.platform.hr.dto.OvertimeDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OvertimeServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID me = UUID.randomUUID();
    private final UUID hr = UUID.randomUUID();
    private static final Set<String> HR = Set.of("HR_OVERTIME_APPROVE", "HR_ATTENDANCE_MANAGE");
    private static final Set<String> NOBODY = Set.of();

    HrOvertimeClaimRepository claimRepo;
    HrEmployeeRepository employeeRepo;
    AttendanceService attendance;
    EmployeeViewService viewService;
    OvertimeService service;

    HrEmployee emp;
    final List<HrOvertimeClaim> claims = new ArrayList<>();
    boolean above = false;

    static LocalDate monday() { return LocalDate.now(AttendanceRules.ZONE).minusDays(14).with(DayOfWeek.MONDAY); }

    @BeforeEach
    void setUp() {
        claimRepo = mock(HrOvertimeClaimRepository.class);
        employeeRepo = mock(HrEmployeeRepository.class);
        attendance = mock(AttendanceService.class);
        viewService = mock(EmployeeViewService.class);
        service = new OvertimeService(claimRepo, employeeRepo, attendance, viewService);

        emp = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.of(2024, 1, 1), "PERMANENT", new BigDecimal("18000"), "MONTHLY");
        emp.setUserId(me);
        when(employeeRepo.findActiveById(any(), eq(emp.getId()))).thenReturn(Optional.of(emp));
        when(employeeRepo.findAllById(any())).thenReturn(List.of(emp));
        when(employeeRepo.findByUserId(any(), eq(me))).thenReturn(Optional.of(emp));

        when(claimRepo.save(any())).thenAnswer(i -> { HrOvertimeClaim c = i.getArgument(0); if (!claims.contains(c)) claims.add(c); return c; });
        when(claimRepo.forEmployee(any(), any(), any(), any())).thenAnswer(i -> new ArrayList<>(claims));
        when(claimRepo.findByTenantAndId(any(), any())).thenAnswer(i -> claims.stream().filter(c -> c.getId().equals(i.getArgument(1))).findFirst());
        when(claimRepo.byStatus(any(), any())).thenAnswer(i -> {
            Collection<String> st = i.getArgument(1);
            return claims.stream().filter(c -> st.contains(c.getStatus())).toList();
        });
        when(claimRepo.takenBy(any(), any())).thenAnswer(i -> claims.stream().filter(c -> i.getArgument(1).equals(c.getPayRunId())).toList());
        when(claimRepo.payableIn(any(), any(), any())).thenAnswer(i -> claims.stream()
                .filter(c -> "APPROVED".equals(c.getStatus()) && !c.getWorkDate().isAfter(i.getArgument(1)) && (c.getPayRunId() == null || c.getPayRunId().equals(i.getArgument(2)))).toList());
    }

    /** A timesheet with the given days: (offset from Monday, kind, minutes, status). */
    private void timesheet(Object[]... rows) {
        LocalDate mon = monday();
        List<Day> days = new ArrayList<>();
        for (Object[] r : rows) {
            int off = (Integer) r[0];
            days.add(new Day(mon.plusDays(off), (String) r[3], 480, 480 + (Integer) r[2], 0, 0, (String) r[1], (Integer) r[2], List.of(), List.of(), null, null));
        }
        Timesheet t = new Timesheet(emp.getId(), "EMP-1", "Jane Dlamini", "Standard week", 2400, mon, mon.plusDays(6), days, List.of(), 0, 0, 0, 0, false, above);
        when(attendance.timesheetFor(any(), any(), any(), any())).thenReturn(t);
    }

    private RequestResult ask(UUID actor, Set<String> held) {
        return service.request(tenant, new RangeRequest(emp.getId(), monday(), monday().plusDays(6)), actor, held);
    }

    @Test
    void anHourOfOvertimeIsPaidAtTheActsRate() {
        timesheet(new Object[]{0, "OVERTIME", 120, "PRESENT"});
        RequestResult r = ask(me, NOBODY);

        assertThat(r.created()).isEqualTo(1);
        HrOvertimeClaim c = claims.get(0);
        // R18,000 x 12 / 52 / 40 = R103.8462 an hour; 2 hours at 1.5 = R311.54
        assertThat(c.getHourlyRate()).isEqualByComparingTo("103.8462");
        assertThat(c.getMultiplier()).isEqualByComparingTo("1.5");
        assertThat(c.getAmount()).isEqualByComparingTo("311.54");
        assertThat(c.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void sundayWorkIsPaidDouble() {
        timesheet(new Object[]{6, "SUNDAY", 120, "PRESENT"});
        ask(me, NOBODY);
        assertThat(claims.get(0).getMultiplier()).isEqualByComparingTo("2.0");
        assertThat(claims.get(0).getAmount()).isEqualByComparingTo("415.38");
    }

    @Test
    void askingTwiceChangesNothing() {
        timesheet(new Object[]{0, "OVERTIME", 120, "PRESENT"});
        ask(me, NOBODY);
        RequestResult second = ask(me, NOBODY);
        assertThat(claims).hasSize(1);
        assertThat(second.created()).isEqualTo(0);
        assertThat(second.updated()).isEqualTo(0);
    }

    @Test
    void aWaitingClaimFollowsACorrectionAndGoesWhenTheOvertimeDoes() {
        timesheet(new Object[]{0, "OVERTIME", 120, "PRESENT"});
        ask(me, NOBODY);
        timesheet(new Object[]{0, "OVERTIME", 60, "PRESENT"});
        RequestResult r = ask(me, NOBODY);
        assertThat(r.updated()).isEqualTo(1);
        assertThat(claims.get(0).getMinutes()).isEqualTo(60);

        timesheet(new Object[]{0, null, 0, "PRESENT"});
        RequestResult gone = ask(me, NOBODY);
        assertThat(gone.withdrawn()).isEqualTo(1);
        assertThat(claims.get(0).getStatus()).isEqualTo("WITHDRAWN");
    }

    @Test
    void anApprovedClaimIsNotChangedByANewRequest() {
        timesheet(new Object[]{0, "OVERTIME", 120, "PRESENT"});
        ask(me, NOBODY);
        service.approve(tenant, claims.get(0).getId(), null, hr, HR);
        timesheet(new Object[]{0, "OVERTIME", 30, "PRESENT"});
        ask(me, NOBODY);
        assertThat(claims.get(0).getMinutes()).isEqualTo(120);
        assertThat(claims.get(0).getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void daysThatCannotBeClaimedAreSaidSo() {
        timesheet(new Object[]{0, "PUBLIC_HOLIDAY", 300, "PRESENT"}, new Object[]{1, "OVERTIME", 60, "INCOMPLETE"});
        RequestResult r = ask(me, NOBODY);
        assertThat(claims).isEmpty();
        assertThat(r.skipped()).hasSize(2);
        assertThat(r.skipped().get(0).reason()).contains("s 18");
        assertThat(r.skipped().get(1).reason()).contains("no clock-out");
    }

    @Test
    void noClaimIsMadeAboveTheThreshold() {
        above = true;
        timesheet(new Object[]{0, "OVERTIME", 120, "PRESENT"});
        RequestResult r = ask(me, NOBODY);
        assertThat(claims).isEmpty();
        assertThat(r.skipped().get(0).reason()).contains("threshold");
    }

    @Test
    void anEmployeeWithNoSalaryGetsNoClaim() {
        emp.setGrossSalary(BigDecimal.ZERO);
        timesheet(new Object[]{0, "OVERTIME", 120, "PRESENT"});
        RequestResult r = ask(me, NOBODY);
        assertThat(claims).isEmpty();
        assertThat(r.skipped().get(0).reason()).contains("salary");
    }

    @Test
    void onlyTheEmployeeTheirManagerOrHrCanAsk() {
        timesheet(new Object[]{0, "OVERTIME", 120, "PRESENT"});
        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> ask(stranger, NOBODY)).isInstanceOf(AccessDeniedException.class);
        UUID manager = UUID.randomUUID();
        when(viewService.teamOf(any(), eq(manager))).thenReturn(Set.of(emp.getId()));
        assertThat(ask(manager, Set.of("HR_TEAM_READ")).created()).isEqualTo(1);
    }

    @Test
    void nobodyDecidesTheirOwnOvertimeAndOrdinaryStaffDecideNothing() {
        timesheet(new Object[]{0, "OVERTIME", 120, "PRESENT"});
        ask(me, NOBODY);
        UUID id = claims.get(0).getId();
        assertThatThrownBy(() -> service.approve(tenant, id, null, me, HR)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.approve(tenant, id, null, me, NOBODY)).isInstanceOf(AccessDeniedException.class);
        service.approve(tenant, id, "OK", hr, HR);
        assertThat(claims.get(0).getStatus()).isEqualTo("APPROVED");
        assertThat(claims.get(0).getDecidedBy()).isEqualTo(hr);
    }

    @Test
    void rejectingNeedsAReasonAndAnApprovedClaimCanBeTakenBackUntilPaid() {
        timesheet(new Object[]{0, "OVERTIME", 120, "PRESENT"});
        ask(me, NOBODY);
        UUID id = claims.get(0).getId();
        service.approve(tenant, id, null, hr, HR);
        assertThatThrownBy(() -> service.reject(tenant, id, " ", hr, HR)).isInstanceOf(IllegalArgumentException.class);
        service.reject(tenant, id, "Not authorised by the manager", hr, HR);
        assertThat(claims.get(0).getStatus()).isEqualTo("REJECTED");

        // a rejected claim cannot simply be approved: it needs a new request
        assertThatThrownBy(() -> service.approve(tenant, id, null, hr, HR)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aClaimInAPayRunCannotBeChanged() {
        timesheet(new Object[]{0, "OVERTIME", 120, "PRESENT"});
        ask(me, NOBODY);
        UUID id = claims.get(0).getId();
        service.approve(tenant, id, null, hr, HR);
        claims.get(0).linkTo(UUID.randomUUID());
        assertThatThrownBy(() -> service.reject(tenant, id, "Too late", hr, HR)).isInstanceOf(IllegalStateException.class).hasMessageContaining("pay run");
    }

    @Test
    void withdrawingIsForTheEmployeeOrHrAndOnlyWhileWaitingOrRejected() {
        timesheet(new Object[]{0, "OVERTIME", 120, "PRESENT"});
        ask(me, NOBODY);
        UUID id = claims.get(0).getId();
        assertThatThrownBy(() -> service.withdraw(tenant, id, UUID.randomUUID(), NOBODY)).isInstanceOf(AccessDeniedException.class);
        service.withdraw(tenant, id, me, NOBODY);
        assertThat(claims.get(0).getStatus()).isEqualTo("WITHDRAWN");
    }

    @Test
    void listingIsForApproversAndTeamManagers() {
        timesheet(new Object[]{0, "OVERTIME", 120, "PRESENT"});
        ask(me, NOBODY);
        assertThat(service.list(tenant, "PENDING", HR, hr)).hasSize(1);
        assertThat(service.list(tenant, "PENDING", HR, hr).get(0).canDecide()).isTrue();
        assertThatThrownBy(() -> service.list(tenant, null, NOBODY, me)).isInstanceOf(AccessDeniedException.class);
        UUID manager = UUID.randomUUID();
        when(viewService.teamOf(any(), eq(manager))).thenReturn(Set.of());
        assertThat(service.list(tenant, null, Set.of("HR_TEAM_READ"), manager)).isEmpty();
    }

    @Test
    void payrollTakesApprovedClaimsUpToThePeriodEndOnce() {
        timesheet(new Object[]{0, "OVERTIME", 120, "PRESENT"}, new Object[]{1, "OVERTIME", 60, "PRESENT"});
        ask(me, NOBODY);
        for (HrOvertimeClaim c : claims) service.approve(tenant, c.getId(), null, hr, HR);
        UUID run = UUID.randomUUID();

        Map<UUID, List<HrOvertimeClaim>> taken = service.takeFor(tenant, run, monday().plusDays(6));
        assertThat(taken.get(emp.getId())).hasSize(2);
        service.markTaken(taken.get(emp.getId()), run);

        // a second run does not get them, but the same run asked again does
        assertThat(service.takeFor(tenant, UUID.randomUUID(), monday().plusDays(6))).isEmpty();
        assertThat(service.takeFor(tenant, run, monday().plusDays(6)).get(emp.getId())).hasSize(2);
        // work after the period end waits for a later run
        assertThat(service.takeFor(tenant, UUID.randomUUID(), monday().minusDays(1))).isEmpty();
    }
}
