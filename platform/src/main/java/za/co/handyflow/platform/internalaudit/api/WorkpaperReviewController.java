package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.WorkpaperReviewService;
import za.co.handyflow.platform.internalaudit.dto.WorkpaperReviewDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal-audit")
@RequiredArgsConstructor
@Tag(name = "Internal Audit - Workpaper Review", description = "Review notes and links from workpapers to tests, exceptions and findings")
public class WorkpaperReviewController {

    private final WorkpaperReviewService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/workpapers/{id}/review-notes")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<List<NoteResponse>>> notes(@PathVariable UUID id) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.forWorkpaper(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/workpapers/{id}/review-notes")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<NoteResponse>> raise(@PathVariable UUID id, @RequestBody TextRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                service.raise(TenantContext.getTenantIdAsObject(), id, req.text(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/review-notes/{id}/respond")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<NoteResponse>> respond(@PathVariable UUID id, @RequestBody TextRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.respond(TenantContext.getTenantIdAsObject(), id, req.text(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/review-notes/{id}/clear")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<NoteResponse>> clear(@PathVariable UUID id) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.clear(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/review-notes/{id}/return")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<NoteResponse>> returnNote(@PathVariable UUID id, @RequestBody TextRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.returnNote(TenantContext.getTenantIdAsObject(), id, req.text(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/workpapers/{id}/links")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<List<LinkResponse>>> links(@PathVariable UUID id) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.linksFor(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/workpapers/{id}/links")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<LinkResponse>> link(@PathVariable UUID id, @RequestBody LinkRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                service.link(TenantContext.getTenantIdAsObject(), id, req.type(), req.id(), TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/workpapers/{id}/links/{linkId}")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> unlink(@PathVariable UUID id, @PathVariable UUID linkId) {
        featureGuard.requireModule("internal-audit");
        service.unlink(TenantContext.getTenantIdAsObject(), id, linkId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/linked-workpapers")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<List<LinkResponse>>> workpapersFor(@RequestParam String type, @RequestParam UUID id) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.workpapersFor(TenantContext.getTenantIdAsObject(), type, id)));
    }
}
