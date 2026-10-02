package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.agriculture.application.internal.AgFinanceSettingsService;
import za.co.handyflow.platform.agriculture.application.internal.AgLabourCostService;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostEntryResponse;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.CostLabourRequest;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.FinanceSettingsResponse;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.LabourOverview;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.UpdateFinanceSettingsRequest;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Costs labour from HR into the cost ledger (ADR-001, W3). Every endpoint needs AGRICULTURE_FINANCE.
 * <p>
 * Salaries stay behind HR's own rule: salary-based rates are only offered and only used for callers who also hold an HR permission (the same
 * ones HR itself accepts for reading an employee: HR_READ, HR_MANAGE, USER_READ). Without one, labour can still be costed at a typed-in rate,
 * which is how casual workers with no HR record are handled. A caller without HR access therefore gets no salary-derived figure from this API.
 */
@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Labour costing", description = "Cost recorded labour from HR salaries or typed-in rates")
public class AgLabourController {

    /** HR's own authorities for reading an employee (see HrController#getEmployee). */
    private static final Set<String> HR_READ_AUTHORITIES = Set.of("HR_READ", "HR_MANAGE", "USER_READ");

    private final AgLabourCostService labourService;
    private final AgFinanceSettingsService settingsService;
    private final FeatureGuard featureGuard;

    @GetMapping("/finance/settings")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE')")
    @Operation(summary = "Standard hours per week and labour on-cost; the defaults (45 h, 0%) apply until saved")
    public ResponseEntity<ApiResponse<FinanceSettingsResponse>> getSettings() {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(settingsService.get(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/finance/settings")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE')")
    @Operation(summary = "Save the settings; they apply to labour costed from now on, past costs keep the rate they were costed at")
    public ResponseEntity<ApiResponse<FinanceSettingsResponse>> updateSettings(@Valid @RequestBody UpdateFinanceSettingsRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(settingsService.update(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), request)));
    }

    @GetMapping("/farms/{farmId}/labour/uncosted")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE')")
    @Operation(summary = "Labour hours recorded on input applications and harvests that are not costed yet, with the HR rate where the caller may see it")
    public ResponseEntity<ApiResponse<LabourOverview>> uncosted(@PathVariable UUID farmId) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(labourService.overview(TenantContext.getTenantIdAsObject(), farmId, canUseHrRates())));
    }

    @PostMapping("/farms/{farmId}/labour/cost")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE')")
    @Operation(summary = "Cost the chosen work into the ledger, one LABOUR entry each against its crop cycle; all or nothing",
            description = "Each item may carry a typed-in hourly rate (before on-costs). Without one, the rate comes from the worker's HR record, which needs an HR permission.")
    public ResponseEntity<ApiResponse<List<CostEntryResponse>>> cost(@PathVariable UUID farmId, @Valid @RequestBody CostLabourRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                labourService.cost(TenantContext.getTenantIdAsObject(), farmId, TenantContext.getCurrentUserId(), canUseHrRates(), request)));
    }

    /** True when the caller holds one of the authorities HR itself accepts for reading an employee. */
    private static boolean canUseHrRates() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (HR_READ_AUTHORITIES.contains(a.getAuthority())) return true;
        }
        return false;
    }
}
