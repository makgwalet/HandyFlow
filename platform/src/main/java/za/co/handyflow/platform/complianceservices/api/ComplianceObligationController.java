package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceObligationService;
import za.co.handyflow.platform.complianceservices.dto.ObligationDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services/obligations")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Obligation catalogue", description = "Versioned obligations, written, reviewed and approved by people")
public class ComplianceObligationController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    /** Approving regulatory content is an admin decision, not something every editor can do. */
    private static final String APPROVE = "hasAuthority('COMPLIANCE_SERVICES_ADMIN')";

    private final ComplianceObligationService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<ObligationSummary>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), asOf == null ? LocalDate.now() : asOf)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<ObligationDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ObligationDetail>> create(@Valid @RequestBody CreateObligationRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Obligation created as a draft",
                service.create(TenantContext.getTenantIdAsObject(), request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}/versions/{versionId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ObligationDetail>> saveDraft(@PathVariable UUID id, @PathVariable UUID versionId,
                                                                    @Valid @RequestBody SaveVersionRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Draft saved",
                service.saveDraft(TenantContext.getTenantIdAsObject(), id, versionId, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ObligationDetail>> newVersion(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("New draft started",
                service.newVersion(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/versions/{versionId}/submit")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ObligationDetail>> submit(@PathVariable UUID id, @PathVariable UUID versionId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Sent for review",
                service.submit(TenantContext.getTenantIdAsObject(), id, versionId, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/versions/{versionId}/approve")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<ObligationDetail>> approve(@PathVariable UUID id, @PathVariable UUID versionId,
                                                                  @Valid @RequestBody ApproveRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Approved",
                service.approve(TenantContext.getTenantIdAsObject(), id, versionId, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/versions/{versionId}/reject")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<ObligationDetail>> reject(@PathVariable UUID id, @PathVariable UUID versionId,
                                                                 @Valid @RequestBody RejectRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Sent back",
                service.reject(TenantContext.getTenantIdAsObject(), id, versionId, request.reason(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<ObligationDetail>> retire(@PathVariable UUID id, @RequestBody(required = false) RetireRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Retired",
                service.retire(TenantContext.getTenantIdAsObject(), id, request == null ? null : request.on(), TenantContext.getCurrentUserId())));
    }
}
