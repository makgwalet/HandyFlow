package za.co.handyflow.platform.insurancebrokerage.api;

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
import za.co.handyflow.platform.insurancebrokerage.application.internal.InsBrokPolicyService;
import za.co.handyflow.platform.insurancebrokerage.domain.model.InsBrokPolicy;
import za.co.handyflow.platform.insurancebrokerage.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Every write requires MANAGE or ADMIN — no ADMIN-only gating, same
 * choice {@code InsPolicyController}/{@code LpMatterController} already
 * made for their own equivalent writes (nothing here is as financially
 * irreversible as e.g. certificate issuance elsewhere in this codebase).
 */
@RestController
@RequestMapping("/api/v1/insurance-brokerage/policies")
@RequiredArgsConstructor
@Tag(name = "Insurance Brokerage - Policies", description = "Client-scoped policy lifecycle: quote -> bind -> active -> lapse/cancel/renew")
public class InsBrokPolicyController {

    private final InsBrokPolicyService policyService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_READ','INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<InsBrokPolicyResponse>>> search(
            @RequestParam(required = false) String status, @RequestParam(required = false) String lineOfBusiness,
            @RequestParam(required = false) String q, @PageableDefault(size = 24) Pageable pageable) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success(
                policyService.search(TenantContext.getTenantIdAsObject(), status, lineOfBusiness, q, pageable)
                        .map(this::toResponse)));
    }

    @GetMapping("/clients/{clientId}")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_READ','INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<InsBrokPolicyResponse>>> listForClient(@PathVariable UUID clientId,
            @PageableDefault(size = 24) Pageable pageable) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success(
                policyService.listForClient(TenantContext.getTenantIdAsObject(), clientId, pageable).map(this::toResponse)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_READ','INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokPolicyResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success(toResponse(policyService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @GetMapping("/{id}/renewal-chain")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_READ','INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<List<InsBrokPolicyResponse>>> renewalChain(@PathVariable UUID id) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success(
                policyService.renewalChain(TenantContext.getTenantIdAsObject(), id).stream().map(this::toResponse).toList()));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    @Operation(summary = "Creates a new policy as a QUOTE")
    public ResponseEntity<ApiResponse<InsBrokPolicyResponse>> create(@Valid @RequestBody CreateInsBrokPolicyRequest req) {
        featureGuard.requireModule("insurancebrokerage");
        InsBrokPolicy policy = policyService.createQuote(TenantContext.getTenantIdAsObject(), req.clientId(),
                req.insurerId(), req.quoteReference(), req.lineOfBusiness(), req.assetType(), req.assetReference(),
                req.sumInsured(), req.premiumAmount(), req.premiumFrequency(), req.excessAmount(),
                req.commissionRatePct(), req.startDate(), req.expiryDate(), req.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Quote created", toResponse(policy)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokPolicyResponse>> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateInsBrokPolicyRequest req) {
        featureGuard.requireModule("insurancebrokerage");
        InsBrokPolicy policy = policyService.update(TenantContext.getTenantIdAsObject(), id, req.insurerId(),
                req.lineOfBusiness(), req.assetType(), req.assetReference(), req.sumInsured(), req.premiumAmount(),
                req.premiumFrequency(), req.excessAmount(), req.commissionRatePct(), req.startDate(), req.expiryDate(),
                req.notes());
        return ResponseEntity.ok(ApiResponse.success("Policy updated", toResponse(policy)));
    }

    @PostMapping("/{id}/bind")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    @Operation(summary = "QUOTE -> BOUND: the insurer has accepted the risk and issued a real policy number")
    public ResponseEntity<ApiResponse<InsBrokPolicyResponse>> bind(@PathVariable UUID id,
            @Valid @RequestBody BindInsBrokPolicyRequest req) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success("Policy bound",
                toResponse(policyService.bind(TenantContext.getTenantIdAsObject(), id, req.policyNumber()))));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    @Operation(summary = "BOUND -> ACTIVE: cover has commenced. Also issues this term's commission invoice.")
    public ResponseEntity<ApiResponse<InsBrokPolicyResponse>> activate(@PathVariable UUID id) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success("Policy activated",
                toResponse(policyService.activate(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/{id}/lapse")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokPolicyResponse>> markLapsed(@PathVariable UUID id) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success("Policy marked lapsed",
                toResponse(policyService.markLapsed(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/{id}/reinstate")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokPolicyResponse>> reinstate(@PathVariable UUID id) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success("Policy reinstated",
                toResponse(policyService.reinstate(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokPolicyResponse>> cancel(@PathVariable UUID id,
            @Valid @RequestBody CancelInsBrokPolicyRequest req) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success("Policy cancelled",
                toResponse(policyService.cancel(TenantContext.getTenantIdAsObject(), id, req.reason()))));
    }

    @PostMapping("/{id}/renew")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    @Operation(summary = "Creates the next term directly ACTIVE, marks this row RENEWED, and issues the new term's commission invoice")
    public ResponseEntity<ApiResponse<InsBrokPolicyResponse>> renew(@PathVariable UUID id,
            @Valid @RequestBody RenewInsBrokPolicyRequest req) {
        featureGuard.requireModule("insurancebrokerage");
        InsBrokPolicy renewed = policyService.renew(TenantContext.getTenantIdAsObject(), id, req.policyNumber(),
                req.sumInsured(), req.premiumAmount(), req.startDate(), req.expiryDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Policy renewed", toResponse(renewed)));
    }

    private InsBrokPolicyResponse toResponse(InsBrokPolicy p) {
        return new InsBrokPolicyResponse(p.getId(), p.getClientId(), p.getInsurerId(), p.getPolicyNumber(),
                p.getQuoteReference(), p.getLineOfBusiness(), p.getAssetType(), p.getAssetReference(),
                p.getSumInsured(), p.getPremiumAmount(), p.getPremiumFrequency(), p.getExcessAmount(),
                p.getCommissionRatePct(), p.getStartDate(), p.getExpiryDate(), p.getStatus(), p.getBoundAt(),
                p.getActivatedAt(), p.getCancelledDate(), p.getCancelReason(), p.getRenewalOfPolicyId(), p.getNotes(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
