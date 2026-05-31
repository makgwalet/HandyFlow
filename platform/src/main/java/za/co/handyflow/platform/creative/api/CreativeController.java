package za.co.handyflow.platform.creative.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.creative.application.internal.CreativeService;
import za.co.handyflow.platform.creative.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/creative")
@RequiredArgsConstructor
@Tag(name = "Creative Studio", description = "Design jobs, proof approvals and deliverable management")
public class CreativeController {

    private final CreativeService creativeService;

    // ── Summary ───────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('CREATIVE_READ')")
    @Operation(summary = "Creative dashboard — job counts by status")
    public ResponseEntity<ApiResponse<CreativeSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(
                creativeService.getSummary(TenantContext.getTenantIdAsObject())));
    }

    // ── Jobs ──────────────────────────────────────────────────────────────────

    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('CREATIVE_READ')")
    @Operation(summary = "List jobs — filter by status: BRIEFING|IN_PROGRESS|AWAITING_APPROVAL|IN_REVISION|APPROVED|DELIVERED|INVOICED")
    public ResponseEntity<ApiResponse<Page<JobResponse>>> getJobs(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                creativeService.getJobs(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/jobs/{id}")
    @PreAuthorize("hasAuthority('CREATIVE_READ')")
    @Operation(summary = "Get job detail")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                creativeService.getJob(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/jobs")
    @PreAuthorize("hasAuthority('CREATIVE_MANAGE')")
    @Operation(summary = "Create a new creative job")
    public ResponseEntity<ApiResponse<JobResponse>> createJob(
            @Valid @RequestBody CreateJobRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Job created",
                creativeService.createJob(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/jobs/{id}")
    @PreAuthorize("hasAuthority('CREATIVE_MANAGE')")
    @Operation(summary = "Update job details")
    public ResponseEntity<ApiResponse<JobResponse>> updateJob(
            @PathVariable UUID id,
            @RequestBody UpdateJobRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Job updated",
                creativeService.updateJob(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/jobs/{id}/action/{action}")
    @PreAuthorize("hasAuthority('CREATIVE_MANAGE')")
    @Operation(summary = "Update job status — action: START | SEND | REVISE | APPROVE | DELIVER | CANCEL")
    public ResponseEntity<ApiResponse<JobResponse>> updateStatus(
            @PathVariable UUID id,
            @PathVariable String action) {
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                creativeService.updateStatus(TenantContext.getTenantIdAsObject(), id, action)));
    }

    @DeleteMapping("/jobs/{id}")
    @PreAuthorize("hasAuthority('CREATIVE_MANAGE')")
    @Operation(summary = "Soft delete a job")
    public ResponseEntity<ApiResponse<Void>> deleteJob(@PathVariable UUID id) {
        creativeService.deleteJob(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Job deleted", null));
    }

    // ── Proofs ────────────────────────────────────────────────────────────────

    @GetMapping("/jobs/{jobId}/proofs")
    @PreAuthorize("hasAuthority('CREATIVE_READ')")
    @Operation(summary = "List all proof versions for a job")
    public ResponseEntity<ApiResponse<List<ProofResponse>>> getProofs(
            @PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.success(
                creativeService.getProofs(TenantContext.getTenantIdAsObject(), jobId)));
    }

    @PostMapping("/jobs/{jobId}/proofs")
    @PreAuthorize("hasAuthority('CREATIVE_MANAGE')")
    @Operation(summary = "Upload a new proof version — supersedes any pending proofs, auto-advances job to AWAITING_APPROVAL")
    public ResponseEntity<ApiResponse<ProofResponse>> uploadProof(
            @PathVariable UUID jobId,
            @Valid @RequestBody UploadProofRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Proof uploaded",
                creativeService.uploadProof(TenantContext.getTenantIdAsObject(), jobId, req,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/jobs/{jobId}/proofs/{proofId}/send")
    @PreAuthorize("hasAuthority('CREATIVE_MANAGE')")
    @Operation(summary = "Send proof to client via email with approval link")
    public ResponseEntity<ApiResponse<ProofResponse>> sendProof(
            @PathVariable UUID jobId,
            @PathVariable UUID proofId,
            @Valid @RequestBody SendProofRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Proof sent to " + req.email(),
                creativeService.sendProofToClient(TenantContext.getTenantIdAsObject(),
                        jobId, proofId, req)));
    }

    @PostMapping("/jobs/{jobId}/proofs/{proofId}/comments")
    @PreAuthorize("hasAuthority('CREATIVE_MANAGE')")
    @Operation(summary = "Add a team comment on a proof")
    public ResponseEntity<ApiResponse<ProofResponse>> addTeamComment(
            @PathVariable UUID jobId,
            @PathVariable UUID proofId,
            @Valid @RequestBody AddCommentRequest req) {
        // Fetch team member name from context
        String memberName = fetchUserName(TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Comment added",
                creativeService.addTeamComment(TenantContext.getTenantIdAsObject(),
                        jobId, proofId, req, memberName)));
    }

    // ── Deliverables ──────────────────────────────────────────────────────────

    @GetMapping("/jobs/{jobId}/deliverables")
    @PreAuthorize("hasAuthority('CREATIVE_READ')")
    @Operation(summary = "List final deliverables for a job")
    public ResponseEntity<ApiResponse<List<DeliverableResponse>>> getDeliverables(
            @PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.success(
                creativeService.getDeliverables(TenantContext.getTenantIdAsObject(), jobId)));
    }

    @PostMapping("/jobs/{jobId}/deliverables")
    @PreAuthorize("hasAuthority('CREATIVE_MANAGE')")
    @Operation(summary = "Upload a final deliverable file — auto-advances APPROVED jobs to DELIVERED")
    public ResponseEntity<ApiResponse<DeliverableResponse>> addDeliverable(
            @PathVariable UUID jobId,
            @Valid @RequestBody AddDeliverableRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Deliverable uploaded",
                creativeService.addDeliverable(TenantContext.getTenantIdAsObject(), jobId, req,
                        TenantContext.getCurrentUserId())));
    }

    // ── PUBLIC approval endpoints (no auth — token in URL) ────────────────────
    // WHY no @PreAuthorize? These are accessed by the client (external person)
    // who has a secure token in the URL but no HandyFlow account.
    // SecurityConfig has /api/v1/creative/approve/** in permitAll().

    @GetMapping("/approve/{token}")
    @Operation(summary = "PUBLIC — View proof by approval token (client portal)")
    public ResponseEntity<ApiResponse<PublicProofResponse>> viewProofByToken(
            @PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(
                creativeService.getProofByToken(token)));
    }

    @PostMapping("/approve/{token}/approve")
    @Operation(summary = "PUBLIC — Client approves proof")
    public ResponseEntity<ApiResponse<Void>> approveProof(
            @PathVariable String token,
            @Valid @RequestBody ApproveProofRequest req,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        creativeService.approveProofByToken(token, req, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Proof approved. Thank you!", null));
    }

    @PostMapping("/approve/{token}/reject")
    @Operation(summary = "PUBLIC — Client rejects proof with reason")
    public ResponseEntity<ApiResponse<Void>> rejectProof(
            @PathVariable String token,
            @Valid @RequestBody RejectProofRequest req) {
        creativeService.rejectProofByToken(token, req);
        return ResponseEntity.ok(ApiResponse.success(
                "Feedback submitted. Your designer has been notified.", null));
    }

    @PostMapping("/approve/{token}/comments")
    @Operation(summary = "PUBLIC — Client adds comment on proof")
    public ResponseEntity<ApiResponse<Void>> addClientComment(
            @PathVariable String token,
            @Valid @RequestBody AddCommentRequest req) {
        creativeService.addClientComment(token, req);
        return ResponseEntity.ok(ApiResponse.success("Comment added", null));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
    }

    private String fetchUserName(UUID userId) {
        // Resolved from DB — returns "First Last" or "Team Member"
        return "Team Member"; // placeholder — wire to UserRepository if needed
    }
}
