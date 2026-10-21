package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.AttendanceDtos.*;
import za.co.handyflow.platform.shared.PublicHolidayRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AttendanceServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID me = UUID.randomUUID();       // the employee's login
    private final UUID hr = UUID.randomUUID();
    private static final Set<String> HR = Set.of("HR_ATTENDANCE_MANAGE", "HR_READ");
    private static final Set<String> NOBODY = Set.of();

    HrWorkScheduleRepository scheduleRepo;
    HrEmployeeScheduleRepository assignmentRepo;
    HrAttendanceEntryRepository entryRepo;
    HrAttendanceLogRepository logRepo;
    HrEmployeeRepository employeeRepo;
    HrLeaveRequestRepository leaveRepo;
    PublicHolidayRepository holidayRepo;
    EmployeeViewService viewService;
    OvertimeClaimLookup lookup;
    AttendanceService service;

    HrEmployee emp;
    final List<HrWorkSchedule> schedules = new ArrayList<>();
    final List<HrAttendanceEntry> entries = new ArrayList<>();
    final List<HrAttendanceLog> logs = new ArrayList<>();
    final List<HrLeaveRequest> leave = new ArrayList<>();
    final List<LocalDate> holidays = new ArrayList<>();
    String locked;

    @BeforeEach
    void setUp() {
        scheduleRepo = mock(HrWorkScheduleRepository.class);
        assignmentRepo = mock(HrEmployeeScheduleRepository.class);
        entryRepo = mock(HrAttendanceEntryRepository.class);
        logRepo = mock(HrAttendanceLogRepository.class);
        employeeRepo = mock(HrEmployeeRepository.class);
        leaveRepo = mock(HrLeaveRequestRepository.class);
        holidayRepo = mock(PublicHolidayRepository.class);
        viewService = mock(EmployeeViewService.class);
        lookup = mock(OvertimeClaimLookup.class);
        service = new AttendanceService(scheduleRepo, assignmentRepo, entryRepo, logRepo, employeeRepo, leaveRepo, holidayRepo, viewService, lookup);

        emp = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.of(2024, 1, 1), "PERMANENT", new BigDecimal("18000"), "MONTHLY");
        emp.setUserId(me);
        when(employeeRepo.findByUserId(any(), eq(me))).thenReturn(Optional.of(emp));
        when(employeeRepo.findActiveById(any(), eq(emp.getId()))).thenReturn(Optional.of(emp));
        when(employeeRepo.findAllActiveList(any())).thenReturn(List.of(emp));

        when(scheduleRepo.forTenant(any())).thenAnswer(i -> new ArrayList<>(schedules));
        when(scheduleRepo.defaultFor(any())).thenAnswer(i -> schedules.stream().filter(s -> s.isDefaultSchedule() && s.isActive()).findFirst());
        when(scheduleRepo.findByTenantAndId(any(), any())).thenAnswer(i -> schedules.stream().filter(s -> s.getId().equals(i.getArgument(1))).findFirst());
        when(scheduleRepo.save(any())).thenAnswer(i -> { HrWorkSchedule s = i.getArgument(0); if (!schedules.contains(s)) schedules.add(s); return s; });
        when(scheduleRepo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(assignmentRepo.forEmployee(any(), any())).thenReturn(Optional.empty());
        when(assignmentRepo.forTenant(any())).thenReturn(List.of());

        when(entryRepo.save(any())).thenAnswer(i -> { HrAttendanceEntry e = i.getArgument(0); if (!entries.contains(e)) entries.add(e); return e; });
        when(entryRepo.findByTenantAndId(any(), any())).thenAnswer(i -> entries.stream().filter(e -> e.getId().equals(i.getArgument(1))).findFirst());
        when(entryRepo.openFor(any(), any())).thenAnswer(i -> entries.stream().filter(HrAttendanceEntry::isOpen).findFirst());
        when(entryRepo.forEmployee(any(), any(), any(), any())).thenAnswer(i -> {
            LocalDate from = i.getArgument(2), to = i.getArgument(3);
            return entries.stream().filter(e -> "ACTIVE".equals(e.getStatus()) && !e.getWorkDate().isBefore(from) && !e.getWorkDate().isAfter(to)).toList();
        });
        when(entryRepo.forTenant(any(), any(), any())).thenAnswer(i -> new ArrayList<>(entries));
        when(entryRepo.overlapping(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(logRepo.save(any())).thenAnswer(i -> { logs.add(i.getArgument(0)); return i.getArgument(0); });
        when(logRepo.forEntries(any(), any())).thenAnswer(i -> new ArrayList<>(logs));

        when(leaveRepo.overlapping(any(), any(), any(), any())).thenAnswer(i -> new ArrayList<>(leave));
        when(holidayRepo.datesBetween(any(), any())).thenAnswer(i -> new ArrayList<>(holidays));
        when(lookup.claimsFor(any(), any(), any(), any())).thenReturn(Map.of());
        when(lookup.lockReason(any(), any(), any())).thenAnswer(i -> locked);
    }

    private static Instant at(LocalDate d, int h, int m) { return d.atTime(h, m).atZone(AttendanceRules.ZONE).toInstant(); }
    private static LocalDate today() { return LocalDate.now(AttendanceRules.ZONE); }
    /** A Monday at least two weeks ago, so the day is over. */
    private static LocalDate pastMonday() { return today().minusDays(14).with(DayOfWeek.MONDAY); }

    private Day dayOf(Timesheet t, LocalDate d) { return t.days().stream().filter(x -> x.date().equals(d)).findFirst().orElseThrow(); }

    @Test
    void theFirstCallMakesAStandardWeekTheDefault() {
        List<Schedule> list = service.schedules(tenant);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).isDefault()).isTrue();
        assertThat(list.get(0).weeklyMinutes()).isEqualTo(2400);
    }

    @Test
    void clockInThenOutMakesOneEntryWithALog() {
        service.clockIn(tenant, me);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).isOpen()).isTrue();
        Mine m = service.clockOut(tenant, me);
        assertThat(entries.get(0).getClockOut()).isNotNull();
        assertThat(m.clockedIn()).isFalse();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(1).getAction()).isEqualTo("CLOCK_OUT");
    }

    @Test
    void cannotClockInTwiceOrOutWhenNotIn() {
        assertThatThrownBy(() -> service.clockOut(tenant, me)).isInstanceOf(IllegalStateException.class).hasMessageContaining("not clocked in");
        service.clockIn(tenant, me);
        assertThatThrownBy(() -> service.clockIn(tenant, me)).isInstanceOf(IllegalStateException.class).hasMessageContaining("already clocked in");
    }

    @Test
    void aLoginWithNoEmployeeCannotClockIn() {
        assertThatThrownBy(() -> service.clockIn(tenant, UUID.randomUUID())).isInstanceOf(IllegalStateException.class).hasMessageContaining("not linked");
    }

    @Test
    void aTerminatedEmployeeCannotClockIn() {
        emp.terminate(LocalDate.now());
        assertThatThrownBy(() -> service.clockIn(tenant, me)).isInstanceOf(IllegalStateException.class).hasMessageContaining("active");
    }

    @Test
    void aFullDayIsPresentAndAMissingDayIsAbsent() {
        LocalDate mon = pastMonday();
        service.add(tenant, new EntryRequest(emp.getId(), at(mon, 8, 0), at(mon, 17, 0), "Forgot to clock"), hr, HR);
        Timesheet t = service.timesheet(tenant, emp.getId(), mon, mon.plusDays(2), HR, hr);
        assertThat(dayOf(t, mon).status()).isEqualTo("PRESENT");
        assertThat(dayOf(t, mon).workedMinutes()).isEqualTo(480);
        assertThat(dayOf(t, mon.plusDays(1)).status()).isEqualTo("ABSENT");
        assertThat(t.absentDays()).isEqualTo(2);
    }

    @Test
    void leaveAndHolidaysAreNotAbsence() {
        LocalDate mon = pastMonday();
        holidays.add(mon);
        HrLeaveRequest r = mock(HrLeaveRequest.class);
        when(r.getStatus()).thenReturn("APPROVED");
        when(r.getStartDate()).thenReturn(mon.plusDays(1));
        when(r.getEndDate()).thenReturn(mon.plusDays(1));
        when(r.isHalfDay()).thenReturn(false);
        leave.add(r);
        Timesheet t = service.timesheet(tenant, emp.getId(), mon, mon.plusDays(1), HR, hr);
        assertThat(dayOf(t, mon).status()).isEqualTo("PUBLIC_HOLIDAY");
        assertThat(dayOf(t, mon.plusDays(1)).status()).isEqualTo("ON_LEAVE");
        assertThat(t.absentDays()).isEqualTo(0);
    }

    @Test
    void overtimeShowsOnTheTimesheet() {
        LocalDate mon = pastMonday();
        service.add(tenant, new EntryRequest(emp.getId(), at(mon, 8, 0), at(mon, 19, 0), "Late job"), hr, HR);
        Timesheet t = service.timesheet(tenant, emp.getId(), mon, mon, HR, hr);
        assertThat(dayOf(t, mon).overtimeMinutes()).isEqualTo(120);
        assertThat(t.overtimeMinutes()).isEqualTo(120);
    }

    @Test
    void anEmployeeAboveTheThresholdIsMarked() {
        emp.setGrossSalary(new BigDecimal("30000"));
        LocalDate mon = pastMonday();
        assertThat(service.timesheet(tenant, emp.getId(), mon, mon, HR, hr).aboveThreshold()).isTrue();
    }

    @Test
    void correctingKeepsTheOldTimesInTheLogAndNeedsAReason() {
        LocalDate mon = pastMonday();
        service.add(tenant, new EntryRequest(emp.getId(), at(mon, 8, 0), at(mon, 16, 0), "Missed"), hr, HR);
        UUID id = entries.get(0).getId();

        assertThatThrownBy(() -> service.correct(tenant, id, new EntryRequest(null, at(mon, 8, 0), at(mon, 17, 0), " "), hr, HR))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reason");
        service.correct(tenant, id, new EntryRequest(null, at(mon, 8, 0), at(mon, 17, 0), "Left at five"), hr, HR);

        assertThat(entries.get(0).getClockOut()).isEqualTo(at(mon, 17, 0));
        HrAttendanceLog last = logs.get(logs.size() - 1);
        assertThat(last.getAction()).isEqualTo("CORRECT");
        assertThat(last.getOldOut()).isEqualTo(at(mon, 16, 0));
        assertThat(last.getNewOut()).isEqualTo(at(mon, 17, 0));
        assertThat(last.getReason()).isEqualTo("Left at five");
    }

    @Test
    void aVoidedEntryNoLongerCountsAndCannotBeVoidedAgain() {
        LocalDate mon = pastMonday();
        service.add(tenant, new EntryRequest(emp.getId(), at(mon, 8, 0), at(mon, 17, 0), "Added"), hr, HR);
        UUID id = entries.get(0).getId();
        service.voidEntry(tenant, id, "Entered by mistake", hr, HR);

        assertThat(dayOf(service.timesheet(tenant, emp.getId(), mon, mon, HR, hr), mon).workedMinutes()).isEqualTo(0);
        assertThatThrownBy(() -> service.voidEntry(tenant, id, "again", hr, HR)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.correct(tenant, id, new EntryRequest(null, at(mon, 8, 0), at(mon, 9, 0), "x"), hr, HR)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approvedOvertimeLocksTheDay() {
        LocalDate mon = pastMonday();
        service.add(tenant, new EntryRequest(emp.getId(), at(mon, 8, 0), at(mon, 19, 0), "Job"), hr, HR);
        UUID id = entries.get(0).getId();
        locked = "Overtime for this day has been approved. Reject it first if the time has to change.";

        assertThatThrownBy(() -> service.correct(tenant, id, new EntryRequest(null, at(mon, 8, 0), at(mon, 17, 0), "Trim"), hr, HR))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("approved");
        assertThatThrownBy(() -> service.voidEntry(tenant, id, "Trim", hr, HR)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.add(tenant, new EntryRequest(emp.getId(), at(mon, 20, 0), at(mon, 21, 0), "More"), hr, HR))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsOverlapsAndFutureTimes() {
        LocalDate mon = pastMonday();
        when(entryRepo.overlapping(any(), any(), any(), any(), any())).thenReturn(List.of(mock(HrAttendanceEntry.class)));
        assertThatThrownBy(() -> service.add(tenant, new EntryRequest(emp.getId(), at(mon, 8, 0), at(mon, 17, 0), "x"), hr, HR))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("overlaps");
        when(entryRepo.overlapping(any(), any(), any(), any(), any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.add(tenant, new EntryRequest(emp.getId(), at(today().plusDays(2), 8, 0), at(today().plusDays(2), 9, 0), "x"), hr, HR))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("future");
    }

    @Test
    void onlyHrOrTheManagerOfAPersonCanCorrectTheirTime() {
        LocalDate mon = pastMonday();
        EntryRequest req = new EntryRequest(emp.getId(), at(mon, 8, 0), at(mon, 17, 0), "x");
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() -> service.add(tenant, req, stranger, NOBODY)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.add(tenant, req, me, Set.of("HR_TEAM_READ"))).isInstanceOf(AccessDeniedException.class); // not their own

        UUID manager = UUID.randomUUID();
        when(viewService.teamOf(any(), eq(manager))).thenReturn(Set.of(emp.getId()));
        service.add(tenant, req, manager, Set.of("HR_TEAM_READ"));
        assertThat(entries).hasSize(1);

        UUID other = UUID.randomUUID();
        when(viewService.teamOf(any(), eq(other))).thenReturn(Set.of());
        assertThatThrownBy(() -> service.add(tenant, new EntryRequest(emp.getId(), at(mon, 18, 0), at(mon, 19, 0), "x"), other, Set.of("HR_TEAM_READ")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void peopleSeeTheirOwnTimesheetAndManagersTheirTeamButNotOthers() {
        LocalDate mon = pastMonday();
        service.timesheet(tenant, emp.getId(), mon, mon, NOBODY, me);

        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> service.timesheet(tenant, emp.getId(), mon, mon, NOBODY, stranger)).isInstanceOf(AccessDeniedException.class);

        UUID manager = UUID.randomUUID();
        when(viewService.teamOf(any(), eq(manager))).thenReturn(Set.of(emp.getId()));
        assertThat(service.timesheet(tenant, emp.getId(), mon, mon, Set.of("HR_TEAM_READ"), manager).canCorrect()).isTrue();
        assertThat(service.timesheet(tenant, emp.getId(), mon, mon, NOBODY, me).canCorrect()).isFalse();
    }

    @Test
    void theRegisterCountsPresentLateAndAbsentDays() {
        LocalDate mon = pastMonday();
        service.add(tenant, new EntryRequest(emp.getId(), at(mon, 8, 0), at(mon, 17, 0), "x"), hr, HR);
        service.add(tenant, new EntryRequest(emp.getId(), at(mon.plusDays(1), 9, 30), at(mon.plusDays(1), 17, 30), "x"), hr, HR);

        List<RegisterRow> rows = service.register(tenant, mon, mon.plusDays(2), HR, hr);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).presentDays()).isEqualTo(2);
        assertThat(rows.get(0).lateDays()).isEqualTo(1);
        assertThat(rows.get(0).absentDays()).isEqualTo(1);
        assertThatThrownBy(() -> service.register(tenant, mon, mon.plusDays(2), NOBODY, UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void refusesVeryLongPeriods() {
        LocalDate mon = pastMonday();
        assertThatThrownBy(() -> service.timesheet(tenant, emp.getId(), mon, mon.plusDays(90), HR, hr)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("62");
    }

    @Test
    void scheduleChangesNeedPermissionAndSense() {
        ScheduleRequest ok = new ScheduleRequest("Early", List.of("MON", "TUE"), LocalTime.of(6, 0), LocalTime.of(14, 0), 30);
        assertThatThrownBy(() -> service.saveSchedule(tenant, null, ok, NOBODY)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.saveSchedule(tenant, null, new ScheduleRequest("Bad", List.of(), LocalTime.of(6, 0), LocalTime.of(14, 0), 30), HR))
                .isInstanceOf(IllegalArgumentException.class);
        Schedule s = service.saveSchedule(tenant, null, ok, HR);
        assertThat(s.isDefault()).isFalse();
        assertThat(s.dailyMinutes()).isEqualTo(450);
    }

    @Test
    void theDefaultCannotBeSwitchedOffAndTheNewDefaultReplacesTheOld() {
        List<Schedule> first = service.schedules(tenant);
        Schedule early = service.saveSchedule(tenant, null, new ScheduleRequest("Early", List.of("MON"), LocalTime.of(6, 0), LocalTime.of(14, 0), 30), HR);
        assertThatThrownBy(() -> service.setScheduleActive(tenant, first.get(0).id(), false, HR)).isInstanceOf(IllegalStateException.class);
        service.makeDefault(tenant, early.id(), HR);
        assertThat(schedules.stream().filter(HrWorkSchedule::isDefaultSchedule).count()).isEqualTo(1L);
    }

    @Test
    void anEmployeeWorksTheirAssignedScheduleNotTheDefault() {
        service.schedules(tenant);
        Schedule four = service.saveSchedule(tenant, null, new ScheduleRequest("Four days", List.of("MON", "TUE", "WED", "THU"), LocalTime.of(7, 0), LocalTime.of(17, 0), 60), HR);
        HrEmployeeSchedule a = HrEmployeeSchedule.of(tenant.getValue(), emp.getId(), four.id(), hr);
        when(assignmentRepo.forEmployee(any(), eq(emp.getId()))).thenReturn(Optional.of(a));

        LocalDate fri = pastMonday().plusDays(4);
        Timesheet t = service.timesheet(tenant, emp.getId(), fri, fri, HR, hr);
        assertThat(t.scheduleName()).isEqualTo("Four days");
        assertThat(dayOf(t, fri).status()).isEqualTo("REST_DAY");
    }

    @Test
    void entryTimesAreReadInSouthAfricanTime() {
        // 23:30 UTC on Sunday is 01:30 on Monday in South Africa
        Instant in = LocalDate.of(2026, 11, 1).atTime(23, 30).toInstant(ZoneOffset.UTC);
        HrAttendanceEntry e = HrAttendanceEntry.of(tenant.getValue(), emp.getId(), in, null, "CLOCK", me);
        assertThat(e.getWorkDate()).isEqualTo(LocalDate.of(2026, 11, 2));
    }
}
