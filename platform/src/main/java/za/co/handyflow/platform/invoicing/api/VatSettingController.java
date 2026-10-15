package za.co.handyflow.platform.invoicing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.invoicing.application.internal.VatSettingService;
import za.co.handyflow.platform.invoicing.dto.VatSettingRequest;
import za.co.handyflow.platform.invoicing.dto.VatSettingsOverview;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoicing/vat-settings")
@RequiredArgsConstructor
@Tag(name = "Company VAT setting")
public class VatSettingController {

    private final VatSettingService vat;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE', 'INVOICE_READ')")
    @Operation(summary = "The VAT treatment in force today, changes still to start, and past changes")
    public ResponseEntity<ApiResponse<VatSettingsOverview>> overview() {
        return ResponseEntity.ok(ApiResponse.success("VAT setting", vat.overview(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Change the company's VAT treatment from a date (today or later)")
    public ResponseEntity<ApiResponse<VatSettingsOverview>> change(@RequestBody VatSettingRequest request) {
        return ResponseEntity.ok(ApiResponse.success("VAT setting changed",
                vat.set(TenantContext.getTenantIdAsObject(), request, UserContext.getCurrentUserId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Cancel a VAT change that has not started yet")
    public ResponseEntity<ApiResponse<VatSettingsOverview>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("VAT change cancelled",
                vat.cancel(TenantContext.getTenantIdAsObject(), id, UserContext.getCurrentUserId())));
    }
}
