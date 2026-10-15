package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthChecklistService;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/earthmoving/checklists")
@RequiredArgsConstructor
@Tag(name = "Earthmoving inspection checklists")
public class EarthChecklistController {

    private final EarthChecklistService checklists;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "For each inspection type: the checklist in force, the draft, and the history")
    public ResponseEntity<ApiResponse<List<ChecklistTypeOverview>>> overview() {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Checklists", checklists.overview(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "The checklist in force for one inspection type; empty when a type has none yet")
    public ResponseEntity<ApiResponse<ActiveChecklistResponse>> active(@RequestParam(required = false) String type) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Checklist", checklists.active(TenantContext.getTenantIdAsObject(), type)));
    }

    @GetMapping("/versions/{id}")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "One checklist version with its questions")
    public ResponseEntity<ApiResponse<ChecklistVersionResponse>> version(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Checklist version", checklists.version(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/draft")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Start a draft for an inspection type, copied from what is in force")
    public ResponseEntity<ApiResponse<ChecklistVersionResponse>> draft(@RequestParam String type) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Draft started",
                checklists.createDraft(TenantContext.getTenantIdAsObject(), type, UserContext.getCurrentUserId())));
    }

    @PutMapping("/versions/{id}")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Replace the questions of a draft")
    public ResponseEntity<ApiResponse<ChecklistVersionResponse>> save(@PathVariable UUID id, @Valid @RequestBody SaveChecklistDraftRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Draft saved",
                checklists.saveDraft(TenantContext.getTenantIdAsObject(), id, request, UserContext.getCurrentUserId())));
    }

    @PostMapping("/versions/{id}/publish")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Publish a draft from a date; a published version is never changed")
    public ResponseEntity<ApiResponse<ChecklistVersionResponse>> publish(@PathVariable UUID id, @RequestBody(required = false) PublishChecklistRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Checklist published",
                checklists.publish(TenantContext.getTenantIdAsObject(), id, request, UserContext.getCurrentUserId())));
    }

    @DeleteMapping("/versions/{id}")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Discard a draft")
    public ResponseEntity<ApiResponse<Void>> discard(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        checklists.discard(TenantContext.getTenantIdAsObject(), id, UserContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Draft discarded", null));
    }
}
