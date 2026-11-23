// security/api/MonthlyPackEmailController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.MonthlyPackEmailService;
import za.co.handyflow.platform.security.application.internal.ReportEmailRules;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Security - Monthly service report email")
public class MonthlyPackEmailController {

    public record ContactRequest(@Size(max = 100) String name, @NotBlank @Size(max = 254) String email, @Size(max = 40) String phone) {}
    public record RecipientRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 254) String email) {}

    private final MonthlyPackEmailService service;
    private final FeatureGuard featureGuard;

    /** Optional so a hand-built controller still works; in the application it is always there. Limits a user to the sites in their scope. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;

    private void site(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }


    @GetMapping("/api/v1/security/report-settings")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "The monitored contact printed in every emailed monthly report (null until set)")
    public ResponseEntity<ApiResponse<ReportEmailRules.Contact>> contact() {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("OK", service.contact(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/api/v1/security/report-settings")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Set the monitored contact for report questions", description = "Required before any report can be emailed. Audited.")
    public ResponseEntity<ApiResponse<ReportEmailRules.Contact>> setContact(@Valid @RequestBody ContactRequest r) {
        featureGuard.requireModule("security");
        // The contact is printed on every site's report, so it is a whole-company setting.
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkUnrestricted(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("OK", service.setContact(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), r.name(), r.email(), r.phone())));
    }

    @GetMapping("/api/v1/security/sites/{siteId}/report-recipients")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Who receives this site's monthly service report")
    public ResponseEntity<ApiResponse<List<MonthlyPackEmailService.Recipient>>> list(@PathVariable UUID siteId) {
        featureGuard.requireModule("security");
        site(siteId);
        return ResponseEntity.ok(ApiResponse.success("OK", service.recipients(TenantContext.getTenantIdAsObject(), siteId)));
    }

    @PostMapping("/api/v1/security/sites/{siteId}/report-recipients")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Add a recipient (at most five per site)")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> add(@PathVariable UUID siteId, @Valid @RequestBody RecipientRequest r) {
        featureGuard.requireModule("security");
        site(siteId);
        UUID id = service.add(TenantContext.getTenantIdAsObject(), siteId, TenantContext.getCurrentUserId(), r.name(), r.email());
        return ResponseEntity.ok(ApiResponse.success("OK", Map.of("id", id)));
    }

    @DeleteMapping("/api/v1/security/sites/{siteId}/report-recipients/{recipientId}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Remove a recipient")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID siteId, @PathVariable UUID recipientId) {
        featureGuard.requireModule("security");
        site(siteId);
        service.remove(TenantContext.getTenantIdAsObject(), siteId, recipientId, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/api/v1/security/sites/{siteId}/monthly-pack/email")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Email a finished month's service report to the site's recipients",
            description = "One message per recipient. Queued, not confirmed delivered. Refused for a month still in progress, " +
                    "with no recipients, with no contact set, or if the same month was sent in the last ten minutes. Every send, and every refusal by the mail service, is audited.")
    public ResponseEntity<ApiResponse<MonthlyPackEmailService.Sent>> send(@PathVariable UUID siteId, @RequestParam(required = false) String month) {
        featureGuard.requireModule("security");
        site(siteId);
        return ResponseEntity.ok(ApiResponse.success("Queued", service.send(TenantContext.getTenantIdAsObject(), siteId, TenantContext.getCurrentUserId(), month)));
    }
}
