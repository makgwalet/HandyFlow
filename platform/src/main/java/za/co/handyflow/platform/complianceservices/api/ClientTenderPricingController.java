package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ClientTenderPricingService;
import za.co.handyflow.platform.complianceservices.dto.ClientTenderPricingResponse;
import za.co.handyflow.platform.complianceservices.dto.SaveClientTenderPricingLineRequest;
import za.co.handyflow.platform.complianceservices.dto.SaveClientTenderPricingSettingsRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** Pricing for a client's tender (ADR-004, client side). Commercially sensitive, so even reading it needs MANAGE or ADMIN, not just READ. */
@RestController
@RequestMapping("/api/v1/compliance-services/tenders")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Client Tender Pricing", description = "Cost-based price schedule for a client's tender")
public class ClientTenderPricingController {

    private final ClientTenderPricingService pricingService;
    private final FeatureGuard featureGuard;

    @GetMapping("/{id}/pricing")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    @Operation(summary = "The client tender's price schedule and computed breakdown")
    public ResponseEntity<ApiResponse<ClientTenderPricingResponse>> getPricing(@PathVariable UUID id) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(pricingService.getPricing(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/{id}/pricing/settings")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    @Operation(summary = "Set overhead, contingency, profit and VAT treatment (locked once submitted)")
    public ResponseEntity<ApiResponse<ClientTenderPricingResponse>> saveSettings(
            @PathVariable UUID id, @Valid @RequestBody SaveClientTenderPricingSettingsRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Pricing settings saved",
                pricingService.saveSettings(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/pricing/lines")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    @Operation(summary = "Add a line to the price schedule (locked once submitted)")
    public ResponseEntity<ApiResponse<ClientTenderPricingResponse>> addLine(
            @PathVariable UUID id, @Valid @RequestBody SaveClientTenderPricingLineRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Line added",
                pricingService.addLine(TenantContext.getTenantIdAsObject(), id, request, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/pricing/lines/{lineId}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    @Operation(summary = "Change a price schedule line (locked once submitted)")
    public ResponseEntity<ApiResponse<ClientTenderPricingResponse>> updateLine(
            @PathVariable UUID lineId, @Valid @RequestBody SaveClientTenderPricingLineRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Line updated",
                pricingService.updateLine(TenantContext.getTenantIdAsObject(), lineId, request, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/pricing/lines/{lineId}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    @Operation(summary = "Remove a price schedule line (locked once submitted)")
    public ResponseEntity<ApiResponse<ClientTenderPricingResponse>> deleteLine(@PathVariable UUID lineId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Line removed", pricingService.deleteLine(TenantContext.getTenantIdAsObject(), lineId)));
    }
}
