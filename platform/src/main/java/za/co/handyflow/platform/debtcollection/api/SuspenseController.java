package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.SuspenseService;
import za.co.handyflow.platform.debtcollection.domain.model.SuspenseStatus;
import za.co.handyflow.platform.debtcollection.dto.DismissSuspenseRequest;
import za.co.handyflow.platform.debtcollection.dto.ResolveSuspenseRequest;
import za.co.handyflow.platform.debtcollection.dto.SuspenseItemResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Suspense queue — payments and replies a verified webhook delivered that could not be matched to exactly one case.
 * Allocating to a case runs the normal ledger/inbound path; dismissing needs a reason and is a supervisor action.
 */
@RestController
@RequestMapping("/api/v1/debtcollection/suspense")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Suspense", description = "Unmatched payments and replies awaiting a decision")
public class SuspenseController {

    private final SuspenseService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "List suspense items (default PENDING)")
    public ResponseEntity<ApiResponse<List<SuspenseItemResponse>>> list(@RequestParam(required = false) SuspenseStatus status) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                service.list(TenantContext.getTenantIdAsObject(), status).stream().map(SuspenseItemResponse::of).toList()));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Allocate the payment / attach the reply to a case (idempotent on the external reference)")
    public ResponseEntity<ApiResponse<SuspenseItemResponse>> resolve(@PathVariable UUID id,
                                                                     @Valid @RequestBody ResolveSuspenseRequest req) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Resolved", SuspenseItemResponse.of(service.resolve(
                TenantContext.getTenantIdAsObject(), id, req.caseId(), req.note(), TenantContext.getCurrentUserId(),
                TenantContext.getCurrentUserName()))));
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<SuspenseItemResponse>> dismiss(@PathVariable UUID id,
                                                                     @Valid @RequestBody DismissSuspenseRequest req) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success("Dismissed", SuspenseItemResponse.of(service.dismiss(
                TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId()))));
    }
}
