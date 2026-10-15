package za.co.handyflow.platform.earthmoving.api;

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
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthWorkOrderService;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/earthmoving/work-orders")
@RequiredArgsConstructor
@Tag(name = "Earthmoving - Work orders", description = "Repair and service work orders")
public class EarthWorkOrderController {

    private final EarthWorkOrderService service;
    private final FeatureGuard featureGuard;

    private static za.co.handyflow.platform.shared.TenantId tenant() { return TenantContext.getTenantIdAsObject(); }

    @GetMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "List work orders, optionally by status or asset")
    public ResponseEntity<ApiResponse<Page<WorkOrderResponse>>> list(
            @RequestParam(required = false) String status, @RequestParam(required = false) UUID assetId,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.list(tenant(), status, assetId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.get(tenant(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_WRITE')")
    @Operation(summary = "Raise a work order")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> create(@Valid @RequestBody CreateWorkOrderRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Work order raised",
                service.create(tenant(), request, UserContext.getCurrentUserId())));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> assign(@PathVariable UUID id, @Valid @RequestBody AssignWorkOrderRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.assign(tenant(), id, request.userId())));
    }

    @PatchMapping("/{id}/start")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> start(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.start(tenant(), id)));
    }

    @PatchMapping("/{id}/awaiting-parts")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> awaitParts(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.awaitParts(tenant(), id)));
    }

    @PatchMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> resume(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.resume(tenant(), id)));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> cancel(@PathVariable UUID id, @RequestBody(required = false) WorkOrderReasonRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.cancel(tenant(), id, request == null ? null : request.reason())));
    }

    @PatchMapping("/{id}/blocks-release")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    @Operation(summary = "Change whether this work order stops the machine being released (a reason is needed to turn it off)")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> blocksRelease(@PathVariable UUID id, @Valid @RequestBody WorkOrderBlocksReleaseRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.setBlocksRelease(tenant(), id, request.blocksRelease(), request.reason())));
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> addLine(@PathVariable UUID id, @Valid @RequestBody AddWorkOrderLineRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.addLine(tenant(), id, request)));
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> removeLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.removeLine(tenant(), id, lineId)));
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    @Operation(summary = "Record the work done and test result. This does not release the machine.")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> complete(@PathVariable UUID id, @Valid @RequestBody CompleteWorkOrderRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Work order completed",
                service.complete(tenant(), id, request, UserContext.getCurrentUserId())));
    }
}
