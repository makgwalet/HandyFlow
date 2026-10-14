package za.co.handyflow.platform.accountant.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accountant.application.internal.EngagementLetterService;
import za.co.handyflow.platform.accountant.application.internal.PracticeWorkloadService;
import za.co.handyflow.platform.accountant.application.internal.ServiceScheduleService;
import za.co.handyflow.platform.accountant.dto.PracticeServiceDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Engagement letters, recurring services, workload and WIP (finance F22). Reads need ACCOUNTANT_READ, changes ACCOUNTANT_WRITE. */
@RestController
@RequestMapping("/api/v1/accountant")
@RequiredArgsConstructor
@Tag(name = "Practice services")
public class PracticeServiceController {

    private final EngagementLetterService letters;
    private final ServiceScheduleService schedules;
    private final PracticeWorkloadService workload;

    private static <T> ResponseEntity<ApiResponse<T>> ok(String m, T body) { return ResponseEntity.ok(ApiResponse.success(m, body)); }

    // ── Engagement letters ──
    @GetMapping("/clients/{clientId}/letters")
    @PreAuthorize("hasAuthority('ACCOUNTANT_READ')")
    public ResponseEntity<ApiResponse<List<LetterResponse>>> listLetters(@PathVariable UUID clientId) {
        return ok("Success", letters.list(TenantContext.getTenantIdAsObject(), clientId));
    }

    @PostMapping("/clients/{clientId}/letters")
    @PreAuthorize("hasAuthority('ACCOUNTANT_WRITE')")
    public ResponseEntity<ApiResponse<LetterResponse>> createLetter(@PathVariable UUID clientId, @RequestBody LetterRequest req) {
        return ok("Letter drafted", letters.create(TenantContext.getTenantIdAsObject(), clientId, req, TenantContext.getCurrentUserId()));
    }

    @PutMapping("/letters/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTANT_WRITE')")
    public ResponseEntity<ApiResponse<LetterResponse>> updateLetter(@PathVariable UUID id, @RequestBody LetterRequest req) {
        return ok("Saved", letters.update(TenantContext.getTenantIdAsObject(), id, req));
    }

    @GetMapping("/letters/{id}/text")
    @PreAuthorize("hasAuthority('ACCOUNTANT_READ')")
    public ResponseEntity<ApiResponse<LetterText>> letterText(@PathVariable UUID id) {
        return ok("Success", letters.text(TenantContext.getTenantIdAsObject(), id));
    }

    @PostMapping("/letters/{id}/send")
    @PreAuthorize("hasAuthority('ACCOUNTANT_WRITE')")
    @Operation(summary = "Freeze the wording and mark the letter as sent to the client")
    public ResponseEntity<ApiResponse<LetterResponse>> sendLetter(@PathVariable UUID id) {
        return ok("Marked as sent", letters.send(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId()));
    }

    @PostMapping("/letters/{id}/accept")
    @PreAuthorize("hasAuthority('ACCOUNTANT_WRITE')")
    @Operation(summary = "Record that the client accepted (signed copy, email or other). Replaces any earlier accepted letter.")
    public ResponseEntity<ApiResponse<LetterResponse>> acceptLetter(@PathVariable UUID id, @RequestBody AcceptRequest req) {
        return ok("Acceptance recorded", letters.accept(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId()));
    }

    @PostMapping("/letters/{id}/decline")
    @PreAuthorize("hasAuthority('ACCOUNTANT_WRITE')")
    public ResponseEntity<ApiResponse<LetterResponse>> declineLetter(@PathVariable UUID id, @RequestBody(required = false) DeclineRequest req) {
        return ok("Recorded", letters.decline(TenantContext.getTenantIdAsObject(), id, req));
    }

    @PostMapping("/letters/{id}/end")
    @PreAuthorize("hasAuthority('ACCOUNTANT_WRITE')")
    public ResponseEntity<ApiResponse<LetterResponse>> endLetter(@PathVariable UUID id) {
        return ok("Ended", letters.end(TenantContext.getTenantIdAsObject(), id));
    }

    // ── Recurring services ──
    @GetMapping("/service-schedules")
    @PreAuthorize("hasAuthority('ACCOUNTANT_READ')")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> listSchedules(@RequestParam(required = false) UUID clientId) {
        return ok("Success", schedules.list(TenantContext.getTenantIdAsObject(), clientId));
    }

    @PostMapping("/clients/{clientId}/service-schedules")
    @PreAuthorize("hasAuthority('ACCOUNTANT_WRITE')")
    public ResponseEntity<ApiResponse<ScheduleResponse>> createSchedule(@PathVariable UUID clientId, @RequestBody ScheduleRequest req) {
        return ok("Schedule created", schedules.create(TenantContext.getTenantIdAsObject(), clientId, req, TenantContext.getCurrentUserId()));
    }

    @PostMapping("/service-schedules/{id}/{action}")
    @PreAuthorize("hasAuthority('ACCOUNTANT_WRITE')")
    @Operation(summary = "action is pause, resume or end")
    public ResponseEntity<ApiResponse<ScheduleResponse>> scheduleAction(@PathVariable UUID id, @PathVariable String action) {
        String target = switch (action) { case "pause" -> "PAUSED"; case "resume" -> "ACTIVE"; case "end" -> "ENDED";
            default -> throw new za.co.handyflow.platform.shared.HandyFlowException("Unknown action", org.springframework.http.HttpStatus.BAD_REQUEST, "BAD_ACTION"); };
        return ok("Updated", schedules.setStatus(TenantContext.getTenantIdAsObject(), id, target));
    }

    @GetMapping("/service-runs")
    @PreAuthorize("hasAuthority('ACCOUNTANT_READ')")
    public ResponseEntity<ApiResponse<List<RunResponse>>> runs(@RequestParam(defaultValue = "DUE") String status) {
        return ok("Success", schedules.runs(TenantContext.getTenantIdAsObject(), status));
    }

    @PostMapping("/service-runs/generate")
    @PreAuthorize("hasAuthority('ACCOUNTANT_WRITE')")
    @Operation(summary = "Check now for runs that have come due (the daily job does this too)")
    public ResponseEntity<ApiResponse<GenerateResult>> generate() {
        return ok("Checked", new GenerateResult(schedules.generateDue(TenantContext.getTenantIdAsObject().getValue(), LocalDate.now())));
    }

    @PostMapping("/service-runs/{id}/bill")
    @PreAuthorize("hasAuthority('ACCOUNTANT_WRITE')")
    @Operation(summary = "Make the fee note for this run (posts revenue like any fee note)")
    public ResponseEntity<ApiResponse<RunResponse>> bill(@PathVariable UUID id) {
        return ok("Fee note made", schedules.bill(TenantContext.getTenantIdAsObject(), id));
    }

    @PostMapping("/service-runs/{id}/skip")
    @PreAuthorize("hasAuthority('ACCOUNTANT_WRITE')")
    public ResponseEntity<ApiResponse<RunResponse>> skip(@PathVariable UUID id, @RequestBody SkipRequest req) {
        return ok("Skipped", schedules.skip(TenantContext.getTenantIdAsObject(), id, req.note()));
    }

    // ── Workload, WIP, profitability ──
    @GetMapping("/workload")
    @PreAuthorize("hasAuthority('ACCOUNTANT_READ')")
    @Operation(summary = "Per person: clients, deadlines, hours, unbilled time, runs due. Without all-clients access, only your own row.")
    public ResponseEntity<ApiResponse<List<WorkloadRow>>> workload() {
        return ok("Success", workload.workload(TenantContext.getTenantIdAsObject(), LocalDate.now()));
    }

    @GetMapping("/wip")
    @PreAuthorize("hasAuthority('ACCOUNTANT_READ')")
    public ResponseEntity<ApiResponse<WipReport>> wip(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ok("Success", workload.wip(TenantContext.getTenantIdAsObject(), asOf));
    }

    @GetMapping("/profitability")
    @PreAuthorize("hasAuthority('ACCOUNTANT_READ')")
    public ResponseEntity<ApiResponse<ProfitReport>> profitability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok("Success", workload.profitability(TenantContext.getTenantIdAsObject(), from, to));
    }
}
