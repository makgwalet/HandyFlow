package za.co.handyflow.platform.hr.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.AttendanceService;
import za.co.handyflow.platform.hr.application.internal.HrAccess;
import za.co.handyflow.platform.hr.dto.AttendanceDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Clocking in and out, timesheets, corrections and work schedules. */
@RestController
@RequestMapping("/api/v1/hr/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance", description = "Clock in and out, the day-by-day record, corrections, and work schedules")
public class AttendanceController {

    /** Anyone signed in: the service decides what they may see or do. */
    private static final String SIGNED_IN = "isAuthenticated()";
    private static final String MANAGE = "hasAnyAuthority('HR_MANAGE','HR_ATTENDANCE_MANAGE')";
    /** Corrections are also open to team managers; the service checks the person is in their team. */
    private static final String CORRECT = "hasAnyAuthority('HR_MANAGE','HR_ATTENDANCE_MANAGE','HR_TEAM_READ')";

    private final AttendanceService service;

    @GetMapping("/me")
    @PreAuthorize(SIGNED_IN)
    @Operation(summary = "Whether you are clocked in, and your last two weeks")
    public ResponseEntity<ApiResponse<Mine>> me(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(service.mine(TenantContext.getTenantIdAsObject(), user(auth))));
    }

    @PostMapping("/clock-in")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<Mine>> clockIn(Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Clocked in", service.clockIn(TenantContext.getTenantIdAsObject(), user(auth))));
    }

    @PostMapping("/clock-out")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<Mine>> clockOut(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Clocked out", service.clockOut(TenantContext.getTenantIdAsObject(), user(auth))));
    }

    @GetMapping("/timesheet")
    @PreAuthorize(SIGNED_IN)
    @Operation(summary = "One employee's days in a period (62 days at most). Your own, your team's, or anyone's with HR access")
    public ResponseEntity<ApiResponse<Timesheet>> timesheet(@RequestParam UUID employeeId,
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(service.timesheet(TenantContext.getTenantIdAsObject(), employeeId, from, to, HrAccess.held(auth), user(auth))));
    }

    @GetMapping("/register")
    @PreAuthorize(SIGNED_IN)
    @Operation(summary = "Everyone's attendance in a period, one row each. Team managers see their own team")
    public ResponseEntity<ApiResponse<List<RegisterRow>>> register(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                   Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(service.register(TenantContext.getTenantIdAsObject(), from, to, HrAccess.held(auth), user(auth))));
    }

    @PostMapping("/entries")
    @PreAuthorize(CORRECT)
    @Operation(summary = "Add a missed entry, with a reason")
    public ResponseEntity<ApiResponse<Timesheet>> add(@RequestBody EntryRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Entry added",
                service.add(TenantContext.getTenantIdAsObject(), req, user(auth), HrAccess.held(auth))));
    }

    @PutMapping("/entries/{id}")
    @PreAuthorize(CORRECT)
    @Operation(summary = "Correct the times of an entry, with a reason. The old times stay in the log")
    public ResponseEntity<ApiResponse<Timesheet>> correct(@PathVariable UUID id, @RequestBody EntryRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Entry corrected",
                service.correct(TenantContext.getTenantIdAsObject(), id, req, user(auth), HrAccess.held(auth))));
    }

    @PostMapping("/entries/{id}/void")
    @PreAuthorize(CORRECT)
    public ResponseEntity<ApiResponse<Timesheet>> voidEntry(@PathVariable UUID id, @RequestBody VoidRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Entry voided",
                service.voidEntry(TenantContext.getTenantIdAsObject(), id, req.reason(), user(auth), HrAccess.held(auth))));
    }

    // ── Schedules ───────────────────────────────────────────────────────────────────────────────

    @GetMapping("/schedules")
    @PreAuthorize(SIGNED_IN)
    public ResponseEntity<ApiResponse<List<Schedule>>> schedules() {
        return ResponseEntity.ok(ApiResponse.success(service.schedules(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/schedules")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Schedule>> addSchedule(@RequestBody ScheduleRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Schedule added",
                service.saveSchedule(TenantContext.getTenantIdAsObject(), null, req, HrAccess.held(auth))));
    }

    @PutMapping("/schedules/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Schedule>> saveSchedule(@PathVariable UUID id, @RequestBody ScheduleRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Schedule saved",
                service.saveSchedule(TenantContext.getTenantIdAsObject(), id, req, HrAccess.held(auth))));
    }

    @PutMapping("/schedules/{id}/default")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Schedule>> makeDefault(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(service.makeDefault(TenantContext.getTenantIdAsObject(), id, HrAccess.held(auth))));
    }

    @PutMapping("/schedules/{id}/active")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Schedule>> setActive(@PathVariable UUID id, @RequestBody ActiveRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(service.setScheduleActive(TenantContext.getTenantIdAsObject(), id, req.active(), HrAccess.held(auth))));
    }

    @PutMapping("/employees/{employeeId}/schedule")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Put an employee on a schedule")
    public ResponseEntity<ApiResponse<Schedule>> assign(@PathVariable UUID employeeId, @RequestBody AssignRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Schedule assigned",
                service.assign(TenantContext.getTenantIdAsObject(), employeeId, req.scheduleId(), user(auth), HrAccess.held(auth))));
    }

    private static UUID user(Authentication auth) {
        if (auth == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }
}
