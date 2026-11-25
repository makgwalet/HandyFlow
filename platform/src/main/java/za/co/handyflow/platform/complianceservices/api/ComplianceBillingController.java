package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceBillingService;
import za.co.handyflow.platform.complianceservices.dto.BillingDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Billing", description = "Scheduled bills of active agreements, raised as draft invoices in Invoicing")
public class ComplianceBillingController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";

    private final ComplianceBillingService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/billing-events")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<EventView>>> listAll(@RequestParam(required = false) String filter) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.listAll(TenantContext.getTenantIdAsObject(), filter, LocalDate.now())));
    }

    @GetMapping("/clients/{clientId}/agreements/{agreementId}/billing-events")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<EventView>>> forAgreement(@PathVariable UUID clientId, @PathVariable UUID agreementId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.forAgreement(TenantContext.getTenantIdAsObject(), clientId, agreementId, LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/agreements/{agreementId}/billing-events/generate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Generated>> generate(@PathVariable UUID clientId, @PathVariable UUID agreementId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Bills made", service.generate(TenantContext.getTenantIdAsObject(), clientId, agreementId, LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/billing-events/{id}/raise")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<EventView>> raise(@PathVariable UUID clientId, @PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Draft invoice raised. Review and issue it in Invoicing",
                service.raise(TenantContext.getTenantIdAsObject(), clientId, id, TenantContext.getCurrentUserId(), LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/billing-events/{id}/skip")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<EventView>> skip(@PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Bill skipped", service.skip(TenantContext.getTenantIdAsObject(), clientId, id, request.reason(), LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/billing-events/{id}/restore")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<EventView>> restore(@PathVariable UUID clientId, @PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Bill brought back", service.restore(TenantContext.getTenantIdAsObject(), clientId, id, LocalDate.now())));
    }

    @PostMapping("/clients/{clientId}/billing-events/{id}/release")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<EventView>> release(@PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Invoice released", service.release(TenantContext.getTenantIdAsObject(), clientId, id, request.reason(), LocalDate.now())));
    }
}
