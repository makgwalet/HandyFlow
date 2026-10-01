package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.agriculture.application.internal.AgCostEntryService;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostEntryResponse;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostTotalsResponse;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CreateCostEntryRequest;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.ReverseCostEntryRequest;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * The Agriculture cost ledger (ADR-001, W1). Everything here needs AGRICULTURE_FINANCE, not just AGRICULTURE_MANAGE: ledger rows
 * carry the rates behind labour and equipment cost, which are derived from salaries.
 */
@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Cost ledger", description = "Direct costs allocated to crop cycles, groups, animals and enterprises")
public class AgCostEntryController {

    private final AgCostEntryService costEntryService;
    private final FeatureGuard featureGuard;

    @PostMapping("/farms/{farmId}/cost-entries")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE')")
    @Operation(summary = "Record an OTHER_DIRECT cost and allocate it across targets",
            description = "allocations' percentages must total 100; the parts add up exactly to the amount. Returns one row per target. " +
                    "Labour, equipment and fuel cannot be entered by hand.")
    public ResponseEntity<ApiResponse<List<CostEntryResponse>>> create(@PathVariable UUID farmId, @Valid @RequestBody CreateCostEntryRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                costEntryService.createManual(TenantContext.getTenantIdAsObject(), farmId, TenantContext.getCurrentUserId(), request)));
    }

    @GetMapping("/farms/{farmId}/cost-entries")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE')")
    @Operation(summary = "Ledger rows for a farm, newest first, optionally for one target (targetType and targetId together)")
    public ResponseEntity<ApiResponse<Page<CostEntryResponse>>> list(@PathVariable UUID farmId,
                                                                     @RequestParam(required = false) String targetType,
                                                                     @RequestParam(required = false) UUID targetId,
                                                                     @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                costEntryService.list(TenantContext.getTenantIdAsObject(), farmId, targetType, targetId, pageable)));
    }

    @GetMapping("/farms/{farmId}/cost-entries/totals")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE')")
    @Operation(summary = "Net cost by category for a farm, or for one target (reversals are netted out)")
    public ResponseEntity<ApiResponse<CostTotalsResponse>> totals(@PathVariable UUID farmId,
                                                                 @RequestParam(required = false) String targetType,
                                                                 @RequestParam(required = false) UUID targetId) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                costEntryService.totals(TenantContext.getTenantIdAsObject(), farmId, targetType, targetId)));
    }

    @PostMapping("/cost-entries/groups/{allocationGroupId}/reverse")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE')")
    @Operation(summary = "Reverse a whole allocation group (a split cost is reversed as a whole)",
            description = "Rows are never edited or deleted: the originals become REVERSED and negative REVERSAL rows net them out, " +
                    "dated like the original so the correction lands in the same period. 409 if already reversed.")
    public ResponseEntity<ApiResponse<List<CostEntryResponse>>> reverse(@PathVariable UUID allocationGroupId,
                                                                       @RequestBody(required = false) ReverseCostEntryRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(costEntryService.reverseGroup(TenantContext.getTenantIdAsObject(), allocationGroupId,
                TenantContext.getCurrentUserId(), request == null ? null : request.reason())));
    }
}
