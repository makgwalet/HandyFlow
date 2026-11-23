package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.agriculture.application.internal.AgComplianceService;
import za.co.handyflow.platform.agriculture.application.internal.AgFarmService;
import za.co.handyflow.platform.agriculture.dto.FarmResponse;
import za.co.handyflow.platform.agriculture.dto.UpdateFarmMarksRequest;
import za.co.handyflow.platform.agriculture.dto.ProduceWithdrawalResponse;
import za.co.handyflow.platform.agriculture.dto.WithdrawalStatusResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.UUID;

/** Registered marks on the farm, and whether an animal, group or crop is inside a withdrawal period / pre-harvest interval. */
@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Compliance", description = "Farm marks, withdrawal periods, pre-harvest intervals")
public class AgComplianceController {

    private final AgComplianceService complianceService;
    private final AgFarmService farmService;
    private final FeatureGuard featureGuard;

    @PutMapping("/farms/{farmId}/marks")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Set the farm's registered brand mark, ear mark and mark registration number")
    public ResponseEntity<ApiResponse<FarmResponse>> updateMarks(@PathVariable UUID farmId, @RequestBody UpdateFarmMarksRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Farm marks updated",
                farmService.updateMarks(TenantContext.getTenantIdAsObject(), farmId, request)));
    }

    @GetMapping("/animals/{animalId}/withdrawal")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "Is this animal in a withdrawal period? (today, or on asOf)")
    public ResponseEntity<ApiResponse<WithdrawalStatusResponse>> animalWithdrawal(
            @PathVariable UUID animalId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(complianceService.animalStatus(TenantContext.getTenantIdAsObject(), animalId, asOf)));
    }

    @GetMapping("/groups/{groupId}/withdrawal")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<WithdrawalStatusResponse>> groupWithdrawal(
            @PathVariable UUID groupId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(complianceService.groupStatus(TenantContext.getTenantIdAsObject(), groupId, asOf)));
    }

    @GetMapping("/animals/{animalId}/produce-withdrawal")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "Is milk or egg from this animal held? (today, or on asOf)")
    public ResponseEntity<ApiResponse<ProduceWithdrawalResponse>> animalProduceWithdrawal(
            @PathVariable UUID animalId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(complianceService.animalProduceStatus(TenantContext.getTenantIdAsObject(), animalId, asOf)));
    }

    @GetMapping("/groups/{groupId}/produce-withdrawal")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<ProduceWithdrawalResponse>> groupProduceWithdrawal(
            @PathVariable UUID groupId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(complianceService.groupProduceStatus(TenantContext.getTenantIdAsObject(), groupId, asOf)));
    }

    @GetMapping("/crop-cycles/{cropCycleId}/pre-harvest")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<WithdrawalStatusResponse>> cropPreHarvest(
            @PathVariable UUID cropCycleId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(complianceService.cropStatus(TenantContext.getTenantIdAsObject(), cropCycleId, asOf)));
    }
}
