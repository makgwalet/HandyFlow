package za.co.handyflow.platform.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.admin.application.internal.AdminAuthService;
import za.co.handyflow.platform.admin.application.internal.AdminService;
import za.co.handyflow.platform.admin.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
@Tag(name = "Admin Auth", description = "Superadmin authentication with TOTP 2FA")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AdminService        adminService;

    /*
     * Phase 8 NOTE: TENANT_SIGNED_UP notifications are emitted from the
     * tenant-facing RegistrationService / AuthService, not from here.
     * AdminAuthController only handles admin logins and impersonation.
     * See RegistrationService.registerTenant() — inject AdminNotificationService
     * there and call notifyTenantSignedUp() after the tenant row is committed.
     */

    @PostMapping("/login")
    @Operation(summary = "Step 1 — Password login. Returns partialToken if TOTP enabled, or TOTP_SETUP_REQUIRED if first login")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> login(
            @Valid @RequestBody AdminLoginRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAuthService.login(req, getIp(http))));
    }

    @PostMapping("/verify-totp")
    @Operation(summary = "Step 2 — Submit 6-digit TOTP code. Returns full 30-minute JWT on success")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> verifyTotp(
            @Valid @RequestBody AdminTotpRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAuthService.verifyTotp(req, getIp(http))));
    }

    @PostMapping("/totp/setup")
    @Operation(summary = "Generate TOTP secret for a new admin — call after password login when state=TOTP_SETUP_REQUIRED")
    public ResponseEntity<ApiResponse<AdminTotpSetupResponse>> setupTotp(
            @RequestParam UUID adminId) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAuthService.setupTotp(adminId)));
    }

    @PostMapping("/totp/confirm")
    @Operation(summary = "Confirm TOTP setup by entering first code from authenticator app")
    public ResponseEntity<ApiResponse<Void>> confirmTotp(
            @RequestParam UUID adminId,
            @Valid @RequestBody AdminTotpConfirmRequest req) {
        adminAuthService.confirmTotpSetup(adminId, req.code());
        return ResponseEntity.ok(ApiResponse.success(
                "TOTP enabled. Use Google Authenticator for all future logins.", null));
    }

    @PostMapping("/impersonate")
    @Operation(summary = "Generate a read-only 15-minute impersonation token for a tenant — logged in audit trail")
    public ResponseEntity<ApiResponse<String>> impersonate(
            @Valid @RequestBody ImpersonateRequest req,
            HttpServletRequest http) {
        UUID tenantId  = adminService.resolveTenantBySlug(req.tenantSlug());
        UUID adminId   = getAdminId();
        String adminEmail = getAdminEmail();

        String token = adminAuthService.impersonateTenant(
                adminId, adminEmail, tenantId,
                req.tenantSlug(), req.reason(), getIp(http));

        return ResponseEntity.ok(ApiResponse.success(
                "Impersonation token generated — expires in 15 minutes. Read-only.", token));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Phase 1a: AdminJwtFilter stores adminId (UUID string) as principal. */
    private UUID getAdminId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null)
            throw new za.co.handyflow.platform.shared.HandyFlowException(
                    "No admin context", org.springframework.http.HttpStatus.UNAUTHORIZED, "NO_CONTEXT");
        return UUID.fromString(auth.getPrincipal().toString());
    }

    /** Phase 1b/1d: AdminJwtFilter stores email in authentication.getDetails() Map. */
    @SuppressWarnings("unchecked")
    private String getAdminEmail() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof java.util.Map) {
            var details = (java.util.Map<String, String>) auth.getDetails();
            String email = details.get("email");
            if (email != null && !email.isBlank()) return email;
        }
        return "unknown-admin";
    }

    private String getIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
    }
}
