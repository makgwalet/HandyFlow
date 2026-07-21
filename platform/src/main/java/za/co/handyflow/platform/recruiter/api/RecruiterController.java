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
import za.co.handyflow.platform.recruiter.application.internal.RecruiterPdfGenerator;
import za.co.handyflow.platform.recruiter.application.internal.RecruiterService;
import za.co.handyflow.platform.recruiter.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Fixes applied:
 * 1. Removed JdbcTemplate injection — slug resolution moved into RecruiterService.
 *    Controllers should not hold data-access concerns.
 * 2. moveStage: original used @PostMapping but fetched changed-by name as
 *    hardcoded "Recruiter". Now passes TenantContext.getCurrentUserId() so
 *    the service can look up the real user name for the audit trail.
 * 3. Missing ConvertToEmployeeRequest DTO — referenced in controller but
 *    not provided in uploads. Added as a separate file.
 * 4. updateProfile used @PutMapping which was declared correctly — kept.
 * 5. Public portal endpoints are split off — SecurityConfig must add
 *    /api/v1/recruiter/careers/** and /api/v1/recruiter/portal/** to permitAll().
 */
@RestController
@RequestMapping("/api/v1/recruiter")
@RequiredArgsConstructor
@Tag(name = "Recruiter", description = "Job postings, applicant tracking pipeline and careers page")
public class RecruiterController {

    private final RecruiterService recruiterService;
    private final RecruiterPdfGenerator recruiterPdfGenerator;

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
            @PathVariable UUID id, @PathVariable String action) {
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

    // ── Interview round templates ───────────────────────────────────────────────

    @GetMapping("/jobs/{jobId}/interview-rounds")
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "List a job's defined interview rounds, in order")
    public ResponseEntity<ApiResponse<List<InterviewRoundResponse>>> getInterviewRounds(
            @PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.success(
                recruiterService.getInterviewRounds(TenantContext.getTenantIdAsObject(), jobId)));
    }

    @PostMapping("/jobs/{jobId}/interview-rounds")
    @PreAuthorize("hasAuthority('RECRUITER_ADMIN')")
    @Operation(summary = "Add an interview round to a job's process")
    public ResponseEntity<ApiResponse<InterviewRoundResponse>> createInterviewRound(
            @PathVariable UUID jobId, @Valid @RequestBody InterviewRoundRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Round added",
                recruiterService.createInterviewRound(TenantContext.getTenantIdAsObject(), jobId, req)));
    }

    @PutMapping("/jobs/{jobId}/interview-rounds/{roundId}")
    @PreAuthorize("hasAuthority('RECRUITER_ADMIN')")
    @Operation(summary = "Edit an interview round's name, sequence, or description")
    public ResponseEntity<ApiResponse<InterviewRoundResponse>> updateInterviewRound(
            @PathVariable UUID jobId, @PathVariable UUID roundId,
            @Valid @RequestBody InterviewRoundRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Round updated",
                recruiterService.updateInterviewRound(TenantContext.getTenantIdAsObject(), jobId, roundId, req)));
    }

    @DeleteMapping("/jobs/{jobId}/interview-rounds/{roundId}")
    @PreAuthorize("hasAuthority('RECRUITER_ADMIN')")
    @Operation(summary = "Remove an interview round — fails if any scheduled interview already references it")
    public ResponseEntity<ApiResponse<Void>> deleteInterviewRound(
            @PathVariable UUID jobId, @PathVariable UUID roundId) {
        recruiterService.deleteInterviewRound(TenantContext.getTenantIdAsObject(), jobId, roundId);
        return ResponseEntity.ok(ApiResponse.success("Round removed", null));
    }

    // ── Applications — staff pipeline management ──────────────────────────────

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
    public ResponseEntity<ApiResponse<ApplicationResponse>> getApplication(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                recruiterService.getApplication(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/applications/{id}/stage")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Move application to a new pipeline stage")
    public ResponseEntity<ApiResponse<ApplicationResponse>> moveStage(
            @PathVariable UUID id,
            @Valid @RequestBody MoveStageRequest req) {
        // FIX: was hardcoded "Recruiter" — now resolves actual user name in service.
        return ResponseEntity.ok(ApiResponse.success("Stage updated",
                recruiterService.moveStage(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
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

    @PostMapping("/applications/{appId}/interviews/{intId}/reschedule")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Reschedule or postpone an interview with a reason — closes out the old interview and creates a new one")
    public ResponseEntity<ApiResponse<InterviewResponse>> rescheduleInterview(
            @PathVariable UUID appId, @PathVariable UUID intId,
            @Valid @RequestBody RescheduleInterviewRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Interview rescheduled",
                recruiterService.rescheduleInterview(TenantContext.getTenantIdAsObject(), appId, intId, req)));
    }

    @PostMapping("/applications/{appId}/interviews/{intId}/outcome")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Record interview outcome")
    public ResponseEntity<ApiResponse<InterviewResponse>> recordOutcome(
            @PathVariable UUID appId, @PathVariable UUID intId,
            @RequestBody RecordInterviewOutcomeRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Outcome recorded",
                recruiterService.recordOutcome(TenantContext.getTenantIdAsObject(),
                        appId, intId, req)));
    }

    @PostMapping("/applications/{id}/convert-to-employee")
    @PreAuthorize("hasAuthority('RECRUITER_ADMIN')")
    @Operation(summary = "Convert hired applicant to HR employee, or mark as placed externally — application must already be HIRED")
    public ResponseEntity<ApiResponse<UUID>> convertToEmployee(
            @PathVariable UUID id,
            @Valid @RequestBody ConvertToEmployeeRequest req) {
        UUID employeeId = recruiterService.convertToEmployee(
                TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId());
        // employeeId is null for external/agency placements (createHrRecord=false) —
        // no hr_employees row was created, so message accordingly.
        String message = employeeId != null
                ? "Employee record created — go to HR to complete onboarding"
                : "Application marked as placed — no HR record created";
        return ResponseEntity.ok(ApiResponse.success(message, employeeId));
    }

    // ── PDFs ─────────────────────────────────────────────────────────────────

    @GetMapping("/applications/{id}/offer-letter")
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "Download the offer letter PDF — application must have offer terms recorded (move to OFFER with salary details first)")
    public ResponseEntity<byte[]> downloadOfferLetter(@PathVariable UUID id) {
        byte[] pdf = recruiterPdfGenerator.generateOfferLetter(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=offer-letter.pdf")
                .body(pdf);
    }

    @PostMapping("/applications/{id}/offer-letter/send")
    @PreAuthorize("hasAuthority('RECRUITER_MANAGE')")
    @Operation(summary = "Generate the offer letter PDF and email it to the applicant as an attachment")
    public ResponseEntity<ApiResponse<Void>> sendOfferLetter(@PathVariable UUID id) {
        recruiterService.sendOfferLetter(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Offer letter sent", null));
    }

    @GetMapping("/applications/{id}/scorecard")
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "Download a one-page candidate scorecard PDF (interviews, scores, notes) for hiring-committee review")
    public ResponseEntity<byte[]> downloadScorecard(@PathVariable UUID id) {
        byte[] pdf = recruiterPdfGenerator.generateScorecard(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=candidate-scorecard.pdf")
                .body(pdf);
    }

    @GetMapping("/applications/{id}/cv")
    @PreAuthorize("hasAuthority('RECRUITER_READ')")
    @Operation(summary = "View the candidate's uploaded CV — 404 if none was uploaded")
    public ResponseEntity<byte[]> viewCv(@PathVariable UUID id) {
        byte[] pdf = recruiterService.getCvBytes(TenantContext.getTenantIdAsObject(), id);
        // "inline", not "attachment" — this is View CV, meant to open in a
        // preview tab, unlike the offer-letter/scorecard downloads (which
        // were explicitly corrected to attachment earlier this session
        // because those are documents meant to be saved).
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=cv.pdf")
                .body(pdf);
    }

    @PutMapping("/applications/{id}/referral")
    @PreAuthorize("hasAuthority('RECRUITER_ADMIN')")
    @Operation(summary = "Link a referral to a real employee and/or update bonus status — RECRUITER_ADMIN since this drives an actual payout")
    public ResponseEntity<ApiResponse<ApplicationResponse>> updateReferral(
            @PathVariable UUID id, @RequestBody LinkReferralRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Referral updated",
                recruiterService.updateReferral(TenantContext.getTenantIdAsObject(), id, req)));
    }

    // ── PUBLIC careers page — no auth ─────────────────────────────────────────

    @GetMapping("/careers/{tenantSlug}")
    @Operation(summary = "PUBLIC — Get all open jobs for a tenant's careers page")
    public ResponseEntity<ApiResponse<List<JobResponse>>> getPublicJobs(
            @PathVariable String tenantSlug) {
        // FIX: slug resolution moved to service — no JdbcTemplate in controller.
        return ResponseEntity.ok(ApiResponse.success(
                recruiterService.getPublicJobsBySlug(tenantSlug)));
    }

    @GetMapping("/careers/{tenantSlug}/{slug}")
    @Operation(summary = "PUBLIC — Get a specific job posting by slug")
    public ResponseEntity<ApiResponse<JobResponse>> getPublicJob(
            @PathVariable String tenantSlug, @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(
                recruiterService.getPublicJobBySlugAndTenant(tenantSlug, slug)));
    }

    @PostMapping("/careers/{tenantSlug}/jobs/{jobId}/apply")
    @Operation(summary = "PUBLIC — Submit a job application (no login needed)")
    public ResponseEntity<ApiResponse<PublicApplicationResponse>> apply(
            @PathVariable String tenantSlug, @PathVariable UUID jobId,
            @Valid @RequestBody SubmitApplicationRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                "Application submitted! Check your email for a tracking link.",
                recruiterService.submitApplicationBySlug(tenantSlug, jobId, req)));
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
            @PathVariable String token, @PathVariable UUID applicationId) {
        recruiterService.withdrawApplication(token, applicationId);
        return ResponseEntity.ok(ApiResponse.success("Application withdrawn", null));
    }
}