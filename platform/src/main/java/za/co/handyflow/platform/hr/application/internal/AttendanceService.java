package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.AttendanceDtos.*;
import za.co.handyflow.platform.shared.PublicHolidayRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Clocking in and out, the day-by-day record that follows from it, and the corrections that keep that record honest.
 * <p>
 * An employee clocks themselves in and out (their login is linked to their employee record). Nobody edits an entry silently:
 * a manager or HR corrects it with a reason, and the old values stay in the log. The meaning of a day (late, absent, overtime)
 * is worked out by {@link AttendanceRules} from the entries, the employee's schedule, public holidays and approved leave, each
 * time it is asked for, so changing a schedule or fixing an entry changes the answer everywhere.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService implements AttendanceRegister {

    private static final int MAX_DAYS = 62;

    private final HrWorkScheduleRepository scheduleRepo;
    private final HrEmployeeScheduleRepository assignmentRepo;
    private final HrAttendanceEntryRepository entryRepo;
    private final HrAttendanceLogRepository logRepo;
    private final HrEmployeeRepository employeeRepo;
    private final HrLeaveRequestRepository leaveRepo;
    private final PublicHolidayRepository holidayRepo;
    private final EmployeeViewService viewService;
    private final OvertimeClaimLookup overtimeLock;

    // ── Schedules ───────────────────────────────────────────────────────────────────────────────

    @Transactional
    public List<Schedule> schedules(TenantId tenantId) {
        ensureDefault(tenantId);
        WorkingTimeRules.Values rules = WorkingTimeRules.on(LocalDate.now(AttendanceRules.ZONE));
        return scheduleRepo.forTenant(tenantId).stream().map(s -> schedule(s, rules)).toList();
    }

    @Transactional
    public Schedule saveSchedule(TenantId tenantId, UUID id, ScheduleRequest req, Set<String> held) {
        requireManage(held);
        Set<DayOfWeek> days = AttendanceRules.parseDays(req.days() == null ? null : String.join(",", req.days()));
        int brk = req.breakMinutes() == null ? 0 : req.breakMinutes();
        fail(AttendanceRules.scheduleProblem(req.name(), days, req.startTime(), req.endTime(), brk));
        AttendanceRules.Schedule s = new AttendanceRules.Schedule(days, req.startTime(), req.endTime(), brk);
        HrWorkSchedule w;
        if (id == null) {
            ensureDefault(tenantId);
            w = HrWorkSchedule.of(tenantId.getValue(), req.name(), s, false);
        } else {
            w = scheduleOf(tenantId, id);
            w.apply(req.name(), s);
        }
        return schedule(scheduleRepo.save(w), WorkingTimeRules.on(LocalDate.now(AttendanceRules.ZONE)));
    }

    @Transactional
    public Schedule makeDefault(TenantId tenantId, UUID id, Set<String> held) {
        requireManage(held);
        HrWorkSchedule w = scheduleOf(tenantId, id);
        if (!w.isActive()) throw new IllegalStateException("An inactive schedule cannot be the default.");
        scheduleRepo.defaultFor(tenantId).ifPresent(d -> { d.setDefaultSchedule(false); scheduleRepo.saveAndFlush(d); });
        w.setDefaultSchedule(true);
        return schedule(scheduleRepo.save(w), WorkingTimeRules.on(LocalDate.now(AttendanceRules.ZONE)));
    }

    @Transactional
    public Schedule setScheduleActive(TenantId tenantId, UUID id, boolean active, Set<String> held) {
        requireManage(held);
        HrWorkSchedule w = scheduleOf(tenantId, id);
        if (!active && w.isDefaultSchedule()) throw new IllegalStateException("Make another schedule the default before switching this one off.");
        if (!active && assignmentRepo.forTenant(tenantId).stream().anyMatch(a -> a.getScheduleId().equals(id)))
            throw new IllegalStateException("People are still on this schedule. Move them to another one first.");
        w.setActive(active);
        return schedule(scheduleRepo.save(w), WorkingTimeRules.on(LocalDate.now(AttendanceRules.ZONE)));
    }

    @Transactional
    public Schedule assign(TenantId tenantId, UUID employeeId, UUID scheduleId, UUID actor, Set<String> held) {
        requireManage(held);
        employee(tenantId, employeeId);
        HrWorkSchedule w = scheduleOf(tenantId, scheduleId);
        if (!w.isActive()) throw new IllegalStateException("That schedule is switched off.");
        HrEmployeeSchedule a = assignmentRepo.forEmployee(tenantId, employeeId).orElse(null);
        if (a == null) assignmentRepo.save(HrEmployeeSchedule.of(tenantId.getValue(), employeeId, scheduleId, actor));
        else { a.reassign(scheduleId, actor); assignmentRepo.save(a); }
        return schedule(w, WorkingTimeRules.on(LocalDate.now(AttendanceRules.ZONE)));
    }

    /** The schedule an employee works: their own, or the tenant's default (made if there is none). */
    HrWorkSchedule scheduleFor(TenantId tenantId, UUID employeeId) {
        Optional<HrEmployeeSchedule> a = assignmentRepo.forEmployee(tenantId, employeeId);
        if (a.isPresent()) {
            Optional<HrWorkSchedule> w = scheduleRepo.findByTenantAndId(tenantId, a.get().getScheduleId());
            if (w.isPresent()) return w.get();
        }
        return ensureDefault(tenantId);
    }

    private HrWorkSchedule ensureDefault(TenantId tenantId) {
        Optional<HrWorkSchedule> d = scheduleRepo.defaultFor(tenantId);
        if (d.isPresent()) return d.get();
        if (!scheduleRepo.forTenant(tenantId).isEmpty()) throw new IllegalStateException("No schedule is marked as the default. Choose one under Schedules.");
        AttendanceRules.Schedule std = new AttendanceRules.Schedule(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(8, 0), LocalTime.of(17, 0), 60);
        return scheduleRepo.save(HrWorkSchedule.of(tenantId.getValue(), "Standard week", std, true));
    }

    // ── Clocking ────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public Mine clockIn(TenantId tenantId, UUID userId) {
        HrEmployee emp = me(tenantId, userId);
        if (!"ACTIVE".equals(emp.getStatus())) throw new IllegalStateException("Only active employees can clock in.");
        if (entryRepo.openFor(tenantId, emp.getId()).isPresent()) throw new IllegalStateException("You are already clocked in.");
        Instant now = Instant.now();
        HrAttendanceEntry e = entryRepo.save(HrAttendanceEntry.of(tenantId.getValue(), emp.getId(), now, null, HrAttendanceEntry.CLOCK, userId));
        logRepo.save(HrAttendanceLog.of(tenantId.getValue(), e.getId(), "ADD", null, null, now, null, "Clocked in", userId));
        return mine(tenantId, userId);
    }

    @Transactional
    public Mine clockOut(TenantId tenantId, UUID userId) {
        HrEmployee emp = me(tenantId, userId);
        HrAttendanceEntry e = entryRepo.openFor(tenantId, emp.getId()).orElseThrow(() -> new IllegalStateException("You are not clocked in."));
        Instant now = Instant.now();
        if (!now.isAfter(e.getClockIn())) throw new IllegalStateException("The clock-out time must be after the clock-in time.");
        e.clockOutAt(now);
        entryRepo.save(e);
        logRepo.save(HrAttendanceLog.of(tenantId.getValue(), e.getId(), "CLOCK_OUT", e.getClockIn(), null, e.getClockIn(), now, "Clocked out", userId));
        return mine(tenantId, userId);
    }

    @Transactional
    public Mine mine(TenantId tenantId, UUID userId) {
        HrEmployee emp = me(tenantId, userId);
        Optional<HrAttendanceEntry> open = entryRepo.openFor(tenantId, emp.getId());
        LocalDate today = LocalDate.now(AttendanceRules.ZONE);
        Timesheet t = timesheetOf(tenantId, emp, today.minusDays(13), today, false);
        return new Mine(emp.getId(), open.isPresent(), open.map(HrAttendanceEntry::getClockIn).orElse(null), scheduleFor(tenantId, emp.getId()).getName(), t);
    }

    // ── Reading ─────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public Timesheet timesheet(TenantId tenantId, UUID employeeId, LocalDate from, LocalDate to, Set<String> held, UUID actor) {
        HrEmployee emp = employee(tenantId, employeeId);
        checkMayView(tenantId, emp, held, actor);
        return timesheetOf(tenantId, emp, from, to, canCorrect(tenantId, emp, held, actor));
    }

    @Override
    @Transactional
    public List<RegisterRow> register(TenantId tenantId, LocalDate from, LocalDate to, Set<String> held, UUID actor) {
        checkRange(from, to);
        List<HrEmployee> people = employeeRepo.findAllActiveList(tenantId);
        if (HrAccess.teamOnly(held)) {
            Set<UUID> team = viewService.teamOf(tenantId, actor);
            people = people.stream().filter(e -> team.contains(e.getId())).toList();
        } else if (!HrAccess.seesAll(held) && !canManage(held)) {
            throw new AccessDeniedException("You do not have access to the attendance register.");
        }
        Map<UUID, List<HrAttendanceEntry>> entries = entryRepo.forTenant(tenantId, from, to).stream().collect(Collectors.groupingBy(HrAttendanceEntry::getEmployeeId));
        Set<LocalDate> holidays = new HashSet<>(holidayRepo.datesBetween(from, to));
        Map<UUID, List<HrLeaveRequest>> leave = new HashMap<>();
        for (HrEmployee e : people) leave.put(e.getId(), approvedLeave(tenantId, e.getId(), from, to));
        List<RegisterRow> out = new ArrayList<>();
        for (HrEmployee e : people) {
            Timesheet t = build(tenantId, e, from, to, entries.getOrDefault(e.getId(), List.of()), holidays, leave.get(e.getId()), Map.of(), false);
            int present = 0, late = 0, absent = 0, onLeave = 0, flagged = 0;
            for (Day d : t.days()) {
                switch (d.status()) {
                    case AttendanceRules.PRESENT -> present++;
                    case AttendanceRules.LATE -> { present++; late++; }
                    case AttendanceRules.ABSENT -> absent++;
                    case AttendanceRules.ON_LEAVE -> onLeave++;
                    default -> { }
                }
                if (!d.flags().isEmpty()) flagged++;
            }
            out.add(new RegisterRow(e.getId(), e.getEmployeeNumber(), e.getFullName(), e.getDepartment(), present, late, absent, onLeave,
                    t.workedMinutes(), t.overtimeMinutes(), flagged + t.weekFlags().size()));
        }
        return out;
    }

    // ── Corrections ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public Timesheet add(TenantId tenantId, EntryRequest req, UUID actor, Set<String> held) {
        if (req.employeeId() == null) throw new IllegalArgumentException("Choose the employee.");
        HrEmployee emp = employee(tenantId, req.employeeId());
        requireMayCorrect(tenantId, emp, held, actor);
        fail(reasonProblem(req.reason()));
        fail(AttendanceRules.entryProblem(req.clockIn(), req.clockOut(), Instant.now()));
        if (req.clockOut() == null) throw new IllegalArgumentException("Enter the time out. An open entry can only be made by clocking in.");
        lockCheck(tenantId, emp.getId(), AttendanceRules.workDate(req.clockIn()));
        noOverlap(tenantId, emp.getId(), req.clockIn(), req.clockOut(), new UUID(0, 0));
        HrAttendanceEntry e = entryRepo.save(HrAttendanceEntry.of(tenantId.getValue(), emp.getId(), req.clockIn(), req.clockOut(), HrAttendanceEntry.MANUAL, actor));
        logRepo.save(HrAttendanceLog.of(tenantId.getValue(), e.getId(), "ADD", null, null, req.clockIn(), req.clockOut(), req.reason().trim(), actor));
        LocalDate d = e.getWorkDate();
        return timesheetOf(tenantId, emp, d.minusDays(6), d.plusDays(6), true);
    }

    @Transactional
    public Timesheet correct(TenantId tenantId, UUID entryId, EntryRequest req, UUID actor, Set<String> held) {
        HrAttendanceEntry e = entry(tenantId, entryId);
        HrEmployee emp = employee(tenantId, e.getEmployeeId());
        requireMayCorrect(tenantId, emp, held, actor);
        if (!HrAttendanceEntry.ACTIVE.equals(e.getStatus())) throw new IllegalStateException("This entry has been voided.");
        fail(reasonProblem(req.reason()));
        fail(AttendanceRules.entryProblem(req.clockIn(), req.clockOut(), Instant.now()));
        lockCheck(tenantId, emp.getId(), e.getWorkDate());
        lockCheck(tenantId, emp.getId(), AttendanceRules.workDate(req.clockIn()));
        noOverlap(tenantId, emp.getId(), req.clockIn(), req.clockOut(), e.getId());
        Instant oldIn = e.getClockIn(), oldOut = e.getClockOut();
        e.correct(req.clockIn(), req.clockOut());
        entryRepo.save(e);
        logRepo.save(HrAttendanceLog.of(tenantId.getValue(), e.getId(), "CORRECT", oldIn, oldOut, req.clockIn(), req.clockOut(), req.reason().trim(), actor));
        LocalDate d = e.getWorkDate();
        return timesheetOf(tenantId, emp, d.minusDays(6), d.plusDays(6), true);
    }

    @Transactional
    public Timesheet voidEntry(TenantId tenantId, UUID entryId, String reason, UUID actor, Set<String> held) {
        HrAttendanceEntry e = entry(tenantId, entryId);
        HrEmployee emp = employee(tenantId, e.getEmployeeId());
        requireMayCorrect(tenantId, emp, held, actor);
        if (!HrAttendanceEntry.ACTIVE.equals(e.getStatus())) throw new IllegalStateException("This entry has already been voided.");
        fail(reasonProblem(reason));
        lockCheck(tenantId, emp.getId(), e.getWorkDate());
        e.voidEntry();
        entryRepo.save(e);
        logRepo.save(HrAttendanceLog.of(tenantId.getValue(), e.getId(), "VOID", e.getClockIn(), e.getClockOut(), null, null, reason.trim(), actor));
        LocalDate d = e.getWorkDate();
        return timesheetOf(tenantId, emp, d.minusDays(6), d.plusDays(6), true);
    }

    // ── Building a timesheet ────────────────────────────────────────────────────────────────────

    /** For other services in this module that have already decided the caller may see this employee. */
    Timesheet timesheetFor(TenantId tenantId, HrEmployee emp, LocalDate from, LocalDate to) {
        return timesheetOf(tenantId, emp, from, to, false);
    }

    private Timesheet timesheetOf(TenantId tenantId, HrEmployee emp, LocalDate from, LocalDate to, boolean canCorrect) {
        checkRange(from, to);
        List<HrAttendanceEntry> entries = entryRepo.forEmployee(tenantId, emp.getId(), from, to);
        Set<LocalDate> holidays = new HashSet<>(holidayRepo.datesBetween(from, to));
        return build(tenantId, emp, from, to, entries, holidays, approvedLeave(tenantId, emp.getId(), from, to),
                overtimeLock.claimsFor(tenantId, emp.getId(), from, to), canCorrect);
    }

    private Timesheet build(TenantId tenantId, HrEmployee emp, LocalDate from, LocalDate to, List<HrAttendanceEntry> entries, Set<LocalDate> holidays,
                            List<HrLeaveRequest> leave, Map<LocalDate, OvertimeClaimLookup.ClaimRef> claims, boolean canCorrect) {
        HrWorkSchedule ws = scheduleFor(tenantId, emp.getId());
        AttendanceRules.Schedule sch = ws.toSchedule();
        WorkingTimeRules.Values rules = WorkingTimeRules.on(to);
        LocalDate today = LocalDate.now(AttendanceRules.ZONE);
        Map<LocalDate, List<HrAttendanceEntry>> byDay = entries.stream().collect(Collectors.groupingBy(HrAttendanceEntry::getWorkDate));
        Map<UUID, List<HrAttendanceLog>> logs = entries.isEmpty() ? Map.of()
                : logRepo.forEntries(tenantId, entries.stream().map(HrAttendanceEntry::getId).toList()).stream().collect(Collectors.groupingBy(HrAttendanceLog::getEntryId));

        List<Day> days = new ArrayList<>();
        List<AttendanceRules.Day> ruleDays = new ArrayList<>();
        Instant prevOut = null;
        int worked = 0, late = 0, absent = 0, overtime = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<HrAttendanceEntry> day = byDay.getOrDefault(d, List.of());
            List<AttendanceRules.Punch> punches = day.stream().map(e -> new AttendanceRules.Punch(e.getClockIn(), e.getClockOut())).toList();
            boolean open = day.stream().anyMatch(HrAttendanceEntry::isOpen);
            Instant firstIn = day.stream().map(HrAttendanceEntry::getClockIn).min(Comparator.naturalOrder()).orElse(null);
            int mins = AttendanceRules.workedMinutes(punches, ws.getBreakMinutes(), rules);
            final LocalDate date = d;
            Optional<HrLeaveRequest> lv = leave.stream().filter(r -> !date.isBefore(r.getStartDate()) && !date.isAfter(r.getEndDate())).findFirst();
            boolean halfDayLeave = lv.isPresent() && lv.get().isHalfDay();
            AttendanceRules.Day r = AttendanceRules.day(d, sch, holidays.contains(d), lv.isPresent() && !halfDayLeave, mins, firstIn, open, today, rules);
            List<String> flags = new ArrayList<>(r.flags());
            int shortMinutes = r.shortMinutes();
            if (halfDayLeave) { flags.add("Half-day leave."); shortMinutes = 0; }
            if (firstIn != null) {
                String rest = AttendanceRules.restProblem(prevOut, firstIn, rules);
                if (rest != null) flags.add(rest);
            }
            Instant lastOut = day.stream().map(HrAttendanceEntry::getClockOut).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            if (lastOut != null) prevOut = lastOut;

            AttendanceRules.Day fin = new AttendanceRules.Day(r.date(), r.status(), r.expectedMinutes(), r.workedMinutes(), r.lateMinutes(), shortMinutes,
                    r.overtimeKind(), r.overtimeMinutes(), flags);
            ruleDays.add(fin);
            OvertimeClaimLookup.ClaimRef claim = claims.get(d);
            days.add(new Day(d, r.status(), r.expectedMinutes(), mins, r.lateMinutes(), shortMinutes, r.overtimeKind(), r.overtimeMinutes(), flags,
                    day.stream().map(e -> entryView(e, logs.getOrDefault(e.getId(), List.of()))).toList(),
                    claim == null ? null : claim.id(), claim == null ? null : claim.status()));
            worked += mins;
            if (AttendanceRules.LATE.equals(r.status())) late++;
            if (AttendanceRules.ABSENT.equals(r.status())) absent++;
            if (!AttendanceRules.OT_HOLIDAY.equals(r.overtimeKind())) overtime += r.overtimeMinutes();
        }

        List<String> weekFlags = new ArrayList<>();
        Map<LocalDate, List<AttendanceRules.Day>> weeks = new TreeMap<>();
        for (AttendanceRules.Day d : ruleDays) weeks.computeIfAbsent(d.date().with(DayOfWeek.MONDAY), k -> new ArrayList<>()).add(d);
        weeks.forEach((monday, list) -> {
            // A week that is only partly inside the range cannot be judged against the weekly limits.
            boolean whole = !monday.isBefore(from) && !monday.plusDays(6).isAfter(to);
            if (whole) for (String f : AttendanceRules.weekFlags(list, rules)) weekFlags.add("Week of " + monday + ": " + f);
        });

        boolean above = WorkingTimeRules.aboveThreshold(emp.getGrossSalary(), rules);
        return new Timesheet(emp.getId(), emp.getEmployeeNumber(), emp.getFullName(), ws.getName(), sch.weeklyMinutes(), from, to, days, weekFlags,
                worked, late, absent, overtime, canCorrect, above);
    }

    private Entry entryView(HrAttendanceEntry e, List<HrAttendanceLog> log) {
        boolean corrected = log.stream().anyMatch(l -> "CORRECT".equals(l.getAction())) || HrAttendanceEntry.MANUAL.equals(e.getSource());
        return new Entry(e.getId(), e.getClockIn(), e.getClockOut(), e.getSource(), corrected,
                log.stream().map(l -> new LogLine(l.getAction(), l.getOldIn(), l.getOldOut(), l.getNewIn(), l.getNewOut(), l.getReason(), l.getDoneBy(), l.getDoneAt())).toList());
    }

    private List<HrLeaveRequest> approvedLeave(TenantId tenantId, UUID employeeId, LocalDate from, LocalDate to) {
        return leaveRepo.overlapping(tenantId, employeeId, from, to).stream().filter(r -> "APPROVED".equals(r.getStatus())).toList();
    }

    // ── Access and checks ───────────────────────────────────────────────────────────────────────

    static boolean canManage(Set<String> held) { return held.contains("HR_ATTENDANCE_MANAGE") || held.contains("HR_MANAGE"); }

    private static void requireManage(Set<String> held) {
        if (!canManage(held)) throw new AccessDeniedException("You need the attendance management permission for this.");
    }

    private void checkMayView(TenantId tenantId, HrEmployee emp, Set<String> held, UUID actor) {
        if (actor != null && actor.equals(emp.getUserId())) return;
        if (HrAccess.seesAll(held) || canManage(held)) return;
        if (HrAccess.teamOnly(held) && viewService.teamOf(tenantId, actor).contains(emp.getId())) return;
        throw new AccessDeniedException("You can only see your own attendance and that of your team.");
    }

    private boolean canCorrect(TenantId tenantId, HrEmployee emp, Set<String> held, UUID actor) {
        if (canManage(held)) return true;
        if (!held.contains("HR_TEAM_READ")) return false;
        if (actor != null && actor.equals(emp.getUserId())) return false; // nobody corrects their own time without HR
        return viewService.teamOf(tenantId, actor).contains(emp.getId());
    }

    private void requireMayCorrect(TenantId tenantId, HrEmployee emp, Set<String> held, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this change cannot be recorded.");
        if (!canCorrect(tenantId, emp, held, actor)) throw new AccessDeniedException("Only HR, or the manager of this person, can correct their time.");
    }

    private void lockCheck(TenantId tenantId, UUID employeeId, LocalDate date) {
        String why = overtimeLock.lockReason(tenantId, employeeId, date);
        if (why != null) throw new IllegalStateException(why);
    }

    private void noOverlap(TenantId tenantId, UUID employeeId, Instant in, Instant out, UUID exceptId) {
        Instant end = out == null ? in.plusSeconds(1) : out;
        if (!entryRepo.overlapping(tenantId, employeeId, in, end, exceptId).isEmpty())
            throw new IllegalStateException("That overlaps another entry for this person.");
    }

    private static String reasonProblem(String reason) {
        if (reason == null || reason.isBlank()) return "Give the reason for the change.";
        if (reason.trim().length() > 500) return "Keep the reason under 500 characters.";
        return null;
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new IllegalArgumentException("Choose the dates.");
        if (to.isBefore(from)) throw new IllegalArgumentException("The end date is before the start date.");
        if (from.plusDays(MAX_DAYS).isBefore(to)) throw new IllegalArgumentException("Choose a period of " + MAX_DAYS + " days or less.");
    }

    private static void fail(String problem) { if (problem != null) throw new IllegalArgumentException(problem); }

    private Schedule schedule(HrWorkSchedule w, WorkingTimeRules.Values rules) {
        AttendanceRules.Schedule s = w.toSchedule();
        List<String> days = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) if (s.works(d)) days.add(d.name().substring(0, 3));
        return new Schedule(w.getId(), w.getName(), days, w.getStartTime(), w.getEndTime(), w.getBreakMinutes(), w.isDefaultSchedule(), w.isActive(),
                s.dailyMinutes(), s.weeklyMinutes(), AttendanceRules.scheduleWarnings(s, rules));
    }

    private HrEmployee me(TenantId tenantId, UUID userId) {
        if (userId == null) throw new AccessDeniedException("Your user could not be identified.");
        return employeeRepo.findByUserId(tenantId, userId).orElseThrow(() -> new IllegalStateException("Your login is not linked to an employee record. Ask HR to link it."));
    }

    private HrEmployee employee(TenantId tenantId, UUID id) {
        return employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
    }

    private HrWorkSchedule scheduleOf(TenantId tenantId, UUID id) {
        return scheduleRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("WorkSchedule", id.toString()));
    }

    private HrAttendanceEntry entry(TenantId tenantId, UUID id) {
        return entryRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("AttendanceEntry", id.toString()));
    }
}
