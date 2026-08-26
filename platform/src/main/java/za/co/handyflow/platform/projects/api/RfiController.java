package za.co.handyflow.platform.projects.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.projects.application.internal.RfiService;
import za.co.handyflow.platform.projects.dto.CreateRfiRequest;
import za.co.handyflow.platform.projects.dto.RespondRfiRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * FIX: backlog 6.3 — three new endpoints (attach/list evidence, link a
 * Change Order). Every existing endpoint below needed zero code changes
 * for the RfiResponse DTO switch — they were already declared as
 * ResponseEntity<?> and simply wrap whatever RfiService now returns.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Validated
public class RfiController {

    private final RfiService rfiService;

    @GetMapping("/{projectId}/rfis")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<?> getRfis(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(rfiService.getRfis(projectId)));
    }

    @GetMapping("/rfis/{rfiId}")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<?> getRfi(@PathVariable UUID rfiId) {
        return ResponseEntity.ok(ApiResponse.success(rfiService.getRfi(rfiId)));
    }

    @PostMapping("/{projectId}/rfis")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<?> createRfi(@PathVariable UUID projectId,
                                       @Valid @RequestBody CreateRfiRequest req) {
        return ResponseEntity.status(201)
                .body(ApiResponse.success("RFI created", rfiService.createRfi(projectId, req)));
    }

    /** DRAFT → SUBMITTED */
    @PostMapping("/rfis/{rfiId}/submit")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<?> submit(@PathVariable UUID rfiId) {
        return ResponseEntity.ok(ApiResponse.success("RFI submitted", rfiService.submit(rfiId)));
    }

    /** SUBMITTED → RESPONDED */
    @PostMapping("/rfis/{rfiId}/respond")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<?> respond(@PathVariable UUID rfiId,
                                     @Valid @RequestBody RespondRfiRequest req) {
        return ResponseEntity.ok(ApiResponse.success("RFI responded", rfiService.respond(rfiId, req)));
    }

    /** RESPONDED → CLOSED */
    @PostMapping("/rfis/{rfiId}/close")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<?> close(@PathVariable UUID rfiId) {
        return ResponseEntity.ok(ApiResponse.success("RFI closed", rfiService.close(rfiId)));
    }

    /** Any open status → CANCELLED */
    @PostMapping("/rfis/{rfiId}/cancel")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<?> cancel(@PathVariable UUID rfiId,
                                    @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.success("RFI cancelled", rfiService.cancel(rfiId, reason)));
    }

    /**
     * FIX: backlog 6.3 — links this RFI to the Change Order its answer
     * resulted in. changeOrderId is a request param, not a path segment
     * — this reads as an action on the RFI ("link it to X"), matching
     * the same shape as every other action endpoint in this controller,
     * rather than a nested-resource URL.
     */
    @PostMapping("/rfis/{rfiId}/link-change-order")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<?> linkChangeOrder(@PathVariable UUID rfiId,
                                             @RequestParam UUID changeOrderId) {
        return ResponseEntity.ok(ApiResponse.success("RFI linked to change order",
                rfiService.linkChangeOrder(rfiId, changeOrderId)));
    }

    // ── Evidence attachments ─────────────────────────────────────────────────

    /**
     * FIX: backlog 6.3 — same multipart upload pattern already proven
     * for Payroll Bureau's logo attachments and Recruitment Agency's CV
     * uploads.
     */
    @PostMapping(value = "/rfis/{rfiId}/attachments", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<?> attachEvidence(@PathVariable UUID rfiId,
                                            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(201).body(ApiResponse.success("Attachment uploaded",
                rfiService.attachEvidence(rfiId, file,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/rfis/{rfiId}/attachments")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<?> getAttachments(@PathVariable UUID rfiId) {
        return ResponseEntity.ok(ApiResponse.success(rfiService.getAttachments(rfiId)));
    }
}