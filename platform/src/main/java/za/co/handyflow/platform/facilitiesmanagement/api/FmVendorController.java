package za.co.handyflow.platform.facilitiesmanagement.api;

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
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmVendorService;
import za.co.handyflow.platform.facilitiesmanagement.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilitiesmanagement/vendors")
@RequiredArgsConstructor
public class FmVendorController {

    private final FmVendorService vendorService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<Page<FmVendorResponse>>> getVendors(@PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(vendorService.getVendors(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmVendorResponse>> createVendor(@Valid @RequestBody UpsertFmVendorRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Vendor added",
                vendorService.createVendor(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmVendorResponse>> updateVendor(@PathVariable UUID id, @Valid @RequestBody UpsertFmVendorRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Vendor updated",
                vendorService.updateVendor(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmVendorResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Vendor deactivated",
                vendorService.deactivate(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmVendorResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Vendor reactivated",
                vendorService.reactivate(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Onboarding & compliance documents ────────────────────────────────────

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_VENDOR_APPROVE')")
    @io.swagger.v3.oas.annotations.Operation(summary = "Clear a vendor to receive work. Needs all required documents in force; the person who added the vendor cannot approve it unless ADMIN (audited)")
    public ResponseEntity<ApiResponse<FmVendorResponse>> approve(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Vendor approved", vendorService.approve(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_VENDOR_APPROVE')")
    public ResponseEntity<ApiResponse<FmVendorResponse>> suspend(@PathVariable UUID id, @Valid @RequestBody FmReasonRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Vendor suspended", vendorService.suspend(TenantContext.getTenantIdAsObject(), id, request.reason())));
    }

    @GetMapping("/{id}/compliance")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmVendorComplianceResponse>> compliance(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(vendorService.getCompliance(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<List<FmVendorDocumentResponse>>> documents(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(vendorService.getDocuments(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/documents")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmVendorDocumentResponse>> addDocument(@PathVariable UUID id, @Valid @RequestBody FmVendorDocumentRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document added",
                vendorService.addDocument(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/documents/{documentId}/revoke")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmVendorDocumentResponse>> revokeDocument(
            @PathVariable UUID id, @PathVariable UUID documentId, @Valid @RequestBody FmReasonRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Document revoked",
                vendorService.revokeDocument(TenantContext.getTenantIdAsObject(), id, documentId, request.reason())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteVendor(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        vendorService.deleteVendor(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Vendor deleted", null));
    }
}
