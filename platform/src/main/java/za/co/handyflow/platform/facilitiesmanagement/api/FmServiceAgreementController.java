package za.co.handyflow.platform.facilitiesmanagement.api;

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
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmServiceAgreementService;
import za.co.handyflow.platform.facilitiesmanagement.dto.CreateFmServiceAgreementRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmServiceAgreementResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpdateFmServiceAgreementRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilitiesmanagement/service-agreements")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Service Agreements", description = "Per-client RETAINER or TIME_AND_MATERIALS commercial agreement, driving FmBillingService's billing decision")
public class FmServiceAgreementController {

    private final FmServiceAgreementService agreementService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<Page<FmServiceAgreementResponse>>> getAgreements(
            @RequestParam UUID clientId, @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                agreementService.getAgreements(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmServiceAgreementResponse>> getAgreement(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(agreementService.getAgreement(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmServiceAgreementResponse>> createAgreement(@Valid @RequestBody CreateFmServiceAgreementRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Service agreement created",
                agreementService.createAgreement(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmServiceAgreementResponse>> updateAgreement(
            @PathVariable UUID id, @RequestBody UpdateFmServiceAgreementRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Service agreement updated",
                agreementService.updateAgreement(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmServiceAgreementResponse>> end(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Service agreement ended",
                agreementService.end(TenantContext.getTenantIdAsObject(), id)));
    }
}
