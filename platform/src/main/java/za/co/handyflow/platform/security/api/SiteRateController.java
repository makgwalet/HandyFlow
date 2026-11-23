package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.SiteRateService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Security - Site contract rates")
public class SiteRateController {

    public record AddRateRequest(@NotBlank String rateType, @NotNull Long amountCents, Boolean vatInclusive,
                                 @NotNull LocalDate effectiveFrom, LocalDate effectiveTo, @Size(max = 500) String note) {}
    public record EndRateRequest(@NotNull LocalDate lastDay) {}

    private final SiteRateService service;
    private final FeatureGuard featureGuard;

    /** Optional so a hand-built controller still works; in the application it is always there. Limits a user to the sites in their scope. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;

    private void site(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    @GetMapping("/api/v1/security/sites/{siteId}/rates")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "The contract rates of a site, newest first (what the client is charged)")
    public ResponseEntity<ApiResponse<List<SiteRateService.Row>>> list(@PathVariable UUID siteId) {
        featureGuard.requireModule("security");
        site(siteId);
        return ResponseEntity.ok(ApiResponse.success("OK", service.list(TenantContext.getTenantIdAsObject(), siteId)));
    }

    @PostMapping("/api/v1/security/sites/{siteId}/rates")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Add a contract rate (amount in cents). A new open-ended rate ends the current open-ended one the day before.",
            description = "rateType is HOURLY (cents per guard hour) or MONTHLY_FIXED (cents per month). Dates are calendar days in the company's time zone. "
                    + "Rates cannot overlap and are never deleted. Audited.")
    public ResponseEntity<ApiResponse<SiteRateService.Row>> add(@PathVariable UUID siteId, @Valid @RequestBody AddRateRequest r) {
        featureGuard.requireModule("security");
        site(siteId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("OK", service.add(TenantContext.getTenantIdAsObject(),
                TenantContext.getCurrentUserId(), siteId, r.rateType(), r.amountCents(), Boolean.TRUE.equals(r.vatInclusive()), r.effectiveFrom(), r.effectiveTo(), r.note())));
    }

    @PostMapping("/api/v1/security/sites/{siteId}/rates/{rateId}/end")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "End an open-ended rate on its last day")
    public ResponseEntity<ApiResponse<SiteRateService.Row>> end(@PathVariable UUID siteId, @PathVariable UUID rateId, @Valid @RequestBody EndRateRequest r) {
        featureGuard.requireModule("security");
        site(siteId);
        return ResponseEntity.ok(ApiResponse.success("OK", service.end(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId, rateId, r.lastDay())));
    }
}
