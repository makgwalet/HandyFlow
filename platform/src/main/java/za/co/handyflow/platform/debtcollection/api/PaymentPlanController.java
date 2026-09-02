package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.PaymentPlanService;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlan;
import za.co.handyflow.platform.debtcollection.dto.CancelPlanRequest;
import za.co.handyflow.platform.debtcollection.dto.MarkPlanDefaultedRequest;
import za.co.handyflow.platform.debtcollection.dto.PaymentPlanResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * PaymentPlan state transitions, addressed by planId directly — separate
 * from DebtCollectionCaseController (which owns plan creation/listing,
 * always in the context of a case) because these actions only need the
 * plan's own id.
 */
@RestController
@RequestMapping("/api/v1/debtcollection/payment-plans")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Payment Plans", description = "Structured repayment agreement state transitions")
public class PaymentPlanController {

    private final PaymentPlanService paymentPlanService;
    private final FeatureGuard featureGuard;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<PaymentPlanResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        PaymentPlan plan = paymentPlanService.get(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(PaymentPlanResponse.of(plan)));
    }

    @PostMapping("/{id}/mark-installment-paid")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<PaymentPlanResponse>> markInstallmentPaid(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        PaymentPlan plan = paymentPlanService.markInstallmentPaid(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Installment recorded", PaymentPlanResponse.of(plan)));
    }

    @PostMapping("/{id}/mark-defaulted")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<PaymentPlanResponse>> markDefaulted(
            @PathVariable UUID id, @Valid @RequestBody MarkPlanDefaultedRequest req) {
        featureGuard.requireModule("debtcollection");
        PaymentPlan plan = paymentPlanService.markDefaulted(TenantContext.getTenantIdAsObject(), id, req.reason());
        return ResponseEntity.ok(ApiResponse.success("Plan marked defaulted", PaymentPlanResponse.of(plan)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<PaymentPlanResponse>> cancel(
            @PathVariable UUID id, @Valid @RequestBody CancelPlanRequest req) {
        featureGuard.requireModule("debtcollection");
        PaymentPlan plan = paymentPlanService.cancel(TenantContext.getTenantIdAsObject(), id, req.reason());
        return ResponseEntity.ok(ApiResponse.success("Plan cancelled", PaymentPlanResponse.of(plan)));
    }
}
