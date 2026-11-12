package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelRequisitionService;
import za.co.handyflow.platform.fuel.dto.FuelWholesaleDtos.ReasonRequest;
import za.co.handyflow.platform.fuel.dto.RequisitionDtos.ConvertRequest;
import za.co.handyflow.platform.fuel.dto.RequisitionDtos.RequisitionRequest;
import za.co.handyflow.platform.fuel.dto.RequisitionDtos.RequisitionView;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** B05: purchase requisitions: request, approve (Approvals engine), convert to a draft purchase order. */
@RestController
@RequestMapping("/api/v1/fuel/requisitions")
@RequiredArgsConstructor
@Tag(name = "Fuel Requisitions", description = "Requests to buy fuel, approved before ordering")
public class FuelRequisitionController {

    private final FuelRequisitionService requisitions;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<RequisitionView>>> list() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(requisitions.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_PURCHASING_MANAGE')")
    public ResponseEntity<ApiResponse<RequisitionView>> create(@Valid @RequestBody RequisitionRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Requisition raised",
                requisitions.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_PURCHASING_MANAGE')")
    public ResponseEntity<ApiResponse<RequisitionView>> update(@PathVariable UUID id, @Valid @RequestBody RequisitionRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Requisition updated", requisitions.update(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_PURCHASING_MANAGE')")
    public ResponseEntity<ApiResponse<RequisitionView>> submit(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Requisition submitted", requisitions.submit(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_PURCHASING_MANAGE')")
    public ResponseEntity<ApiResponse<RequisitionView>> convert(@PathVariable UUID id, @Valid @RequestBody(required = false) ConvertRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Draft purchase order raised",
                requisitions.convert(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_PURCHASING_MANAGE')")
    public ResponseEntity<ApiResponse<RequisitionView>> cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Requisition cancelled", requisitions.cancel(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }
}
