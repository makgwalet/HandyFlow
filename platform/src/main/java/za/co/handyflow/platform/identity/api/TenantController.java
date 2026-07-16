package za.co.handyflow.platform.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.application.internal.TenantService;
import za.co.handyflow.platform.identity.dto.UpdateTenantProfileRequest;
import za.co.handyflow.platform.identity.dto.UpdateBillingContactRequest;
import za.co.handyflow.platform.identity.dto.UploadLogoRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/identity/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant Profile", description = "Company profile, logo and billing details")
public class TenantController {

    private final TenantService tenantService;

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get current tenant company profile")
    public ResponseEntity<ApiResponse<TenantDetails>> getProfile() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                tenantService.getTenantDetails(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/me")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Update company profile — name, VAT, address, bank details")
    public ResponseEntity<ApiResponse<TenantDetails>> updateProfile(
            @Valid @RequestBody UpdateTenantProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated",
                tenantService.updateProfile(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/me/logo")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Upload company logo as base64 — appears on all PDFs")
    public ResponseEntity<ApiResponse<TenantDetails>> uploadLogo(
            @Valid @RequestBody UploadLogoRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Logo updated",
                tenantService.uploadLogo(TenantContext.getTenantIdAsObject(), req)));
    }

    // NEW: designate a dedicated billing contact, separate from the
    // tenant's own login/notification email — see
    // BillingRecipientResolver for how this is actually used to route
    // subscription invoices, receipts, and past-due notices away from
    // every active user and toward whoever the tenant admin actually
    // wants seeing that.
    @PutMapping("/me/billing-contact")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Set the dedicated billing contact for subscription/payment communications")
    public ResponseEntity<ApiResponse<TenantDetails>> updateBillingContact(
            @Valid @RequestBody UpdateBillingContactRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Billing contact updated",
                tenantService.updateBillingContact(TenantContext.getTenantIdAsObject(), req)));
    }
}