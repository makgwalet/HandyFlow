package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.DisputeService;
import za.co.handyflow.platform.debtcollection.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Debtor disputes: records with an owner, a response date and a resolution (finance F16). */
@RestController
@RequestMapping("/api/v1/debtcollection")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Disputes", description = "Debtor disputes")
public class DisputeController {

    private static final String READ = "hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')";

    private final DisputeService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/disputes")
    @PreAuthorize(READ)
    @Operation(summary = "Every unresolved dispute, soonest response date first")
    public ResponseEntity<ApiResponse<List<DisputeResponse>>> unresolved() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Success", service.unresolved(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/cases/{caseId}/disputes")
    @PreAuthorize(READ)
    @Operation(summary = "Disputes on a case")
    public ResponseEntity<ApiResponse<List<DisputeResponse>>> forCase(@PathVariable UUID caseId) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Success", service.forCase(TenantContext.getTenantIdAsObject(), caseId)));
    }

    @PostMapping("/cases/{caseId}/disputes")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Record a dispute; the case is paused")
    public ResponseEntity<ApiResponse<DisputeResponse>> open(@PathVariable UUID caseId, @RequestBody OpenDisputeRequest req) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Dispute recorded", service.open(TenantContext.getTenantIdAsObject(), caseId, req)));
    }

    @PostMapping("/disputes/{id}/take-on")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Take the dispute into review")
    public ResponseEntity<ApiResponse<DisputeResponse>> takeOn(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("In review", service.takeOn(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/disputes/{id}/assign")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Change who owns the dispute")
    public ResponseEntity<ApiResponse<DisputeResponse>> assign(@PathVariable UUID id, @RequestBody AssignDisputeRequest req) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Assigned", service.assign(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/disputes/{id}/resolve")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Resolve the dispute; the case resumes when no other dispute is open")
    public ResponseEntity<ApiResponse<DisputeResponse>> resolve(@PathVariable UUID id, @RequestBody ResolveDisputeRequest req) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Resolved", service.resolve(TenantContext.getTenantIdAsObject(), id, req)));
    }
}
