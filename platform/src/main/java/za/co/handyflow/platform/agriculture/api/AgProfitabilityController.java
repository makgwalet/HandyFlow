package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.agriculture.application.internal.AgProfitabilityService;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.ProfitabilityResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * The farm's gross-margin report (ADR-001, W5). It shows revenue, so it needs AGRICULTURE_FINANCE and INVOICE_READ, like the Sales screen: a person who
 * can see farm margins can see the invoice data they are made from. Read-only; computed live.
 */
@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Profitability", description = "Gross margin per crop cycle, group, animal and enterprise")
public class AgProfitabilityController {

    private final AgProfitabilityService profitabilityService;
    private final FeatureGuard featureGuard;

    @GetMapping("/farms/{farmId}/profitability")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE') and hasAuthority('INVOICE_READ')")
    @Operation(summary = "Gross margin (revenue ex-VAT minus direct production costs) per unit, with finished units separate from those still running",
            description = "Gross margin only: overheads, finance costs, depreciation and tax belong to Accounting. Revenue is computed live and never stored. Optional seasonId limits it to that season's crop cycles.")
    public ResponseEntity<ApiResponse<ProfitabilityResponse>> profitability(@PathVariable UUID farmId, @RequestParam(required = false) UUID seasonId) {
        featureGuard.requireModule("agriculture");
        featureGuard.requireModule("invoicing");
        return ResponseEntity.ok(ApiResponse.success(profitabilityService.farm(TenantContext.getTenantIdAsObject(), farmId, seasonId)));
    }
}
