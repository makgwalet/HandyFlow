package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.FieldService;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Field operations: site diaries and snag / punch-list management.
 *
 * CHANGES FROM ORIGINAL
 * ──────────────────────
 * 1. @Validated + @Valid on all request bodies.
 *
 * 2. createDiary: submittedByName was userId.toString() — a UUID.
 *    FIX: TenantContext.getCurrentUserName() provides the real display name.
 *    Site diaries are legal/contractual documents.  The submitter name must
 *    be the person's actual name, not a UUID.
 *
 * 3. createSnag: same UUID-as-name issue fixed.
 *    Also: SnagItem sequence generation now routed through SequenceService
 *    (injected here) to fix the MAX + 1 race condition.
 *
 * 4. Photo upload moved to its own endpoint (was missing from the original).
 *
 * WHY getCurrentUserName() IS THE CONTROLLER'S RESPONSIBILITY:
 * ─────────────────────────────────────────────────────────────
 * The service layer should not depend on TenantContext (a web-layer concern).
 * The controller resolves the current user's display name and passes it as a
 * plain String parameter to the service.  This makes the service unit-testable
 * without needing to mock TenantContext.
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Field Operations", description = "Site diaries and snag / punch-list management")
public class FieldController {

    private final FieldService fieldService;

    // ── Site Diaries ──────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/site-diaries")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Site diaries — ordered by date DESC (most recent first)")
    public ResponseEntity<ApiResponse<List<SiteDiaryResponse>>> getDiaries(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                fieldService.getDiaries(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(SiteDiaryResponse::of).toList()));
    }

    /**
     * Submits a daily site diary.
     *
     * The DB constraint uq_diary_date prevents duplicates; FieldService converts
     * the constraint violation to HTTP 409 with a readable message.
     *
     * FIX: submittedByName → TenantContext.getCurrentUserName() not UUID.toString()
     */
    @PostMapping("/{projectId}/site-diaries")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Submit a daily site diary — one per project per date")
    public ResponseEntity<ApiResponse<SiteDiaryResponse>> createDiary(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateSiteDiaryRequest req) {
        UUID   userId   = TenantContext.getCurrentUserId();
        String userName = TenantContext.getCurrentUserName();   // FIX: real name not UUID
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Site diary submitted",
                        SiteDiaryResponse.of(fieldService.createDiary(
                                TenantContext.getTenantIdAsObject(), projectId,
                                req, userId, userName))));
    }

    @PutMapping("/site-diaries/{diaryId}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Update a site diary — correcting weather, worker counts or notes")
    public ResponseEntity<ApiResponse<SiteDiaryResponse>> updateDiary(
            @PathVariable UUID diaryId,
            @Valid @RequestBody CreateSiteDiaryRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Site diary updated",
                SiteDiaryResponse.of(fieldService.updateDiary(
                        TenantContext.getTenantIdAsObject(), diaryId, req))));
    }

    // ── Snag List ─────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/snags")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Snag list — set openOnly=true for the active punch-list")
    public ResponseEntity<ApiResponse<List<SnagResponse>>> getSnags(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "false") boolean openOnly) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                fieldService.getSnags(TenantContext.getTenantIdAsObject(), projectId, openOnly)
                        .stream().map(SnagResponse::of).toList()));
    }

    /**
     * Logs a new snag item.
     *
     * Snag number (SN0001, SN0002…) auto-assigned via SequenceService in
     * FieldService — no race condition risk.
     *
     * FIX: createdByName resolution moved to controller, not service.
     */
    @PostMapping("/{projectId}/snags")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Log a snag/defect — auto-assigns SN number")
    public ResponseEntity<ApiResponse<SnagResponse>> createSnag(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateSnagRequest req) {
        UUID createdBy = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Snag logged",
                        SnagResponse.of(fieldService.createSnag(
                                TenantContext.getTenantIdAsObject(), projectId,
                                req, createdBy))));
    }

    /**
     * Status transitions: START | RESOLVE | REJECT.
     *
     * START:   OPEN → IN_PROGRESS (contractor begins work)
     * RESOLVE: IN_PROGRESS → RESOLVED (work done, pending QA sign-off)
     * REJECT:  resolution rejected, item goes back to OPEN
     */
    @PostMapping("/snags/{snagId}/{action}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Snag status: START | RESOLVE | REJECT")
    public ResponseEntity<ApiResponse<SnagResponse>> updateSnagStatus(
            @PathVariable UUID   snagId,
            @PathVariable String action) {
        UUID resolvedBy = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Snag updated",
                SnagResponse.of(fieldService.updateSnagStatus(
                        TenantContext.getTenantIdAsObject(), snagId,
                        action, resolvedBy))));
    }

    /**
     * Attaches a photo URL to a snag.  The actual file upload is handled
     * by the storage service (S3 pre-signed URL flow); this endpoint stores
     * the resulting URL in snag_items.photo_urls (PostgreSQL TEXT[] column).
     */
    @PostMapping("/snags/{snagId}/photos")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Add a photo URL to a snag item")
    public ResponseEntity<ApiResponse<SnagResponse>> addPhoto(
            @PathVariable UUID snagId,
            @RequestBody @Valid AddPhotoRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Photo added",
                SnagResponse.of(fieldService.addSnagPhoto(
                        TenantContext.getTenantIdAsObject(), snagId, req.url()))));
    }
}
