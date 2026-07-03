package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthmovingIncidentService;
import za.co.handyflow.platform.earthmoving.dto.CreateIncidentRequest;
import za.co.handyflow.platform.earthmoving.dto.IncidentResponse;
import za.co.handyflow.platform.earthmoving.dto.ResolveIncidentRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/earthmoving/incidents")
@RequiredArgsConstructor
@Tag(name = "Earthmoving - Incidents", description = "Breakdown, accident and safety incident reporting")
public class EarthmovingIncidentController {

    private final EarthmovingIncidentService incidentService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List incidents, optionally filtered by status or severity")
    public ResponseEntity<ApiResponse<Page<IncidentResponse>>> getIncidents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(
                incidentService.getIncidents(TenantContext.getTenantIdAsObject(), status, severity, pageable)));
    }

    @GetMapping("/asset/{assetId}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List incidents for one asset")
    public ResponseEntity<ApiResponse<Page<IncidentResponse>>> getIncidentsForAsset(
            @PathVariable UUID assetId,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(
                incidentService.getIncidentsForAsset(TenantContext.getTenantIdAsObject(), assetId, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Report an incident — BREAKDOWN/ACCIDENT types auto-transition the asset to BREAKDOWN status where legal")
    public ResponseEntity<ApiResponse<IncidentResponse>> reportIncident(@Valid @RequestBody CreateIncidentRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Incident reported",
                        incidentService.reportIncident(TenantContext.getTenantIdAsObject(), request, UserContext.getCurrentUserId())));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Mark an incident as resolved")
    public ResponseEntity<ApiResponse<IncidentResponse>> resolveIncident(
            @PathVariable UUID id,
            @RequestBody(required = false) ResolveIncidentRequest request) {
        featureGuard.requireModule("earthmoving");
        ResolveIncidentRequest body = request != null ? request : new ResolveIncidentRequest(null);
        return ResponseEntity.ok(ApiResponse.success("Incident resolved",
                incidentService.resolveIncident(TenantContext.getTenantIdAsObject(), id, body, UserContext.getCurrentUserId())));
    }
}