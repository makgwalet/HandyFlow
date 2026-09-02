package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalpractice.application.internal.LpRetainerAgreementService;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/legal-practice/retainer-agreements")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Retainer Agreements", description = "Client-level standing retainers")
public class LpRetainerAgreementController {

    private final LpRetainerAgreementService retainerService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<List<LpRetainerAgreementResponse>>> getForClient(@RequestParam UUID clientId) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                retainerService.listForClient(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpRetainerAgreementResponse>> getAgreement(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                retainerService.getAgreement(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpRetainerAgreementResponse>> createAgreement(
            @Valid @RequestBody CreateLpRetainerAgreementRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Retainer agreement created",
                retainerService.createAgreement(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpRetainerAgreementResponse>> updateAgreement(
            @PathVariable UUID id, @Valid @RequestBody UpdateLpRetainerAgreementRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Retainer agreement updated",
                retainerService.updateAgreement(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpRetainerAgreementResponse>> cancelAgreement(
            @PathVariable UUID id, @RequestBody(required = false) CancelRetainerRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Retainer agreement cancelled",
                retainerService.cancelAgreement(TenantContext.getTenantIdAsObject(), id,
                        req != null ? req : new CancelRetainerRequest(null))));
    }
}
