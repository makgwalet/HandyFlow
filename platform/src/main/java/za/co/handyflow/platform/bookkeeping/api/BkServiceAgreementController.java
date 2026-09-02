package za.co.handyflow.platform.bookkeeping.api;

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
import za.co.handyflow.platform.bookkeeping.application.internal.BkServiceAgreementService;
import za.co.handyflow.platform.bookkeeping.dto.BkServiceAgreementResponse;
import za.co.handyflow.platform.bookkeeping.dto.CreateBkServiceAgreementRequest;
import za.co.handyflow.platform.bookkeeping.dto.UpdateBkServiceAgreementRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookkeeping/service-agreements")
@RequiredArgsConstructor
@Tag(name = "Bookkeeping - Service Agreements", description = "Per-client RETAINER or TIME_AND_MATERIALS commercial agreement, driving BkBillingService's billing decision")
public class BkServiceAgreementController {

    private final BkServiceAgreementService agreementService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<BkServiceAgreementResponse>>> getAgreements(
            @RequestParam UUID clientId, @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(
                agreementService.getAgreements(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkServiceAgreementResponse>> getAgreement(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(agreementService.getAgreement(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkServiceAgreementResponse>> createAgreement(@Valid @RequestBody CreateBkServiceAgreementRequest request) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Service agreement created",
                agreementService.createAgreement(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkServiceAgreementResponse>> updateAgreement(
            @PathVariable UUID id, @RequestBody UpdateBkServiceAgreementRequest request) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Service agreement updated",
                agreementService.updateAgreement(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkServiceAgreementResponse>> end(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Service agreement ended",
                agreementService.end(TenantContext.getTenantIdAsObject(), id)));
    }
}
