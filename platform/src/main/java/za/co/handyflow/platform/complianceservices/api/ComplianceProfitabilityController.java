package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceProfitabilityService;
import za.co.handyflow.platform.complianceservices.dto.ProfitDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Cost and margin are for administrators only. */
@RestController
@RequestMapping("/api/v1/compliance-services/profitability")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Profitability", description = "Cost rates and the profitability report (administrators only)")
public class ComplianceProfitabilityController {

    private static final String ADMIN = "hasAuthority('COMPLIANCE_SERVICES_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ComplianceProfitabilityService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/cost-rates")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<List<CostRateView>>> costRates() {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.costRates(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/cost-rates")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<CostRateView>> setRate(@Valid @RequestBody CostRateRequest body) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.setRate(TenantContext.getTenantIdAsObject(), body, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/report")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<Report>> report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("complianceservices");
        LocalDate end = to != null ? to : LocalDate.now(SAST);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        return ResponseEntity.ok(ApiResponse.success(service.report(TenantContext.getTenantIdAsObject(), start, end)));
    }
}
