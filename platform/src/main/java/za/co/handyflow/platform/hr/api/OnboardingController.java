package za.co.handyflow.platform.hr.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.HrAccess;
import za.co.handyflow.platform.hr.application.internal.OnboardingService;
import za.co.handyflow.platform.hr.dto.OnboardingDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Onboarding of new employees and the task list it is made from. */
@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
@Tag(name = "Onboarding", description = "Tasks to finish when someone joins, checked against the employee record")
public class OnboardingController {

    private static final String READ = "hasAnyAuthority('HR_READ','HR_MANAGE','HR_TEAM_READ','HR_ONBOARDING_MANAGE')";
    /** Anyone signed in may try: the service lets only managers and the person the task is assigned to tick it. */
    private static final String WORK = "isAuthenticated()";
    private static final String MANAGE = "hasAnyAuthority('HR_MANAGE','HR_ONBOARDING_MANAGE')";

    private final OnboardingService service;

    @GetMapping("/onboardings")
    @PreAuthorize(READ)
    @Operation(summary = "Onboardings with their progress. Managers with team access see their own team only")
    public ResponseEntity<ApiResponse<List<Summary>>> list(@RequestParam(required = false) String status, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status, HrAccess.held(auth), user(auth))));
    }

    @GetMapping("/onboardings/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<Detail>> get(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id, HrAccess.held(auth), user(auth))));
    }

    @PostMapping("/employees/{employeeId}/onboarding")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Start onboarding for an employee. If one exists it is returned as it is")
    public ResponseEntity<ApiResponse<Detail>> start(@PathVariable UUID employeeId, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Onboarding started",
                service.startFor(TenantContext.getTenantIdAsObject(), employeeId, user(auth), HrAccess.held(auth))));
    }

    @PostMapping("/onboardings/{id}/tasks")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> addTask(@PathVariable UUID id, @RequestBody TaskRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Task added",
                service.addTask(TenantContext.getTenantIdAsObject(), id, req, user(auth), HrAccess.held(auth))));
    }

    @PostMapping("/onboarding-tasks/{taskId}/complete")
    @PreAuthorize(WORK)
    @Operation(summary = "Tick a task. Managers can tick any; others only tasks assigned to them. Tasks checked against the record cannot be ticked")
    public ResponseEntity<ApiResponse<Detail>> complete(@PathVariable UUID taskId, @RequestBody(required = false) NoteRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Task done",
                service.complete(TenantContext.getTenantIdAsObject(), taskId, req == null ? null : req.note(), user(auth), HrAccess.held(auth))));
    }

    @PostMapping("/onboarding-tasks/{taskId}/waive")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> waive(@PathVariable UUID taskId, @RequestBody NoteRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Task waived",
                service.waive(TenantContext.getTenantIdAsObject(), taskId, req.note(), user(auth), HrAccess.held(auth))));
    }

    @PostMapping("/onboarding-tasks/{taskId}/reopen")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> reopen(@PathVariable UUID taskId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Task reopened",
                service.reopen(TenantContext.getTenantIdAsObject(), taskId, user(auth), HrAccess.held(auth))));
    }

    @PutMapping("/onboarding-tasks/{taskId}/assignee")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> assign(@PathVariable UUID taskId, @RequestBody AssignRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                service.assign(TenantContext.getTenantIdAsObject(), taskId, req.assigneeId(), user(auth), HrAccess.held(auth))));
    }

    @PutMapping("/onboarding-tasks/{taskId}/due")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> due(@PathVariable UUID taskId, @RequestBody DueRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                service.setDue(TenantContext.getTenantIdAsObject(), taskId, req.dueDate(), user(auth), HrAccess.held(auth))));
    }

    @PutMapping("/onboardings/{id}/probation")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> probation(@PathVariable UUID id, @RequestBody ProbationRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Probation end date saved",
                service.setProbation(TenantContext.getTenantIdAsObject(), id, req.probationEndDate(), user(auth), HrAccess.held(auth))));
    }

    @PostMapping("/onboardings/{id}/finish")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Finish onboarding. Refused while any required task is open")
    public ResponseEntity<ApiResponse<Detail>> finish(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Onboarding finished",
                service.finish(TenantContext.getTenantIdAsObject(), id, user(auth), HrAccess.held(auth))));
    }

    @PostMapping("/onboardings/{id}/cancel")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Detail>> cancel(@PathVariable UUID id, @RequestBody NoteRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Onboarding cancelled",
                service.cancel(TenantContext.getTenantIdAsObject(), id, req.note(), user(auth), HrAccess.held(auth))));
    }

    // ── The task list ───────────────────────────────────────────────────────────────────────────

    @GetMapping("/onboarding-templates")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<Template>>> templates() {
        return ResponseEntity.ok(ApiResponse.success(service.templates(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/onboarding-templates")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Template>> addTemplate(@RequestBody TemplateRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Task added to the list",
                service.saveTemplate(TenantContext.getTenantIdAsObject(), null, req, HrAccess.held(auth))));
    }

    @PutMapping("/onboarding-templates/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Template>> saveTemplate(@PathVariable UUID id, @RequestBody TemplateRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Task saved",
                service.saveTemplate(TenantContext.getTenantIdAsObject(), id, req, HrAccess.held(auth))));
    }

    @PutMapping("/onboarding-templates/{id}/active")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Template>> setActive(@PathVariable UUID id, @RequestBody ActiveRequest req, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                service.setTemplateActive(TenantContext.getTenantIdAsObject(), id, req.active(), HrAccess.held(auth))));
    }

    private static UUID user(Authentication auth) {
        if (auth == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }
}
