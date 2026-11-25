package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceQualityService;
import za.co.handyflow.platform.complianceservices.dto.QualityDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Quality review", description = "Checklists, second-person review, rework and sampling")
public class ComplianceQualityController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_QUALITY_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String ADMIN = "hasAuthority('COMPLIANCE_SERVICES_ADMIN')";

    private final ComplianceQualityService service;
    private final FeatureGuard featureGuard;

    // ---- checklists ----

    @GetMapping("/quality/templates")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<TemplateView>>> templates() {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.templates(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/quality/templates")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<TemplateView>> createTemplate(@Valid @RequestBody TemplateRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Checklist saved", service.createTemplate(TenantContext.getTenantIdAsObject(), request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/quality/templates/{id}")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<TemplateView>> editTemplate(@PathVariable UUID id, @Valid @RequestBody TemplateRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.editTemplate(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PutMapping("/quality/templates/{id}/active")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<TemplateView>> setActive(@PathVariable UUID id, @RequestBody ActiveRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.setTemplateActive(TenantContext.getTenantIdAsObject(), id, request.active())));
    }

    // ---- reviews ----

    @GetMapping("/quality/reviews")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<ReviewSummary>>> list(@RequestParam(required = false) String status) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status)));
    }

    @GetMapping("/clients/{clientId}/quality-reviews")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<ReviewSummary>>> forClient(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.forClient(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @GetMapping("/quality/reviews/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<ReviewDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{clientId}/quality-reviews")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ReviewDetail>> start(@PathVariable UUID clientId, @Valid @RequestBody StartRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Review started", service.start(TenantContext.getTenantIdAsObject(), clientId, request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/quality/reviews/{id}/checks/{position}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ReviewDetail>> mark(@PathVariable UUID id, @PathVariable int position, @Valid @RequestBody MarkRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.mark(TenantContext.getTenantIdAsObject(), id, position, request)));
    }

    @PostMapping("/quality/reviews/{id}/complete")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ReviewDetail>> complete(@PathVariable UUID id, @Valid @RequestBody CompleteRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Review completed", service.complete(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/quality/reviews/{id}/next-round")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ReviewDetail>> nextRound(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Next round started", service.nextRound(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/quality/sample")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<SampleResult>> sample(@Valid @RequestBody SampleRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.sample(TenantContext.getTenantIdAsObject(), request, TenantContext.getCurrentUserId())));
    }
}
