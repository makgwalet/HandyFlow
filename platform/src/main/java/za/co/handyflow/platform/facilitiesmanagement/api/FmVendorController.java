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
import za.co.handyflow.platform.facilitiesmanagement.dto.FmVendorResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpsertFmVendorRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteVendor(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        vendorService.deleteVendor(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Vendor deleted", null));
    }
}
