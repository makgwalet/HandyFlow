package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceApprovalRuleService;
import za.co.handyflow.platform.complianceservices.dto.ApprovalRuleDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services/approval-rules")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Approval rules", description = "Who must approve a quote, beyond the discount limits")
public class ComplianceApprovalRuleController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String ADMIN = "hasAuthority('COMPLIANCE_SERVICES_ADMIN')";

    private final ComplianceApprovalRuleService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<RuleView>>> list() {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<RuleView>> propose(@Valid @RequestBody RuleRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.propose(TenantContext.getTenantIdAsObject(), body, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<RuleView>> activate(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.activate(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<RuleView>> retire(@PathVariable UUID id, @Valid @RequestBody NoteRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.retire(TenantContext.getTenantIdAsObject(), id, body.note(), TenantContext.getCurrentUserId())));
    }
}
