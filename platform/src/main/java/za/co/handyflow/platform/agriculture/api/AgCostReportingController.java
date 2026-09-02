package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.agriculture.application.internal.AgCostReportingService;
import za.co.handyflow.platform.agriculture.dto.AnimalCostSummaryResponse;
import za.co.handyflow.platform.agriculture.dto.CropCycleCostSummaryResponse;
import za.co.handyflow.platform.agriculture.dto.GroupCostSummaryResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Cost-per-animal / cost-per-group / cost-per-hectare — a direct port of
 * {@code FleetController}'s own cost-summary endpoint pair
 * ({@code /vehicles/{id}/cost-summary} and {@code /cost-summary}), applied
 * three times over (animals, groups, crop cycles) since this module has
 * three distinct cost-bearing units rather than fleet's one. See
 * {@code AgCostReportingService}'s own Javadoc for exactly what is and
 * isn't included in these figures — in particular, this is cost only, no
 * revenue/profitability, and group totals exclude acquisition cost.
 */
@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Cost Reporting", description = "Cost-per-animal, cost-per-group, and cost-per-hectare rollups")
public class AgCostReportingController {

    private final AgCostReportingService costReportingService;
    private final FeatureGuard featureGuard;

    // ── Animals ──────────────────────────────────────────────────────────

    @GetMapping("/animals/{id}/cost-summary")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "Cost breakdown for one animal (acquisition + health + feed cost, and cost per kg liveweight)")
    public ResponseEntity<ApiResponse<AnimalCostSummaryResponse>> getAnimalCostSummary(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                costReportingService.getAnimalCostSummary(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/farms/{farmId}/animals/cost-summary")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "Cost breakdown for every animal on a farm, sorted most expensive first")
    public ResponseEntity<ApiResponse<List<AnimalCostSummaryResponse>>> getFarmAnimalCostSummaries(@PathVariable UUID farmId) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                costReportingService.getFarmAnimalCostSummaries(TenantContext.getTenantIdAsObject(), farmId)));
    }

    // ── Groups ───────────────────────────────────────────────────────────

    @GetMapping("/groups/{id}/cost-summary")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "Cost breakdown for one group (health + feed cost — excludes acquisition cost, see service Javadoc)")
    public ResponseEntity<ApiResponse<GroupCostSummaryResponse>> getGroupCostSummary(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                costReportingService.getGroupCostSummary(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/farms/{farmId}/groups/cost-summary")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "Cost breakdown for every group on a farm, sorted most expensive first")
    public ResponseEntity<ApiResponse<List<GroupCostSummaryResponse>>> getFarmGroupCostSummaries(@PathVariable UUID farmId) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                costReportingService.getFarmGroupCostSummaries(TenantContext.getTenantIdAsObject(), farmId)));
    }

    // ── Crop cycles ──────────────────────────────────────────────────────

    @GetMapping("/crop-cycles/{id}/cost-summary")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "Cost-per-hectare breakdown for one crop cycle (seed + input cost, and yield per hectare)")
    public ResponseEntity<ApiResponse<CropCycleCostSummaryResponse>> getCropCycleCostSummary(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                costReportingService.getCropCycleCostSummary(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/farms/{farmId}/crop-cycles/cost-summary")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "Cost-per-hectare breakdown for every crop cycle on a farm, sorted most expensive per hectare first")
    public ResponseEntity<ApiResponse<List<CropCycleCostSummaryResponse>>> getFarmCropCycleCostSummaries(@PathVariable UUID farmId) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                costReportingService.getFarmCropCycleCostSummaries(TenantContext.getTenantIdAsObject(), farmId)));
    }
}
