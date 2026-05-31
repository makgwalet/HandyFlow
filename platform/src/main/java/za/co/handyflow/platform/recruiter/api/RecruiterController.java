package za.co.handyflow.platform.recruiter.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.recruiter.application.internal.RecruiterService;
import za.co.handyflow.platform.recruiter.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruiter")
@RequiredArgsConstructor
@Tag(name = "Recruiter", description = "Job postings, applicant tracking pipeline and careers page")
public class RecruiterController {

    private final RecruiterService recruiterService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    // ── Summary ───────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "Recruiter dashboard — job and pipeline counts")
    public ResponseEntity<ApiResponse<RecruiterSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(
                recruiterService.getSummary(TenantContext.getTenantIdAsObject())));
    }

    // ── Jobs ──────────────────────────────────────────────────────────────────

    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "List jobs — filter by status: DRAFT|OPEN|PAUSED|CLOSED|FILLED")
    public ResponseEntity<ApiResponse<Page<JobResponse>>> getJobs(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                recruiterService.getJobs(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/jobs/{id}")
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "Get job detail")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                recruiterService.getJob(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/jobs")
    @PreAuthorize("hasAuthority('RECRUITER_ADMIN')")
    @Operation(summary = "Create a job posting — starts as DRAFT")
    public ResponseEntity<ApiResponse<JobResponse>> createJob(
            @Valid @RequestBody CreateJobRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Job created",
                recruiterService.createJob(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/jobs/{id}")
    @PreAuthorize("hasAuthority('RECRUITER_ADMIN')")
    @Operation(summary = "Update job details")
    public ResponseEntity<ApiResponse<JobResponse>> updateJob(
            @PathVariable UUID id,
            @RequestBody CreateJobRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Job updated",
                recruiterService.updateJob(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/jobs/{id}/action/{action}")
    @PreAuthorize("hasAuthority('RECRUITER_ADMIN')")
    @Operation(summary = "Update job status — action: PUBLISH | PAUSE | CLOSE | FILL")
    public ResponseEntity<ApiResponse<JobResponse>> updateStatus(
            @PathVariable UUID id,
            @PathVariable String action) {
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                recruiterService.updateJobStatus(TenantContext.getTenantIdAsObject(), id, action)));
    }

    @DeleteMapping("/jobs/{id}")
    @PreAuthorize("hasAuthority('RECRUITER_ADMIN')")
    @Operation(summary = "Delete a job")
    public ResponseEntity<ApiResponse<Void>> deleteJob(@PathVariable UUID id) {
        recruiterService.deleteJob(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Job deleted", null));
    }

    // ── Applications — staff view ─────────────────────────────────────────────

    @GetMapping("/applications")
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "List all applications — filter by stage and optionally jobId")
    public ResponseEntity<ApiResponse<Page<ApplicationResponse>>> getApplications(
            @RequestParam(required = false) UUID   jobId,
            @RequestParam(required = false) String stage,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                recruiterService.getApplications(TenantContext.getTenantIdAsObject(),
                        jobId, stage, pageable)));
    }

    @GetMapping("/applications/{id}")
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "Get application detail with interviews and stage history")
    public ResponseEntity<ApiResponse<ApplicationResponse>> getApplication(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                recruiterService.getApplication(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/applications/{id}/stage")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Move application to a new pipeline stage — SCREENING|INTERVIEW|ASSESSMENT|OFFER|HIRED|REJECTED")
    public ResponseEntity<ApiResponse<ApplicationResponse>> moveStage(
            @PathVariable UUID id,
            @Valid @RequestBody MoveStageRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Stage updated",
                recruiterService.moveStage(TenantContext.getTenantIdAsObject(), id, req,
                        "Recruiter")));
    }

    @PostMapping("/applications/{id}/score")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Score an application (1-5) and add notes")
    public ResponseEntity<ApiResponse<ApplicationResponse>> scoreApplication(
            @PathVariable UUID id,
            @RequestBody ScoreApplicationRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Application scored",
                recruiterService.scoreApplication(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/applications/{id}/interviews")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Schedule an interview for an application")
    public ResponseEntity<ApiResponse<InterviewResponse>> scheduleInterview(
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleInterviewRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Interview scheduled",
                recruiterService.scheduleInterview(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/applications/{appId}/interviews/{intId}/outcome")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Record interview outcome")
    public ResponseEntity<ApiResponse<InterviewResponse>> recordOutcome(
            @PathVariable UUID appId,
            @PathVariable UUID intId,
            @RequestBody RecordInterviewOutcomeRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Outcome recorded",
                recruiterService.recordOutcome(TenantContext.getTenantIdAsObject(),
                        appId, intId, req)));
    }

    @PostMapping("/applications/{id}/convert-to-employee")
    @PreAuthorize("hasAuthority('RECRUITER_ADMIN')")
    @Operation(summary = "Convert hired applicant to HR employee — pre-fills employee record")
    public ResponseEntity<ApiResponse<UUID>> convertToEmployee(
            @PathVariable UUID id,
            @Valid @RequestBody ConvertToEmployeeRequest req) {
        UUID employeeId = recruiterService.convertToEmployee(
                TenantContext.getTenantIdAsObject(), id, req);
        return ResponseEntity.ok(ApiResponse.success(
                "Employee record created — go to HR to complete onboarding", employeeId));
    }

    // ── PUBLIC careers page — no auth ─────────────────────────────────────────
    // SecurityConfig must have /api/v1/recruiter/careers/** in permitAll()

    @GetMapping("/careers/{tenantSlug}")
    @Operation(summary = "PUBLIC — Get all open jobs for a tenant's careers page")
    public ResponseEntity<ApiResponse<List<JobResponse>>> getPublicJobs(
            @PathVariable String tenantSlug) {
        TenantId tenantId = resolveTenantBySlug(tenantSlug);
        return ResponseEntity.ok(ApiResponse.success(
                recruiterService.getPublicJobs(tenantId)));
    }

    @GetMapping("/careers/{tenantSlug}/{slug}")
    @Operation(summary = "PUBLIC — Get a specific job posting by slug")
    public ResponseEntity<ApiResponse<JobResponse>> getPublicJob(
            @PathVariable String tenantSlug,
            @PathVariable String slug) {
        TenantId tenantId = resolveTenantBySlug(tenantSlug);
        return ResponseEntity.ok(ApiResponse.success(
                recruiterService.getPublicJobBySlug(tenantId, slug)));
    }

    @PostMapping("/careers/{tenantSlug}/jobs/{jobId}/apply")
    @Operation(summary = "PUBLIC — Submit a job application (no login needed)")
    public ResponseEntity<ApiResponse<PublicApplicationResponse>> apply(
            @PathVariable String tenantSlug,
            @PathVariable UUID jobId,
            @Valid @RequestBody SubmitApplicationRequest req) {
        TenantId tenantId = resolveTenantBySlug(tenantSlug);
        return ResponseEntity.status(201).body(ApiResponse.success(
                "Application submitted! Check your email for a tracking link.",
                recruiterService.submitApplication(tenantId, jobId, req)));
    }

    // ── PUBLIC applicant portal — token-gated, no login ──────────────────────

    @GetMapping("/portal/{token}")
    @Operation(summary = "PUBLIC — Applicant tracks all their applications via portal token")
    public ResponseEntity<ApiResponse<List<PublicApplicationResponse>>> trackApplications(
            @PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(
                recruiterService.getMyApplications(token)));
    }

    @PutMapping("/portal/{token}/profile")
    @Operation(summary = "PUBLIC — Applicant updates their profile and CV via portal token")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @PathVariable String token,
            @RequestBody UpdateApplicantRequest req) {
        recruiterService.updateApplicantProfile(token, req);
        return ResponseEntity.ok(ApiResponse.success("Profile updated", null));
    }

    @PostMapping("/portal/{token}/applications/{applicationId}/withdraw")
    @Operation(summary = "PUBLIC — Applicant withdraws their application")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @PathVariable String token,
            @PathVariable UUID applicationId) {
        recruiterService.withdrawApplication(token, applicationId);
        return ResponseEntity.ok(ApiResponse.success("Application withdrawn", null));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private TenantId resolveTenantBySlug(String slug) {
        try {
            String id = jdbc.queryForObject(
                    "SELECT id::text FROM tenants WHERE slug = ?", String.class, slug);
            return TenantId.of(id);
        } catch (Exception e) {
            throw new za.co.handyflow.platform.shared.HandyFlowException(
                    "Company not found: " + slug,
                    org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
    }
}
