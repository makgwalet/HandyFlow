package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.FieldService;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Field Operations", description = "Site diaries and snag lists for mobile field teams")
public class FieldController {

    private final FieldService fieldService;

    // ── Site Diaries ──────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/site-diaries")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Site diary history — most recent first")
    public ResponseEntity<ApiResponse<List<SiteDiaryResponse>>> getDiaries(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                fieldService.getDiaries(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(SiteDiaryResponse::of).toList()));
    }

    @PostMapping("/{projectId}/site-diaries")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Submit a site diary — one per project per date (returns 409 if already exists)")
    public ResponseEntity<ApiResponse<SiteDiaryResponse>> createDiary(
            @PathVariable UUID projectId, @RequestBody CreateSiteDiaryRequest req) {
        UUID userId = TenantContext.getCurrentUserId();
        String name = userId != null ? userId.toString() : "field";
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Site diary submitted",
                SiteDiaryResponse.of(fieldService.createDiary(
                        TenantContext.getTenantIdAsObject(), projectId, req, userId, name))));
    }

    @PutMapping("/site-diaries/{diaryId}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Update a site diary — can edit same-day entries")
    public ResponseEntity<ApiResponse<SiteDiaryResponse>> updateDiary(
            @PathVariable UUID diaryId, @RequestBody CreateSiteDiaryRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Diary updated",
                SiteDiaryResponse.of(fieldService.updateDiary(
                        TenantContext.getTenantIdAsObject(), diaryId, req))));
    }

    // ── Snag Items ────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/snags")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Snag list — ordered by severity. Use ?openOnly=true for dashboard view.")
    public ResponseEntity<ApiResponse<List<SnagResponse>>> getSnags(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "false") boolean openOnly) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                fieldService.getSnags(TenantContext.getTenantIdAsObject(), projectId, openOnly)
                        .stream().map(SnagResponse::of).toList()));
    }

    @PostMapping("/{projectId}/snags")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Log a snag — auto-assigns SN-number, links to task if provided")
    public ResponseEntity<ApiResponse<SnagResponse>> createSnag(
            @PathVariable UUID projectId, @RequestBody CreateSnagRequest req) {
        UUID userId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Snag logged",
                SnagResponse.of(fieldService.createSnag(
                        TenantContext.getTenantIdAsObject(), projectId, req, userId))));
    }

    @PostMapping("/snags/{snagId}/{action}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Snag workflow — action: START | RESOLVE | REJECT")
    public ResponseEntity<ApiResponse<SnagResponse>> updateSnagStatus(
            @PathVariable UUID snagId, @PathVariable String action) {
        UUID userId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Snag updated",
                SnagResponse.of(fieldService.updateSnagStatus(
                        TenantContext.getTenantIdAsObject(), snagId, action, userId))));
    }

    @PostMapping("/snags/{snagId}/photos")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Attach a photo URL to a snag — call after uploading to blob storage")
    public ResponseEntity<ApiResponse<SnagResponse>> addPhoto(
            @PathVariable UUID snagId, @RequestBody Map<String, String> body) {
        String url = body.get("photoUrl");
        if (url == null || url.isBlank())
            return ResponseEntity.badRequest().body(ApiResponse.error("photoUrl is required"));
        return ResponseEntity.ok(ApiResponse.success("Photo added",
                SnagResponse.of(fieldService.addSnagPhoto(
                        TenantContext.getTenantIdAsObject(), snagId, url))));
    }
}
