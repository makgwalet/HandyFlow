package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.DeviceComplianceService;
import za.co.handyflow.platform.security.application.internal.SiteScopeService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

@Tag(name = "Security - Device compliance")
@RestController
@RequiredArgsConstructor
public class DeviceComplianceController {

    public record PolicyRequest(String minAppVersion, Integer minOsMajor, Boolean requireScreenLock, Boolean blockRooted) {}

    public record PostureRequest(@NotBlank String deviceHardwareId, String osVersion, Boolean screenLockOn, Boolean rooted) {}

    private final DeviceComplianceService service;
    private final FeatureGuard featureGuard;

    /** Optional so a hand-built controller still works; in the application it is always there. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SiteScopeService scope;

    /** The policy and the report cover every phone in the company, so a user limited to some sites does not get them. */
    private TenantId wholeCompany() {
        TenantId t = TenantContext.getTenantIdAsObject();
        SiteScopeService.checkUnrestricted(scope, t, TenantContext.getCurrentUserId());
        return t;
    }

    @GetMapping("/api/v1/security/devices/compliance-policy")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "What the company requires of a guard phone")
    public ResponseEntity<ApiResponse<DeviceComplianceService.PolicyView>> policy() {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("OK", service.policy(wholeCompany())));
    }

    @PutMapping("/api/v1/security/devices/compliance-policy")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Set what the company requires of a guard phone",
            description = "Empty or false means not required. Report only: no phone is blocked and no guard is signed out.")
    public ResponseEntity<ApiResponse<DeviceComplianceService.PolicyView>> savePolicy(@RequestBody PolicyRequest req) {
        featureGuard.requireModule("security");
        TenantId t = wholeCompany();
        return ResponseEntity.ok(ApiResponse.success("Saved", service.savePolicy(t, TenantContext.getCurrentUserId(),
                req.minAppVersion(), req.minOsMajor(), req.requireScreenLock(), req.blockRooted())));
    }

    @GetMapping("/api/v1/security/devices/compliance")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Active phones that fall short of the policy, or have not reported enough to tell")
    public ResponseEntity<ApiResponse<DeviceComplianceService.Report>> report() {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("OK", service.report(wholeCompany())));
    }

    @PostMapping("/api/v1/guard/me/device-posture")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "The phone reports its operating system, screen lock and root status",
            description = "Always the caller's own phone; a phone that belongs to another guard answers 404. Leave out what the app cannot tell: a missing value never overwrites a known one.")
    public ResponseEntity<ApiResponse<Void>> posture(@Valid @RequestBody PostureRequest req) {
        featureGuard.requireModule("security");
        service.reportPosture(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(),
                req.deviceHardwareId(), req.osVersion(), req.screenLockOn(), req.rooted());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
