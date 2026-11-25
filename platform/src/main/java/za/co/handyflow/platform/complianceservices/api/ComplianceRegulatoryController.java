package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceRegulatoryService;
import za.co.handyflow.platform.complianceservices.dto.RegulatoryDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Regulatory changes", description = "Changes in the rules, who they touch, and what was done about them")
public class ComplianceRegulatoryController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String ADMIN = "hasAuthority('COMPLIANCE_SERVICES_ADMIN')";

    private final ComplianceRegulatoryService service;
    private final FeatureGuard featureGuard;

    // ---- sources ----

    @GetMapping("/regulatory/sources")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<SourceView>>> sources() {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.sources(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/regulatory/sources")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<SourceView>> createSource(@Valid @RequestBody SourceRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Source added", service.createSource(TenantContext.getTenantIdAsObject(), request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/regulatory/sources/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<SourceView>> editSource(@PathVariable UUID id, @Valid @RequestBody SourceRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.editSource(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PutMapping("/regulatory/sources/{id}/active")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<SourceView>> setActive(@PathVariable UUID id, @RequestBody ActiveRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.setSourceActive(TenantContext.getTenantIdAsObject(), id, request.active())));
    }

    // ---- changes ----

    @GetMapping("/regulatory/changes")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<ChangeSummary>>> list(@RequestParam(required = false) String status) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status)));
    }

    @GetMapping("/regulatory/changes/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<ChangeDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/regulatory/changes")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ChangeDetail>> raise(@Valid @RequestBody ChangeRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Change recorded",
                service.raise(TenantContext.getTenantIdAsObject(), request, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PutMapping("/regulatory/changes/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ChangeDetail>> edit(@PathVariable UUID id, @Valid @RequestBody ChangeRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.edit(TenantContext.getTenantIdAsObject(), id, request, LocalDate.now())));
    }

    @PostMapping("/regulatory/changes/{id}/start-review")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ChangeDetail>> startReview(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.startReview(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/regulatory/changes/{id}/confirm")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<ChangeDetail>> confirm(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Change confirmed", service.confirm(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/regulatory/changes/{id}/dismiss")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<ChangeDetail>> dismiss(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Change dismissed", service.dismiss(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/regulatory/changes/{id}/refresh-impacts")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Generated>> refresh(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.refreshImpacts(TenantContext.getTenantIdAsObject(), id)));
    }

    // ---- impacts ----

    @GetMapping("/clients/{clientId}/regulatory-impacts")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<ImpactView>>> forClient(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.forClient(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping("/regulatory/impacts/{impactId}/act")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ImpactView>> act(@PathVariable UUID impactId, @Valid @RequestBody ImpactActionRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.act(TenantContext.getTenantIdAsObject(), impactId, request, TenantContext.getCurrentUserId())));
    }
}
