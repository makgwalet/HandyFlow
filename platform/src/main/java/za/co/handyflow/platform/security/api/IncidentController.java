// security/api/IncidentController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.IncidentService;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security/incidents")
@RequiredArgsConstructor
@Tag(name = "Security - Incidents", description = "Incident reporting and lifecycle management")
public class IncidentController {

    private final IncidentService incidentService;
    private final FeatureGuard    featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(
            summary = "List incidents with optional status/severity filters",
            description = "Paginated and sorted in SQL (fixes in-memory filtering bug). " +
                    "Supports ?sort=severity,desc or ?sort=createdAt,asc etc."
    )
    public ResponseEntity<ApiResponse<Page<IncidentResponse>>> getIncidents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            Pageable pageable) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Success",
                incidentService.getIncidents(
                        TenantContext.getTenantIdAsObject(), status, severity, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(
            summary = "Report a new incident",
            description = "siteId and guardId are validated as belonging to this tenant. " +
                    "incident.type is now set from the request (THEFT, FIRE, ASSAULT, etc.)."
    )
    public ResponseEntity<ApiResponse<IncidentResponse>> createIncident(
            @Valid @RequestBody CreateIncidentRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.status(201).body(ApiResponse.success("Incident reported",
                incidentService.createIncident(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Acknowledge an incident — OPEN → ACKNOWLEDGED",
            description = "Records who acknowledged and when (fixes missing acknowledgedBy audit trail)."
    )
    public ResponseEntity<ApiResponse<IncidentResponse>> acknowledge(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        // Fix bug #20: pass the authenticated user so acknowledgedBy is recorded.
        UUID actorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Acknowledged",
                incidentService.acknowledge(TenantContext.getTenantIdAsObject(), id, actorId)));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Resolve an incident — ACKNOWLEDGED → RESOLVED",
            description = "Records who resolved and when (fixes missing resolvedBy audit trail)."
    )
    public ResponseEntity<ApiResponse<IncidentResponse>> resolve(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        UUID actorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Resolved",
                incidentService.resolve(TenantContext.getTenantIdAsObject(), id, actorId)));
    }
}
