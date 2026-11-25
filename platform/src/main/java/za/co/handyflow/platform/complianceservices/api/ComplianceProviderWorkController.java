package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceProviderWorkService;
import za.co.handyflow.platform.complianceservices.dto.ProviderWorkDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Provider work", description = "Work given to outside providers")
public class ComplianceProviderWorkController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String ADMIN = "hasAuthority('COMPLIANCE_SERVICES_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ComplianceProviderWorkService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() { return LocalDate.now(SAST); }

    @GetMapping("/provider-assignments")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<AssignmentView>>> all(@RequestParam(required = false) String status) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.all(TenantContext.getTenantIdAsObject(), status, today())));
    }

    @GetMapping("/clients/{clientId}/provider-assignments")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<AssignmentView>>> forClient(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.forClient(TenantContext.getTenantIdAsObject(), clientId, today())));
    }

    @GetMapping("/providers/{providerId}/assignments")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<AssignmentView>>> forProvider(@PathVariable UUID providerId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.forProvider(TenantContext.getTenantIdAsObject(), providerId, today())));
    }

    @GetMapping("/providers/{providerId}/performance")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<PerformanceView>> performance(@PathVariable UUID providerId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.performance(TenantContext.getTenantIdAsObject(), providerId)));
    }

    @GetMapping("/provider-assignments/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<AssignmentDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id, today())));
    }

    @PostMapping("/clients/{clientId}/provider-assignments")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AssignmentDetail>> propose(@PathVariable UUID clientId, @Valid @RequestBody AssignmentRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.propose(TenantContext.getTenantIdAsObject(), clientId, body, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/provider-assignments/{id}/conflict")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AssignmentDetail>> declare(@PathVariable UUID id, @Valid @RequestBody DeclarationRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.declare(TenantContext.getTenantIdAsObject(), id, body.conflict(), body.note(), TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/provider-assignments/{id}/conflict/approve")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<AssignmentDetail>> approve(@PathVariable UUID id, @Valid @RequestBody NoteRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.approveConflict(TenantContext.getTenantIdAsObject(), id, body.note(), TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/provider-assignments/{id}/activate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AssignmentDetail>> activate(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.activate(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/provider-assignments/{id}/complete")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AssignmentDetail>> complete(@PathVariable UUID id, @Valid @RequestBody CompleteRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.complete(TenantContext.getTenantIdAsObject(), id, body.rating(), body.note(), TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/provider-assignments/{id}/cancel")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AssignmentDetail>> cancel(@PathVariable UUID id, @Valid @RequestBody NoteRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.cancel(TenantContext.getTenantIdAsObject(), id, body.note(), TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/provider-assignments/{id}/deliverables")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AssignmentDetail>> expect(@PathVariable UUID id, @Valid @RequestBody DeliverableRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.expect(TenantContext.getTenantIdAsObject(), id, body, today())));
    }

    @PostMapping("/provider-deliverables/{id}/received")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AssignmentDetail>> received(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.received(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/provider-deliverables/{id}/review")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AssignmentDetail>> review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.review(TenantContext.getTenantIdAsObject(), id, body.accepted(), body.note(), TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/provider-deliverables/{id}/waive")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AssignmentDetail>> waive(@PathVariable UUID id, @Valid @RequestBody NoteRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.waive(TenantContext.getTenantIdAsObject(), id, body.note(), TenantContext.getCurrentUserId(), today())));
    }
}
