package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseAdjustmentApprovalService;
import za.co.handyflow.platform.warehousing.dto.AdjustmentThresholdRequest;
import za.co.handyflow.platform.warehousing.dto.ApprovalDecisionRequest;
import za.co.handyflow.platform.warehousing.dto.RequestAdjustmentRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Adjustment approval", description = "Second-person approval of stock adjustments")
public class WhseAdjustmentApprovalController {

    private final WhseAdjustmentApprovalService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/adjustment-requests/pending")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN','WAREHOUSING_ADJUST_APPROVE')")
    public ResponseEntity<ApiResponse<List<WhseAdjustmentApprovalService.Item>>> pending() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.pending(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/adjustment-approval-threshold")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> threshold() {
        featureGuard.requireModule("warehousing");
        Map<String, BigDecimal> body = new java.util.HashMap<>();
        body.put("thresholdQty", service.threshold(TenantContext.getTenantIdAsObject()));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PutMapping("/adjustment-approval-threshold")
    @PreAuthorize("hasAuthority('WAREHOUSING_ADMIN')")
    @Operation(summary = "Set the number of units above which an adjustment needs a second person; null switches the rule off. The change is audited.")
    public ResponseEntity<ApiResponse<Void>> setThreshold(@Valid @RequestBody AdjustmentThresholdRequest req) {
        featureGuard.requireModule("warehousing");
        service.setThreshold(TenantContext.getTenantIdAsObject(), req.thresholdQty(), req.reason(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Threshold saved", null));
    }

    @PostMapping("/inventory/{id}/adjustment-requests")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Ask for a stock adjustment that a second person must approve")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> request(@PathVariable UUID id, @Valid @RequestBody RequestAdjustmentRequest req) {
        featureGuard.requireModule("warehousing");
        UUID requestId = service.request(TenantContext.getTenantIdAsObject(), id, req.delta(), req.reason(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Adjustment requested", Map.of("id", requestId)));
    }

    @PostMapping("/adjustment-requests/{id}/approve")
    @PreAuthorize("hasAuthority('WAREHOUSING_ADJUST_APPROVE')")
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable UUID id, @RequestBody(required = false) ApprovalDecisionRequest req) {
        featureGuard.requireModule("warehousing");
        service.approve(TenantContext.getTenantIdAsObject(), id, req == null ? null : req.note(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Adjustment approved and applied", null));
    }

    @PostMapping("/adjustment-requests/{id}/reject")
    @PreAuthorize("hasAuthority('WAREHOUSING_ADJUST_APPROVE')")
    public ResponseEntity<ApiResponse<Void>> reject(@PathVariable UUID id, @Valid @RequestBody ApprovalDecisionRequest req) {
        featureGuard.requireModule("warehousing");
        service.reject(TenantContext.getTenantIdAsObject(), id, req.note(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Adjustment rejected", null));
    }

    @PostMapping("/adjustment-requests/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        service.cancel(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Adjustment request cancelled", null));
    }
}
