package za.co.handyflow.platform.insurance.api;

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
import za.co.handyflow.platform.insurance.application.internal.InsPolicyService;
import za.co.handyflow.platform.insurance.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Permission triplet {@code INSURANCE_READ}/{@code INSURANCE_MANAGE}/
 * {@code INSURANCE_ADMIN} — matching every other module's own convention
 * (confirmed directly against {@code LpMatterController}/
 * {@code TrainProvController}). Reads open to all three; writes and
 * lifecycle transitions require MANAGE or ADMIN — no operation here is
 * financially irreversible enough to warrant ADMIN-only gating the way
 * {@code TrainProvCertificateController.issue/revoke} or
 * {@code FacilityComplianceCertificateController.revoke} are, so MANAGE
 * covers everything (mirrors {@code LpMatterController}'s own gating,
 * which does not ADMIN-lock ordinary matter CRUD either).
 */
@RestController
@RequestMapping("/api/v1/insurance/policies")
@RequiredArgsConstructor
@Tag(name = "Insurance", description = "A tenant's own insurance policies on its business assets")
public class InsPolicyController {

    private final InsPolicyService policyService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('INSURANCE_READ','INSURANCE_MANAGE','INSURANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<InsPolicyResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String lineOfBusiness,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("insurance");
        return ResponseEntity.ok(ApiResponse.success(
                policyService.search(TenantContext.getTenantIdAsObject(), status, lineOfBusiness, search, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('INSURANCE_READ','INSURANCE_MANAGE','INSURANCE_ADMIN')")
    public ResponseEntity<ApiResponse<InsPolicyResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("insurance");
        return ResponseEntity.ok(ApiResponse.success(policyService.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/renewal-chain")
    @PreAuthorize("hasAnyAuthority('INSURANCE_READ','INSURANCE_MANAGE','INSURANCE_ADMIN')")
    @Operation(summary = "Every policy row linked to this one via renewalOfPolicyId — the full term-by-term history")
    public ResponseEntity<ApiResponse<List<InsPolicyResponse>>> renewalChain(@PathVariable UUID id) {
        featureGuard.requireModule("insurance");
        return ResponseEntity.ok(ApiResponse.success(policyService.renewalChain(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('INSURANCE_MANAGE','INSURANCE_ADMIN')")
    public ResponseEntity<ApiResponse<InsPolicyResponse>> create(@Valid @RequestBody CreateInsPolicyRequest req) {
        featureGuard.requireModule("insurance");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Policy created", policyService.create(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('INSURANCE_MANAGE','INSURANCE_ADMIN')")
    public ResponseEntity<ApiResponse<InsPolicyResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpdateInsPolicyRequest req) {
        featureGuard.requireModule("insurance");
        return ResponseEntity.ok(ApiResponse.success(
                "Policy updated", policyService.update(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('INSURANCE_MANAGE','INSURANCE_ADMIN')")
    public ResponseEntity<ApiResponse<InsPolicyResponse>> cancel(@PathVariable UUID id, @RequestBody(required = false) CancelInsPolicyRequest req) {
        featureGuard.requireModule("insurance");
        CancelInsPolicyRequest body = req != null ? req : new CancelInsPolicyRequest(null, null);
        return ResponseEntity.ok(ApiResponse.success(
                "Policy cancelled", policyService.cancel(TenantContext.getTenantIdAsObject(), id, body)));
    }

    @PostMapping("/{id}/lapse")
    @PreAuthorize("hasAnyAuthority('INSURANCE_MANAGE','INSURANCE_ADMIN')")
    public ResponseEntity<ApiResponse<InsPolicyResponse>> markLapsed(@PathVariable UUID id) {
        featureGuard.requireModule("insurance");
        return ResponseEntity.ok(ApiResponse.success(
                "Policy marked lapsed", policyService.markLapsed(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/reinstate")
    @PreAuthorize("hasAnyAuthority('INSURANCE_MANAGE','INSURANCE_ADMIN')")
    public ResponseEntity<ApiResponse<InsPolicyResponse>> reinstate(@PathVariable UUID id) {
        featureGuard.requireModule("insurance");
        return ResponseEntity.ok(ApiResponse.success(
                "Policy reinstated", policyService.reinstate(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/renew")
    @PreAuthorize("hasAnyAuthority('INSURANCE_MANAGE','INSURANCE_ADMIN')")
    public ResponseEntity<ApiResponse<InsPolicyResponse>> renew(@PathVariable UUID id, @Valid @RequestBody RenewInsPolicyRequest req) {
        featureGuard.requireModule("insurance");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Policy renewed", policyService.renew(TenantContext.getTenantIdAsObject(), id, req)));
    }
}
