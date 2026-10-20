package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthFieldSyncService;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

/** Entries captured on a phone, sent when there is a connection. Safe to send again: each entry has its own submission id. */
@RestController
@RequestMapping("/api/v1/earthmoving/field")
@RequiredArgsConstructor
@Tag(name = "Earthmoving field capture")
public class EarthFieldController {

    private final EarthFieldSyncService field;
    private final FeatureGuard featureGuard;

    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('EARTHMOVING_WRITE')")
    @Operation(summary = "Send queued entries. Each comes back SYNCED, REJECTED or CONFLICT; one bad entry never stops the rest")
    public ResponseEntity<ApiResponse<List<FieldEntryResult>>> sync(@Valid @RequestBody FieldSyncRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Synced", field.sync(TenantContext.getTenantIdAsObject(), request, UserContext.getCurrentUserId())));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    public ResponseEntity<ApiResponse<List<FieldEntryResult>>> mine(@RequestParam(defaultValue = "50") int limit) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("My entries", field.mine(TenantContext.getTenantIdAsObject(), UserContext.getCurrentUserId(), limit)));
    }

    @GetMapping("/conflicts")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    public ResponseEntity<ApiResponse<List<FieldEntryResult>>> conflicts() {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Conflicts", field.openConflicts(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/conflicts/{id}/review")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    public ResponseEntity<ApiResponse<FieldEntryResult>> review(@PathVariable UUID id, @RequestBody ReviewFieldConflictRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Conflict reviewed", field.review(TenantContext.getTenantIdAsObject(), id, request, UserContext.getCurrentUserId())));
    }
}
