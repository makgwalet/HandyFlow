package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.PayPremiumService;
import za.co.handyflow.platform.security.application.internal.SiteScopeService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Security - Sunday and public-holiday pay")
@RestController
@RequestMapping("/api/v1/security/payroll/premiums")
@RequiredArgsConstructor
public class PayPremiumController {

    public record SaveRequest(boolean enabled, BigDecimal sundayMultiplier, BigDecimal holidayMultiplier) {}

    public record DayRequest(LocalDate date, String name) {}

    private final PayPremiumService service;
    private final SiteScopeService scope;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "The company's Sunday and public-holiday pay settings")
    public ResponseEntity<ApiResponse<PayPremiumService.SettingsView>> get() {
        TenantId t = TenantContext.getTenantIdAsObject();
        SiteScopeService.checkUnrestricted(scope, t, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(service.settingsView(t)));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Switch Sunday and public-holiday pay on or off and set the multipliers (each between 1.50 and 4.00)")
    public ResponseEntity<ApiResponse<PayPremiumService.SettingsView>> save(@RequestBody SaveRequest req) {
        TenantId t = TenantContext.getTenantIdAsObject();
        SiteScopeService.checkUnrestricted(scope, t, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(service.save(t, TenantContext.getCurrentUserId(), req.enabled(), req.sundayMultiplier(), req.holidayMultiplier())));
    }

    @GetMapping("/holidays")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "The company's public holidays in a year: national, added and taken off")
    public ResponseEntity<ApiResponse<List<PayPremiumService.HolidayView>>> holidays(@RequestParam int year) {
        TenantId t = TenantContext.getTenantIdAsObject();
        SiteScopeService.checkUnrestricted(scope, t, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(service.holidays(t, year)));
    }

    @PostMapping("/holidays")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Add a day the company treats as a public holiday, or put back a national day it had taken off")
    public ResponseEntity<ApiResponse<List<PayPremiumService.HolidayView>>> add(@RequestBody DayRequest req) {
        TenantId t = TenantContext.getTenantIdAsObject();
        SiteScopeService.checkUnrestricted(scope, t, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(service.add(t, TenantContext.getCurrentUserId(), req.date(), req.name())));
    }

    @DeleteMapping("/holidays")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Take a day off the company's public holidays")
    public ResponseEntity<ApiResponse<List<PayPremiumService.HolidayView>>> remove(@RequestParam LocalDate date) {
        TenantId t = TenantContext.getTenantIdAsObject();
        SiteScopeService.checkUnrestricted(scope, t, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(service.remove(t, TenantContext.getCurrentUserId(), date)));
    }
}
