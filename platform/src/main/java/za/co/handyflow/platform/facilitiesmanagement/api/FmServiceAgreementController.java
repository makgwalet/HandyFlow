package za.co.handyflow.platform.facilitiesmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmServiceAgreementService;
import za.co.handyflow.platform.facilitiesmanagement.dto.CreateFmServiceAgreementRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmResolvedTermsResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmServiceAgreementResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpdateFmServiceAgreementRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.UUID;

/** Contracts: coverage, rate card and SLA targets. A contract manager holds FACILITIESMANAGEMENT_CONTRACT_MANAGE. All changes are audited. */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement/service-agreements")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Service Agreements", description = "Per-client contracts: RETAINER or TIME_AND_MATERIALS, site/category coverage, rate card and SLA targets")
public class FmServiceAgreementController {

    private static final String CAN_READ = "hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_CONTRACT_MANAGE','FACILITIESMANAGEMENT_SLA_MANAGE')";
    private static final String CAN_WRITE = "hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_CONTRACT_MANAGE')";

    private final FmServiceAgreementService agreementService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<Page<FmServiceAgreementResponse>>> getAgreements(
            @RequestParam UUID clientId, @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                agreementService.getAgreements(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @GetMapping("/resolve")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Which agreement, coverage, rate card and SLA would work at this client site, of this category, on this date fall under?")
    public ResponseEntity<ApiResponse<FmResolvedTermsResponse>> resolveTerms(
            @RequestParam UUID clientId, @RequestParam UUID siteId, @RequestParam String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                agreementService.previewTerms(TenantContext.getTenantIdAsObject(), clientId, siteId, category, date)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<FmServiceAgreementResponse>> getAgreement(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(agreementService.getAgreement(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Create an agreement. Rejected if it would cover the same sites and categories as another active agreement in overlapping dates.")
    public ResponseEntity<ApiResponse<FmServiceAgreementResponse>> createAgreement(@Valid @RequestBody CreateFmServiceAgreementRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Service agreement created",
                agreementService.createAgreement(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Update an agreement. Applies to work raised from now on; existing work orders keep the terms they were raised under.")
    public ResponseEntity<ApiResponse<FmServiceAgreementResponse>> updateAgreement(
            @PathVariable UUID id, @Valid @RequestBody UpdateFmServiceAgreementRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Service agreement updated",
                agreementService.updateAgreement(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize(CAN_WRITE)
    public ResponseEntity<ApiResponse<FmServiceAgreementResponse>> end(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Service agreement ended",
                agreementService.end(TenantContext.getTenantIdAsObject(), id)));
    }
}