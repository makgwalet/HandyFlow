package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.projects.application.internal.RfiService;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * RFI workflow. Every URL that existed before is unchanged; this chunk adds assign, clarify, close
 * with a no-change note, reopen, impact, events (full history) and a project summary.
 * All routes are project-first or /rfis/{id}, so ProjectAccessInterceptor already covers them.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Validated
@Tag(name = "RFIs", description = "Requests for information: assignment, answers, clarification, impact, escalation")
public class RfiController {

    private final RfiService rfiService;

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/rfis")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "RFIs for a project; optional filters: status, assignedToMe, overdueOnly")
    public ResponseEntity<ApiResponse<List<RfiResponse>>> getRfis(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean assignedToMe,
            @RequestParam(defaultValue = "false") boolean overdueOnly) {
        return ResponseEntity.ok(ApiResponse.success(rfiService.getRfis(projectId, status, assignedToMe, overdueOnly)));
    }

    @GetMapping("/{projectId}/rfis/summary")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "RFI report: counts by status, overdue, awaiting closure, unresolved impact, average response time, ageing")
    public ResponseEntity<ApiResponse<RfiSummaryResponse>> summary(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(rfiService.summary(projectId)));
    }

    @GetMapping("/rfis/{rfiId}")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "One RFI, including closureBlocker — why it cannot be closed yet, if so")
    public ResponseEntity<ApiResponse<RfiResponse>> getRfi(@PathVariable UUID rfiId) {
        return ResponseEntity.ok(ApiResponse.success(rfiService.getRfi(rfiId)));
    }

    @GetMapping("/rfis/{rfiId}/events")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Full history: every answer (with its text), clarification, reassignment, closure and escalation")
    public ResponseEntity<ApiResponse<List<RfiEventResponse>>> events(@PathVariable UUID rfiId) {
        return ResponseEntity.ok(ApiResponse.success(rfiService.getEvents(rfiId)));
    }

    // ── Workflow ──────────────────────────────────────────────────────────────

    @PostMapping("/{projectId}/rfis")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Create an RFI (the due date may not precede the requested date)")
    public ResponseEntity<ApiResponse<RfiResponse>> createRfi(
            @PathVariable UUID projectId, @Valid @RequestBody CreateRfiRequest req) {
        return ResponseEntity.status(201)
                .body(ApiResponse.success("RFI created", rfiService.createRfi(projectId, req)));
    }

    /** DRAFT → SUBMITTED */
    @PostMapping("/rfis/{rfiId}/submit")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<ApiResponse<RfiResponse>> submit(@PathVariable UUID rfiId) {
        return ResponseEntity.ok(ApiResponse.success("RFI submitted", rfiService.submit(rfiId)));
    }

    @PutMapping("/rfis/{rfiId}/assign")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Assign who must answer. Once assigned, only that person (or a PM_ADMIN) can respond.")
    public ResponseEntity<ApiResponse<RfiResponse>> assign(
            @PathVariable UUID rfiId, @Valid @RequestBody AssignRfiRequest req) {
        return ResponseEntity.ok(ApiResponse.success("RFI assigned", rfiService.assign(rfiId, req)));
    }

    /** SUBMITTED → RESPONDED */
    @PostMapping("/rfis/{rfiId}/respond")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<ApiResponse<RfiResponse>> respond(
            @PathVariable UUID rfiId, @Valid @RequestBody RespondRfiRequest req) {
        return ResponseEntity.ok(ApiResponse.success("RFI responded", rfiService.respond(rfiId, req)));
    }

    @PostMapping("/rfis/{rfiId}/clarify")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "RESPONDED → SUBMITTED: send the answer back for clarification (comment required). The earlier answer stays in /events.")
    public ResponseEntity<ApiResponse<RfiResponse>> clarify(
            @PathVariable UUID rfiId, @Valid @RequestBody RfiCommentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Clarification requested",
                rfiService.requestClarification(rfiId, req.comment())));
    }

    @PutMapping("/rfis/{rfiId}/impact")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Flag that the answer affects schedule and/or cost (optionally naming the task). A flagged RFI can't be closed until a live change order handles it or a reason is recorded.")
    public ResponseEntity<ApiResponse<RfiResponse>> impact(
            @PathVariable UUID rfiId, @Valid @RequestBody RfiImpactRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Impact updated", rfiService.updateImpact(rfiId, req)));
    }

    /** RESPONDED → CLOSED. Body optional: only needed to record why no change order is required. */
    @PostMapping("/rfis/{rfiId}/close")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Close an answered RFI. 409 UNRESOLVED_IMPACT if it is flagged as affecting schedule/cost and nothing handles that.")
    public ResponseEntity<ApiResponse<RfiResponse>> close(
            @PathVariable UUID rfiId, @RequestBody(required = false) @Valid CloseRfiRequest req) {
        return ResponseEntity.ok(ApiResponse.success("RFI closed",
                rfiService.close(rfiId, req != null ? req.noChangeNote() : null)));
    }

    @PostMapping("/rfis/{rfiId}/reopen")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "CLOSED → RESPONDED, with a reason. History is kept.")
    public ResponseEntity<ApiResponse<RfiResponse>> reopen(
            @PathVariable UUID rfiId, @Valid @RequestBody RfiCommentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("RFI reopened", rfiService.reopen(rfiId, req.comment())));
    }

    /** Any open status → CANCELLED */
    @PostMapping("/rfis/{rfiId}/cancel")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<ApiResponse<RfiResponse>> cancel(
            @PathVariable UUID rfiId, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.success("RFI cancelled", rfiService.cancel(rfiId, reason)));
    }

    /** Links this RFI to the Change Order its answer resulted in (same tenant and project). */
    @PostMapping("/rfis/{rfiId}/link-change-order")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<ApiResponse<RfiResponse>> linkChangeOrder(
            @PathVariable UUID rfiId, @RequestParam UUID changeOrderId) {
        return ResponseEntity.ok(ApiResponse.success("RFI linked to change order",
                rfiService.linkChangeOrder(rfiId, changeOrderId)));
    }

    // ── Evidence attachments ──────────────────────────────────────────────────

    @PostMapping(value = "/rfis/{rfiId}/attachments", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID rfiId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(201).body(ApiResponse.success("Attachment uploaded",
                rfiService.attachEvidence(rfiId, file,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/rfis/{rfiId}/attachments")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> getAttachments(@PathVariable UUID rfiId) {
        return ResponseEntity.ok(ApiResponse.success(rfiService.getAttachments(rfiId)));
    }
}