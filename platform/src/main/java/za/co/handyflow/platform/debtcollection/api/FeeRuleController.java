package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.FeeRuleService;
import za.co.handyflow.platform.debtcollection.domain.model.FeeRule;
import za.co.handyflow.platform.debtcollection.dto.CreateFeeRuleRequest;
import za.co.handyflow.platform.debtcollection.dto.FeeRuleReasonRequest;
import za.co.handyflow.platform.debtcollection.dto.FeeRuleResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Versioned fee rules. Nothing is pre-loaded: each tenant defines its own rules, with a mandatory legal-basis note,
 * and a second DEBTCOLLECTION_ADMIN must approve before a rule can be charged.
 */
@RestController
@RequestMapping("/api/v1/debtcollection/fee-rules")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Fee Rules", description = "Versioned, approved fee rules with limits")
public class FeeRuleController {

    private final FeeRuleService service;
    private final FeatureGuard featureGuard;

    @PostMapping
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Draft a fee rule (the next version if the code exists); not usable until approved")
    public ResponseEntity<ApiResponse<FeeRuleResponse>> draft(@Valid @RequestBody CreateFeeRuleRequest req) {
        featureGuard.requireModule("debtcollection");
        FeeRule r = service.draft(TenantContext.getTenantIdAsObject(), req.code(), req.description(), req.calcMethod(),
                req.fixedAmount(), req.percentOfOutstanding(), req.maxPerCase(), req.maxChargesPerMonth(),
                req.effectiveFrom(), req.effectiveTo(), req.legalBasisNote(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Fee rule drafted", FeeRuleResponse.of(r)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Approve (four-eyes: not the author); supersedes the previously approved version of the code")
    public ResponseEntity<ApiResponse<FeeRuleResponse>> approve(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        FeeRule r = service.approve(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(),
                TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Fee rule approved", FeeRuleResponse.of(r)));
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<FeeRuleResponse>> retire(@PathVariable UUID id, @Valid @RequestBody FeeRuleReasonRequest req) {
        featureGuard.requireModule("debtcollection");
        FeeRule r = service.retire(TenantContext.getTenantIdAsObject(), id, req.reason());
        return ResponseEntity.ok(ApiResponse.success("Fee rule retired", FeeRuleResponse.of(r)));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Every rule and version")
    public ResponseEntity<ApiResponse<List<FeeRuleResponse>>> list() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                service.listAll(TenantContext.getTenantIdAsObject()).stream().map(FeeRuleResponse::of).toList()));
    }

    @GetMapping("/approved")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Rules that can currently be charged (APPROVED)")
    public ResponseEntity<ApiResponse<List<FeeRuleResponse>>> approved() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                service.listApproved(TenantContext.getTenantIdAsObject()).stream().map(FeeRuleResponse::of).toList()));
    }
}
