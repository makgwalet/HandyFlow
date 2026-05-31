package za.co.handyflow.platform.security.api;

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
@Tag(name = "Security - Incidents")
public class IncidentController {

    private final IncidentService incidentService;
    private final FeatureGuard    featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<IncidentResponse>>> getIncidents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            Pageable pageable) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Success",
                incidentService.getIncidents(TenantContext.getTenantIdAsObject(),
                        status, severity, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<ApiResponse<IncidentResponse>> createIncident(
            @Valid @RequestBody CreateIncidentRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.status(201).body(ApiResponse.success("Incident reported",
                incidentService.createIncident(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<IncidentResponse>> acknowledge(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Acknowledged",
                incidentService.acknowledge(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<IncidentResponse>> resolve(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Resolved",
                incidentService.resolve(TenantContext.getTenantIdAsObject(), id)));
    }
}